package com.exiledradio.wildupdatebackport.warden;

import com.exiledradio.wildupdatebackport.network.NetworkHandler;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.function.Consumer;

/**
 * Packet 40: the sonic boom's line of particles. 1.19 sends one {@code sonic_boom} particle at each whole block along
 * the line from the warden's chest to the target's eyes (and 6 past it), each to the players within 32 blocks of it;
 * this sends the line once to every player within 32 blocks of any point on it.
 */
public class SonicBoomPacket implements IMessage {

    public Vec3d from = Vec3d.ZERO;
    public Vec3d dir = Vec3d.ZERO;
    /** Particles at {@code from + dir * i} for i = 1 .. count - 1. */
    public int count;

    public SonicBoomPacket() {
    }

    SonicBoomPacket(Vec3d from, Vec3d dir, int count) {
        this.from = from;
        this.dir = dir;
        this.count = count;
    }

    static void send(WorldServer world, Vec3d from, Vec3d dir, int count) {
        SonicBoomPacket packet = new SonicBoomPacket(from, dir, count);
        for (EntityPlayer player : world.playerEntities) {
            if (player instanceof EntityPlayerMP) {
                for (int i = 1; i < count; i++) {
                    Vec3d p = from.add(dir.scale(i));
                    if (player.getDistanceSq(p.x, p.y, p.z) < 32.0D * 32.0D) {
                        NetworkHandler.channel.sendTo(packet, (EntityPlayerMP) player);
                        break;
                    }
                }
            }
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.from = new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
        this.dir = new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
        this.count = buf.readShort();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeDouble(this.from.x);
        buf.writeDouble(this.from.y);
        buf.writeDouble(this.from.z);
        buf.writeDouble(this.dir.x);
        buf.writeDouble(this.dir.y);
        buf.writeDouble(this.dir.z);
        buf.writeShort(Math.min(this.count, Short.MAX_VALUE));
    }

    /** Hands the packet to the client's particle code; the client sets {@link #client} in its pre-init. */
    public static class Handler implements IMessageHandler<SonicBoomPacket, IMessage> {
        public static Consumer<SonicBoomPacket> client = packet -> {
        };

        @Override
        public IMessage onMessage(SonicBoomPacket message, MessageContext ctx) {
            client.accept(message);
            return null;
        }
    }
}
