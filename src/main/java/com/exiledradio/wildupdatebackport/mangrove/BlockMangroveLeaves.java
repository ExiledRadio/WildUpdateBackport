package com.exiledradio.wildupdatebackport.mangrove;

import com.exiledradio.backportcore.waterlog.Waterloggable;

import net.minecraft.block.BlockLeaves;
import net.minecraft.block.BlockPlanks;
import net.minecraft.block.IGrowable;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Mangrove leaves.
 *
 * <p>Vanilla's {@link BlockLeaves} underneath, so decay, shearing, fancy and fast graphics and every
 * leaf check in other mods work as an oak leaf's. What 1.19 changes:
 * <ul>
 *   <li><b>No sapling drop</b>: mangrove leaves drop only sticks (1-2, at 1.19's chances by Fortune:
 *       2%, 2.2%, 2.5%, 3.3%, 10%); the propagule grows hanging under them instead.</li>
 *   <li><b>Bone meal</b> on leaves with air below grows a new hanging propagule (age 0) under them.</li>
 *   <li>They hold a water source ({@link Waterloggable}, with Fluidlogged API), as every 1.19 leaf does.</li>
 *   <li>Tinted with 1.19's fixed mangrove color, {@code 0x92C648} (client).</li>
 *   <li><b>Decay at 1.19's range</b>: a mangrove canopy spreads up to 6 blocks from its logs, past
 *       1.12.2's 4-block leaf rule, so these leaves use 1.19's: they stay while a log is within 6 steps
 *       through leaves. A changed neighbor starts the check (1.19 updates leaf distances the same way), so
 *       cutting a tree down still takes all of its canopy with it.</li>
 * </ul>
 * The two decay properties are vanilla's and are left out of the blockstate file, as vanilla's are.
 */
public class BlockMangroveLeaves extends BlockLeaves implements IGrowable, Waterloggable {

    /** 1.19's loot table: the chance of sticks at Fortune 0 to 4. */
    private static final float[] STICK_CHANCE = {0.02F, 0.022222223F, 0.025F, 0.033333335F, 0.1F};

    public BlockMangroveLeaves() {
        this.setDefaultState(this.blockState.getBaseState().withProperty(CHECK_DECAY, true).withProperty(DECAYABLE, true));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, CHECK_DECAY, DECAYABLE);
    }

    /** Vanilla leaves' layout: bit 4 set = placed by a player (never decays), bit 8 = check decay. */
    @Override
    @SuppressWarnings("deprecation")
    public IBlockState getStateFromMeta(int meta) {
        return this.getDefaultState().withProperty(DECAYABLE, (meta & 4) == 0).withProperty(CHECK_DECAY, (meta & 8) > 0);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        int meta = 0;
        if (!state.getValue(DECAYABLE)) {
            meta |= 4;
        }
        if (state.getValue(CHECK_DECAY)) {
            meta |= 8;
        }
        return meta;
    }

    /** A leaf placed by hand never decays, as vanilla's. */
    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, net.minecraft.util.EnumFacing facing, float hitX,
                                            float hitY, float hitZ, int meta, net.minecraft.entity.EntityLivingBase placer) {
        return this.getDefaultState().withProperty(DECAYABLE, false).withProperty(CHECK_DECAY, false);
    }

    @Override
    public BlockPlanks.EnumType getWoodType(int meta) {
        // Only vanilla's own leaves use this, for their sapling and item variant; ours have neither.
        return BlockPlanks.EnumType.OAK;
    }

    @Override
    @SuppressWarnings("deprecation")
    public MapColor getMapColor(IBlockState state, IBlockAccess world, BlockPos pos) {
        return MapColor.FOLIAGE;
    }

    @Override
    public int damageDropped(IBlockState state) {
        return 0;
    }

    @Override
    public void getDrops(NonNullList<ItemStack> drops, IBlockAccess world, BlockPos pos, IBlockState state, int fortune) {
        Random random = world instanceof World ? ((World) world).rand : RANDOM;
        if (random.nextFloat() < STICK_CHANCE[Math.max(0, Math.min(fortune, 4))]) {
            drops.add(new ItemStack(Items.STICK, 1 + random.nextInt(2)));
        }
    }

    @Override
    public List<ItemStack> onSheared(ItemStack item, IBlockAccess world, BlockPos pos, int fortune) {
        return Collections.singletonList(new ItemStack(this));
    }

    /** 1.19's leaf range: a log within this many steps through leaves keeps a leaf alive. */
    static final int RANGE = 6;

    @Override
    public void updateTick(World world, BlockPos pos, IBlockState state, Random random) {
        if (world.isRemote || !state.getValue(CHECK_DECAY) || !state.getValue(DECAYABLE)) {
            return;
        }
        if (logWithin(world, pos, RANGE)) {
            world.setBlockState(pos, state.withProperty(CHECK_DECAY, false), 4);
        } else {
            this.dropBlockAsItem(world, pos, state, 0);
            world.setBlockToAir(pos);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void neighborChanged(IBlockState state, World world, BlockPos pos, net.minecraft.block.Block block, BlockPos from) {
        if (!world.isRemote && state.getValue(DECAYABLE) && !state.getValue(CHECK_DECAY)) {
            world.setBlockState(pos, state.withProperty(CHECK_DECAY, true), 4);
        }
    }

    /** @return true if a log is reachable from {@code pos} in at most {@code range} steps through leaves */
    static boolean logWithin(World world, BlockPos pos, int range) {
        java.util.Set<BlockPos> seen = new java.util.HashSet<BlockPos>();
        java.util.ArrayDeque<BlockPos> frontier = new java.util.ArrayDeque<BlockPos>();
        frontier.add(pos);
        seen.add(pos);
        for (int step = 1; step <= range && !frontier.isEmpty(); step++) {
            java.util.ArrayDeque<BlockPos> next = new java.util.ArrayDeque<BlockPos>();
            for (BlockPos p : frontier) {
                for (net.minecraft.util.EnumFacing f : net.minecraft.util.EnumFacing.values()) {
                    BlockPos q = p.offset(f);
                    if (!seen.add(q) || !world.isBlockLoaded(q)) {
                        continue;
                    }
                    IBlockState s = world.getBlockState(q);
                    if (s.getBlock().canSustainLeaves(s, world, q)) {
                        return true;
                    }
                    if (s.getBlock().isLeaves(s, world, q)) {
                        next.add(q);
                    }
                }
            }
            frontier = next;
        }
        return false;
    }

    /** Lets the client switch between fancy and fast leaves, as it does for vanilla's. */
    public void setFancy(boolean fancy) {
        this.setGraphicsLevel(fancy);
    }

    // --- bone meal ----------------------------------------------------------------------------

    @Override
    public boolean canGrow(World world, BlockPos pos, IBlockState state, boolean isClient) {
        return world.isAirBlock(pos.down()) && MangroveContent.propagule() != null;
    }

    @Override
    public boolean canUseBonemeal(World world, Random random, BlockPos pos, IBlockState state) {
        return true;
    }

    @Override
    public void grow(World world, Random random, BlockPos pos, IBlockState state) {
        BlockPropagule propagule = MangroveContent.propagule();
        if (propagule != null) {
            world.setBlockState(pos.down(), propagule.hanging(0), 2);
        }
    }
}
