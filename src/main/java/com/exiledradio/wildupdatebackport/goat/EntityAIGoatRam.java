package com.exiledradio.wildupdatebackport.goat;

import com.exiledradio.wildupdatebackport.ModConfig;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.monster.EntityGhast;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.pathfinding.Path;
import net.minecraft.pathfinding.PathPoint;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.EnumDifficulty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Ramming, as 1.17's two behaviors (prepare, then ram) in one goal.
 *
 * <h2>Prepare</h2>
 * When the ram cooldown runs out, the goat picks the nearest player, armor stand or mob it can see within 16
 * blocks (never a goat or a ghast, never anyone in Peaceful, never a creative player). It looks for a place to
 * charge from: 4 to 7 blocks from the target in a straight line along x or z, over walkable ground all the
 * way, that it can walk to. It walks there, lowers its head for a second, and charges. If the target leaves
 * its block meanwhile, the goat starts over; with nobody to ram or nowhere to charge from, it gives up and
 * waits the shortest cooldown.
 *
 * <h2>Ram</h2>
 * It runs at the far edge of the target's block at three times its speed. The first valid target it touches
 * takes 2 damage (1 from a kid) without being angered, and is thrown back; a raised shield halves the throw.
 * Then, or if it arrives without touching anyone, the cooldown starts again.
 */
public class EntityAIGoatRam extends EntityAIBase {

    public static final int MIN_DISTANCE = 4, MAX_DISTANCE = 7, PREPARE_TICKS = 20;
    public static final int SEE_RANGE = 16;
    private static final int PREPARE_TIMEOUT = 160, RAM_TIMEOUT = 200;
    private static final double WALK_SPEED = 1.25D, RAM_SPEED = 3.0D;
    private static final float SPEED_TO_FORCE = 1.65F;

    private final EntityGoat goat;

    private EntityLivingBase target;
    /** Where the goat charges from and the block the target stood in when that was chosen; null with no plan. */
    private BlockPos startPos, targetPos;
    /** The goat's age in ticks when it reached {@link #startPos}, or -1. */
    private int reachedAt;
    /** The point the charge runs at; null while still preparing. */
    private Vec3d ramTarget;
    /** From the target toward the goat: the direction knockback is measured against. */
    private Vec3d ramDirection = Vec3d.ZERO;
    private int phaseStart;
    private boolean rammed;

    public EntityAIGoatRam(EntityGoat goat) {
        this.goat = goat;
        this.setMutexBits(3);
    }

    @Override
    public boolean shouldExecute() {
        return ModConfig.goats.ramming && this.goat.getRamCooldown() == 0 && !this.goat.isMidJump();
    }

    @Override
    public void startExecuting() {
        this.ramTarget = null;
        this.rammed = false;
        this.startPos = null;
        this.phaseStart = this.goat.ticksExisted;
        this.target = this.findTarget();
        if (this.target != null) {
            this.choosePosition();
        }
    }

    @Override
    public boolean shouldContinueExecuting() {
        if (this.rammed) {
            return false;
        }
        if (this.ramTarget != null) {
            return this.goat.ticksExisted - this.phaseStart < RAM_TIMEOUT;
        }
        return this.startPos != null && this.target.isEntityAlive() && this.goat.ticksExisted - this.phaseStart < PREPARE_TIMEOUT;
    }

    @Override
    public void resetTask() {
        if (!this.rammed) {
            // Gave up or was interrupted: the head comes up, and the goat tries again after the shortest wait
            // if it never charged, after a full one if it did.
            this.goat.setLoweringHead(false);
            this.goat.setRamCooldown(this.ramTarget == null ? this.goat.failedRamCooldown() : this.goat.nextRamCooldown());
        }
        this.goat.getNavigator().clearPath();
        this.target = null;
        this.startPos = null;
        this.ramTarget = null;
    }

    @Override
    public void updateTask() {
        if (this.ramTarget != null) {
            this.ram();
        } else if (this.startPos != null) {
            // With nobody to ram or nowhere to charge from, the goal ends on its next check.
            this.prepare();
        }
    }

