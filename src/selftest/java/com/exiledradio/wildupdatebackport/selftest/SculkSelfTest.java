package com.exiledradio.wildupdatebackport.selftest;

import com.exiledradio.wildupdatebackport.gameevent.GameEvent;
import com.exiledradio.wildupdatebackport.gameevent.GameEventContext;
import com.exiledradio.wildupdatebackport.gameevent.GameEvents;
import com.exiledradio.wildupdatebackport.gameevent.PositionSource;
import com.exiledradio.wildupdatebackport.gameevent.VibrationListener;
import com.exiledradio.wildupdatebackport.sculk.BlockSculkCatalyst;
import com.exiledradio.wildupdatebackport.sculk.BlockSculkSensor;
import com.exiledradio.wildupdatebackport.sculk.BlockSculkShrieker;
import com.exiledradio.wildupdatebackport.sculk.BlockSculkVein;
import com.exiledradio.wildupdatebackport.sculk.KillMobNearCatalystTrigger;
import com.exiledradio.wildupdatebackport.sculk.SculkContent;
import com.exiledradio.wildupdatebackport.sculk.SculkExperience;
import com.exiledradio.wildupdatebackport.sculk.SculkSpreader;
import com.exiledradio.wildupdatebackport.sculk.SculkTags;
import com.exiledradio.wildupdatebackport.sculk.TileSculkCatalyst;
import com.exiledradio.wildupdatebackport.sculk.TileSculkSensor;
import com.exiledradio.wildupdatebackport.sculk.TileSculkShrieker;
import com.exiledradio.wildupdatebackport.sculk.TileSculkVein;
import com.exiledradio.wildupdatebackport.sculk.WardenSpawnTracker;
import com.exiledradio.wildupdatebackport.sculk.gen.SculkPatch;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Enchantments;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Stream S: sculk, veins, catalysts, sensors, shriekers and the spread. */
public final class SculkSelfTest implements SelfTest {

    @Override
    public String name() {
        return "sculk";
    }

    /** Every draw is 0: every chance hits, shuffles keep their order. */
    static final class Zero extends Random {
        @Override
        public int nextInt(int bound) {
            return 0;
        }

        @Override
        public float nextFloat() {
            return 0.0F;
        }

        @Override
        public double nextDouble() {
            return 0.0D;
        }
    }

    /** Every draw is the largest: no chance hits. */
    static final class Max extends Random {
        @Override
        public int nextInt(int bound) {
            return bound - 1;
        }
    }

    private BlockSculkVein vein;
    private Block sculk;
    private BlockSculkCatalyst catalyst;
    private BlockSculkSensor sensor;
    private BlockSculkShrieker shrieker;

    @Override
    public void run(Check c) {
        WorldServer world = c.world;
        this.vein = SculkContent.veinBlock();
        this.sculk = SculkContent.sculkBlock();
        this.catalyst = SculkContent.catalystBlock();
        this.sensor = SculkContent.sensorBlock();
        this.shrieker = SculkContent.shriekerBlock();
        if (!c.ok(this.sculk != null && this.vein != null && this.catalyst != null && this.sensor != null && this.shrieker != null,
                "all five sculk blocks are registered")) {
            return;
        }
        if (world.getDifficulty() == EnumDifficulty.PEACEFUL) {
            world.getWorldInfo().setDifficulty(EnumDifficulty.EASY);
        }
        blocks(c, world);
        drops(c, world);
        veins(c, world);
        spreadRules(c, world);
        catalystDeath(c, world);
        sensor(c, world);
        shrieker(c, world);
        worldGen(c, world);
    }

    // --- 1. the blocks -----------------------------------------------------------------------

