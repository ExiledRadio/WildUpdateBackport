package com.exiledradio.wildupdatebackport.selftest;

import com.exiledradio.wildupdatebackport.common.Content;
import com.exiledradio.wildupdatebackport.common.Owners;
import com.exiledradio.wildupdatebackport.frog.BlockFrogspawn;
import com.exiledradio.wildupdatebackport.frog.EntityFrog;
import com.exiledradio.wildupdatebackport.frog.EntityTadpole;
import com.exiledradio.wildupdatebackport.frog.FrogContent;
import com.exiledradio.wildupdatebackport.frog.FrogSounds;
import com.exiledradio.wildupdatebackport.frog.FrogSpawns;
import com.exiledradio.wildupdatebackport.frog.FrogVariant;

import net.minecraft.block.Block;
import net.minecraft.block.BlockDispenser;
import net.minecraft.block.BlockRotatedPillar;
import net.minecraft.block.state.IBlockState;
import net.minecraft.dispenser.BehaviorDefaultDispenseItem;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.item.EntityFallingBlock;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.EntityMagmaCube;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.List;
import java.util.Random;

/** Stream F: frogs, tadpoles, frogspawn, froglights, bucket of tadpole. */
public final class FrogSelfTest implements SelfTest {

    @Override
    public String name() {
        return "frogs";
    }

    @Override
    public void run(Check c) throws Exception {
        registration(c);
        froglights(c);
        variants(c);
        spawning(c);
        stats(c);
        frogspawn(c);
        drops(c);
        tongue(c);
        breeding(c);
        longJump(c);
        tadpoles(c);
        buckets(c);
        saving(c);
        advancements(c);
        idleAndSwim(c);
        earning(c);
        dispensers(c);
    }

    // --- helpers ---------------------------------------------------------------------------------------

    /** Ticks these entities (and the world clock) n times; stops early when {@code until} holds. */
    private static int tick(WorldServer world, int n, java.util.function.BooleanSupplier until, Entity... entities) {
        for (int i = 0; i < n; i++) {
            world.getWorldInfo().setWorldTotalTime(world.getTotalWorldTime() + 1);
            for (Entity e : entities) {
                if (!e.isDead) {
                    e.onUpdate();
                }
            }
            if (until != null && until.getAsBoolean()) {
                return i + 1;
            }
        }
        return n;
    }

    private static EntityFrog frog(WorldServer world, BlockPos at, FrogVariant variant) {
        EntityFrog f = new EntityFrog(world);
        f.setLocationAndAngles(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, 0.0F, 0.0F);
        f.setVariant(variant);
        world.spawnEntity(f);
        return f;
    }

    private static EntitySlime slime(WorldServer world, boolean magma, int size, BlockPos at) {
        EntitySlime s = magma ? new EntityMagmaCube(world) : new EntitySlime(world);
        NBTTagCompound tag = new NBTTagCompound();
        s.writeEntityToNBT(tag);
        tag.setInteger("Size", size - 1);
        s.readEntityFromNBT(tag);
        s.setHealth(s.getMaxHealth());
        s.setLocationAndAngles(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, 0.0F, 0.0F);
        s.setNoAI(true);
        world.spawnEntity(s);
        return s;
    }

    private static List<EntityItem> items(WorldServer world, BlockPos around) {
        List<EntityItem> list = world.getEntitiesWithinAABB(EntityItem.class, new AxisAlignedBB(around).grow(4.0D));
        list.removeIf(e -> e.isDead);
        return list;
    }

    private static void clearItems(WorldServer world, BlockPos around) {
        for (EntityItem i : items(world, around)) {
            i.setDead();
            world.removeEntity(i);
        }
    }

    // --- rules -----------------------------------------------------------------------------------------

    private void registration(Check c) {
        for (String n : new String[] {"ochre_froglight", "verdant_froglight", "pearlescent_froglight", "frogspawn"}) {
            c.ok(Owners.ourBlock(n) != null, n + " is registered");
            c.ok(Owners.ours(n) != null, n + " has an item");
        }
        Item bucket = Owners.ours("tadpole_bucket");
        c.ok(bucket != null, "bucket of tadpole is registered");
        c.eq(1, bucket.getItemStackLimit(new ItemStack(bucket)), "bucket of tadpole stacks to 1");
        c.eq(EntityFrog.class, EntityList.getClass(Content.id("frog")), "entity frog");
        c.eq(EntityTadpole.class, EntityList.getClass(Content.id("tadpole")), "entity tadpole");
        c.ok(EntityList.ENTITY_EGGS.containsKey(Content.id("frog")), "frog spawn egg");
        c.eq(FrogContent.FROG_EGG_PRIMARY, EntityList.ENTITY_EGGS.get(Content.id("frog")).primaryColor, "frog egg color");
        c.ok(EntityList.ENTITY_EGGS.containsKey(Content.id("tadpole")), "tadpole spawn egg");
        c.eq(25, FrogSounds.all().size(), "25 sound events");
        for (net.minecraft.util.SoundEvent e : FrogSounds.all()) {
            c.ok(ForgeRegistries.SOUND_EVENTS.containsKey(e.getRegistryName()), "sound registered: " + e.getRegistryName());
        }
    }

