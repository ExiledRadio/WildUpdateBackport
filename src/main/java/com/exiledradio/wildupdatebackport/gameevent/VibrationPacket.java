package com.exiledradio.wildupdatebackport.gameevent;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.function.Consumer;

/**
 * Server to client (packet 5): a vibration leaving {@code from} for a listener, arriving in {@code ticks}
 * ticks; the client draws 1.19's vibration particle along the way.
 */
public class VibrationPacket implements IMessage {

    public Vec3d from = Vec3d.ZERO;
    public NBTTagCompound target = new NBTTagCompound();
    public int ticks;

    public VibrationPacket() {
    }

    public VibrationPacket(Vec3d from, PositionSource target, int ticks) {
        this.from = from;
        target.write(this.target);
        this.ticks = ticks;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.from = new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
        this.target = ByteBufUtils.readTag(buf);
        this.ticks = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeDouble(this.from.x);
        buf.writeDouble(this.from.y);
        buf.writeDouble(this.from.z);
        ByteBufUtils.writeTag(buf, this.target);
        buf.writeInt(this.ticks);
    }

    /**
     * Hands the packet to the client's particle code. The client sets {@link #client} in its pre-init; this class
     * names no client type, so a dedicated server loads it safely.
     */
    public static class Handler implements IMessageHandler<VibrationPacket, IMessage> {
        public static Consumer<VibrationPacket> client = packet -> {
        };

        @Override
        public IMessage onMessage(VibrationPacket message, MessageContext ctx) {
            client.accept(message);
            return null;
        }
    }
}
