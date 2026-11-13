package com.exiledradio.wildupdatebackport.warden;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.WorldServer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * 1.19.2's {@code AngerManagement}: the warden's anger at each suspect, 0 to 150.
 *
 * <p>Ticked every 20 ticks: every suspect's anger drops by 1 and a suspect at 1 or that can no longer be targeted is
 * forgotten. A suspect that left (unloaded, or went to another dimension) is kept by its UUID and looked up again
 * every 2 ticks of this tick. Suspects are sorted angry first, then players first, then by anger; the warden is angry
 * at the first one that it can target. {@code highestAnger} is 1.19's own quirk: the sort records the largest anger
 * it compares, and a lone suspect's anger is taken as it is.
 */
public final class AngerManagement {

    public static final int CONVERSION_DELAY = 2;
    public static final int MAX_ANGER = 150;

    private int conversionDelay = new Random().nextInt(CONVERSION_DELAY + 1);
    int highestAnger;
    private final Predicate<Entity> filter;
    final List<Entity> suspects = new ArrayList<Entity>();
    final Map<Entity, Integer> angerBySuspect = new HashMap<Entity, Integer>();
    final Map<UUID, Integer> angerByUuid = new LinkedHashMap<UUID, Integer>();
    private final Comparator<Entity> sorter = this::compare;

    public AngerManagement(Predicate<Entity> filter) {
        this.filter = filter;
    }

    /** @param stillTargetable the warden's {@code canTargetEntity} */
    public void tick(WorldServer world, Predicate<Entity> stillTargetable) {
        if (--this.conversionDelay <= 0) {
            convertFromUuids(world);
            this.conversionDelay = CONVERSION_DELAY;
        }
        for (Iterator<Map.Entry<UUID, Integer>> it = this.angerByUuid.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Integer> e = it.next();
            if (e.getValue() <= 1) {
                it.remove();
            } else {
                e.setValue(e.getValue() - 1);
            }
        }
        for (Iterator<Map.Entry<Entity, Integer>> it = this.angerBySuspect.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Entity, Integer> e = it.next();
            int anger = e.getValue();
            Entity suspect = e.getKey();
            boolean left = hasLeft(suspect, world);
            if (anger > 1 && stillTargetable.test(suspect) && !left) {
                e.setValue(anger - 1);
            } else {
                this.suspects.remove(suspect);
                it.remove();
                if (anger > 1 && left) {
                    this.angerByUuid.put(suspect.getUniqueID(), anger - 1);
                }
            }
        }
        sortAndUpdateHighestAnger();
    }

    /** 1.19's unloaded or changed-dimension removal: gone from this world but not killed. */
    private static boolean hasLeft(Entity e, WorldServer world) {
        boolean killed = e instanceof EntityLivingBase ? ((EntityLivingBase) e).getHealth() <= 0.0F : e.isDead && e.world == world;
        if (killed) {
            return false;
        }
        return e.world != world || e.isDead || !e.isAddedToWorld();
    }

    private void sortAndUpdateHighestAnger() {
        this.highestAnger = 0;
        this.suspects.sort(this.sorter);
        if (this.suspects.size() == 1) {
            this.highestAnger = anger(this.suspects.get(0));
        }
    }

    private void convertFromUuids(WorldServer world) {
        for (Iterator<Map.Entry<UUID, Integer>> it = this.angerByUuid.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Integer> e = it.next();
            Entity found = world.getEntityFromUuid(e.getKey());
            if (found != null) {
                this.angerBySuspect.put(found, e.getValue());
                this.suspects.add(found);
                it.remove();
            }
        }
    }

    /** @return the suspect's anger after the increase, at most 150 (plus any anger it had while away) */
    public int increaseAnger(Entity suspect, int amount) {
        boolean isNew = !this.angerBySuspect.containsKey(suspect);
        Integer old = this.angerBySuspect.get(suspect);
        int anger = Math.min(MAX_ANGER, (old == null ? 0 : old) + amount);
        this.angerBySuspect.put(suspect, anger);
        if (isNew) {
            Integer away = this.angerByUuid.remove(suspect.getUniqueID());
            anger += away == null ? 0 : away;
            this.angerBySuspect.put(suspect, anger);
            this.suspects.add(suspect);
        }
        sortAndUpdateHighestAnger();
        return anger;
    }

    public void clearAnger(Entity suspect) {
        this.angerBySuspect.remove(suspect);
        this.suspects.remove(suspect);
        sortAndUpdateHighestAnger();
    }

    @Nullable
    private Entity getTopSuspect() {
        for (Entity e : this.suspects) {
            if (this.filter.test(e)) {
                return e;
            }
        }
        return null;
    }

    /** @return the anger at this entity, or with none the highest anger (1.19's {@code getActiveAnger}) */
    public int getActiveAnger(@Nullable Entity target) {
        return target == null ? this.highestAnger : anger(target);
    }

    @Nullable
    public EntityLivingBase getActiveEntity() {
        Entity top = getTopSuspect();
        return top instanceof EntityLivingBase ? (EntityLivingBase) top : null;
    }

    public int anger(Entity e) {
        Integer a = this.angerBySuspect.get(e);
        return a == null ? 0 : a;
    }

    private int compare(Entity a, Entity b) {
        if (a.equals(b)) {
            return 0;
        }
        int x = anger(a);
        int y = anger(b);
        this.highestAnger = Math.max(this.highestAnger, Math.max(x, y));
        boolean angryA = AngerLevel.byAnger(x).isAngry();
        boolean angryB = AngerLevel.byAnger(y).isAngry();
        if (angryA != angryB) {
            return angryA ? -1 : 1;
        }
        boolean playerA = a instanceof EntityPlayer;
        boolean playerB = b instanceof EntityPlayer;
        if (playerA != playerB) {
            return playerA ? -1 : 1;
        }
        return Integer.compare(y, x);
    }

    // --- saving (1.19's "anger": {"suspects": [{uuid, anger}]}) -------------------------------------------------

    public NBTTagCompound write() {
        NBTTagList list = new NBTTagList();
        for (Entity e : this.suspects) {
            list.appendTag(pair(e.getUniqueID(), anger(e)));
        }
        for (Map.Entry<UUID, Integer> e : this.angerByUuid.entrySet()) {
            list.appendTag(pair(e.getKey(), e.getValue()));
        }
        NBTTagCompound tag = new NBTTagCompound();
        tag.setTag("suspects", list);
        return tag;
    }

    private static NBTTagCompound pair(UUID id, int anger) {
        NBTTagCompound t = new NBTTagCompound();
        t.setUniqueId("uuid", id);
        t.setInteger("anger", anger);
        return t;
    }

    public void read(NBTTagCompound tag) {
        this.suspects.clear();
        this.angerBySuspect.clear();
        this.angerByUuid.clear();
        NBTTagList list = tag.getTagList("suspects", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound t = list.getCompoundTagAt(i);
            if (t.hasUniqueId("uuid")) {
                this.angerByUuid.put(t.getUniqueId("uuid"), Math.max(0, t.getInteger("anger")));
            }
        }
    }
}
