package com.exiledradio.wildupdatebackport.network;

import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;

/**
 * The mod's one network channel.
 *
 * <h2>Packet ids</h2>
 * Each stream owns a block of five ids (see {@code docs/PLAN.md}) and registers its packets here, inside
 * its own block, so two streams can never pick the same number and merges stay mechanical:
 *
 * <pre>
 *   0-4   common          25-29 sculk         50-54 changes
 *   5-9   vibrations      30-34 frogs         55-59 swamp
 *   10-14 mangrove        35-39 allay         60-64 deep dark
 *   15-19 mud             40-44 warden        65-69 waterlogging
 *   20-24 boats           45-49 items
 * </pre>
 *
 * <p>The channel name is short because Forge caps channel names at 20 characters, one fewer than the
 * mod id.
 */
public final class NetworkHandler {

    public static final String CHANNEL = "wubackport";

    public static SimpleNetworkWrapper channel;

    private NetworkHandler() {
    }

    public static void init() {
        channel = NetworkRegistry.INSTANCE.newSimpleChannel(CHANNEL);
        // Streams register their packets below, in id order.
        channel.registerMessage(com.exiledradio.wildupdatebackport.gameevent.VibrationPacket.Handler.class,
                com.exiledradio.wildupdatebackport.gameevent.VibrationPacket.class, 5, net.minecraftforge.fml.relauncher.Side.CLIENT);
        channel.registerMessage(com.exiledradio.wildupdatebackport.items.LastDeathLocation.Handler.class,
                com.exiledradio.wildupdatebackport.items.LastDeathLocation.Packet.class, 45, net.minecraftforge.fml.relauncher.Side.CLIENT);
    }
}
