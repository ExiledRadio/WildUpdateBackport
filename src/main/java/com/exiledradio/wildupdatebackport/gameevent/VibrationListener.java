package com.exiledradio.wildupdatebackport.gameevent;

import com.exiledradio.wildupdatebackport.network.NetworkHandler;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.network.NetworkRegistry;

import javax.annotation.Nullable;
import java.util.Set;
import java.util.UUID;

/**
 * 1.19.2's vibration rules, for sculk sensors, shriekers, wardens and allays.
 *
 * <ul>
 *   <li>Only events in the listener's set ({@code #vibrations} by default) are heard.</li>
 *   <li>A spectator makes none; a sneaking entity makes none of {@code #ignore_vibrations_sneaking} (and may
 *       earn <i>Sneak 100</i>); a dropped wool item makes none; an event touching wool or a carpet makes none.</li>
 *   <li>Occlusion: the vibration is blocked when the line from each of the six points just off the event's
 *       block center to the listener's block center passes through wool.</li>
 *   <li>One at a time: while a vibration travels to it, the listener hears nothing else. The dispatcher hands
 *       out each tick's events closest first, so the nearest wins.</li>
 *   <li>Travel time: one tick per block of distance, shown by the vibration particle; then the owner's
 *       {@link Config#onSignalReceive} runs. A vibration in flight is saved with its owner.</li>
 * </ul>
 */
public class VibrationListener implements GameEventListener {

    /** What owns a vibration listener decides what it listens to and what hearing does. */
    public interface Config {
        default Set<GameEvent> listenableEvents() {
            return GameEventTags.VIBRATIONS;
        }

        /** True for sensors and wardens: sneaking past them earns <i>Sneak 100</i>. */
        default boolean canTriggerAvoidVibration() {
            return false;
        }

        boolean shouldListen(WorldServer world, VibrationListener listener, BlockPos origin, GameEvent event, GameEventContext context);

        void onSignalReceive(WorldServer world, VibrationListener listener, BlockPos origin, GameEvent event,
                             @Nullable Entity source, @Nullable Entity projectileOwner, float distance);

        default void onSignalSchedule() {
        }
    }

    private final PositionSource position;
    private final int radius;
    private final Config config;

    @Nullable
    private GameEvent receiving;
    private Vec3d receivingFrom = Vec3d.ZERO;
    private float receivingDistance;
    private int travelTicks;
    @Nullable
    private UUID sourceId;
    @Nullable
    private UUID ownerId;
    @Nullable
    private Entity sourceEntity;

    public VibrationListener(PositionSource position, int radius, Config config) {
        this.position = position;
        this.radius = radius;
        this.config = config;
    }

    @Override
    public PositionSource position() {
        return this.position;
    }

    @Override
    public int radius() {
        return this.radius;
    }

    /** @return true while a vibration travels to this listener */
    public boolean isReceiving() {
        return this.receiving != null;
    }

    /** The owner's tick: counts down a vibration in flight and delivers it on arrival. */
    public void tick(World world) {
        if (!(world instanceof WorldServer) || this.receiving == null) {
            return;
        }
        if (--this.travelTicks <= 0) {
            this.travelTicks = 0;
            WorldServer server = (WorldServer) world;
            Entity source = this.sourceEntity;
            if (source == null && this.sourceId != null) {
                source = server.getEntityFromUuid(this.sourceId);
            }
            Entity owner = this.ownerId != null ? server.getEntityFromUuid(this.ownerId) : projectileOwner(source);
            GameEvent event = this.receiving;
            this.receiving = null;
            this.sourceEntity = null;
            this.config.onSignalReceive(server, this, new BlockPos(this.receivingFrom), event, source, owner, this.receivingDistance);
        }
    }

    @Override
    public boolean handle(WorldServer world, GameEvent event, Vec3d origin, GameEventContext context) {
        if (this.receiving != null || !isValidVibration(event, context)) {
            return false;
        }
        Vec3d here = this.position.position(world);
        if (here == null || !this.config.shouldListen(world, this, new BlockPos(origin), event, context)) {
            return false;
        }
        if (isOccluded(world, origin, here)) {
            return false;
        }
        this.receivingDistance = (float) origin.distanceTo(here);
        this.receiving = event;
        this.receivingFrom = origin;
        this.sourceEntity = context.source;
        this.sourceId = context.source != null ? context.source.getUniqueID() : null;
        Entity owner = projectileOwner(context.source);
        this.ownerId = owner != null ? owner.getUniqueID() : null;
        this.travelTicks = MathHelper.floor(this.receivingDistance);
        NetworkHandler.channel.sendToAllAround(new VibrationPacket(origin, this.position, this.travelTicks),
                new NetworkRegistry.TargetPoint(world.provider.getDimension(), origin.x, origin.y, origin.z, 64.0D));
        this.config.onSignalSchedule();
        return true;
    }