    // ------------------------------------------------------------------------------------------
    // Prepare
    // ------------------------------------------------------------------------------------------

    private void prepare() {
        this.goat.getLookHelper().setLookPositionWithEntity(this.target, 30.0F, 30.0F);
        if (!new BlockPos(this.target).equals(this.targetPos)) {
            this.goat.setLoweringHead(false);
            this.goat.getNavigator().clearPath();
            this.choosePosition();
            return;
        }
        BlockPos at = new BlockPos(this.goat);
        if (!at.equals(this.startPos)) {
            if (this.goat.getNavigator().noPath()) {
                this.goat.getNavigator().tryMoveToXYZ(this.startPos.getX() + 0.5D, this.startPos.getY(), this.startPos.getZ() + 0.5D, WALK_SPEED);
            }
            return;
        }
        this.goat.setLoweringHead(true);
        if (this.reachedAt < 0) {
            this.reachedAt = this.goat.ticksExisted;
        }
        if (this.goat.ticksExisted - this.reachedAt >= PREPARE_TICKS) {
            this.goat.playRamSound(GoatContent.PREPARE_RAM, this.goat.voicePitch());
            this.startRam(at);
        }
    }

    private void startRam(BlockPos from) {
        // The far edge of the target's block, so the charge runs through where the target stands.
        double dx = 0.5D * Math.signum((double) (this.targetPos.getX() - from.getX()));
        double dz = 0.5D * Math.signum((double) (this.targetPos.getZ() - from.getZ()));
        this.ramTarget = new Vec3d(this.targetPos.getX() + 0.5D + dx, this.targetPos.getY(), this.targetPos.getZ() + 0.5D + dz);
        this.ramDirection = new Vec3d(from.getX() - this.ramTarget.x, 0.0D, from.getZ() - this.ramTarget.z).normalize();
        this.phaseStart = this.goat.ticksExisted;
        this.goat.getNavigator().tryMoveToXYZ(this.ramTarget.x, this.ramTarget.y, this.ramTarget.z, RAM_SPEED);
    }