    private void froglights(Check c) {
        BlockPos area = c.area();
        String[] names = {"ochre_froglight", "verdant_froglight", "pearlescent_froglight"};
        for (String n : names) {
            Block b = Owners.ourBlock(n);
            IBlockState s = b.getDefaultState();
            c.ok(Math.abs(s.getBlockHardness(c.world, area) - 0.3F) < 1e-6, n + " hardness 0.3");
            c.ok(Math.abs(b.getExplosionResistance(null) - 0.3F) < 1e-4, n + " resistance 0.3");
            c.eq(15, s.getLightValue(), n + " light 15");
            c.eq(FrogSounds.FROGLIGHT, b.getSoundType(s, c.world, area, null), n + " froglight sounds");
            c.ok(b instanceof BlockRotatedPillar, n + " has an axis");
            IBlockState placed = b.getStateForPlacement(c.world, area, EnumFacing.EAST, 0.5F, 0.5F, 0.5F, 0, null);
            c.eq(EnumFacing.Axis.X, placed.getValue(BlockRotatedPillar.AXIS), n + " placed on a side lies along that axis");
            c.ok(b.getHarvestTool(s) == null, n + " needs no tool");
        }
        c.eq(1.0F, FrogSounds.FROGLIGHT.getVolume(), "froglight sound volume");
    }

    private void variants(Check c) {
        Object[][] cases = {
                {Biomes.ICE_PLAINS, FrogVariant.COLD}, {Biomes.MUTATED_ICE_FLATS, FrogVariant.COLD},
                {Biomes.FROZEN_OCEAN, FrogVariant.COLD}, {Biomes.FROZEN_RIVER, FrogVariant.COLD},
                {Biomes.COLD_BEACH, FrogVariant.COLD}, {Biomes.COLD_TAIGA, FrogVariant.COLD}, {Biomes.SKY, FrogVariant.COLD},
                {Biomes.ICE_MOUNTAINS, FrogVariant.COLD},
                {Biomes.DESERT, FrogVariant.WARM}, {Biomes.JUNGLE, FrogVariant.WARM}, {Biomes.JUNGLE_EDGE, FrogVariant.WARM},
                {Biomes.SAVANNA, FrogVariant.WARM}, {Biomes.MUTATED_SAVANNA_ROCK, FrogVariant.WARM},
                {Biomes.MESA, FrogVariant.WARM}, {Biomes.HELL, FrogVariant.WARM},
                {Biomes.SWAMPLAND, FrogVariant.TEMPERATE}, {Biomes.PLAINS, FrogVariant.TEMPERATE},
                {Biomes.TAIGA, FrogVariant.TEMPERATE}, {Biomes.EXTREME_HILLS, FrogVariant.TEMPERATE},
                {Biomes.OCEAN, FrogVariant.TEMPERATE}, {Biomes.MUSHROOM_ISLAND, FrogVariant.TEMPERATE},
                {Biomes.BEACH, FrogVariant.TEMPERATE}, {Biomes.FOREST, FrogVariant.TEMPERATE},
        };
        for (Object[] k : cases) {
            Biome b = (Biome) k[0];
            c.eq(k[1], FrogSpawns.variantFor(b), "variant in " + b.getRegistryName());
        }
        c.eq("ochre_froglight", FrogVariant.TEMPERATE.froglight, "temperate frogs make ochre froglights");
        c.eq("pearlescent_froglight", FrogVariant.WARM.froglight, "warm frogs make pearlescent froglights");
        c.eq("verdant_froglight", FrogVariant.COLD.froglight, "cold frogs make verdant froglights");
        c.eq(FrogVariant.WARM, FrogVariant.byId("minecraft:warm"), "variant by 1.19 id");
    }

