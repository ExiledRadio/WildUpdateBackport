package com.exiledradio.wildupdatebackport.sculk;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.Random;
import java.util.Set;

/**
 * 1.19's {@code SculkBehaviour}: how a block takes part in a sculk charge's journey. Sculk and the sculk vein
 * implement it; every other block a charge passes through uses {@link #DEFAULT}.
 */
public interface SculkBehaviour {

    /** Any other block: veins grow into the space (or onto the given faces), and the charge decays. */
    SculkBehaviour DEFAULT = new SculkBehaviour() {
        @Override
        public boolean attemptSpreadVein(World world, BlockPos pos, IBlockState state, @Nullable Set<EnumFacing> facings, boolean worldGen) {
            if (facings == null) {
                return SculkContent.veinBlock().sameSpaceSpreader().spreadAll(world, pos, worldGen) > 0L;
            } else if (!facings.isEmpty()) {
                // Into air, or a water source the vein keeps (1.19; only with Fluidlogged API).
                return (world.isAirBlock(pos) || BlockSculkVein.waterSource(world, pos)) && BlockSculkVein.regrow(world, pos, facings);
            }
            return SculkBehaviour.super.attemptSpreadVein(world, pos, state, facings, worldGen);
        }

        @Override
        public int attemptUseCharge(SculkSpreader.ChargeCursor cursor, World world, BlockPos catalyst, Random random,
                                    SculkSpreader spreader, boolean spreadVeins) {
            return cursor.getDecayDelay() > 0 ? cursor.getCharge() : 0;
        }

        @Override
        public int updateDecayDelay(int delay) {
            return Math.max(delay - 1, 0);
        }
    };

    default int sculkSpreadDelay() {
        return 1;
    }

    default void onDischarged(World world, IBlockState state, BlockPos pos, Random random) {
    }

    default boolean attemptSpreadVein(World world, BlockPos pos, IBlockState state, @Nullable Set<EnumFacing> facings, boolean worldGen) {
        return SculkContent.veinBlock().spreader().spreadAll(world, pos, worldGen) > 0L;
    }

    default boolean canChangeBlockStateOnSpread() {
        return true;
    }

    default int updateDecayDelay(int delay) {
        return 1;
    }

    int attemptUseCharge(SculkSpreader.ChargeCursor cursor, World world, BlockPos catalyst, Random random, SculkSpreader spreader,
                         boolean spreadVeins);
}
