package com.exiledradio.wildupdatebackport.frog;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityMoveHelper;
import net.minecraft.util.math.MathHelper;

/**
 * 1.19's {@code SmoothSwimmingMoveControl}, which frogs and tadpoles use on land and in water.
 *
 * <p>While the mob has a path it turns toward the next point (at most {@code maxTurnY} degrees a tick) and
 * moves at speed x {@code outsideWaterSpeed} on land, or in water at speed x {@code inWaterSpeed}, pitched
 * toward the point (at most {@code maxTurnX} degrees) so it swims up and down. In water it drifts up by
 * 0.005 a tick. Unlike 1.12.2's own move helper it never jumps: these mobs step up whole blocks instead.
 */
public class SmoothSwimMoveHelper extends EntityMoveHelper {

    private final int maxTurnX;
    private final int maxTurnY;
    private final float inWaterSpeed;
    private final float outsideWaterSpeed;
    private final boolean applyGravity;

    public SmoothSwimMoveHelper(EntityLiving mob, int maxTurnX, int maxTurnY, float inWaterSpeed, float outsideWaterSpeed, boolean applyGravity) {
        super(mob);
        this.maxTurnX = maxTurnX;
        this.maxTurnY = maxTurnY;
        this.inWaterSpeed = inWaterSpeed;
        this.outsideWaterSpeed = outsideWaterSpeed;
        this.applyGravity = applyGravity;
    }

    /** @return true while the mob follows a path (frogs have two navigators, see {@link EntityFrog}) */
    protected boolean navigating() {
        return !this.entity.getNavigator().noPath();
    }

    @Override
    public void onUpdateMoveHelper() {
        if (this.applyGravity && this.entity.isInWater()) {
            this.entity.motionY += 0.005D;
        }
        if (this.action == Action.MOVE_TO && navigating()) {
            double dx = this.posX - this.entity.posX;
            double dy = this.posY - this.entity.posY;
            double dz = this.posZ - this.entity.posZ;
            double d2 = dx * dx + dy * dy + dz * dz;
            if (d2 < 2.5000003E-7F) {
                this.entity.setMoveForward(0.0F);
                return;
            }
            float yaw = (float) (MathHelper.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
            this.entity.rotationYaw = this.limitAngle(this.entity.rotationYaw, yaw, this.maxTurnY);
            this.entity.renderYawOffset = this.entity.rotationYaw;
            this.entity.rotationYawHead = this.entity.rotationYaw;
            float speed = (float) (this.speed * this.entity.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).getAttributeValue());
            if (this.entity.isInWater()) {
                this.entity.setAIMoveSpeed(speed * this.inWaterSpeed);
                double h = Math.sqrt(dx * dx + dz * dz);
                if (Math.abs(dy) > 1.0E-5F || Math.abs(h) > 1.0E-5F) {
                    float pitch = -((float) (MathHelper.atan2(dy, h) * (180.0D / Math.PI)));
                    pitch = MathHelper.clamp(MathHelper.wrapDegrees(pitch), -this.maxTurnX, this.maxTurnX);
                    this.entity.rotationPitch = this.limitAngle(this.entity.rotationPitch, pitch, 5.0F);
                }
                float cos = MathHelper.cos(this.entity.rotationPitch * 0.017453292F);
                float sin = MathHelper.sin(this.entity.rotationPitch * 0.017453292F);
                this.entity.moveForward = cos * speed;
                this.entity.moveVertical = -sin * speed;
            } else {
                this.entity.setAIMoveSpeed(speed * this.outsideWaterSpeed);
            }
        } else {
            this.entity.setAIMoveSpeed(0.0F);
            this.entity.setMoveStrafing(0.0F);
            this.entity.setMoveVertical(0.0F);
            this.entity.setMoveForward(0.0F);
        }
    }

    /** Points the mob at a position directly, without a path (frogs' and tadpoles' swimming). */
    public void setAction(Action action) {
        this.action = action;
    }
}