    private boolean isValidVibration(GameEvent event, GameEventContext context) {
        if (!this.config.listenableEvents().contains(event)) {
            return false;
        }
        Entity source = context.source;
        if (source != null) {
            if (source instanceof EntityPlayer && ((EntityPlayer) source).isSpectator()) {
                return false;
            }
            if (source.isSneaking() && GameEventTags.IGNORE_VIBRATIONS_SNEAKING.contains(event)) {
                if (this.config.canTriggerAvoidVibration() && source instanceof EntityPlayerMP) {
                    AvoidVibrationTrigger.INSTANCE.trigger((EntityPlayerMP) source);
                }
                return false;
            }
            if (GameEventTags.dampens(source)) {
                return false;
            }
        }
        IBlockState state = context.state;
        return state == null || !GameEventTags.dampens(state);
    }

    @Nullable
    static Entity projectileOwner(@Nullable Entity e) {
        if (e instanceof EntityArrow) {
            return ((EntityArrow) e).shootingEntity;
        }
        if (e instanceof EntityThrowable) {
            EntityLivingBase thrower = ((EntityThrowable) e).getThrower();
            return thrower;
        }
        if (e instanceof EntityFireball) {
            return ((EntityFireball) e).shootingEntity;
        }
        return null;
    }

    /** 1.19.2's {@code isOccluded}: wool on every one of the six lines. */
    static boolean isOccluded(World world, Vec3d from, Vec3d to) {
        Vec3d a = new Vec3d(Math.floor(from.x) + 0.5D, Math.floor(from.y) + 0.5D, Math.floor(from.z) + 0.5D);
        Vec3d b = new Vec3d(Math.floor(to.x) + 0.5D, Math.floor(to.y) + 0.5D, Math.floor(to.z) + 0.5D);
        for (EnumFacing f : EnumFacing.values()) {
            Vec3d start = a.add(f.getXOffset() * 1.0E-5D, f.getYOffset() * 1.0E-5D, f.getZOffset() * 1.0E-5D);
            if (!woolInLine(world, start, b)) {
                return false;
            }
        }
        return true;
    }

    /** @return true if the segment passes through a block that occludes vibrations (a voxel walk) */
    static boolean woolInLine(World world, Vec3d from, Vec3d to) {
        int x = MathHelper.floor(from.x);
        int y = MathHelper.floor(from.y);
        int z = MathHelper.floor(from.z);
        int ex = MathHelper.floor(to.x);
        int ey = MathHelper.floor(to.y);
        int ez = MathHelper.floor(to.z);
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        int sx = dx > 0 ? 1 : dx < 0 ? -1 : 0;
        int sy = dy > 0 ? 1 : dy < 0 ? -1 : 0;
        int sz = dz > 0 ? 1 : dz < 0 ? -1 : 0;
        double tdx = sx == 0 ? Double.MAX_VALUE : Math.abs(1.0D / dx);
        double tdy = sy == 0 ? Double.MAX_VALUE : Math.abs(1.0D / dy);
        double tdz = sz == 0 ? Double.MAX_VALUE : Math.abs(1.0D / dz);
        double tx = sx == 0 ? Double.MAX_VALUE : tdx * (sx > 0 ? (x + 1 - from.x) : (from.x - x));
        double ty = sy == 0 ? Double.MAX_VALUE : tdy * (sy > 0 ? (y + 1 - from.y) : (from.y - y));
        double tz = sz == 0 ? Double.MAX_VALUE : tdz * (sz > 0 ? (z + 1 - from.z) : (from.z - z));
        for (int steps = 0; steps < 200; steps++) {
            if (GameEventTags.occludes(world.getBlockState(new BlockPos(x, y, z)))) {
                return true;
            }
            if (x == ex && y == ey && z == ez) {
                return false;
            }
            if (tx <= ty && tx <= tz) {
                if (tx > 1.0D) {
                    return false;
                }
                x += sx;
                tx += tdx;
            } else if (ty <= tz) {
                if (ty > 1.0D) {
                    return false;
                }
                y += sy;
                ty += tdy;
            } else {
                if (tz > 1.0D) {
                    return false;
                }
                z += sz;
                tz += tdz;
            }
        }
        return false;
    }

    // --- saving -------------------------------------------------------------------------------

    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        if (this.receiving != null) {
            tag.setString("event", this.receiving.name().toString());
            tag.setFloat("distance", this.receivingDistance);
            tag.setDouble("x", this.receivingFrom.x);
            tag.setDouble("y", this.receivingFrom.y);
            tag.setDouble("z", this.receivingFrom.z);
            tag.setInteger("travel", this.travelTicks);
            if (this.sourceId != null) {
                tag.setUniqueId("source", this.sourceId);
            }
            if (this.ownerId != null) {
                tag.setUniqueId("owner", this.ownerId);
            }
        }
        return tag;
    }

    public void readFromNBT(NBTTagCompound tag) {
        this.receiving = tag.hasKey("event", 8) ? GameEvent.get(new ResourceLocation(tag.getString("event"))) : null;
        if (this.receiving != null) {
            this.receivingDistance = tag.getFloat("distance");
            this.receivingFrom = new Vec3d(tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"));
            this.travelTicks = tag.getInteger("travel");
            this.sourceId = tag.hasUniqueId("source") ? tag.getUniqueId("source") : null;
            this.ownerId = tag.hasUniqueId("owner") ? tag.getUniqueId("owner") : null;
        }
    }
}