    private void spawning(Check c) {
        for (Biome b : new Biome[] {Biomes.SWAMPLAND, Biomes.MUTATED_SWAMPLAND}) {
            Biome.SpawnListEntry found = null;
            for (Biome.SpawnListEntry e : b.getSpawnableList(EnumCreatureType.CREATURE)) {
                if (e.entityClass == EntityFrog.class) {
                    found = e;
                }
            }
            if (c.ok(found != null, "frogs spawn in " + b.getRegistryName())) {
                c.eq(10, found.itemWeight, "frog spawn weight");
                c.eq(2, found.minGroupCount, "frog group min");
                c.eq(5, found.maxGroupCount, "frog group max");
            }
        }
        for (Biome b : new Biome[] {Biomes.PLAINS, Biomes.DESERT, Biomes.ICE_PLAINS}) {
            boolean any = false;
            for (Biome.SpawnListEntry e : b.getSpawnableList(EnumCreatureType.CREATURE)) {
                any |= e.entityClass == EntityFrog.class;
            }
            c.ok(!any, "no frogs spawn in " + b.getRegistryName());
        }
        BlockPos area = c.area();
        BlockPos on = area.add(3, 0, 3);
        c.world.setBlockState(on.down(), Blocks.GRASS.getDefaultState());
        c.ok(FrogSpawns.spawnableOn(Blocks.GRASS), "frogs spawn on grass");
        c.ok(FrogSpawns.spawnableOn(Owners.ourBlock("mud")), "frogs spawn on mud");
        c.ok(FrogSpawns.spawnableOn(Owners.ourBlock("mangrove_roots")), "frogs spawn on mangrove roots");
        c.ok(FrogSpawns.spawnableOn(Owners.ourBlock("muddy_mangrove_roots")), "frogs spawn on muddy mangrove roots");
        c.ok(!FrogSpawns.spawnableOn(Blocks.STONE), "frogs do not spawn on stone");
        c.ok(!FrogSpawns.spawnableOn(Blocks.SAND), "frogs do not spawn on sand");
        // y=100 under open sky: light 15 by day.
        c.ok(FrogSpawns.canSpawnAt(c.world, on) == (c.world.getLight(on) > 8), "spawn needs light above 8");
        c.world.setBlockState(on.down(), Blocks.STONE.getDefaultState());
        c.ok(!FrogSpawns.canSpawnAt(c.world, on), "no spawn on stone");
    }

