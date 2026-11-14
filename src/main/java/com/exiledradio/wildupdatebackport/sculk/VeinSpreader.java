package com.exiledradio.wildupdatebackport.sculk;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * 1.19.2's {@code MultifaceSpreader} with the sculk vein's spreader config ({@code SculkVeinSpreaderConfig}): from
 * each face of a source, veins spread to the same block (another face), along the same plane into the next block,
 * or wrapped around the edge of the supporting block.
 *
 * <p>The vein's faces live in its tile entity, so this works on positions rather than states; a source's faces
 * are read once, before anything is placed, as vanilla reads them from the source state it was handed.
 */
public final class VeinSpreader {

    /** 1.19's {@code SpreadType}: where a face can spread to, from {@code pos} toward {@code dir}, off {@code face}. */
    public enum Type {
        SAME_POSITION {
            @Override
            BlockPos pos(BlockPos pos, EnumFacing dir, EnumFacing face) {
                return pos;
            }

            @Override
            EnumFacing face(EnumFacing dir, EnumFacing face) {
                return dir;
            }
        },
        SAME_PLANE {
            @Override
            BlockPos pos(BlockPos pos, EnumFacing dir, EnumFacing face) {
                return pos.offset(dir);
            }

            @Override
            EnumFacing face(EnumFacing dir, EnumFacing face) {
                return face;
            }
        },
        WRAP_AROUND {
            @Override
            BlockPos pos(BlockPos pos, EnumFacing dir, EnumFacing face) {
                return pos.offset(dir).offset(face);
            }

            @Override
            EnumFacing face(EnumFacing dir, EnumFacing face) {
                return dir.getOpposite();
            }
        };

        abstract BlockPos pos(BlockPos pos, EnumFacing dir, EnumFacing face);

        abstract EnumFacing face(EnumFacing dir, EnumFacing face);
    }

    public static final Type[] DEFAULT_SPREAD_ORDER = {Type.SAME_POSITION, Type.SAME_PLANE, Type.WRAP_AROUND};

    private final Type[] types;

    public VeinSpreader(Type... types) {
        this.types = types;
    }

    /**
     * Spreads from every face of the block at {@code pos} (from every side when it is not a vein) toward every
     * direction.
     *
     * @return how many faces were placed
     */
    public long spreadAll(World world, BlockPos pos, boolean worldGen) {
        IBlockState source = world.getBlockState(pos);
        boolean other = !BlockSculkVein.isVein(source);
        int mask = other ? 0 : BlockSculkVein.faces(world, pos);
        long placed = 0;
        for (EnumFacing face : EnumFacing.VALUES) {
            if (other || BlockSculkVein.has(mask, face)) {
                for (EnumFacing dir : EnumFacing.VALUES) {
                    if (spreadFromFaceTowardDirection(world, pos, other, mask, face, dir)) {
                        placed++;
                    }
                }
            }
        }
        return placed;
    }

    /**
     * 1.19's {@code spreadFromFaceTowardRandomDirection}: from one face of the vein at {@code pos}, toward the first
     * direction (in shuffled order) it can spread to. Used by the deep dark's {@code sculk_vein} feature (stream DD).
     *
     * @return true if a face was placed
     */
    public boolean spreadFromFaceTowardRandomDirection(World world, BlockPos pos, EnumFacing face, java.util.Random random) {
        IBlockState source = world.getBlockState(pos);
        boolean other = !BlockSculkVein.isVein(source);
        int mask = other ? 0 : BlockSculkVein.faces(world, pos);
        java.util.List<EnumFacing> dirs = new java.util.ArrayList<EnumFacing>(java.util.Arrays.asList(EnumFacing.VALUES));
        java.util.Collections.shuffle(dirs, random);
        for (EnumFacing dir : dirs) {
            if (spreadFromFaceTowardDirection(world, pos, other, mask, face, dir)) {
                return true;
            }
        }
        return false;
    }

    private boolean spreadFromFaceTowardDirection(World world, BlockPos pos, boolean other, int mask, EnumFacing face, EnumFacing dir) {
        if (dir.getAxis() == face.getAxis()) {
            return false;
        }
        if (!other && !(BlockSculkVein.has(mask, face) && !BlockSculkVein.has(mask, dir))) {
            return false;
        }
        for (Type type : this.types) {
            BlockPos to = type.pos(pos, dir, face);
            EnumFacing onto = type.face(dir, face);
            if (canSpreadInto(world, pos, to, onto)) {
                return place(world, to, onto);
            }
        }
        return false;
    }

    private static boolean canSpreadInto(World world, BlockPos source, BlockPos to, EnumFacing face) {
        IBlockState at = world.getBlockState(to);
        return stateCanBeReplaced(world, source, to, face, at) && isValidStateForPlacement(world, at, to, face);
    }

    /** {@code MultifaceBlock.isValidStateForPlacement}: the face is free and the block behind it holds a vein. */
    static boolean isValidStateForPlacement(World world, IBlockState at, BlockPos pos, EnumFacing face) {
        if (BlockSculkVein.isVein(at) && BlockSculkVein.has(BlockSculkVein.faces(world, pos), face)) {
            return false;
        }
        return BlockSculkVein.canAttachTo(world, face, pos.offset(face));
    }

    /** {@code SculkVeinSpreaderConfig.stateCanBeReplaced}. */
    private static boolean stateCanBeReplaced(World world, BlockPos source, BlockPos to, EnumFacing face, IBlockState at) {
        Block behind = world.getBlockState(to.offset(face)).getBlock();
        if (behind == SculkContent.sculkBlock() || behind == SculkContent.catalystBlock() || behind == Blocks.PISTON_EXTENSION) {
            return false;
        }
        int manhattan = Math.abs(source.getX() - to.getX()) + Math.abs(source.getY() - to.getY()) + Math.abs(source.getZ() - to.getZ());
        if (manhattan == 2) {
            BlockPos edge = source.offset(face.getOpposite());
            if (SculkSpreader.ChargeCursor.sturdy(world, edge, face)) {
                return false;
            }
        }
        Material m = at.getMaterial();
        // Lava, flowing water and every other fluid: never. A water source: the vein takes it and keeps the water
        // (1.19 waterlogs it), only with Fluidlogged API.
        if (m.isLiquid()) {
            return BlockSculkVein.waterSource(world, to);
        }
        if (m == Material.FIRE) {
            return false;
        }
        return m.isReplaceable() || at.getBlock().isAir(at, world, to) || BlockSculkVein.isVein(at);
    }

    private static boolean place(World world, BlockPos pos, EnumFacing face) {
        IBlockState at = world.getBlockState(pos);
        if (!isValidStateForPlacement(world, at, pos, face)) {
            return false;
        }
        int mask = BlockSculkVein.isVein(at) ? BlockSculkVein.faces(world, pos) : 0;
        return BlockSculkVein.set(world, pos, mask | BlockSculkVein.bit(face), 2);
    }
}
