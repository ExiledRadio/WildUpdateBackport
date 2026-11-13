package com.exiledradio.wildupdatebackport.sculk;

import com.exiledradio.wildupdatebackport.network.NetworkHandler;

import io.netty.buffer.ByteBuf;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Packet 25: sculk's client effects, 1.19's level events and particles.
 *
 * <ul>
 *   <li>{@link #CHARGE}: level event 3006, a charge moving (data: strength in bits 6+, the faces it travels on in
 *       bits 0-5) or spent (data 0: the pop and the charge sound);</li>
 *   <li>{@link #SHRIEK}: level event 3007, the shriek's rings and sound;</li>
 *   <li>{@link #SOUL}: a catalyst's bloom, souls rising off its top (data: how many).</li>
 * </ul>
 * One packet carries every entry of a step, sent to players within 64 blocks.
 */
public class SculkEffectPacket implements IMessage {

    public static final byte CHARGE = 0;
    public static final byte SHRIEK = 1;
    public static final byte SOUL = 2;

    /** One effect at one block. */
    public static final class Entry {
        public final byte type;
        public final BlockPos pos;
        public final int data;

        public Entry(byte type, BlockPos pos, int data) {
            this.type = type;
            this.pos = pos;
            this.data = data;
        }
    }

    public List<Entry> entries = new ArrayList<Entry>();

    public SculkEffectPacket() {
    }

    public SculkEffectPacket(List<Entry> entries) {
        this.entries = entries;
    }

    /** Sends effects to the players near {@code around}; nothing when the list is empty or the world is a client's. */
    public static void send(World world, BlockPos around, List<Entry> entries) {
        if (world.isRemote || entries.isEmpty() || NetworkHandler.channel == null) {
            return;
        }
        NetworkHandler.channel.sendToAllAround(new SculkEffectPacket(new ArrayList<Entry>(entries)),
                new NetworkRegistry.TargetPoint(world.provider.getDimension(), around.getX() + 0.5D, around.getY() + 0.5D,
                        around.getZ() + 0.5D, 64.0D));
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int n = buf.readUnsignedShort();
        this.entries = new ArrayList<Entry>(n);
        for (int i = 0; i < n; i++) {
            byte type = buf.readByte();
            BlockPos pos = BlockPos.fromLong(buf.readLong());
            this.entries.add(new Entry(type, pos, buf.readInt()));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        int n = Math.min(this.entries.size(), 65535);
        buf.writeShort(n);
        for (int i = 0; i < n; i++) {
            Entry e = this.entries.get(i);
            buf.writeByte(e.type);
            buf.writeLong(e.pos.toLong());
            buf.writeInt(e.data);
        }
    }

    /** Hands the packet to the client's effect code, which sets {@link #client}; names no client type. */
    public static class Handler implements IMessageHandler<SculkEffectPacket, IMessage> {
        public static Consumer<SculkEffectPacket> client = packet -> {
        };

        @Override
        public IMessage onMessage(SculkEffectPacket message, MessageContext ctx) {
            client.accept(message);
            return null;
        }
    }
}
