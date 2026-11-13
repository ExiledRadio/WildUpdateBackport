package com.exiledradio.wildupdatebackport.items;

import com.exiledradio.wildupdatebackport.network.NetworkHandler;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerChangedDimensionEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerRespawnEvent;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * 1.19's per-player last death location ({@code Player.lastDeathLocation}), which the recovery compass points to.
 *
 * <p>Set where the player dies (their block position, in their dimension), copied to the respawned player as
 * 1.19's {@code restoreFrom} copies it, saved with the player (in Forge's player data as
 * {@code LastDeathLocation: {dimension, pos: [x, y, z]}}, 1.19's tag with 1.12.2's numeric dimension), and sent to
 * the player's own client on login, respawn and dimension change, as 1.19's login and respawn packets carry it.
 * Other players' locations never reach a client, as in 1.19.
 */
public final class LastDeathLocation {

    public static final String TAG = "LastDeathLocation";

    public final int dimension;
    public final BlockPos pos;

    public LastDeathLocation(int dimension, BlockPos pos) {
        this.dimension = dimension;
        this.pos = pos;
    }

    @Nullable
    public static LastDeathLocation get(EntityPlayer player) {
        NBTTagCompound data = player.getEntityData();
        if (!data.hasKey(TAG, 10)) {
            return null;
        }
        NBTTagCompound tag = data.getCompoundTag(TAG);
        int[] pos = tag.getIntArray("pos");
        if (pos.length != 3) {
            return null;
        }
        return new LastDeathLocation(tag.getInteger("dimension"), new BlockPos(pos[0], pos[1], pos[2]));
    }

    public static void set(EntityPlayer player, @Nullable LastDeathLocation where) {
        if (where == null) {
            player.getEntityData().removeTag(TAG);
            return;
        }
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("dimension", where.dimension);
        tag.setIntArray("pos", new int[] {where.pos.getX(), where.pos.getY(), where.pos.getZ()});
        player.getEntityData().setTag(TAG, tag);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof LastDeathLocation && ((LastDeathLocation) o).dimension == this.dimension
                && ((LastDeathLocation) o).pos.equals(this.pos);
    }

    @Override
    public int hashCode() {
        return this.pos.hashCode() * 31 + this.dimension;
    }

    @Override
    public String toString() {
        return this.dimension + "@" + this.pos;
    }

    // --- events (registered by ItemsContent) ---------------------------------------------------

    public static final class Events {

        /** 1.19 sets the location in {@code die}, after the death is certain: a cancelled death leaves it. */
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void death(LivingDeathEvent event) {
            if (event.isCanceled() || !(event.getEntityLiving() instanceof EntityPlayerMP) || event.getEntityLiving() instanceof FakePlayer) {
                return;
            }
            EntityPlayerMP player = (EntityPlayerMP) event.getEntityLiving();
            set(player, new LastDeathLocation(player.dimension, new BlockPos(player)));
        }

        /** 1.19's {@code restoreFrom}: kept through death and through the End's exit portal alike. */
        @SubscribeEvent
        public void clone(PlayerEvent.Clone event) {
            set(event.getEntityPlayer(), get(event.getOriginal()));
        }

        @SubscribeEvent
        public void login(PlayerLoggedInEvent event) {
            sync(event.player);
        }

        @SubscribeEvent
        public void respawn(PlayerRespawnEvent event) {
            sync(event.player);
        }

        @SubscribeEvent
        public void dimension(PlayerChangedDimensionEvent event) {
            sync(event.player);
        }
    }

    static void sync(EntityPlayer player) {
        if (player instanceof EntityPlayerMP && !(player instanceof FakePlayer) && ((EntityPlayerMP) player).connection != null) {
            NetworkHandler.channel.sendTo(new Packet(get(player)), (EntityPlayerMP) player);
        }
    }

    // --- packet 45 ----------------------------------------------------------------------------

    /** Server to client (packet 45): the player's own last death location, or none. */
    public static class Packet implements IMessage {

        @Nullable
        public LastDeathLocation where;

        public Packet() {
        }

        public Packet(@Nullable LastDeathLocation where) {
            this.where = where;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            this.where = buf.readBoolean() ? new LastDeathLocation(buf.readInt(), BlockPos.fromLong(buf.readLong())) : null;
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeBoolean(this.where != null);
            if (this.where != null) {
                buf.writeInt(this.where.dimension);
                buf.writeLong(this.where.pos.toLong());
            }
        }
    }

    /** Hands the location to the client, which sets {@link #client} in its pre-init; names no client type. */
    public static class Handler implements IMessageHandler<Packet, IMessage> {
        public static Consumer<LastDeathLocation> client = where -> {
        };

        @Override
        public IMessage onMessage(Packet message, MessageContext ctx) {
            client.accept(message.where);
            return null;
        }
    }
}
