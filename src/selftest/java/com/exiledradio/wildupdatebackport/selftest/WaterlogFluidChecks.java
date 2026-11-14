package com.exiledradio.wildupdatebackport.selftest;

import git.jbredwards.fluidlogged_api.api.util.FluidState;
import git.jbredwards.fluidlogged_api.api.util.FluidloggedUtils;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidRegistry;

import javax.annotation.Nullable;

/** Fluidlogged API's own answers, for {@link WaterlogSelfTest}; loaded only when Fluidlogged API is installed. */
final class WaterlogFluidChecks {

    private WaterlogFluidChecks() {
    }

    /** @return "flowing water" or "lava" when Fluidlogged API would let it into this state, else null */
    @Nullable
    static String otherFluids(IBlockState state, World world, BlockPos pos) {
        FluidState source = FluidState.of(FluidRegistry.WATER);
        if (FluidloggedUtils.isStateFluidloggable(state, world, pos, source.withLevel(3))) {
            return "flowing water";
        }
        if (FluidloggedUtils.isStateFluidloggable(state, world, pos, FluidState.of(FluidRegistry.LAVA))) {
            return "lava";
        }
        return null;
    }
}
