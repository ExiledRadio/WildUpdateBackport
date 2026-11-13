package com.exiledradio.wildupdatebackport.sculk;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/**
 * 1.19.2's {@code WardenSpawnTracker}: each player's warning level, 0 to 4.
 *
 * <p>A shriek a player caused raises the level of every player within 16 blocks of the shrieker to one above the
 * highest of theirs, unless a warden is within the 48-block cube around it or any of them is on the 200-tick
 * cooldown that each raise starts. The level falls by one every 12000 ticks without a warning.
 *
 * <p>1.19 keeps it on the player (and it starts again at 0 after death); here it is the compound
 * {@code wildupdatebackport:warden_spawn_tracker} in the player's Forge data, with 1.19's three fields, which
 * Forge also leaves behind on death.
 */
public final class WardenSpawnTracker {

    public static final int MAX_WARNING_LEVEL = 4;
    private static final double PLAYER_SEARCH_RADIUS = 16.0D;
    private static final double WARNING_CHECK_DIAMETER = 48.0D;
    private static final int DECREASE_WARNING_LEVEL_EVERY_INTERVAL = 12000;
    private static final int WARNING_LEVEL_INCREASE_COOLDOWN = 200;
    static final String KEY = "wildupdatebackport:warden_spawn_tracker";

    private int ticksSinceLastWarning;
    private int warningLevel;
    private int cooldownTicks;

    private WardenSpawnTracker() {
    }

    public static WardenSpawnTracker of(EntityPlayer player) {
        WardenSpawnTracker t = new WardenSpawnTracker();
        NBTTagCompound tag = player.getEntityData().getCompoundTag(KEY);
        t.ticksSinceLastWarning = Math.max(0, tag.getInteger("ticks_since_last_warning"));
        t.warningLevel = MathHelper.clamp(tag.getInteger("warning_level"), 0, MAX_WARNING_LEVEL);
        t.cooldownTicks = Math.max(0, tag.getInteger("cooldown_ticks"));
        return t;
    }

    public void save(EntityPlayer player) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("ticks_since_last_warning", this.ticksSinceLastWarning);
        tag.setInteger("warning_level", this.warningLevel);
        tag.setInteger("cooldown_ticks", this.cooldownTicks);
        player.getEntityData().setTag(KEY, tag);
    }

    /** The player's server tick. Players who were never warned keep no data. */
    public static void tick(EntityPlayer player) {
        if (!player.getEntityData().hasKey(KEY, 10)) {
            return;
        }
        WardenSpawnTracker t = of(player);
        if (t.ticksSinceLastWarning >= DECREASE_WARNING_LEVEL_EVERY_INTERVAL) {
            t.setWarningLevel(t.warningLevel - 1);
            t.ticksSinceLastWarning = 0;
        } else {
            t.ticksSinceLastWarning++;
        }
        if (t.cooldownTicks > 0) {
            t.cooldownTicks--;
        }
        t.save(player);
    }

    /** @return the new warning level, or empty when no warning was given */
    public static OptionalInt tryWarn(WorldServer world, BlockPos pos, EntityPlayerMP player) {
        if (hasNearbyWarden(world, pos)) {
            return OptionalInt.empty();
        }
        List<EntityPlayerMP> players = nearbyPlayers(world, pos);
        if (!players.contains(player)) {
            players.add(player);
        }
        List<WardenSpawnTracker> trackers = new ArrayList<WardenSpawnTracker>();
        WardenSpawnTracker max = null;
        for (EntityPlayerMP p : players) {
            WardenSpawnTracker t = of(p);
            if (t.onCooldown()) {
                return OptionalInt.empty();
            }
            trackers.add(t);
            if (max == null || t.warningLevel > max.warningLevel) {
                max = t;
            }
        }
        max.increaseWarningLevel();
        for (int i = 0; i < players.size(); i++) {
            WardenSpawnTracker t = trackers.get(i);
            t.copyData(max);
            t.save(players.get(i));
        }
        return OptionalInt.of(max.warningLevel);
    }

    private boolean onCooldown() {
        return this.cooldownTicks > 0;
    }

    private static boolean hasNearbyWarden(WorldServer world, BlockPos pos) {
        double h = WARNING_CHECK_DIAMETER / 2.0D;
        Vec3d c = new Vec3d(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
        AxisAlignedBB box = new AxisAlignedBB(c.x - h, c.y - h, c.z - h, c.x + h, c.y + h, c.z + h);
        for (Entity e : world.getEntitiesWithinAABB(Entity.class, box)) {
            if (BlockSculkSensor.isWarden(e)) {
                return true;
            }
        }
        return false;
    }

    private static List<EntityPlayerMP> nearbyPlayers(WorldServer world, BlockPos pos) {
        Vec3d c = new Vec3d(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
        List<EntityPlayerMP> out = new ArrayList<EntityPlayerMP>();
        for (EntityPlayer p : world.playerEntities) {
            if (p instanceof EntityPlayerMP && p.isEntityAlive() && !p.isSpectator()
                    && p.getPositionVector().squareDistanceTo(c) < PLAYER_SEARCH_RADIUS * PLAYER_SEARCH_RADIUS) {
                out.add((EntityPlayerMP) p);
            }
        }
        return out;
    }

    private void increaseWarningLevel() {
        if (!onCooldown()) {
            this.ticksSinceLastWarning = 0;
            this.cooldownTicks = WARNING_LEVEL_INCREASE_COOLDOWN;
            setWarningLevel(this.warningLevel + 1);
        }
    }

    public void setWarningLevel(int level) {
        this.warningLevel = MathHelper.clamp(level, 0, MAX_WARNING_LEVEL);
    }

    public int warningLevel() {
        return this.warningLevel;
    }

    public int cooldownTicks() {
        return this.cooldownTicks;
    }

    private void copyData(WardenSpawnTracker other) {
        this.warningLevel = other.warningLevel;
        this.cooldownTicks = other.cooldownTicks;
        this.ticksSinceLastWarning = other.ticksSinceLastWarning;
    }
}
