package com.exiledradio.wildupdatebackport.goat;

import com.exiledradio.wildupdatebackport.common.Owners;

import net.minecraft.block.Block;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * The long jump, as 1.17's {@code LongJumpToRandomPos}.
 *
 * <p>When the jump cooldown runs out and the goat stands idle on the ground, it lists every place within 5
 * blocks (each way, up and down) it could stand on and reach with a jump of at most 1.5 blocks per tick,
 * taking off at 65 to 80 degrees, with nothing in the way of the arc. For up to 20 ticks it draws one of them,
 * farther ones more often, and keeps the first it cannot simply walk to. It looks there for two seconds and
 * jumps; the flight is the goat's own ({@link EntityGoat#startLongJump}). With nowhere to jump to it waits
 * half a cooldown.
 */
public class EntityAIGoatLongJump extends EntityAIBase {

    public static final int MAX_HEIGHT = 5, MAX_WIDTH = 5;
    public static final float MAX_VELOCITY = 1.5F;
    private static final int TRIES = 20, PREPARE_TICKS = 40, TIMEOUT = 200;
    private static final double GRAVITY = 0.08D;

    private final EntityGoat goat;

    private static final class Jump {
        final BlockPos target;
        final Vec3d velocity;
        final int weight;

        Jump(BlockPos target, Vec3d velocity, int weight) {
            this.target = target;
            this.velocity = velocity;
            this.weight = weight;
        }
    }

    private final List<Jump> candidates = new ArrayList<Jump>();
    private Jump chosen;
    private Vec3d initialPosition;
    private int tries;
    private int startTick, prepareStart;
    private boolean jumped;

    public EntityAIGoatLongJump(EntityGoat goat) {
        this.goat = goat;
        this.setMutexBits(3);
    }

    @Override
    public boolean shouldExecute() {
        if (this.goat.getJumpCooldown() != 0 || this.goat.isMidJump() || !this.goat.onGround || !this.goat.getNavigator().noPath()) {
            return false;
        }
        // No long jumps off honey (when a mod has a honey block).
        Block honey = Owners.block(Owners.BUZZY_BEES, "honey_block");
        return honey == null || this.goat.world.getBlockState(new BlockPos(this.goat)).getBlock() != honey;
    }

    @Override
    public void startExecuting() {
        this.chosen = null;
        this.jumped = false;
        this.tries = TRIES;
        this.startTick = this.goat.ticksExisted;
        this.candidates.clear();
        this.initialPosition = this.goat.getPositionVector();
        BlockPos at = new BlockPos(this.goat);
        for (BlockPos.MutableBlockPos p : BlockPos.getAllInBoxMutable(at.add(-MAX_WIDTH, -MAX_HEIGHT, -MAX_WIDTH), at.add(MAX_WIDTH, MAX_HEIGHT, MAX_WIDTH))) {
            if ((p.getX() != at.getX() || p.getZ() != at.getZ()) && EntityAIGoatRam.standable(this.goat, p)) {
                Vec3d velocity = this.bestVelocity(new Vec3d(p.getX() + 0.5D, p.getY() + 0.5D, p.getZ() + 0.5D));
                if (velocity != null) {
                    this.candidates.add(new Jump(p.toImmutable(), velocity, MathHelper.ceil(p.distanceSq(at))));
                }
            }
        }
    }

    @Override
    public boolean shouldContinueExecuting() {
        if (this.goat.isMidJump()) {
            // Holds the goat's legs until it lands, so nothing steers it in the air.
            return true;
        }
        return !this.jumped && this.goat.ticksExisted - this.startTick < TIMEOUT && this.initialPosition.equals(this.goat.getPositionVector())
                && this.tries > 0 && (this.chosen != null || !this.candidates.isEmpty());
    }

    @Override
    public void resetTask() {
        if (!this.jumped && !this.goat.isMidJump()) {
            this.goat.setJumpCooldown(this.goat.nextJumpCooldown() / 2);
        }
        this.candidates.clear();
        this.chosen = null;
    }

