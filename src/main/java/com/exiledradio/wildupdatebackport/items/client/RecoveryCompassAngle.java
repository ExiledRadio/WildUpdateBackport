package com.exiledradio.wildupdatebackport.items.client;

import com.exiledradio.wildupdatebackport.items.LastDeathLocation;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.IItemPropertyGetter;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

import javax.annotation.Nullable;

/**
 * The recovery compass's {@code angle}: 1.19's {@code CompassItemPropertyFunction} with the
 * {@code getLastDeathLocation} target.
 *
 * <p>Held by the local player in the dimension where they last died, it points there, swinging toward it with
 * 1.19's wobble; anywhere else, with no death, held by anyone else or in an item frame, it spins (1.19's random
 * wobble plus a per-entity offset). An item with no holder and no frame shows 0.
 */
final class RecoveryCompassAngle implements IItemPropertyGetter {

    /** The local player's own last death location (1.19 sends only your own). */
    @Nullable
    static LastDeathLocation lastDeath;

    private final Wobble wobble = new Wobble();
    private final Wobble wobbleRandom = new Wobble();

    @Override
    public float apply(ItemStack stack, @Nullable World world, @Nullable EntityLivingBase holder) {
        Entity entity = holder != null ? holder : stack.getItemFrame();
        if (entity == null) {
            return 0.0F;
        }
        if (world == null) {
            world = entity.world;
        }
        if (world == null) {
            return 0.0F;
        }
        LastDeathLocation target = entity == Minecraft.getMinecraft().player ? lastDeath : null;
        long time = world.getTotalWorldTime();
        if (!valid(entity, target)) {
            return spinning(entity.getEntityId(), time);
        }
        return towards(entity, time, target.pos);
    }

    static boolean valid(Entity entity, @Nullable LastDeathLocation target) {
        if (target == null || target.dimension != entity.dimension) {
            return false;
        }
        BlockPos p = target.pos;
        return p.distanceSqToCenter(entity.posX, entity.posY, entity.posZ) >= 1.0E-5F;
    }

    private float spinning(int seed, long time) {
        if (this.wobbleRandom.shouldUpdate(time)) {
            this.wobbleRandom.update(time, Math.random());
        }
        double rotation = this.wobbleRandom.rotation + (double) ((float) (seed * 1327217883) / 2.1474836E9F);
        return MathHelper.positiveModulo((float) rotation, 1.0F);
    }

    private float towards(Entity entity, long time, BlockPos pos) {
        double angle = Math.atan2(pos.getZ() + 0.5D - entity.posZ, pos.getX() + 0.5D - entity.posX) / (Math.PI * 2.0D);
        double yaw = entity instanceof EntityLivingBase ? ((EntityLivingBase) entity).renderYawOffset : entity.rotationYaw;
        double visual = MathHelper.positiveModulo(yaw / 360.0D, 1.0D);
        if (entity == Minecraft.getMinecraft().player) {
            if (this.wobble.shouldUpdate(time)) {
                this.wobble.update(time, 0.5D - (visual - 0.25D));
            }
            return MathHelper.positiveModulo((float) (angle + this.wobble.rotation), 1.0F);
        }
        return MathHelper.positiveModulo((float) (0.5D - (visual - 0.25D - angle)), 1.0F);
    }

    /** 1.19's {@code CompassWobble}. */
    static final class Wobble {
        double rotation;
        private double deltaRotation;
        private long lastUpdateTick;

        boolean shouldUpdate(long time) {
            return this.lastUpdateTick != time;
        }

        void update(long time, double target) {
            this.lastUpdateTick = time;
            double d = target - this.rotation;
            d = MathHelper.positiveModulo(d + 0.5D, 1.0D) - 0.5D;
            this.deltaRotation += d * 0.1D;
            this.deltaRotation *= 0.8D;
            this.rotation = MathHelper.positiveModulo(this.rotation + this.deltaRotation, 1.0D);
        }
    }
}
