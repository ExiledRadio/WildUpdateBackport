package com.exiledradio.wildupdatebackport.frog;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;

import javax.annotation.Nullable;
import java.util.Random;

/**
 * What 1.19's brain remembers for a frog or a tadpole, kept as plain fields and counted down on the
 * entity's own tick (never inside a task's {@code shouldExecute}, which a busy task list may not ask).
 */
public final class FrogMemory {

    /** 1.19's {@code TemptingSensor} range for frogs and tadpoles. */
    public static final double TEMPT_RANGE = 10.0D;
    /** {@code FollowTemptation.TEMPTATION_COOLDOWN} */
    public static final int TEMPT_COOLDOWN = 100;

    public boolean panicking;
    public int temptCooldown;
    public boolean tempted;
    @Nullable
    public EntityPlayer temptingPlayer;

    /** {@code LOOK_TARGET}: an entity looked at by {@code LookAtTargetSink}, for 45 to 89 ticks. */
    @Nullable
    public Entity lookTarget;
    public int lookTicks;
    /** {@code RunSometimes(SetEntityLookTarget(player, 6), 30..60)}: ticks until the next look. */
    public int nextLook;

    /** Ticks a "do nothing" behavior still holds the mob idle. */
    public int idleTicks;

    /** Counts the timers down and refreshes the tempting player and the look target, once per tick. */
    void tick(EntityLiving mob, Random random) {
        if (this.temptCooldown > 0) {
            this.temptCooldown--;
        }
        this.temptingPlayer = findTempter(mob);
        if (this.lookTarget != null && (--this.lookTicks <= 0 || !this.lookTarget.isEntityAlive())) {
            this.lookTarget = null;
        }
        if (--this.nextLook <= 0) {
            this.nextLook = 30 + random.nextInt(31);
            EntityPlayer player = mob.world.getClosestPlayerToEntity(mob, 6.0D);
            if (player != null && !player.isSpectator() && mob.getEntitySenses().canSee(player)) {
                look(player, random);
            }
        }
        if (this.lookTarget != null) {
            mob.getLookHelper().setLookPositionWithEntity(this.lookTarget, 35.0F, 40.0F);
        }
        if (this.idleTicks > 0) {
            this.idleTicks--;
        }
    }

    void look(Entity target, Random random) {
        this.lookTarget = target;
        this.lookTicks = 45 + random.nextInt(45);
    }

    /** 1.19's {@code TemptingSensor}: the nearest non-spectator player within 10 holding a slimeball. */
    @Nullable
    static EntityPlayer findTempter(EntityLiving mob) {
        EntityPlayer best = null;
        double bestD = TEMPT_RANGE * TEMPT_RANGE;
        for (EntityPlayer p : mob.world.playerEntities) {
            if (p.isSpectator() || !p.isEntityAlive()) {
                continue;
            }
            double d = p.getDistanceSq(mob);
            if (d <= bestD && (tempts(p.getHeldItem(EnumHand.MAIN_HAND)) || tempts(p.getHeldItem(EnumHand.OFF_HAND)))) {
                best = p;
                bestD = d;
            }
        }
        return best;
    }

    /** Frogs' and tadpoles' food: slimeballs. */
    public static boolean tempts(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == Items.SLIME_BALL;
    }
}