    private EntityLivingBase findTarget() {
        EntityLivingBase best = null;
        double bestDistance = SEE_RANGE * SEE_RANGE;
        for (EntityLivingBase e : this.goat.world.getEntitiesWithinAABB(EntityLivingBase.class,
                this.goat.getEntityBoundingBox().grow(SEE_RANGE, SEE_RANGE, SEE_RANGE))) {
            double distance = this.goat.getDistanceSq(e);
            if (distance < bestDistance && canRam(this.goat, e)) {
                best = e;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** @return whether a goat may ram this: 1.17's combat targeting, minus goats, plus current Java's ghast rule */
    public static boolean canRam(EntityGoat goat, EntityLivingBase e) {
        if (e == goat || e instanceof EntityGoat || e instanceof EntityGhast || !e.isEntityAlive()) {
            return false;
        }
        if (goat.world.getDifficulty() == EnumDifficulty.PEACEFUL || e.getIsInvulnerable()) {
            return false;
        }
        if (e instanceof EntityPlayer && (((EntityPlayer) e).isSpectator() || ((EntityPlayer) e).capabilities.disableDamage)) {
            return false;
        }
        return !goat.isOnSameTeam(e) && goat.world.getWorldBorder().contains(e.getEntityBoundingBox())
                && goat.getEntitySenses().canSee(e);
    }

    /** Picks {@link #startPos} for the current target, or clears it when there is none. */
    private void choosePosition() {
        this.reachedAt = -1;
        this.startPos = null;
        this.targetPos = new BlockPos(this.target);
        if (!this.walkable(this.targetPos)) {
            return;
        }
        List<BlockPos> ends = new ArrayList<BlockPos>();
        for (EnumFacing side : EnumFacing.HORIZONTALS) {
            BlockPos end = this.targetPos;
            for (int i = 0; i < MAX_DISTANCE && this.walkable(end.offset(side)); i++) {
                end = end.offset(side);
            }
            if (Math.abs(end.getX() - this.targetPos.getX()) + Math.abs(end.getZ() - this.targetPos.getZ()) >= MIN_DISTANCE) {
                ends.add(end);
            }
        }
        final BlockPos here = new BlockPos(this.goat);
        Collections.sort(ends, new Comparator<BlockPos>() {
            @Override
            public int compare(BlockPos a, BlockPos b) {
                return Double.compare(a.distanceSq(here), b.distanceSq(here));
            }
        });
        for (BlockPos end : ends) {
            if (end.equals(here) || reaches(this.goat.getNavigator().getPathToPos(end), end)) {
                this.startPos = end;
                break;
            }
        }
        // Whatever walk the goat was on (a wander this goal cut short) ends here.
        this.goat.getNavigator().clearPath();
    }

    /** @return whether a path ends exactly at a position */
    static boolean reaches(Path path, BlockPos pos) {
        if (path == null) {
            return false;
        }
        PathPoint end = path.getFinalPathPoint();
        return end != null && end.x == pos.getX() && end.y == pos.getY() && end.z == pos.getZ();
    }

    private boolean walkable(BlockPos pos) {
        return standable(this.goat, pos);
    }

    /** @return whether a goat can stand at a position and walk there at no extra cost */
    static boolean standable(EntityGoat goat, BlockPos pos) {
        return goat.getNavigator().canEntityStandOnPos(pos) && goat.getPathPriority(
                goat.getNavigator().getNodeProcessor().getPathNodeType(goat.world, pos.getX(), pos.getY(), pos.getZ())) == 0.0F;
    }

    // ------------------------------------------------------------------------------------------
    // Ram
    // ------------------------------------------------------------------------------------------

    private void ram() {
        for (EntityLivingBase e : this.goat.world.getEntitiesWithinAABB(EntityLivingBase.class, this.goat.getEntityBoundingBox())) {
            if (canRam(this.goat, e)) {
                this.hit(e);
                this.finish();
                this.goat.playRamSound(GoatContent.RAM_IMPACT, 1.0F);
                return;
            }
        }
        if (this.goat.getNavigator().noPath()) {
            this.finish();
        }
    }

    private void hit(EntityLivingBase victim) {
        DamageSource source = DamageSource.causeMobDamage(this.goat);
        boolean blocked = blocks(victim, source);
        // A rammed mob does not fight back (1.17's "no aggro" damage): its revenge target stays what it was.
        EntityLivingBase revenge = victim.getRevengeTarget();
        victim.attackEntityFrom(source, (float) this.goat.ramDamage());
        if (victim.getRevengeTarget() == this.goat && revenge != this.goat) {
            victim.setRevengeTarget(revenge);
        }
        PotionEffect speed = this.goat.getActivePotionEffect(MobEffects.SPEED);
        PotionEffect slow = this.goat.getActivePotionEffect(MobEffects.SLOWNESS);
        float effects = 0.25F * ((speed == null ? 0 : speed.getAmplifier() + 1) - (slow == null ? 0 : slow.getAmplifier() + 1));
        float force = MathHelper.clamp(this.goat.getAIMoveSpeed() * SPEED_TO_FORCE, 0.2F, 3.0F) + effects;
        double knockback = (blocked ? 0.5F : 1.0F) * force * (this.goat.isChild() ? 1.0D : 2.5D);
        victim.knockBack(this.goat, (float) knockback, this.ramDirection.x, this.ramDirection.z);
        victim.velocityChanged = true;
    }

    /** @return whether the victim's raised shield faces this damage (1.12 keeps its own check private) */
    private static boolean blocks(EntityLivingBase victim, DamageSource source) {
        Vec3d from = source.getDamageLocation();
        if (source.isUnblockable() || !victim.isActiveItemStackBlocking() || from == null) {
            return false;
        }
        Vec3d toVictim = from.subtractReverse(new Vec3d(victim.posX, victim.posY, victim.posZ)).normalize();
        return new Vec3d(toVictim.x, 0.0D, toVictim.z).dotProduct(victim.getLook(1.0F)) < 0.0D;
    }

    private void finish() {
        this.rammed = true;
        this.goat.setLoweringHead(false);
        this.goat.setRamCooldown(this.goat.nextRamCooldown());
    }
}
