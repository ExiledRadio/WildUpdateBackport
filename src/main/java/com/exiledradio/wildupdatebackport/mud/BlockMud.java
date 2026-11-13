package com.exiledradio.wildupdatebackport.mud;

import com.exiledradio.wildupdatebackport.common.Owners;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.init.Blocks;
import net.minecraft.pathfinding.PathNodeType;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.Random;

/**
 * Mud: a dirt-like block (shovel, hardness 0.5) whose collision box is 14 pixels high, so things sink in a
 * little, as soul sand's 14 pixels do but without slowing anyone. It still holds blocks, conducts redstone
 * and suffocates as a full block, and mobs never path across it (1.19's {@code isPathfindable} false).
 *
 * <h2>Drying into clay</h2>
 * 1.19: mud on top of a block that a pointed dripstone hangs from turns into clay, from the dripstone's own
 * random tick (each stalactite segment 17.6% per tick, never in the Nether). Pointed dripstone is Caves &amp;
 * Cliffs'; its random tick is not ours to add to, so mud does the same from its own random tick: with
 * {@code n} stalactite segments under its support, 1 - (1 - 0.17578125)^n per tick, which is the rate the
 * segments' ticks give together.
 */
public class BlockMud extends Block {

    /** 1.19's collision: 16 x 14 x 16 pixels. */
    private static final AxisAlignedBB COLLISION = new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 0.875D, 1.0D);

    /** 1.19's chance per stalactite segment's random tick. */
    static final float DRIP_CHANCE = 0.17578125F;

    public BlockMud(SoundType sound) {
        super(Material.GROUND, MapColor.CYAN_STAINED_HARDENED_CLAY);
        this.setHardness(0.5F);
        this.setSoundType(sound);
        this.setTickRandomly(true);
    }

    @Override
    @Nullable
    @SuppressWarnings("deprecation")
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return COLLISION;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isFullCube(IBlockState state) {
        // Still drawn and lit as a full block; only the collision is lower.
        return true;
    }

    @Override
    public PathNodeType getAiPathNodeType(IBlockState state, IBlockAccess world, BlockPos pos, @Nullable EntityLiving entity) {
        return PathNodeType.BLOCKED;
    }

    @Override
    public boolean canEntitySpawn(IBlockState state, Entity entity) {
        return true;
    }

    /**
     * 1.19's {@code #dirt} holds mud, so what grows on dirt grows on mud (grass, flowers, saplings, dead bushes,
     * sugar cane beside water): asked as dirt would be. Cactus stays on sand.
     */
    @Override
    public boolean canSustainPlant(IBlockState state, IBlockAccess world, BlockPos pos, net.minecraft.util.EnumFacing direction,
                                   net.minecraftforge.common.IPlantable plantable) {
        if (plantable.getPlant(world, pos.offset(direction)).getBlock() == Blocks.CACTUS) {
            return false;
        }
        return Blocks.DIRT.canSustainPlant(Blocks.DIRT.getDefaultState(), world, pos, direction, plantable);
    }

    @Override
    public void updateTick(World world, BlockPos pos, IBlockState state, Random random) {
        if (world.isRemote || world.provider.doesWaterVaporize()) {
            return;
        }
        int segments = stalactiteSegments(world, pos.down());
        if (segments > 0 && random.nextFloat() < 1.0F - (float) Math.pow(1.0F - DRIP_CHANCE, segments)) {
            world.setBlockState(pos, Blocks.CLAY.getDefaultState());
        }
    }

    /**
     * @return how many downward-pointing pointed dripstone blocks hang in a column under {@code support}
     * (at most 11, as 1.19 searches), 0 without Caves &amp; Cliffs
     */
    static int stalactiteSegments(World world, BlockPos support) {
        Block dripstone = Owners.present(Owners.CAVES_AND_CLIFFS) ? Owners.block(Owners.CAVES_AND_CLIFFS, "pointed_dripstone") : null;
        if (dripstone == null || world.getBlockState(support).getBlock() == dripstone) {
            return 0;
        }
        int n = 0;
        for (BlockPos p = support.down(); n < 11; p = p.down()) {
            IBlockState s = world.getBlockState(p);
            if (s.getBlock() != dripstone || !pointsDown(s)) {
                break;
            }
            n++;
        }
        return n;
    }

    private static boolean pointsDown(IBlockState state) {
        for (IProperty<?> p : state.getPropertyKeys()) {
            if (p.getName().equals("vertical_direction") || p.getName().equals("tip_direction")) {
                return "down".equals(state.getValue(p).toString().toLowerCase(java.util.Locale.ROOT));
            }
        }
        return false;
    }
}
