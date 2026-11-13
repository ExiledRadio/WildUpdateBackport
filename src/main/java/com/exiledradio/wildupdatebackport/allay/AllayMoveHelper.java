package com.exiledradio.wildupdatebackport.allay;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityMoveHelper;
import net.minecraft.util.math.MathHelper;

/**
 * 1.19's {@code FlyingMoveControl(mob, 20, true)}: turns up to 90 degrees a tick toward its target, pitches
 * up to 20, and hovers in place when it has nowhere to go. 1.12.2's {@code EntityFlyHelper} turns more
 * slowly and lets the mob fall as soon as it stops.
 */
final class AllayMoveHelper extends EntityMoveHelper {

    private static final float MAX_TURN = 20.0F;

    AllayMoveHelper(EntityLiving entity) {
        super(entity);
    }

    @Override
    public void onUpdateMoveHelper() {
        if (this.action == Action.MOVE_TO) {
            this.action = Action.WAIT;
            this.entity.setNoGravity(true);
            double dx = this.posX - this.entity.posX;
            double dy = this.posY - this.entity.posY;
            double dz = this.posZ - this.entity.posZ;
            double d = dx * dx + dy * dy + dz * dz;
            if (d < 2.5000003E-7D) {
                this.entity.setMoveVertical(0.0F);
                this.entity.setMoveForward(0.0F);
                return;
            }
            float yaw = (float) (MathHelper.atan2(dz, dx) * (180D / Math.PI)) - 90.0F;
            this.entity.rotationYaw = this.limitAngle(this.entity.rotationYaw, yaw, 90.0F);
            float speed = this.entity.onGround
                    ? (float) (this.speed * this.entity.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).getAttributeValue())
                    : (float) (this.speed * this.entity.getEntityAttribute(SharedMonsterAttributes.FLYING_SPEED).getAttributeValue());
            this.entity.setAIMoveSpeed(speed);
            double h = MathHelper.sqrt(dx * dx + dz * dz);
            if (Math.abs(dy) > 1.0E-5D || Math.abs(h) > 1.0E-5D) {
                float pitch = (float) (-(MathHelper.atan2(dy, h) * (180D / Math.PI)));
                this.entity.rotationPitch = this.limitAngle(this.entity.rotationPitch, pitch, MAX_TURN);
                this.entity.setMoveVertical(dy > 0.0D ? speed : -speed);
            }
        } else {
            // hovers in place: gravity stays off
            this.entity.setMoveVertical(0.0F);
            this.entity.setMoveForward(0.0F);
        }
    }
}
