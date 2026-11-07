package com.exiledradio.wildupdatebackport.gameevent;

import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;

/** Something that hears game events within a radius. 1.19's {@code GameEventListener}. */
public interface GameEventListener {

    PositionSource position();

    int radius();

    /**
     * @param origin where the event happened
     * @return true if this listener took the event
     */
    boolean handle(WorldServer world, GameEvent event, Vec3d origin, GameEventContext context);

    /**
     * 1.19's {@code handleEventsImmediately}: true for listeners that must hear an event as it happens (the
     * sculk catalyst, which needs the dying mob's experience before it drops). Everything else hears the
     * tick's events at the end of the tick, closest first.
     */
    default boolean immediate() {
        return false;
    }
}