    private void blocks(Check c, WorldServer world) {
        for (Block b : SculkContent.blocks()) {
            c.ok(Item.getItemFromBlock(b) != Items.AIR, b.getRegistryName() + " has an item");
        }
        c.eq(0.2F, this.sculk.getBlockHardness(this.sculk.getDefaultState(), world, BlockPos.ORIGIN), "sculk hardness");
        c.eq(0.2F, this.vein.getBlockHardness(this.vein.getDefaultState(), world, BlockPos.ORIGIN), "vein hardness");
        c.eq(3.0F, this.catalyst.getBlockHardness(this.catalyst.getDefaultState(), world, BlockPos.ORIGIN), "catalyst hardness");
        c.eq(1.5F, this.sensor.getBlockHardness(this.sensor.getDefaultState(), world, BlockPos.ORIGIN), "sensor hardness");
        c.eq(3.0F, this.shrieker.getBlockHardness(this.shrieker.getDefaultState(), world, BlockPos.ORIGIN), "shrieker hardness");
        c.ok(Math.abs(this.catalyst.getExplosionResistance(null) - 3.0F) < 0.01F, "catalyst resistance 3");
        c.ok(Math.abs(this.shrieker.getExplosionResistance(null) - 3.0F) < 0.01F, "shrieker resistance 3");
        c.eq(6, this.catalyst.getDefaultState().getLightValue(), "catalyst light 6");
        c.eq(1, this.sensor.getDefaultState().getLightValue(), "sensor light 1");
        c.ok(Math.abs(this.sensor.getDefaultState().getBoundingBox(world, BlockPos.ORIGIN).maxY - 0.5D) < 1e-6, "sensor is half a block high");
        c.ok(Math.abs(this.shrieker.getDefaultState().getCollisionBoundingBox(world, BlockPos.ORIGIN).maxY - 0.5D) < 1e-6,
                "shrieker collision is half a block high");
        c.ok(this.vein.getDefaultState().getCollisionBoundingBox(world, BlockPos.ORIGIN) == null, "veins have no collision");
        for (IBlockState s : this.sensor.getBlockState().getValidStates()) {
            c.eq(s, this.sensor.getStateFromMeta(this.sensor.getMetaFromState(s)), "sensor meta round trip " + s);
        }
        for (IBlockState s : this.shrieker.getBlockState().getValidStates()) {
            c.eq(s, this.shrieker.getStateFromMeta(this.shrieker.getMetaFromState(s)), "shrieker meta round trip " + s);
        }
        c.ok(world.getGameRules().hasRule(SculkContent.DO_WARDEN_SPAWNING) && world.getGameRules().getBoolean(SculkContent.DO_WARDEN_SPAWNING),
                "doWardenSpawning exists and is true");
        c.ok(CriteriaTriggers.get(KillMobNearCatalystTrigger.ID) != null, "the It Spreads trigger is registered");
        c.ok(world.getMinecraftServer().getAdvancementManager().getAdvancement(
                new net.minecraft.util.ResourceLocation("wildupdatebackport", "adventure/kill_mob_near_sculk_catalyst")) != null,
                "It Spreads is loaded");
        c.ok(world.getMinecraftServer().getAdvancementManager().getAdvancement(
                new net.minecraft.util.ResourceLocation("wildupdatebackport", "adventure/avoid_vibration")) != null, "Sneak 100 is loaded");

        // Replaceable: 1.19's #sculk_replaceable, as far as 1.12.2 has its blocks.
        c.ok(SculkTags.replaceable(Blocks.STONE.getDefaultState(), false), "stone is sculk-replaceable");
        c.ok(SculkTags.replaceable(Blocks.DIRT.getDefaultState(), false), "dirt is sculk-replaceable");
        c.ok(SculkTags.replaceable(Blocks.NETHERRACK.getDefaultState(), false), "netherrack is sculk-replaceable");
        c.ok(!SculkTags.replaceable(Blocks.COBBLESTONE.getDefaultState(), false), "cobblestone is not");
        c.ok(!SculkTags.replaceable(Blocks.OBSIDIAN.getDefaultState(), false), "obsidian is not");
        c.ok(!SculkTags.replaceable(Blocks.STONE.getStateFromMeta(2), false), "polished granite is not");
        c.ok(!SculkTags.replaceable(Blocks.SANDSTONE.getStateFromMeta(1), false), "chiseled sandstone is not");

        // Hoes mine sculk at their speed.
        EntityPlayerMP p = Players.real(world, "hoe");
        p.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.DIAMOND_HOE));
        PlayerEvent.BreakSpeed ev = new PlayerEvent.BreakSpeed(p, this.sculk.getDefaultState(), 1.0F, BlockPos.ORIGIN);
        MinecraftForge.EVENT_BUS.post(ev);
        c.ok(Math.abs(ev.getNewSpeed() - 8.0F) < 1e-4, "a diamond hoe mines sculk 8 times faster: " + ev.getNewSpeed());
        PlayerEvent.BreakSpeed stone = new PlayerEvent.BreakSpeed(p, Blocks.STONE.getDefaultState(), 1.0F, BlockPos.ORIGIN);
        MinecraftForge.EVENT_BUS.post(stone);
        c.ok(Math.abs(stone.getNewSpeed() - 1.0F) < 1e-4, "but not stone");
    }

    // --- 2. drops ----------------------------------------------------------------------------

    private void drops(Check c, WorldServer world) {
        BlockPos area = c.area();
        c.eq(1, this.sculk.getExpDrop(this.sculk.getDefaultState(), world, area, 0), "sculk drops 1 experience");
        c.eq(5, this.catalyst.getExpDrop(this.catalyst.getDefaultState(), world, area, 0), "catalyst drops 5 experience");
        c.eq(5, this.sensor.getExpDrop(this.sensor.getDefaultState(), world, area, 0), "sensor drops 5 experience");
        c.eq(5, this.shrieker.getExpDrop(this.shrieker.getDefaultState(), world, area, 0), "shrieker drops 5 experience");
        c.eq(0, this.vein.getExpDrop(this.vein.getDefaultState(), world, area, 0), "veins drop no experience");

        EntityPlayerMP player = Players.real(world, "sculkdrops");
        player.interactionManager.setGameType(GameType.SURVIVAL);
        ItemStack pick = new ItemStack(Items.DIAMOND_PICKAXE);
        ItemStack silk = new ItemStack(Items.DIAMOND_PICKAXE);
        silk.addEnchantment(Enchantments.SILK_TOUCH, 1);
        int i = 0;
        for (Block b : new Block[]{this.sculk, this.catalyst, this.sensor, this.shrieker}) {
            BlockPos at = area.add(2 + 3 * i++, 0, 2);
            player.setPosition(at.getX() + 0.5D, at.getY(), at.getZ() + 1.5D);
            world.setBlockState(at, b.getDefaultState());
            player.setHeldItem(EnumHand.MAIN_HAND, pick.copy());
            player.interactionManager.tryHarvestBlock(at);
            c.eq(0, Drops.count(world, at, Item.getItemFromBlock(b)), b.getRegistryName() + " drops nothing without Silk Touch");
            world.setBlockState(at, b.getDefaultState());
            player.setHeldItem(EnumHand.MAIN_HAND, silk.copy());
            player.interactionManager.tryHarvestBlock(at);
            c.eq(1, Drops.count(world, at, Item.getItemFromBlock(b)), b.getRegistryName() + " drops itself with Silk Touch");
            clearXp(world, at);
        }
        // A generated shrieker picked up places one that cannot summon.
        c.eq(0, this.shrieker.damageDropped(this.shrieker.getDefaultState().withProperty(BlockSculkShrieker.CAN_SUMMON, true)),
                "a can_summon shrieker drops a plain one");

        // A vein on three faces: three veins with Silk Touch, nothing without.
        BlockPos v = area.add(2, 2, 8);
        c.fill(v.add(-1, -1, -1), v.add(1, 1, 1), Blocks.STONE.getDefaultState());
        world.setBlockToAir(v);
        BlockSculkVein.set(world, v, BlockSculkVein.bit(EnumFacing.DOWN) | BlockSculkVein.bit(EnumFacing.NORTH) | BlockSculkVein.bit(EnumFacing.WEST), 3);
        player.setPosition(v.getX() + 0.5D, v.getY(), v.getZ() + 0.5D);
        player.setHeldItem(EnumHand.MAIN_HAND, silk.copy());
        player.interactionManager.tryHarvestBlock(v);
        c.eq(3, Drops.count(world, v, Item.getItemFromBlock(this.vein)), "a three-faced vein drops three veins with Silk Touch");
        BlockSculkVein.set(world, v, BlockSculkVein.bit(EnumFacing.DOWN), 3);
        player.setHeldItem(EnumHand.MAIN_HAND, pick.copy());
        player.interactionManager.tryHarvestBlock(v);
        c.eq(0, Drops.count(world, v, Item.getItemFromBlock(this.vein)), "a vein drops nothing without Silk Touch");
        clearXp(world, v);
    }

    private static void clearXp(WorldServer world, BlockPos at) {
        for (EntityXPOrb orb : world.getEntitiesWithinAABB(EntityXPOrb.class, new AxisAlignedBB(at).grow(4))) {
            orb.setDead();
        }
    }

    // --- 3. veins ----------------------------------------------------------------------------

    private void veins(Check c, WorldServer world) {
        BlockPos area = c.area().add(8, 0, 8);
        // A stone pillar east of an air block above the floor.
        BlockPos at = area.add(0, 0, 0);
        world.setBlockState(at.east(), Blocks.STONE.getDefaultState());
        EntityPlayerMP player = Players.real(world, "veins");
        player.interactionManager.setGameType(GameType.SURVIVAL);
        player.setPosition(at.getX() - 2.5D, at.getY(), at.getZ() + 0.5D);
        player.rotationYaw = -90.0F; // facing east
        player.rotationPitch = 0.0F;
        ItemStack veins = new ItemStack(this.vein, 4);
        player.setHeldItem(EnumHand.MAIN_HAND, veins);
        // Clicking the pillar's west face puts a vein on the east face of the air block.
        player.interactionManager.processRightClickBlock(player, world, veins, EnumHand.MAIN_HAND, at.east(), EnumFacing.WEST, 0.0F, 0.5F, 0.5F);
        c.ok(BlockSculkVein.isVein(world.getBlockState(at)), "a vein is placed in front of the clicked face");
        c.eq(BlockSculkVein.bit(EnumFacing.EAST), BlockSculkVein.faces(world, at), "on the clicked block's face");
        c.eq(3, veins.getCount(), "one vein used");
        // Clicking the vein itself (it has free faces) adds the next face it can: the floor, looking down.
        player.rotationPitch = 90.0F;
        player.interactionManager.processRightClickBlock(player, world, veins, EnumHand.MAIN_HAND, at, EnumFacing.UP, 0.5F, 0.0F, 0.5F);
        c.eq(BlockSculkVein.bit(EnumFacing.EAST) | BlockSculkVein.bit(EnumFacing.DOWN), BlockSculkVein.faces(world, at),
                "a second vein in the same block adds a face");
        IBlockState actual = world.getBlockState(at).getActualState(world, at);
        c.ok(actual.getValue(BlockSculkVein.FACES[EnumFacing.DOWN.getIndex()]) && actual.getValue(BlockSculkVein.FACES[EnumFacing.EAST.getIndex()])
                && !actual.getValue(BlockSculkVein.FACES[EnumFacing.UP.getIndex()]), "getActualState shows the faces");
        // Saved and read back.
        TileEntity te = world.getTileEntity(at);
        NBTTagCompound tag = te.writeToNBT(new NBTTagCompound());
        c.eq((byte) BlockSculkVein.faces(world, at), tag.getByte("Faces"), "faces are saved as the byte Faces");
        TileSculkVein copy = new TileSculkVein();
        copy.readFromNBT(tag);
        c.eq(BlockSculkVein.faces(world, at), copy.faces(), "and read back");
        // Removing the pillar takes that face away; removing the floor too leaves nothing.
        world.setBlockToAir(at.east());
        c.eq(BlockSculkVein.bit(EnumFacing.DOWN), BlockSculkVein.faces(world, at), "a face loses its vein when its support goes");
        world.setBlockToAir(at.down());
        c.eq(Blocks.AIR, world.getBlockState(at).getBlock(), "a vein with no faces left is gone");
        c.eq(0, Drops.count(world, at, Item.getItemFromBlock(this.vein)), "and drops nothing");
        world.setBlockState(at.down(), Blocks.STONE.getDefaultState());
        // Any other block placed into a vein replaces it.
        BlockSculkVein.set(world, at, BlockSculkVein.bit(EnumFacing.DOWN), 3);
        c.ok(world.getBlockState(at).getBlock().isReplaceable(world, at), "a vein is replaceable by other blocks");
    }

    // --- 4. the spread -----------------------------------------------------------------------

    private void spreadRules(Check c, WorldServer world) {
        BlockPos area = c.area();
        BlockPos cat = area.add(4, 0, 16);
        c.fill(area.add(0, -1, 0), area.add(31, -1, 31), Blocks.STONE.getDefaultState());

        // a. Sculk: a charge outside the no-growth radius grows a growth on top and pays 10.
        BlockPos s = cat.add(10, -1, 0);
        world.setBlockState(s, this.sculk.getDefaultState());
        SculkSpreader level = SculkSpreader.createLevelSpreader();
        SculkSpreader.ChargeCursor cursor = new SculkSpreader.ChargeCursor(s, 100);
        int left = ((com.exiledradio.wildupdatebackport.sculk.SculkBehaviour) this.sculk).attemptUseCharge(cursor, world, cat, new Zero(), level, true);
        c.eq(90, left, "growing costs a level charge 10");
        c.eq(this.shrieker, world.getBlockState(s.up()).getBlock(), "one roll in 11 (here: the first) grows a shrieker");
        c.ok(!world.getBlockState(s.up()).getValue(BlockSculkShrieker.CAN_SUMMON), "a catalyst-grown shrieker cannot summon");
        world.setBlockToAir(s.up());
        // b. Within 4 blocks of the catalyst: no growth, the charge decays by 1.
        BlockPos near = cat.add(2, -1, 0);
        world.setBlockState(near, this.sculk.getDefaultState());
        left = ((com.exiledradio.wildupdatebackport.sculk.SculkBehaviour) this.sculk).attemptUseCharge(
                new SculkSpreader.ChargeCursor(near, 100), world, cat, new Zero(), level, true);
        c.eq(99, left, "near the catalyst a charge only decays by 1");
        c.ok(world.isAirBlock(near.up()), "and grows nothing");
        // c. With three growths around already: no more, the charge decays with distance instead.
        world.setBlockState(s.add(1, 1, 0), this.sensor.getDefaultState());
        world.setBlockState(s.add(-1, 1, 0), this.sensor.getDefaultState());
        world.setBlockState(s.add(0, 1, 1), this.sensor.getDefaultState());
        left = ((com.exiledradio.wildupdatebackport.sculk.SculkBehaviour) this.sculk).attemptUseCharge(
                new SculkSpreader.ChargeCursor(s, 100), world, cat, new Zero(), level, true);
        c.ok(world.isAirBlock(s.up()), "no growth with three sensors near");
        // decay penalty: (10 - 4)^2 / (24 - 4)^2 = 0.09, of 100 * 0.5 -> 4
        c.eq(96, left, "the decay grows with distance from the catalyst");
        // d. No roll: the charge stays.
        left = ((com.exiledradio.wildupdatebackport.sculk.SculkBehaviour) this.sculk).attemptUseCharge(
                new SculkSpreader.ChargeCursor(s, 100), world, cat, new Max(), level, true);
        c.eq(100, left, "a step without a roll keeps the charge");
        c.fill(s.add(-1, 1, -1), s.add(1, 1, 1), Blocks.AIR.getDefaultState());

        // e. A vein on stone turns it into sculk, costs 1, and retreats from the new sculk.
        BlockPos v = cat.add(-3, 0, 6);
        BlockSculkVein.set(world, v, BlockSculkVein.bit(EnumFacing.DOWN), 3);
        left = this.vein.attemptUseCharge(new SculkSpreader.ChargeCursor(v, 50), world, cat, new Zero(), level, true);
        c.eq(49, left, "a vein that places sculk spends 1");
        c.eq(this.sculk, world.getBlockState(v.down()).getBlock(), "the stone under the vein became sculk");
        // f. A vein on obsidian cannot place sculk; a hit roll halves the charge.
        BlockPos o = cat.add(-6, 0, 6);
        world.setBlockState(o.down(), Blocks.OBSIDIAN.getDefaultState());
        BlockSculkVein.set(world, o, BlockSculkVein.bit(EnumFacing.DOWN), 3);
        left = this.vein.attemptUseCharge(new SculkSpreader.ChargeCursor(o, 50), world, cat, new Zero(), level, true);
        c.eq(25, left, "on obsidian a vein's charge halves on a hit roll");
        c.eq(Blocks.OBSIDIAN, world.getBlockState(o.down()).getBlock(), "and the obsidian stays");
        c.ok(!level.replaceable(Blocks.OBSIDIAN.getDefaultState()), "obsidian is never replaced");
        // g. Discharging a vein next to sculk removes the faces that touch sculk.
        world.setBlockState(o.down(), this.sculk.getDefaultState());
        this.vein.onDischarged(world, world.getBlockState(o), o, new Zero());
        c.ok(world.isAirBlock(o), "a vein whose only face touches sculk retreats when discharged");

        // h. Cursors: a charge splits at 1000; two on one block merge; saved as 1.19 saves them.
        SculkSpreader sp = SculkSpreader.createLevelSpreader();
        sp.addCursors(cat, 2500);
        c.eq(3, sp.getCursors().size(), "2500 experience is three cursors (1000, 1000, 500)");
        c.eq(500, sp.getCursors().get(2).getCharge(), "the last holds the rest");
        NBTTagCompound tag = new NBTTagCompound();
        sp.save(tag);
        c.eq(3, tag.getTagList("cursors", 10).tagCount(), "cursors are saved as a list");
        c.eq(3, tag.getTagList("cursors", 10).getCompoundTagAt(0).getIntArray("pos").length, "with pos as an int array");
        SculkSpreader read = SculkSpreader.createLevelSpreader();
        read.load(tag);
        c.eq(3, read.getCursors().size(), "and read back");
        SculkSpreader cap = SculkSpreader.createLevelSpreader();
        cap.addCursors(cat, 40000);
        c.eq(32, cap.getCursors().size(), "at most 32 cursors");

        // i. A full run from a catalyst over a stone floor: sculk appears, veins on its edge, nothing near the
        // catalyst grows, and the charge runs out.
        BlockPos run = c.area().add(16, 0, 16);
        BlockPos catalystAt = run.down();
        world.setBlockState(catalystAt, this.catalyst.getDefaultState());
        SculkSpreader full = SculkSpreader.createLevelSpreader();
        // Where a mob died two blocks from the catalyst, on the stone floor.
        full.addCursors(run.add(2, 0, 0), 60);
        Random random = new Random(1019L);
        for (int t = 0; t < 2000 && !full.getCursors().isEmpty(); t++) {
            full.updateCursors(world, catalystAt, random, true);
        }
        c.ok(full.getCursors().isEmpty(), "a charge of 60 is spent within 2000 steps");
        int sculkCount = 0;
        int veinCount = 0;
        boolean growthNear = false;
        for (BlockPos p : BlockPos.getAllInBox(run.add(-16, -2, -16), run.add(15, 3, 15))) {
            Block b = world.getBlockState(p).getBlock();
            if (b == this.sculk) {
                sculkCount++;
            } else if (b == this.vein) {
                veinCount++;
            } else if ((b == this.sensor || b == this.shrieker) && p.distanceSq(run) < 16) {
                growthNear = true;
            }
        }
        com.exiledradio.wildupdatebackport.WildUpdateBackport.LOGGER.info("SELFTEST sculk: a charge of 60 made {} sculk and {} veins", sculkCount, veinCount);
        c.ok(sculkCount > 5, "the charge turned stone into sculk: " + sculkCount);
        c.ok(veinCount > 0, "and laid veins: " + veinCount);
        c.ok(!growthNear, "nothing grew within 4 blocks of the catalyst");
        c.ok(sculkCount <= 60 + 1, "no more sculk than the charge paid for: " + sculkCount);
    }

    // --- 5. a mob dying near a catalyst --------------------------------------------------------

    private void catalystDeath(Check c, WorldServer world) {
        BlockPos area = c.area().add(16, 0, 16);
        BlockPos cat = area.add(0, -1, 0);
        world.setBlockState(cat, this.catalyst.getDefaultState());
        TileEntity te = world.getTileEntity(cat);
        if (!c.ok(te instanceof TileSculkCatalyst, "the catalyst has its tile entity")) {
            return;
        }
        TileSculkCatalyst tile = (TileSculkCatalyst) te;
        tile.onLoad();
        EntityPlayerMP killer = Players.real(world, "spreads");
        killer.setPosition(area.getX() + 3.5D, area.getY(), area.getZ() + 0.5D);
        EntityZombie zombie = new EntityZombie(world);
        zombie.setPosition(area.getX() + 2.5D, area.getY(), area.getZ() + 0.5D);
        world.spawnEntity(zombie);
        int xp = SculkExperience.reward(zombie);
        c.eq(5, xp, "a zombie gives 5 experience");
        zombie.setRevengeTarget(killer);
        zombie.attackEntityFrom(DamageSource.causePlayerDamage(killer), 1000.0F);
        c.ok(zombie.getHealth() <= 0.0F, "the zombie died");
        c.ok(SculkExperience.consumed(zombie), "the catalyst took its experience");
        int charge = 0;
        for (SculkSpreader.ChargeCursor cur : tile.spreader().getCursors()) {
            charge += cur.getCharge();
        }
        c.eq(5, charge, "as a charge of 5");
        c.eq(area.add(2, 0, 0), tile.spreader().getCursors().isEmpty() ? null : tile.spreader().getCursors().get(0).getPos(),
                "at the zombie's feet");
        c.ok(world.getBlockState(cat).getValue(BlockSculkCatalyst.BLOOM), "the catalyst blooms");
        this.catalyst.updateTick(world, cat, world.getBlockState(cat), world.rand);
        c.ok(!world.getBlockState(cat).getValue(BlockSculkCatalyst.BLOOM), "and stops blooming on its tick");
        // 1.12.2 drops experience 20 ticks after death: none here.
        for (int i = 0; i < 21 && !zombie.isDead; i++) {
            zombie.deathTime = Math.max(zombie.deathTime, 19);
            zombie.onUpdate();
        }
        c.eq(0, world.getEntitiesWithinAABB(EntityXPOrb.class, new AxisAlignedBB(area).grow(6)).size(), "the zombie dropped no experience orbs");
        // A baby animal gives nothing, but the catalyst still blooms; a baby zombie (a monster) gives its 12.
        EntityPig piglet = new EntityPig(world);
        piglet.setGrowingAge(-24000);
        piglet.setPosition(area.getX() + 1.5D, area.getY(), area.getZ() + 1.5D);
        world.spawnEntity(piglet);
        int before = tile.spreader().getCursors().size();
        piglet.attackEntityFrom(DamageSource.GENERIC, 1000.0F);
        c.eq(before, tile.spreader().getCursors().size(), "a baby animal's death adds no charge");
        c.ok(world.getBlockState(cat).getValue(BlockSculkCatalyst.BLOOM), "but the catalyst blooms");
        EntityZombie baby = new EntityZombie(world);
        baby.setChild(true);
        baby.setPosition(area.getX() + 1.5D, area.getY(), area.getZ() - 1.5D);
        world.spawnEntity(baby);
        baby.attackEntityFrom(DamageSource.GENERIC, 1000.0F);
        c.eq(before + 1, tile.spreader().getCursors().size(), "a baby zombie's death adds a charge");
        c.eq(12, tile.spreader().getCursors().get(tile.spreader().getCursors().size() - 1).getCharge(), "of 12");
        // Far away: not heard.
        EntityPig pig = new EntityPig(world);
        pig.setPosition(area.getX() + 12.5D, area.getY(), area.getZ() + 0.5D);
        world.spawnEntity(pig);
        pig.attackEntityFrom(DamageSource.GENERIC, 1000.0F);
        c.ok(!SculkExperience.consumed(pig), "a mob dying 12 blocks away keeps its experience");
        // The charge moves on the catalyst's ticks and is saved with it.
        NBTTagCompound tag = tile.writeToNBT(new NBTTagCompound());
        c.ok(tag.getTagList("cursors", 10).tagCount() >= 1, "the catalyst saves its cursors");
        tile.invalidate();
        world.setBlockToAir(cat);
        for (Entity e : world.getEntitiesWithinAABB(Entity.class, new AxisAlignedBB(area).grow(16))) {
            if (!(e instanceof EntityPlayerMP)) {
                e.setDead();
            }
        }
    }

    // --- 6. the sensor -----------------------------------------------------------------------

    private void sensor(Check c, WorldServer world) {
        BlockPos area = c.area().add(16, 0, 16);
        BlockPos at = area;
        world.setBlockState(at, this.sensor.getDefaultState());
        TileSculkSensor tile = (TileSculkSensor) world.getTileEntity(at);
        tile.onLoad();
        GameEvents.flush(world);
        // A vibration 3 blocks away: active, power 15 - floor(3/8 * 15) = 10, the comparator reads eat's frequency.
        GameEvents.emit(world, GameEvent.EAT, at.add(3, 0, 0), GameEventContext.NONE);
        GameEvents.flush(world);
        for (int i = 0; i < 4; i++) {
            tile.update();
        }
        IBlockState state = world.getBlockState(at);
        c.eq(BlockSculkSensor.Phase.ACTIVE, state.getValue(BlockSculkSensor.PHASE), "a vibration activates the sensor");
        c.eq(10, tile.power(), "power falls with distance (3 of 8 blocks: 10)");
        c.eq(10, world.getRedstonePower(at, EnumFacing.EAST), "and powers its neighbors");
        c.eq(GameEvent.EAT.frequency(), state.getComparatorInputOverride(world, at), "a comparator reads the vibration's frequency");
        c.eq(10, TileSculkSensor.redstoneStrengthForDistance(3.0F, 8), "1.19's distance table");
        c.eq(1, TileSculkSensor.redstoneStrengthForDistance(7.99F, 8), "at the edge, 1");
        // While active it hears nothing else.
        GameEvents.emit(world, GameEvent.EXPLODE, at.add(1, 0, 0), GameEventContext.NONE);
        GameEvents.flush(world);
        c.ok(!tile.listener().isReceiving(), "an active sensor hears nothing");
        // 40 ticks later: cooldown for 1 tick, then inactive.
        this.sensor.updateTick(world, at, world.getBlockState(at), world.rand);
        c.eq(BlockSculkSensor.Phase.COOLDOWN, world.getBlockState(at).getValue(BlockSculkSensor.PHASE), "then cooldown");
        c.eq(0, tile.power(), "with no power");
        c.eq(0, world.getBlockState(at).getComparatorInputOverride(world, at), "and no comparator output");
        this.sensor.updateTick(world, at, world.getBlockState(at), world.rand);
        c.eq(BlockSculkSensor.Phase.INACTIVE, world.getBlockState(at).getValue(BlockSculkSensor.PHASE), "then inactive");
        // Its own block being placed is not heard.
        GameEvents.emit(world, GameEvent.BLOCK_PLACE, at, GameEventContext.NONE);
        GameEvents.flush(world);
        c.ok(!tile.listener().isReceiving(), "a sensor does not hear itself placed");
        // Stepped on: full power, step's frequency.
        EntityPig pig = new EntityPig(world);
        pig.setPosition(at.getX() + 0.5D, at.getY() + 0.5D, at.getZ() + 0.5D);
        this.sensor.onEntityWalk(world, at, pig);
        c.eq(BlockSculkSensor.Phase.ACTIVE, world.getBlockState(at).getValue(BlockSculkSensor.PHASE), "stepping on it activates it");
        c.eq(15, tile.power(), "at full power");
        c.eq(GameEvent.STEP.frequency(), world.getBlockState(at).getComparatorInputOverride(world, at), "with a step's frequency");
        this.sensor.updateTick(world, at, world.getBlockState(at), world.rand);
        this.sensor.updateTick(world, at, world.getBlockState(at), world.rand);
        // A sneaking player standing on it activates it too (1.12.2's walking skips them; the player tick does not).
        EntityPlayerMP sneaker = Players.real(world, "sensorstep");
        sneaker.setPosition(at.getX() + 0.5D, at.getY() + 0.5D, at.getZ() + 0.5D);
        sneaker.onGround = true;
        sneaker.setSneaking(true);
        MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, sneaker));
        c.eq(BlockSculkSensor.Phase.ACTIVE, world.getBlockState(at).getValue(BlockSculkSensor.PHASE), "a sneaking player on it activates it");
        this.sensor.updateTick(world, at, world.getBlockState(at), world.rand);
        this.sensor.updateTick(world, at, world.getBlockState(at), world.rand);
        // A sneaking player's step nearby is not heard (and is the Sneak 100 path).
        sneaker.setPosition(at.getX() + 3.5D, at.getY(), at.getZ() + 0.5D);
        GameEvents.emit(world, GameEvent.STEP, sneaker.getPositionVector(), GameEventContext.of(sneaker));
        GameEvents.flush(world);
        c.ok(!tile.listener().isReceiving(), "a sneaking player's step is not heard");
        // Saved: frequency, power and a vibration in flight.
        GameEvents.emit(world, GameEvent.BLOCK_CHANGE, at.add(5, 0, 0), GameEventContext.NONE);
        GameEvents.flush(world);
        NBTTagCompound tag = tile.writeToNBT(new NBTTagCompound());
        c.ok(tag.hasKey("listener") && tag.getCompoundTag("listener").hasKey("event"), "a vibration in flight is saved with the sensor");
        c.ok(tag.hasKey("last_vibration_frequency"), "and its last frequency");
        tile.invalidate();
        world.setBlockToAir(at);
    }

    // --- 7. the shrieker ---------------------------------------------------------------------

    /** Records shriek game events. */
    static final class ShriekRecorder implements VibrationListener.Config {
        final List<GameEvent> heard = new ArrayList<GameEvent>();

        @Override
        public Set<GameEvent> listenableEvents() {
            return Collections.singleton(GameEvent.SHRIEK);
        }

        @Override
        public boolean shouldListen(WorldServer world, VibrationListener l, BlockPos origin, GameEvent event, GameEventContext context) {
            return true;
        }

        @Override
        public void onSignalReceive(WorldServer world, VibrationListener l, BlockPos origin, GameEvent event, @Nullable Entity source,
                                    @Nullable Entity owner, float distance) {
            this.heard.add(event);
        }
    }

    private void shrieker(Check c, WorldServer world) {
        BlockPos area = c.area().add(16, 0, 16);
        BlockPos at = area;
        world.setBlockState(at, this.shrieker.getDefaultState());
        TileSculkShrieker tile = (TileSculkShrieker) world.getTileEntity(at);
        tile.onLoad();
        ShriekRecorder rec = new ShriekRecorder();
        VibrationListener ear = new VibrationListener(new PositionSource.Block(at.add(0, 0, 10)), 32, rec);
        GameEvents.flush(world);
        GameEvents.add(world, ear);
        EntityPlayerMP player = Players.real(world, "shrieker");
        player.interactionManager.setGameType(GameType.SURVIVAL);
        player.setPosition(at.getX() + 0.5D, at.getY() + 0.5D, at.getZ() + 0.5D);
        world.playerEntities.add(player);
        try {
            // A placed shrieker: shrieks, no warning.
            this.shrieker.onEntityWalk(world, at, player);
            c.ok(world.getBlockState(at).getValue(BlockSculkShrieker.SHRIEKING), "a player stepping on a shrieker makes it shriek");
            GameEvents.flush(world);
            for (int i = 0; i < 12; i++) {
                ear.tick(world);
            }
            c.eq(1, rec.heard.size(), "a shriek game event is heard 10 blocks away");
            c.ok(!player.getEntityData().hasKey("wildupdatebackport:warden_spawn_tracker"), "a placed shrieker gives no warning");
            this.shrieker.onEntityWalk(world, at, player);
            this.shrieker.updateTick(world, at, world.getBlockState(at), world.rand);
            c.ok(!world.getBlockState(at).getValue(BlockSculkShrieker.SHRIEKING), "it stops shrieking on its tick");

            // A generated one: each shriek warns, with the 200-tick cooldown between.
            world.setBlockState(at, this.shrieker.getDefaultState().withProperty(BlockSculkShrieker.CAN_SUMMON, true), 2);
            this.shrieker.onEntityWalk(world, at, player);
            c.eq(1, WardenSpawnTracker.of(player).warningLevel(), "a generated shrieker raises the warning level to 1");
            c.eq(200, WardenSpawnTracker.of(player).cooldownTicks(), "and starts the 200-tick cooldown");
            c.eq(1, tile.warningLevel(), "the shrieker remembers the level");
            this.shrieker.updateTick(world, at, world.getBlockState(at), world.rand);
            this.shrieker.onEntityWalk(world, at, player);
            c.ok(!world.getBlockState(at).getValue(BlockSculkShrieker.SHRIEKING), "on cooldown it does not shriek");
            c.eq(1, WardenSpawnTracker.of(player).warningLevel(), "nor warn");
            for (int i = 0; i < 200; i++) {
                WardenSpawnTracker.tick(player);
            }
            for (int level = 2; level <= 4; level++) {
                this.shrieker.onEntityWalk(world, at, player);
                c.eq(level, WardenSpawnTracker.of(player).warningLevel(), "warning level " + level);
                // The response (sound, or a warden at 4: none here without stream W) must not fail.
                this.shrieker.updateTick(world, at, world.getBlockState(at), world.rand);
                for (int i = 0; i < 200; i++) {
                    WardenSpawnTracker.tick(player);
                }
            }
            this.shrieker.onEntityWalk(world, at, player);
            c.eq(4, WardenSpawnTracker.of(player).warningLevel(), "the level stops at 4");
            this.shrieker.updateTick(world, at, world.getBlockState(at), world.rand);
            // The level falls by one after 12000 ticks without a warning.
            for (int i = 0; i < 12001; i++) {
                WardenSpawnTracker.tick(player);
            }
            c.eq(3, WardenSpawnTracker.of(player).warningLevel(), "12000 ticks later the level is 3");
            // Another player near shares the highest level.
            EntityPlayerMP other = Players.real(world, "shrieker2");
            other.setPosition(at.getX() + 3.5D, at.getY(), at.getZ() + 0.5D);
            world.playerEntities.add(other);
            try {
                this.shrieker.onEntityWalk(world, at, other);
                c.eq(4, WardenSpawnTracker.of(other).warningLevel(), "a player near shares the highest level, plus one");
                c.eq(4, WardenSpawnTracker.of(player).warningLevel(), "and so does the first");
                this.shrieker.updateTick(world, at, world.getBlockState(at), world.rand);
            } finally {
                world.playerEntities.remove(other);
            }
            // doWardenSpawning false: shrieks without warning.
            for (int i = 0; i < 200; i++) {
                WardenSpawnTracker.tick(player);
            }
            world.getGameRules().setOrCreateGameRule(SculkContent.DO_WARDEN_SPAWNING, "false");
            int lvl = WardenSpawnTracker.of(player).warningLevel();
            this.shrieker.onEntityWalk(world, at, player);
            c.ok(world.getBlockState(at).getValue(BlockSculkShrieker.SHRIEKING), "with doWardenSpawning off it still shrieks");
            c.eq(lvl, WardenSpawnTracker.of(player).warningLevel(), "but warns no one");
            world.getGameRules().setOrCreateGameRule(SculkContent.DO_WARDEN_SPAWNING, "true");
            this.shrieker.updateTick(world, at, world.getBlockState(at), world.rand);

            // A sensor clicking because of a player makes the shrieker shriek (it hears the tendrils).
            world.setBlockState(at, this.shrieker.getDefaultState(), 2);
            BlockPos sensorAt = at.add(4, 0, 0);
            world.setBlockState(sensorAt, this.sensor.getDefaultState());
            TileSculkSensor sTile = (TileSculkSensor) world.getTileEntity(sensorAt);
            sTile.onLoad();
            GameEvents.flush(world);
            BlockSculkSensor.activate(player, world, sensorAt, world.getBlockState(sensorAt), 15);
            GameEvents.flush(world);
            for (int i = 0; i < 6; i++) {
                tile.update();
            }
            c.ok(world.getBlockState(at).getValue(BlockSculkShrieker.SHRIEKING), "a sensor clicking because of a player makes it shriek");
            this.shrieker.updateTick(world, at, world.getBlockState(at), world.rand);
            BlockSculkSensor.activate(null, world, sensorAt, BlockSculkSensor.canActivate(world.getBlockState(sensorAt))
                    ? world.getBlockState(sensorAt) : this.sensor.getDefaultState(), 15);
            GameEvents.flush(world);
            for (int i = 0; i < 6; i++) {
                tile.update();
            }
            c.ok(!world.getBlockState(at).getValue(BlockSculkShrieker.SHRIEKING), "a sensor clicking with no player behind it does not");
            c.eq(player, TileSculkShrieker.tryGetPlayer(player), "tryGetPlayer: a player");
            c.ok(TileSculkShrieker.tryGetPlayer(new EntityPig(world)) == null, "tryGetPlayer: a pig is nobody");
            sTile.invalidate();
            world.setBlockToAir(sensorAt);
        } finally {
            world.playerEntities.remove(player);
            GameEvents.remove(world, ear);
            tile.invalidate();
            world.setBlockToAir(at);
        }
        // The spawn spot search: the first block down with a full top and nothing solid above.
        BlockPos col = c.area().add(4, 0, 4);
        world.setBlockState(col.add(0, 2, 0), Blocks.STONE.getDefaultState());
        c.eq(col.add(0, 3, 0), TileSculkShrieker.spawnPosition(world, col.add(0, 6, 0), 6), "a warden would stand on the first floor down");
    }

    // --- 8. world generation ---------------------------------------------------------------------

    private void worldGen(Check c, WorldServer world) {
        BlockPos area = c.area();
        // A cave: stone floor and ceiling.
        c.fill(area.add(0, -4, 0), area.add(31, -1, 31), Blocks.STONE.getDefaultState());
        c.fill(area.add(0, 4, 0), area.add(31, 6, 31), Blocks.STONE.getDefaultState());
        BlockPos origin = area.add(16, 0, 16);
        c.ok(SculkPatch.ANCIENT_CITY.place(world, new Random(77L), origin), "a sculk patch is placed on a cave floor");
        int sculkCount = 0;
        int veinCount = 0;
        boolean summoning = false;
        for (BlockPos p : BlockPos.getAllInBox(area.add(0, -4, 0), area.add(31, 4, 31))) {
            IBlockState s = world.getBlockState(p);
            if (s.getBlock() == this.sculk) {
                sculkCount++;
            } else if (s.getBlock() == this.vein) {
                veinCount++;
            } else if (s.getBlock() == this.shrieker && s.getValue(BlockSculkShrieker.CAN_SUMMON)) {
                summoning = true;
            }
        }
        com.exiledradio.wildupdatebackport.WildUpdateBackport.LOGGER.info("SELFTEST sculk: an ancient-city patch made {} sculk, {} veins, summoning shrieker {}",
                sculkCount, veinCount, summoning);
        c.ok(sculkCount > 20, "world generation spreads sculk: " + sculkCount);
        c.ok(veinCount > 0, "and veins: " + veinCount);
        c.ok(summoning || sculkCount > 0, "generated shriekers can summon (when any is placed)");
        c.ok(!SculkPatch.DEEP_DARK.place(world, new Random(1L), area.add(16, 20, 16)), "a patch in open air is not placed");
        c.ok(SculkSpreader.createWorldGenSpreader().isWorldGeneration(), "world generation has its own spreader");
        c.eq(50, SculkSpreader.createWorldGenSpreader().growthSpawnCost(), "with 1.19's growth cost 50");
    }
}
