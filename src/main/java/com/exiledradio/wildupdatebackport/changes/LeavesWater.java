package com.exiledradio.wildupdatebackport.changes;

import com.exiledradio.wildupdatebackport.ModConfig;

import git.jbredwards.fluidlogged_api.api.event.FluidloggableEvent;
import git.jbredwards.fluidlogged_api.api.util.FluidloggedUtils;

import net.minecraft.block.BlockLeaves;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * All leaves waterloggable (1.19, 22w12a: "Leaves are now waterloggable"), with Fluidlogged API: any {@link
 * BlockLeaves} takes a water source, as 1.19's {@code LeavesBlock} is a {@code SimpleWaterloggedBlock}. That includes
 * the leaves of other family mods (Caves &amp; Cliffs' azalea leaves), which Backport Core's rules otherwise keep dry
 * as 1.18 has them: this answers after Core's (receiving its refusal) and before Fluidlogged API's own lists. Vanilla's
 * leaves Fluidlogged API waterlogs by itself (tested), and mangrove leaves are waterloggable by themselves.
 * Registered by name only with Fluidlogged API installed.
 */
public final class LeavesWater {

    private LeavesWater() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH, receiveCanceled = true)
    public static void fluidloggable(FluidloggableEvent event) {
        if (!ChangesHooks.on(ModConfig.changes.leavesWaterlog) || !(event.state.getBlock() instanceof BlockLeaves)
                || event.fluidState.isEmpty()) {
            return;
        }
        if (event.fluidState.isSource() && FluidloggedUtils.isCompatibleFluid(event.fluidState.getFluid(), FluidRegistry.WATER)) {
            event.setCanceled(false);
            event.setResult(Event.Result.ALLOW);
        }
    }
}
