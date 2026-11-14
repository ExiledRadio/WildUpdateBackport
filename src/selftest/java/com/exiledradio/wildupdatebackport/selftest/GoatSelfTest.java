package com.exiledradio.wildupdatebackport.selftest;

import com.exiledradio.wildupdatebackport.WildUpdateBackport;
import com.exiledradio.wildupdatebackport.common.Owners;
import com.exiledradio.wildupdatebackport.goat.EntityGoat;
import com.exiledradio.wildupdatebackport.goat.GoatContent;
import com.exiledradio.wildupdatebackport.items.GoatHorns;
import com.exiledradio.wildupdatebackport.items.Instrument;
import com.exiledradio.wildupdatebackport.items.ItemsContent;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.EntityRegistry;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Stream Gt: our copy of Caves &amp; Cliffs' goat (registered only without it), and goat horns from either goat.
 *
 * <p>Without Caves &amp; Cliffs: the copy is registered as the owner's goat is (name, id 25, sounds, spawning, the
 * boat advancement), saves the owner's tags, is milked and bred as the owner's. With it: nothing of ours is
 * registered, and the owner's goat reads a saved copy's tags as its own (the in-place remap). Both ways: a goat
 * that rams a {@code #snaps_goat_horn} block loses a horn, which drops as our goat horn.
 */
public final class GoatSelfTest implements SelfTest {

    static final ResourceLocation OUR_GOAT = new ResourceLocation(WildUpdateBackport.MOD_ID, "goat");
    static final ResourceLocation CC_GOAT = new ResourceLocation(Owners.CAVES_AND_CLIFFS, "goat");

    @Override
    public String name() {
        return "goats";
    }

    @Override
    public void run(Check c) throws Exception {
        boolean cc = Owners.present(Owners.CAVES_AND_CLIFFS);
        WildUpdateBackport.LOGGER.info("SELFTEST goats: Caves & Cliffs {}", cc ? "installed" : "absent");
        WorldServer world = c.world;
        EntityEntry ours = ForgeRegistries.ENTITIES.getValue(OUR_GOAT);
        boolean advancement = world.getMinecraftServer().getAdvancementManager()
                .getAdvancement(new ResourceLocation(WildUpdateBackport.MOD_ID, GoatContent.BOAT_ADVANCEMENT)) != null;
        boolean sound = ForgeRegistries.SOUND_EVENTS.containsKey(new ResourceLocation(WildUpdateBackport.MOD_ID, "entity.goat.ambient"));
        if (cc) {
            // In a world that had our goat, our name is now an alias of the owner's (Caves & Cliffs' remap).
            c.ok((ours == null || ours.getEntityClass() != EntityGoat.class) && !GoatContent.on(), "with Caves & Cliffs our goat is not registered");
            c.ok(ForgeRegistries.ENTITIES.getValue(CC_GOAT) != null, "Caves & Cliffs' goat is");
            c.ok(!advancement, "nor is our copy of the boat advancement");
            c.ok(!sound, "nor our goat sounds");
            remapInPlace(c);
        } else {
            ours(c, ours, advancement);
        }

        // Horns from whichever goat this game has.
        ResourceLocation goatName = cc ? CC_GOAT : OUR_GOAT;
        if (ItemsContent.goatHorn() == null) {
            c.fail("goat horns are switched off; the horn half of this test needs them");
            return;
        }
        horns(c, goatName, false);
        horns(c, goatName, true);
    }

    // --- our copy ------------------------------------------------------------------------------

    private static void ours(Check c, EntityEntry ours, boolean advancement) {
        if (!c.ok(ours != null && GoatContent.on(), "without Caves & Cliffs our goat is registered")) {
            return;
        }
        c.eq(EntityGoat.class, ours.getEntityClass(), "as the copied EntityGoat");
        EntityRegistry.EntityRegistration reg = EntityRegistry.instance().lookupModSpawn(EntityGoat.class, false);
        c.ok(reg != null && reg.getModEntityId() == GoatContent.GOAT_ENTITY_ID, "with entity id 25 (plan: 25-29)");
        c.ok(reg != null && reg.getTrackingRange() == 80 && reg.getUpdateFrequency() == 3 && reg.sendsVelocityUpdates(),
                "tracked as the owner's: 80 blocks, every 3 ticks, with velocity");
        c.ok(EntityList.ENTITY_EGGS.containsKey(OUR_GOAT), "it has a spawn egg");
        c.ok(advancement, "the boat advancement is ours while the copy is registered");
        int sounds = 0;
        for (ResourceLocation k : ForgeRegistries.SOUND_EVENTS.getKeys()) {
            if (k.getNamespace().equals(WildUpdateBackport.MOD_ID) && k.getPath().startsWith("entity.goat.") && !k.getPath().equals("entity.goat.horn_break")) {
                sounds++;
            }
        }
        c.eq(17, sounds, "the owner's 17 goat sound events, in our namespace");

        boolean spawns = false;
        for (Biome.SpawnListEntry e : Biomes.EXTREME_HILLS.getSpawnableList(net.minecraft.entity.EnumCreatureType.CREATURE)) {
            if (e.entityClass == EntityGoat.class) {
                spawns = e.itemWeight == 10 && e.minGroupCount == 1 && e.maxGroupCount == 3;
            }
        }
        c.ok(spawns, "goats spawn in extreme hills, weight 10, groups of 1-3, as the owner's");

        WorldServer world = c.world;
        BlockPos a = c.area().add(8, 1, 8);
        EntityGoat goat = new EntityGoat(world);
        goat.setLocationAndAngles(a.getX() + 0.5D, a.getY(), a.getZ() + 0.5D, 0.0F, 0.0F);
        goat.onInitialSpawn(world.getDifficultyForLocation(a), null);
        world.spawnEntity(goat);
        c.ok(Math.abs(goat.width - 0.9F) < 1.0E-4F && Math.abs(goat.height - 1.3F) < 1.0E-4F, "0.9 x 1.3, as the owner's");
        c.ok(goat.hasLeftHorn() && goat.hasRightHorn(), "a new goat has both horns");

        // Saved tags: the owner's names.
        goat.setScreamingGoat(true);
        goat.setHorns(false, true);
        goat.setRamCooldown(321);
        NBTTagCompound tag = new NBTTagCompound();
        goat.writeEntityToNBT(tag);
        c.ok(tag.getBoolean("IsScreamingGoat") && !tag.getBoolean("HasLeftHorn") && tag.getBoolean("HasRightHorn"),
                "saved with the owner's IsScreamingGoat / HasLeftHorn / HasRightHorn");
        c.eq(321, tag.getCompoundTag("Brain").getCompoundTag("memories").getCompoundTag("minecraft:ram_cooldown_ticks").getInteger("value"),
                "and its ram cooldown where the owner's brain keeps it");
        EntityGoat back = new EntityGoat(world);
        back.readEntityFromNBT(tag);
        c.ok(back.isScreamingGoat() && !back.hasLeftHorn() && back.hasRightHorn() && back.getRamCooldown() == 321, "and read back");

        // Milk.
        FakePlayer player = c.player("goatmilk", a.east(2));
        goat.setScreamingGoat(false);
        player.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.BUCKET));
        c.ok(goat.processInteract(player, EnumHand.MAIN_HAND), "a bucket milks the goat");
        c.eq(Items.MILK_BUCKET, player.getHeldItem(EnumHand.MAIN_HAND).getItem(), "into a milk bucket");
        c.ok(goat.isBreedingItem(new ItemStack(Items.WHEAT)), "goats breed with wheat");
        EntityGoat kid = goat.createChild(new EntityGoat(world));
        c.ok(kid != null && kid.getClass() == EntityGoat.class, "and have goat kids");
        goat.setDead();
    }

    // --- with Caves & Cliffs: its goat reads ours ----------------------------------------------

    private static void remapInPlace(Check c) {
        WorldServer world = c.world;
        EntityGoat copy = new EntityGoat(world);
        copy.setScreamingGoat(true);
        copy.setHorns(true, false);
        copy.setRamCooldown(222);
        copy.setJumpCooldown(111);
        NBTTagCompound tag = new NBTTagCompound();
        copy.writeEntityToNBT(tag);
        Entity owner = EntityList.createEntityByIDFromName(CC_GOAT, world);
        if (!c.ok(owner instanceof EntityLiving, "Caves & Cliffs' goat can be made")) {
            return;
        }
        owner.readFromNBT(fullTag(copy, tag));
        try {
            c.ok((Boolean) call(owner, "isScreamingGoat"), "Caves & Cliffs' goat reads our IsScreamingGoat");
            c.ok((Boolean) call(owner, "hasLeftHorn") && !(Boolean) call(owner, "hasRightHorn"), "and our horn tags");
            c.eq(222, call(owner, "getRamCooldown"), "and our ram cooldown");
            c.eq(111, call(owner, "getJumpCooldown"), "and our long jump cooldown");
        } catch (ReflectiveOperationException e) {
            c.fail("Caves & Cliffs' goat has no " + e.getMessage());
        }
        boolean remap = false;
        try {
            Class<?> content = Class.forName("com.exiledradio.cavesandcliffsbackport.goat.GoatContent");
            for (Method m : content.getMethods()) {
                remap |= m.getName().equals("remapCopy");
            }
        } catch (ClassNotFoundException e) {
            c.fail("no Caves & Cliffs GoatContent");
        }
        c.ok(remap, "Caves & Cliffs remaps wildupdatebackport:goat onto its own goat (GoatContent.remapCopy)");
    }

    /** Our goat's whole save (position, health, the goat's own tags) for another entity to read. */
    private static NBTTagCompound fullTag(EntityGoat goat, NBTTagCompound own) {
        NBTTagCompound all = goat.writeToNBT(new NBTTagCompound());
        all.merge(own);
        return all;
    }

    static Object call(Object target, String name, Object... args) throws ReflectiveOperationException {
        for (Class<?> k = target.getClass(); k != null; k = k.getSuperclass()) {
            for (Method m : k.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == args.length) {
                    m.setAccessible(true);
                    return m.invoke(target, args);
                }
            }
        }
        throw new NoSuchMethodException(name);
    }

    // --- goat horns from rams ------------------------------------------------------------------

    /** A goat in a fresh area, charging east with its head lowered at a block one ahead of it. */
    private static EntityLiving charging(Check c, ResourceLocation name, boolean screaming, BlockPos at) throws ReflectiveOperationException {
        EntityLiving goat = (EntityLiving) EntityList.createEntityByIDFromName(name, c.world);
        goat.setLocationAndAngles(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, -90.0F, 0.0F);
        c.world.spawnEntity(goat);
        call(goat, "setScreamingGoat", screaming);
        call(goat, "setRamCooldown", 0);
        aim(goat);
        return goat;
    }

    private static void aim(EntityLiving goat) throws ReflectiveOperationException {
        call(goat, "setLoweringHead", true);
        goat.motionX = 0.6D;
        goat.motionZ = 0.0D;
    }

    private static void tick(EntityLiving goat) {
        new GoatHorns.Events().update(new LivingEvent.LivingUpdateEvent(goat));
    }

    private static List<EntityItem> horns(Check c, EntityLiving goat) {
        AxisAlignedBB box = goat.getEntityBoundingBox().grow(3.0D);
        List<EntityItem> items = c.world.getEntitiesWithinAABB(EntityItem.class, box);
        items.removeIf(i -> i.isDead || i.getItem().getItem() != ItemsContent.goatHorn());
        return items;
    }

    private static void horns(Check c, ResourceLocation name, boolean screaming) throws ReflectiveOperationException {
        String who = name + (screaming ? " (screaming)" : "");
        BlockPos at = c.area().add(8, 1, 8);
        c.world.setBlockState(at.east(), Blocks.STONE.getDefaultState(), 2);
        EntityLiving goat = charging(c, name, screaming, at);
        tick(goat);
        boolean left = (Boolean) call(goat, "hasLeftHorn"), right = (Boolean) call(goat, "hasRightHorn");
        c.ok(left != right, who + " ramming stone loses one horn");
        List<EntityItem> dropped = horns(c, goat);
        c.eq(1, dropped.size(), who + " which drops as a goat horn");
        if (!dropped.isEmpty()) {
            Instrument i = Instrument.of(dropped.get(0).getItem());
            c.ok(i != null && (i.ordinal() >= 4) == screaming, who + " a " + (screaming ? "screaming" : "regular") + " instrument (" + i + ")");
            c.eq(Instrument.forGoat(screaming, goat.getUniqueID()), i, who + " the goat's own, seeded by its UUID");
            dropped.get(0).setDead();
        }
        c.ok(!(Boolean) call(goat, "isLoweringHead"), who + " the ram ends: head up");
        c.ok((Integer) call(goat, "getRamCooldown") > 0, who + " with a cooldown");

        // The second horn, then none: the ram still ends, nothing drops.
        aim(goat);
        tick(goat);
        c.ok(!(Boolean) call(goat, "hasLeftHorn") && !(Boolean) call(goat, "hasRightHorn"), who + " a second ram takes the other horn");
        c.eq(1, horns(c, goat).size(), who + " which drops too");
        for (EntityItem e : horns(c, goat)) {
            e.setDead();
        }
        aim(goat);
        tick(goat);
        c.eq(0, horns(c, goat).size(), who + " a hornless goat drops nothing");
        c.ok(!(Boolean) call(goat, "isLoweringHead"), who + " and still ends its ram");

        // Planks snap nothing; a mob in the goat's way is rammed instead.
        call(goat, "setHorns", true, true);
        c.world.setBlockState(at.east(), Blocks.PLANKS.getDefaultState(), 2);
        aim(goat);
        tick(goat);
        c.ok((Boolean) call(goat, "hasLeftHorn") && (Boolean) call(goat, "hasRightHorn") && (Boolean) call(goat, "isLoweringHead"),
                who + " planks snap no horn and the charge goes on");
        c.world.setBlockState(at.east(), Blocks.LOG.getDefaultState(), 2);
        EntityArmorStand stand = new EntityArmorStand(c.world, goat.posX, goat.posY, goat.posZ);
        c.world.spawnEntity(stand);
        tick(goat);
        c.ok((Boolean) call(goat, "hasLeftHorn") && (Boolean) call(goat, "hasRightHorn"), who + " with a mob in its way it rams the mob, not the log");
        stand.setDead();
        tick(goat);
        c.ok(!((Boolean) call(goat, "hasLeftHorn") && (Boolean) call(goat, "hasRightHorn")), who + " a log snaps a horn");
        for (EntityItem e : horns(c, goat)) {
            e.setDead();
        }
        goat.setDead();
    }
}