    @Override
    public void updateTask() {
        if (this.goat.isMidJump()) {
            return;
        }
        if (this.chosen != null) {
            BlockPos t = this.chosen.target;
            this.goat.getLookHelper().setLookPosition(t.getX() + 0.5D, t.getY() + 0.5D, t.getZ() + 0.5D, 30.0F, 30.0F);
            if (this.goat.ticksExisted - this.prepareStart >= PREPARE_TICKS) {
                this.jumped = true;
                this.goat.startLongJump(this.chosen.velocity, this.chosen.target);
            }
            return;
        }
        this.tries--;
        Jump drawn = this.draw();
        if (drawn != null) {
            this.candidates.remove(drawn);
            // Only somewhere it cannot walk to is worth a jump.
            if (!EntityAIGoatRam.reaches(this.goat.getNavigator().getPathToPos(drawn.target), drawn.target)) {
                this.chosen = drawn;
                this.prepareStart = this.goat.ticksExisted;
            }
        }
    }

    private Jump draw() {
        int total = 0;
        for (Jump j : this.candidates) {
            total += j.weight;
        }
        if (total <= 0) {
            return null;
        }
        int roll = this.goat.getRNG().nextInt(total);
        for (Jump j : this.candidates) {
            roll -= j.weight;
            if (roll < 0) {
                return j;
            }
        }
        return null;
    }

    /** @return the slowest of the jumps at 65, 70, 75 and 80 degrees that gets there, or null */
    private Vec3d bestVelocity(Vec3d target) {
        Vec3d best = null;
        for (int angle = 65; angle < 85; angle += 5) {
            Vec3d v = this.velocityForAngle(target, angle);
            if (best == null || v != null && v.lengthSquared() < best.lengthSquared()) {
                best = v;
            }
        }
        return best;
    }

    /** 1.17's projectile math, unchanged: the launch speed for an angle, and a walk along the arc for obstacles. */
    private Vec3d velocityForAngle(Vec3d target, int degrees) {
        Vec3d from = this.goat.getPositionVector();
        Vec3d back = new Vec3d(target.x - from.x, 0.0D, target.z - from.z).normalize().scale(0.5D);
        target = target.subtract(back);
        Vec3d delta = target.subtract(from);
        float angle = (float) degrees * (float) Math.PI / 180.0F;
        double yaw = Math.atan2(delta.z, delta.x);
        double flatSq = delta.x * delta.x + delta.z * delta.z;
        double flat = Math.sqrt(flatSq);
        double rise = delta.y;
        double sin2 = Math.sin((double) (2.0F * angle));
        double cosSq = Math.pow(Math.cos((double) angle), 2.0D);
        double sin = Math.sin((double) angle);
        double cos = Math.cos((double) angle);
        double sinYaw = Math.sin(yaw);
        double cosYaw = Math.cos(yaw);
        double speedSq = flatSq * GRAVITY / (flat * sin2 - 2.0D * rise * cosSq);
        if (speedSq < 0.0D) {
            return null;
        }
        double speed = Math.sqrt(speedSq);
        if (speed > (double) MAX_VELOCITY) {
            return null;
        }
        double forward = speed * cos;
        double up = speed * sin;
        int steps = MathHelper.ceil(flat / forward) * 2;
        double along = 0.0D;
        Vec3d last = null;
        for (int i = 0; i < steps - 1; i++) {
            along += flat / (double) steps;
            double height = sin / cos * along - Math.pow(along, 2.0D) * GRAVITY / (2.0D * speedSq * Math.pow(cos, 2.0D));
            Vec3d point = new Vec3d(from.x + along * cosYaw, from.y + height, from.z + along * sinYaw);
            if (last != null && !this.clear(last, point)) {
                return null;
            }
            last = point;
        }
        return new Vec3d(forward * cosYaw, up, forward * sinYaw).scale((double) 0.95F);
    }

    /** @return whether the goat, at its jumping size, fits everywhere between two points of the arc */
    private boolean clear(Vec3d from, Vec3d to) {
        float scale = this.goat.isChild() ? 0.5F : 1.0F;
        double width = EntityGoat.WIDTH * EntityGoat.JUMP_SCALE * scale;
        double height = EntityGoat.HEIGHT * EntityGoat.JUMP_SCALE * scale;
        Vec3d delta = to.subtract(from);
        double stride = Math.min(width, height);
        int steps = MathHelper.ceil(delta.length() / stride);
        Vec3d step = delta.normalize().scale(stride * (double) 0.9F);
        Vec3d at = from;
        for (int i = 0; i < steps; i++) {
            at = i == steps - 1 ? to : at.add(step);
            AxisAlignedBB box = new AxisAlignedBB(at.x - width / 2.0D, at.y, at.z - width / 2.0D, at.x + width / 2.0D, at.y + height, at.z + width / 2.0D);
            if (!this.goat.world.getCollisionBoxes(this.goat, box).isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