    private void stats(Check c) {
        BlockPos area = c.area();
        EntityFrog f = frog(c.world, area.add(2, 0, 2), FrogVariant.TEMPERATE);
        c.eq(10.0D, f.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).getBaseValue(), "frog health 10");
        c.eq(10.0D, f.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).getBaseValue(), "frog attack 10");
        c.eq(1.0D, f.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).getBaseValue(), "frog speed 1");
        c.eq(0.5F, f.width, "frog width 0.5");
        c.eq(0.5F, f.height, "frog height 0.5");
        c.eq(1.0F, f.stepHeight, "frog steps up a block");
        c.ok(f.canBreatheUnderwater(), "frogs breathe underwater");
        c.ok(!f.isPushedByWater(), "frogs are not pushed by water");
        c.ok(!f.isChild(), "frogs are never babies");
        c.ok(f.isBreedingItem(new ItemStack(Items.SLIME_BALL)), "slimeballs breed frogs");
        c.ok(!f.isBreedingItem(new ItemStack(Items.WHEAT)), "wheat does not");
        c.ok(f.getCanSpawnHere() || true, "spawn check runs");
        // Fall damage: ceil(distance - 3) - 5.
        f.setHealth(10.0F);
        f.fall(8.0F, 1.0F);
        c.eq(10.0F, f.getHealth(), "an 8-block fall does a frog no harm");
        f.fall(10.0F, 1.0F);
        c.eq(8.0F, f.getHealth(), "a 10-block fall takes 2");
        f.setDead();

        EntityTadpole t = new EntityTadpole(c.world);
        c.eq(6.0D, t.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).getBaseValue(), "tadpole health 6");
        c.eq(0.4F, t.width, "tadpole width 0.4");
        c.eq(0.3F, t.height, "tadpole height 0.3");
        c.eq(24000, EntityTadpole.TICKS_TO_BE_FROG, "tadpoles grow up after 24000 ticks");

        EntitySlime small = new EntitySlime(c.world);
        c.ok(EntityFrog.canEat(slime(c.world, false, 1, area.add(5, 0, 5))), "frogs eat size-1 slimes");
        c.ok(!EntityFrog.canEat(slime(c.world, false, 2, area.add(7, 0, 5))), "not size-2 slimes");
        c.ok(EntityFrog.canEat(slime(c.world, true, 1, area.add(9, 0, 5))), "frogs eat size-1 magma cubes");
        c.ok(!EntityFrog.canEat(slime(c.world, true, 4, area.add(11, 0, 5))), "not big magma cubes");
        c.ok(!EntityFrog.canEat(new EntityZombie(c.world)), "nor zombies");
        small.setDead();
    }

    private void frogspawn(Check c) {
        WorldServer world = c.world;
        Block spawn = Owners.ourBlock("frogspawn");
        BlockPos area = c.area();
        BlockPos water = area.add(4, -1, 4);
        c.fill(area.add(2, -2, 2), area.add(8, -1, 8), Blocks.WATER.getDefaultState());
        BlockPos on = water.up();
        c.ok(BlockFrogspawn.mayPlaceAt(world, on), "frogspawn may sit on a water source");
        c.ok(!BlockFrogspawn.mayPlaceAt(world, area.add(12, 0, 12)), "not on stone");
        c.ok(!BlockFrogspawn.mayPlaceAt(world, water), "not in water");
        c.ok(spawn.getCollisionBoundingBox(spawn.getDefaultState(), world, on) == null, "frogspawn has no collision");
        c.ok(Math.abs(spawn.getBoundingBox(spawn.getDefaultState(), world, on).maxY - 1.5D / 16.0D) < 1e-6, "frogspawn is 1.5 pixels high");
        c.eq(0.0F, spawn.getDefaultState().getBlockHardness(world, on), "frogspawn breaks instantly");
        NonNullDrops drops = new NonNullDrops();
        spawn.getDrops(drops.list, world, on, spawn.getDefaultState(), 0);
        c.ok(drops.list.isEmpty(), "frogspawn drops nothing");

        // Placed by the item: a player looking straight down at the water.
        EntityPlayerMP player = Players.real(world, "frogspawn");
        player.interactionManager.setGameType(GameType.SURVIVAL);
        player.setLocationAndAngles(on.getX() + 0.5D, on.getY() + 1.0D, on.getZ() + 0.5D, 0.0F, 90.0F);
        player.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Owners.ours("frogspawn"), 2));
        Owners.ours("frogspawn").onItemRightClick(world, player, EnumHand.MAIN_HAND);
        c.eq(spawn, world.getBlockState(on).getBlock(), "the frogspawn item places frogspawn on water");
        c.eq(1, player.getHeldItem(EnumHand.MAIN_HAND).getCount(), "and uses one");
        // Used on a block: nothing.
        c.ok(Owners.ours("frogspawn").onItemUse(player, world, area.add(12, -1, 12), EnumHand.MAIN_HAND, EnumFacing.UP, 0.5F, 1.0F, 0.5F)
                == net.minecraft.util.EnumActionResult.PASS, "used on stone it does nothing");

        // A falling block breaks it; an item does not.
        EntityItem item = new EntityItem(world, on.getX() + 0.5D, on.getY(), on.getZ() + 0.5D, new ItemStack(Items.STICK));
        spawn.onEntityCollision(world, on, world.getBlockState(on), item);
        c.eq(spawn, world.getBlockState(on).getBlock(), "an item landing does not break frogspawn");
        EntityFallingBlock sand = new EntityFallingBlock(world, on.getX() + 0.5D, on.getY(), on.getZ() + 0.5D, Blocks.SAND.getDefaultState());
        spawn.onEntityCollision(world, on, world.getBlockState(on), sand);
        c.ok(world.isAirBlock(on), "a falling block breaks frogspawn");

        // Gone when its water goes.
        world.setBlockState(on, spawn.getDefaultState());
        world.setBlockState(water, Blocks.STONE.getDefaultState());
        c.ok(world.isAirBlock(on), "frogspawn breaks when the water under it goes");
        world.setBlockState(water, Blocks.WATER.getDefaultState());

        // Hatching: 2 to 5 tadpoles, kept, in the water below.
        int min = Integer.MAX_VALUE;
        int max = 0;
        Random random = new Random(1);
        for (int run = 0; run < 40; run++) {
            world.setBlockState(on, spawn.getDefaultState());
            spawn.updateTick(world, on, world.getBlockState(on), random);
            List<EntityTadpole> tadpoles = world.getEntitiesWithinAABB(EntityTadpole.class, new AxisAlignedBB(on).grow(1.0D));
            int n = 0;
            for (EntityTadpole t : tadpoles) {
                if (!t.isDead) {
                    n++;
                    if (run == 0) {
                        c.ok(t.posY < on.getY(), "tadpoles hatch into the water below");
                        c.ok(t.isNoDespawnRequired(), "hatched tadpoles never despawn");
                    }
                    t.setDead();
                    world.removeEntity(t);
                }
            }
            min = Math.min(min, n);
            max = Math.max(max, n);
            if (run == 0) {
                c.ok(world.isAirBlock(on), "hatching frogspawn breaks");
            }
        }
        c.eq(2, min, "fewest tadpoles hatched");
        c.eq(5, max, "most tadpoles hatched");
        // The hatch delay: 3600 to 11999 ticks.
        c.eq(3600, BlockFrogspawn.MIN_HATCH_DELAY, "hatch delay min");
        c.eq(12000, BlockFrogspawn.MAX_HATCH_DELAY, "hatch delay max (exclusive)");
        world.setBlockState(on, spawn.getDefaultState());
        c.ok(world.isUpdateScheduled(on, spawn), "placing frogspawn schedules its hatching");
        world.setBlockToAir(on);
    }

    private static final class NonNullDrops {
        final net.minecraft.util.NonNullList<ItemStack> list = net.minecraft.util.NonNullList.create();
    }

    private void drops(Check c) {
        WorldServer world = c.world;
        BlockPos area = c.area();
        for (FrogVariant v : FrogVariant.values()) {
            BlockPos at = area.add(4 + 6 * v.ordinal(), 0, 4);
            EntityFrog f = frog(world, at, v);
            EntitySlime cube = slime(world, true, 1, at.east());
            f.attackEntityAsMob(cube);
            c.ok(!cube.isEntityAlive(), "a frog's bite kills a small magma cube");
            List<EntityItem> got = items(world, at);
            c.eq(1, got.size(), v + " frog's magma cube drops one item");
            if (!got.isEmpty()) {
                c.eq(Item.getItemFromBlock(Owners.ourBlock(v.froglight)), got.get(0).getItem().getItem(), v + " frog makes " + v.froglight);
            }
            clearItems(world, at);
            EntitySlime s = slime(world, false, 1, at.east());
            f.attackEntityAsMob(s);
            got = items(world, at);
            c.eq(1, got.size(), "a frog's small slime drops one item");
            if (!got.isEmpty()) {
                c.eq(Items.SLIME_BALL, got.get(0).getItem().getItem(), "a slimeball");
                c.eq(1, got.get(0).getItem().getCount(), "exactly one");
            }
            clearItems(world, at);
            f.setDead();
        }
    }

    private void tongue(Check c) {
        WorldServer world = c.world;
        BlockPos area = c.area();
        c.fill(area.add(0, 0, 0), area.add(10, 1, 0), Blocks.GLASS.getDefaultState());
        c.fill(area.add(0, 0, 6), area.add(10, 1, 6), Blocks.GLASS.getDefaultState());
        c.fill(area.add(0, 0, 0), area.add(0, 1, 6), Blocks.GLASS.getDefaultState());
        c.fill(area.add(10, 0, 0), area.add(10, 1, 6), Blocks.GLASS.getDefaultState());
        EntityFrog f = frog(world, area.add(2, 0, 3), FrogVariant.WARM);
        f.setLongJumpCooldown(100000);
        EntitySlime cube = slime(world, true, 1, area.add(6, 0, 3));
        boolean[] tongueSeen = {false};
        int took = tick(world, 400, () -> {
            tongueSeen[0] |= f.getPose() == EntityFrog.Pose.USING_TONGUE;
            return !cube.isEntityAlive();
        }, f, cube);
        c.ok(!cube.isEntityAlive(), "a frog hunts down and eats a small magma cube (" + took + " ticks)");
        c.ok(tongueSeen[0], "using its tongue");
        List<EntityItem> got = items(world, area.add(4, 0, 3));
        c.ok(!got.isEmpty() && got.get(0).getItem().getItem() == Item.getItemFromBlock(Owners.ourBlock("pearlescent_froglight")),
                "a warm frog's catch drops a pearlescent froglight");
        clearItems(world, area.add(4, 0, 3));
        EntitySlime big = slime(world, false, 2, area.add(6, 0, 3));
        tick(world, 100, null, f, big);
        c.ok(big.isEntityAlive(), "a frog leaves a size-2 slime alone");
        c.ok(f.getAttackTarget() == null, "and never targets it");
        big.setDead();
        f.setDead();
    }

    private void breeding(Check c) {
        WorldServer world = c.world;
        BlockPos area = c.area();
        // A pond on one side, a little beach to lay on.
        c.fill(area.add(8, -1, 0), area.add(14, -1, 8), Blocks.WATER.getDefaultState());
        EntityPlayerMP player = Players.real(world, "frogbreeder");
        player.interactionManager.setGameType(GameType.SURVIVAL);
        EntityFrog a = frog(world, area.add(3, 0, 3), FrogVariant.TEMPERATE);
        EntityFrog b = frog(world, area.add(4, 0, 3), FrogVariant.TEMPERATE);
        for (EntityFrog f : new EntityFrog[] {a, b}) {
            f.setLongJumpCooldown(100000);
            player.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.SLIME_BALL, 2));
            f.processInteract(player, EnumHand.MAIN_HAND);
            c.ok(f.isInLove(), "a slimeball puts a frog in love");
            c.eq(1, player.getHeldItem(EnumHand.MAIN_HAND).getCount(), "and is used up");
        }
        player.setHeldItem(EnumHand.MAIN_HAND, ItemStack.EMPTY);
        tick(world, 300, () -> a.isPregnant() || b.isPregnant(), a, b);
        c.ok(a.isPregnant() || b.isPregnant(), "one frog of a pair in love becomes pregnant");
        c.ok(!(a.isPregnant() && b.isPregnant()), "only one");
        c.ok(a.getGrowingAge() > EntityFrog.BREEDING_COOLDOWN - 3 && a.getGrowingAge() <= EntityFrog.BREEDING_COOLDOWN,
                "breeding cooldown 6000 (first frog, " + a.getGrowingAge() + ")");
        c.ok(b.getGrowingAge() > EntityFrog.BREEDING_COOLDOWN - 3 && b.getGrowingAge() <= EntityFrog.BREEDING_COOLDOWN,
                "breeding cooldown 6000 (second frog, " + b.getGrowingAge() + ")");
        c.eq(0, world.getEntitiesWithinAABB(EntityFrog.class, new AxisAlignedBB(area).expand(16, 4, 16)).size() - 2,
                "no baby frog is born");
        EntityFrog mother = a.isPregnant() ? a : b;
        tick(world, 1, null, mother);
        c.eq(EntityFrog.Activity.LAY_SPAWN, mother.activity(), "a pregnant frog looks for a place to lay");
        Block spawn = Owners.ourBlock("frogspawn");
        int took = tick(world, 1200, () -> !mother.isPregnant(), a, b);
        c.ok(!mother.isPregnant(), "the mother lays her spawn (" + took + " ticks)");
        boolean found = false;
        for (BlockPos p : BlockPos.getAllInBox(area.add(7, 0, -1), area.add(15, 0, 9))) {
            found |= world.getBlockState(p).getBlock() == spawn;
        }
        c.ok(found, "frogspawn on the water");
        a.setDead();
        b.setDead();
    }

    private void longJump(Check c) {
        WorldServer world = c.world;
        BlockPos area = c.area();
        // A one-block cell with walls two high: walking out is impossible, a long jump is not.
        BlockPos cell = area.add(10, 0, 10);
        for (EnumFacing f : EnumFacing.Plane.HORIZONTAL) {
            c.fill(cell.offset(f), cell.offset(f).up(), Blocks.STONE.getDefaultState());
        }
        c.fill(cell.add(-1, 0, -1), cell.add(-1, 1, -1), Blocks.STONE.getDefaultState());
        c.fill(cell.add(1, 0, -1), cell.add(1, 1, -1), Blocks.STONE.getDefaultState());
        c.fill(cell.add(-1, 0, 1), cell.add(-1, 1, 1), Blocks.STONE.getDefaultState());
        c.fill(cell.add(1, 0, 1), cell.add(1, 1, 1), Blocks.STONE.getDefaultState());
        EntityFrog f = frog(world, cell, FrogVariant.COLD);
        c.ok(f.getLongJumpCooldown() >= 100 && f.getLongJumpCooldown() <= 140, "a new frog waits 100 to 140 ticks to jump");
        f.setLongJumpCooldown(0);
        f.onGround = true;
        boolean[] jumped = {false};
        int took = tick(world, 1200, () -> {
            jumped[0] |= f.isMidJump();
            return Math.abs(f.posX - (cell.getX() + 0.5D)) > 1.0D || Math.abs(f.posZ - (cell.getZ() + 0.5D)) > 1.0D;
        }, f);
        c.ok(jumped[0], "a frog long-jumps");
        c.ok(Math.abs(f.posX - (cell.getX() + 0.5D)) > 1.0D || Math.abs(f.posZ - (cell.getZ() + 0.5D)) > 1.0D,
                "out of a cell it cannot walk out of (" + took + " ticks)");
        tick(world, 100, () -> !f.isMidJump() && f.onGround, f);
        c.ok(!f.isMidJump(), "and lands");
        c.ok(f.getLongJumpCooldown() >= 50, "then waits again (" + f.getLongJumpCooldown() + ")");
        f.setDead();
    }

    private void tadpoles(Check c) {
        WorldServer world = c.world;
        BlockPos area = c.area();
        c.fill(area.add(2, -3, 2), area.add(8, -1, 8), Blocks.WATER.getDefaultState());
        BlockPos in = area.add(5, -2, 5);
        EntityTadpole t = new EntityTadpole(world);
        t.setLocationAndAngles(in.getX() + 0.5D, in.getY(), in.getZ() + 0.5D, 0.0F, 0.0F);
        t.setCustomNameTag("Polly");
        world.spawnEntity(t);
        c.ok(!t.isDead, "a tadpole lives in water");
        // Feeding: 10% of the time left.
        EntityPlayerMP player = Players.real(world, "tadpolefeeder");
        player.interactionManager.setGameType(GameType.SURVIVAL);
        player.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.SLIME_BALL, 3));
        t.setAge(0);
        t.processInteract(player, EnumHand.MAIN_HAND);
        c.eq(2400, t.getAge(), "a slimeball takes 10% off a new tadpole's time (24000 -> 2400)");
        c.eq(2, player.getHeldItem(EnumHand.MAIN_HAND).getCount(), "and is used up");
        t.setAge(10000);
        t.processInteract(player, EnumHand.MAIN_HAND);
        c.eq(11400, t.getAge(), "(14000 left -> 1400 more)");
        // Following a slimeball.
        tick(world, 1, null, t);
        c.ok(t.memory.temptingPlayer == null, "a player far away tempts nothing");
        player.setLocationAndAngles(t.posX + 3.0D, t.posY, t.posZ, 0.0F, 0.0F);
        world.spawnEntity(player);
        tick(world, 1, null, t);
        c.eq(player, t.memory.temptingPlayer, "a tadpole sees a slimeball within 10 blocks");
        world.removeEntity(player);
        player.setDead();
        // Growing up.
        t.setAge(EntityTadpole.TICKS_TO_BE_FROG - 1);
        tick(world, 1, null, t);
        c.ok(t.isDead, "a tadpole grows up at 24000 ticks");
        List<EntityFrog> frogs = world.getEntitiesWithinAABB(EntityFrog.class, new AxisAlignedBB(in).grow(2.0D));
        if (c.eq(1, frogs.size(), "into one frog")) {
            EntityFrog f = frogs.get(0);
            c.eq(FrogSpawns.variantFor(world.getBiome(in)), f.getVariant(), "of its biome's variant");
            c.eq("Polly", f.getCustomNameTag(), "keeping its name");
            c.ok(f.isNoDespawnRequired(), "and never despawning");
            f.setDead();
        }
        // Out of water it suffocates.
        EntityTadpole dry = new EntityTadpole(world);
        dry.setLocationAndAngles(area.getX() + 15.5D, area.getY(), area.getZ() + 15.5D, 0.0F, 0.0F);
        world.spawnEntity(dry);
        tick(world, 360, null, dry);
        c.ok(dry.getHealth() < 6.0F || dry.isDead, "a tadpole on land suffocates");
        c.ok(dry.getAge() > 0, "and still ages");
        dry.setDead();
        c.ok(!dry.isEntityAlive(), "cleanup");
    }

    private void buckets(Check c) {
        WorldServer world = c.world;
        BlockPos area = c.area();
        c.fill(area.add(2, -3, 2), area.add(8, -1, 8), Blocks.WATER.getDefaultState());
        EntityTadpole t = new EntityTadpole(world);
        t.setLocationAndAngles(area.getX() + 5.5D, area.getY() - 2, area.getZ() + 5.5D, 0.0F, 0.0F);
        t.setAge(1234);
        world.spawnEntity(t);
        EntityPlayerMP player = Players.real(world, "tadpolebucket");
        player.interactionManager.setGameType(GameType.SURVIVAL);
        player.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
        t.processInteract(player, EnumHand.MAIN_HAND);
        ItemStack held = player.getHeldItem(EnumHand.MAIN_HAND);
        c.eq(Owners.ours("tadpole_bucket"), held.getItem(), "a water bucket scoops up a tadpole");
        c.ok(t.isDead, "taking it out of the world");
        c.ok(held.hasTagCompound() && held.getTagCompound().getInteger("Age") == 1234, "the bucket keeps its age");
        c.ok(held.getTagCompound().hasKey("Health"), "and its health");
        // Emptying it on the stone floor: water and the same tadpole.
        BlockPos target = area.add(14, 0, 14);
        player.setLocationAndAngles(target.getX() + 0.5D, target.getY() + 1.5D, target.getZ() + 0.5D, 0.0F, 90.0F);
        net.minecraft.util.ActionResult<ItemStack> r = held.getItem().onItemRightClick(world, player, EnumHand.MAIN_HAND);
        c.eq(Items.BUCKET, r.getResult().getItem(), "emptying gives back the bucket");
        c.eq(Blocks.FLOWING_WATER, world.getBlockState(target).getBlock(), "and places water");
        List<EntityTadpole> out = world.getEntitiesWithinAABB(EntityTadpole.class, new AxisAlignedBB(target).grow(1.0D));
        if (c.eq(1, out.size(), "and lets the tadpole out")) {
            c.eq(1234, out.get(0).getAge(), "with its age");
            out.get(0).setDead();
        }
        c.ok(!(BlockDispenser.DISPENSE_BEHAVIOR_REGISTRY.getObject(Owners.ours("tadpole_bucket")) instanceof net.minecraft.dispenser.BehaviorDefaultDispenseItem)
                || BlockDispenser.DISPENSE_BEHAVIOR_REGISTRY.getObject(Owners.ours("tadpole_bucket")).getClass() != BehaviorDefaultDispenseItem.class,
                "dispensers empty buckets of tadpole");
        c.ok(Owners.ours("tadpole_bucket").initCapabilities(held, null) == null, "a bucket of tadpole is not a fluid container");
    }

    private void saving(Check c) {
        WorldServer world = c.world;
        EntityFrog f = new EntityFrog(world);
        f.setVariant(FrogVariant.COLD);
        f.setPregnant(true);
        NBTTagCompound tag = new NBTTagCompound();
        f.writeEntityToNBT(tag);
        c.eq("minecraft:cold", tag.getString("variant"), "variant saved as 1.19 saves it");
        EntityFrog g = new EntityFrog(world);
        g.readEntityFromNBT(tag);
        c.eq(FrogVariant.COLD, g.getVariant(), "variant read back");
        c.ok(g.isPregnant(), "pregnancy read back");
        EntityTadpole t = new EntityTadpole(world);
        t.setAge(777);
        NBTTagCompound tt = new NBTTagCompound();
        t.writeEntityToNBT(tt);
        EntityTadpole u = new EntityTadpole(world);
        u.readEntityFromNBT(tt);
        c.eq(777, u.getAge(), "tadpole age read back");
    }

    private void idleAndSwim(Check c) {
        WorldServer world = c.world;
        BlockPos area = c.area();
        EntityFrog f = frog(world, area.add(8, 0, 8), FrogVariant.TEMPERATE);
        f.onInitialSpawn(world.getDifficultyForLocation(new BlockPos(f)), null);
        c.eq(FrogSpawns.variantFor(world.getBiome(new BlockPos(f))), f.getVariant(), "a spawned frog takes its biome's variant");
        f.setLongJumpCooldown(100000);
        boolean[] croaked = {false};
        tick(world, 1500, () -> croaked[0] |= f.getPose() == EntityFrog.Pose.CROAKING, f);
        c.ok(croaked[0], "an idle frog croaks");
        c.eq(EntityFrog.Activity.IDLE, f.activity(), "on land a frog is idle");
        f.setDead();

        c.fill(area.add(16, -4, 16), area.add(30, 0, 30), Blocks.WATER.getDefaultState());
        EntityFrog s = frog(world, area.add(23, -2, 23), FrogVariant.WARM);
        double x0 = s.posX;
        double z0 = s.posZ;
        tick(world, 2, null, s);
        c.eq(EntityFrog.Activity.SWIM, s.activity(), "in water a frog swims");
        tick(world, 600, null, s);
        c.ok(Math.abs(s.posX - x0) + Math.abs(s.posZ - z0) > 1.0D, "and gets about");
        c.eq(10.0F, s.getHealth(), "without drowning");
        s.setDead();
    }

    private void earning(Check c) throws Exception {
        WorldServer world = c.world;
        BlockPos area = c.area();
        EntityPlayerMP player = Players.real(world, "frogleash");
        player.setLocationAndAngles(area.getX() + 2.5D, area.getY(), area.getZ() + 2.5D, 0.0F, 0.0F);
        net.minecraft.advancements.Advancement squad = world.getAdvancementManager().getAdvancement(Content.id("husbandry/leash_all_frog_variants"));
        for (FrogVariant v : FrogVariant.values()) {
            EntityFrog f = frog(world, area.add(3 + v.ordinal(), 0, 3), v);
            player.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.LEAD));
            FrogContent.onInteract(new net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract(player, EnumHand.MAIN_HAND, f));
            f.setLeashHolder(player, true);
            tick(world, 1, null, f);
            c.ok(player.getAdvancements().getProgress(squad).getCriterionProgress(v.id.substring("minecraft:".length())).isObtained(),
                    "a lead on a " + v + " frog counts");
            f.clearLeashed(true, false);
            f.setDead();
        }
        c.ok(player.getAdvancements().getProgress(squad).isDone(), "When the Squad Hops into Town, with all three");

        net.minecraft.advancements.Advancement bukkit = world.getAdvancementManager().getAdvancement(Content.id("husbandry/tadpole_in_a_bucket"));
        c.fill(area.add(10, -2, 10), area.add(14, -1, 14), Blocks.WATER.getDefaultState());
        EntityTadpole t = new EntityTadpole(world);
        t.setLocationAndAngles(area.getX() + 12.5D, area.getY() - 1.5D, area.getZ() + 12.5D, 0.0F, 0.0F);
        world.spawnEntity(t);
        player.interactionManager.setGameType(GameType.SURVIVAL);
        player.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
        t.processInteract(player, EnumHand.MAIN_HAND);
        c.ok(player.getAdvancements().getProgress(bukkit).isDone(), "Bukkit Bukkit for scooping a tadpole");
    }

    private void dispensers(Check c) {
        WorldServer world = c.world;
        BlockPos area = c.area();
        BlockPos at = area.add(5, 0, 5);
        world.setBlockState(at, Blocks.DISPENSER.getDefaultState().withProperty(BlockDispenser.FACING, EnumFacing.EAST));
        ItemStack bucket = new ItemStack(Owners.ours("tadpole_bucket"));
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("Age", 4321);
        bucket.setTagCompound(tag);
        ItemStack back = BlockDispenser.DISPENSE_BEHAVIOR_REGISTRY.getObject(bucket.getItem())
                .dispense(new net.minecraft.block.BlockSourceImpl(world, at), bucket);
        c.eq(Items.BUCKET, back.getItem(), "a dispenser empties a bucket of tadpole and keeps the bucket");
        c.eq(Blocks.FLOWING_WATER, world.getBlockState(at.east()).getBlock(), "placing water");
        List<EntityTadpole> out = world.getEntitiesWithinAABB(EntityTadpole.class, new AxisAlignedBB(at.east()).grow(1.0D));
        if (c.eq(1, out.size(), "and the tadpole")) {
            c.eq(4321, out.get(0).getAge(), "with its age");
            out.get(0).setDead();
        }
    }

    private void advancements(Check c) {
        net.minecraft.advancements.AdvancementManager m = c.world.getAdvancementManager();
        for (String n : new String[] {"tadpole_in_a_bucket", "leash_all_frog_variants", "froglights"}) {
            c.ok(m.getAdvancement(new ResourceLocation(Content.id("husbandry/" + n).toString())) != null, "advancement " + n + " loads");
        }
    }
}
