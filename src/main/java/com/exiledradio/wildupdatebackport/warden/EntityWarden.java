package com.exiledradio.wildupdatebackport.warden;

import com.exiledradio.wildupdatebackport.gameevent.GameEvent;
import com.exiledradio.wildupdatebackport.gameevent.GameEventContext;
import com.exiledradio.wildupdatebackport.gameevent.GameEventTags;
import com.exiledradio.wildupdatebackport.gameevent.GameEvents;
import com.exiledradio.wildupdatebackport.gameevent.PositionSource;
import com.exiledradio.wildupdatebackport.gameevent.VibrationListener;
import com.exiledradio.wildupdatebackport.warden.WardenBrain.Memory;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IEntityLivingData;
import net.minecraft.entity.MoverType;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.pathfinding.PathNavigate;
import net.minecraft.pathfinding.PathNavigateGround;
import net.minecraft.pathfinding.PathNodeType;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntityDamageSourceIndirect;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.Set;

/**
 * The warden: 1.19.2's {@code Warden}.
 *
 * <p>500 health, 30 melee damage (knockback 1.5), knockback resistance 1, speed 0.3, fire immune, never despawns.
 * Blind: it finds others by vibrations (radius 16, {@code #warden_can_listen}), by sniffing, and by being touched or
 * hurt, and grows angry at each suspect separately ({@link AngerManagement}). At 80 anger it roars and fights that
 * one: melee, and the sonic boom (10 damage through armor, shields and Protection, at up to 15 blocks across and 20
 * up or down) when it has not hit in melee for 2 seconds. It emerges from the ground when a shrieker summons it
 * (invulnerable, 134 ticks), gives Darkness to players within 20 blocks every 6 seconds, and digs back down 1200
 * ticks after the last disturbance (invulnerable, 100 ticks; gone without drops). Drops one sculk catalyst.
 *
 * <p>Its mind is {@link WardenBrain}, ticked from {@link #updateAITasks()}. No AI task is added to 1.12's lists.
 */
public class EntityWarden extends EntityMob implements VibrationListener.Config {

    public static final byte EVENT_ATTACK = 4;
    public static final byte EVENT_TENDRILS = 61;
    public static final byte EVENT_SONIC_BOOM = 62;

    public static final int DARKNESS_RADIUS = 20;
    public static final int DARKNESS_INTERVAL = 120;
    public static final int DEFAULT_ANGER = 35;
    public static final int PROJECTILE_ANGER = 10;
    public static final int ON_HURT_ANGER_BOOST = 20;
    public static final int VIBRATION_COOLDOWN_TICKS = 40;
    public static final int TOUCH_COOLDOWN_TICKS = 20;
    public static final int GAME_EVENT_LISTENER_RANGE = 16;

    private static final DataParameter<Integer> POSE = EntityDataManager.createKey(EntityWarden.class, DataSerializers.VARINT);
    private static final DataParameter<Integer> CLIENT_ANGER_LEVEL = EntityDataManager.createKey(EntityWarden.class, DataSerializers.VARINT);

    private int tendrilAnimation;
    private int tendrilAnimationO;
    private int heartAnimation;
    private int heartAnimationO;
    private float nextStep = 0.55F;
    public final AnimationState roarAnimationState = new AnimationState();
    public final AnimationState sniffAnimationState = new AnimationState();
    public final AnimationState emergeAnimationState = new AnimationState();
    public final AnimationState diggingAnimationState = new AnimationState();
    public final AnimationState attackAnimationState = new AnimationState();
    public final AnimationState sonicBoomAnimationState = new AnimationState();

    private final WardenBrain brain;
    private final AngerManagement angerManagement = new AngerManagement(this::canTargetEntity);
    private final VibrationListener listener;
    private boolean listening;

    public EntityWarden(World world) {
        super(world);
        setSize(0.9F, 2.9F);
        this.experienceValue = 5;
        this.isImmuneToFire = true;
        this.brain = new WardenBrain(this);
        this.listener = new VibrationListener(new PositionSource.EntitySource(this, 2.9F * 0.85F), GAME_EVENT_LISTENER_RANGE, this);
        ((PathNavigateGround) getNavigator()).setCanSwim(true);
        ((PathNavigateGround) getNavigator()).setEnterDoors(true);
        setPathPriority(PathNodeType.DAMAGE_OTHER, 8.0F);
        setPathPriority(PathNodeType.LAVA, 8.0F);
        setPathPriority(PathNodeType.DAMAGE_FIRE, 0.0F);
        setPathPriority(PathNodeType.DANGER_FIRE, 0.0F);
    }

    @Override
    protected PathNavigate createNavigator(World world) {
        return new PathNavigateGround(this, world);
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(500.0D);
        getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.3D);
        getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
        getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(30.0D);
        getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE).setBaseValue(16.0D);
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        this.dataManager.register(POSE, WardenPose.STANDING.ordinal());
        this.dataManager.register(CLIENT_ANGER_LEVEL, 0);
    }

    public WardenBrain brain() {
        return this.brain;
    }

    public AngerManagement angerManagement() {
        return this.angerManagement;
    }

    public VibrationListener listener() {
        return this.listener;
    }

    // --- pose ----------------------------------------------------------------------------------------------------

    public WardenPose getPose() {
        return WardenPose.byId(this.dataManager.get(POSE));
    }

    public void setPose(WardenPose pose) {
        this.dataManager.set(POSE, pose.ordinal());
    }

    public boolean isDiggingOrEmerging() {
        WardenPose p = getPose();
        return p == WardenPose.DIGGING || p == WardenPose.EMERGING;
    }

    @Override
    public void notifyDataManagerChange(DataParameter<?> key) {
        if (POSE.equals(key)) {
            // 1.19's dimensions: 1 block tall while digging or emerging.
            setSize(0.9F, isDiggingOrEmerging() ? 1.0F : 2.9F);
            if (this.world.isRemote) {
                switch (getPose()) {
                    case EMERGING:
                        this.emergeAnimationState.start(this.ticksExisted);
                        break;
                    case DIGGING:
                        this.diggingAnimationState.start(this.ticksExisted);
                        break;
                    case ROARING:
                        this.roarAnimationState.start(this.ticksExisted);
                        break;
                    case SNIFFING:
                        this.sniffAnimationState.start(this.ticksExisted);
                        break;
                    default:
                        break;
                }
            }
        }
        super.notifyDataManagerChange(key);
    }

    @Override
    public float getEyeHeight() {
        return 2.9F * 0.85F;
    }

    // --- 1.19's simple overrides ------------------------------------------------------------------------------------

    @Override
    public float getBlockPathWeight(BlockPos pos) {
        return 0.0F;
    }

    @Override
    public boolean isEntityInvulnerable(DamageSource source) {
        return isDiggingOrEmerging() && source != DamageSource.OUT_OF_WORLD || super.isEntityInvulnerable(source);
    }

    @Override
    public boolean isImmuneToExplosions() {
        return isDiggingOrEmerging();
    }

    @Override
    public boolean startRiding(Entity entity, boolean force) {
        return false;
    }

    @Override
    public boolean canBePushed() {
        return !isDiggingOrEmerging() && super.canBePushed();
    }

    @Override
    protected boolean canDespawn() {
        return false;
    }

    @Override
    protected float getSoundVolume() {
        return 4.0F;
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return getPose() != WardenPose.ROARING && !isDiggingOrEmerging() ? getAngerLevel().getAmbientSound() : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return WardenContent.HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return WardenContent.DEATH;
    }

    /** 1.12's whole-block step cadence is replaced by 1.19's warden's: a step every 0.55 blocks walked. */
    @Override
    protected void playStepSound(BlockPos pos, Block block) {
    }

    @Override
    public void move(MoverType type, double x, double y, double z) {
        super.move(type, x, y, z);
        if (!this.world.isRemote && this.distanceWalkedOnStepModified > this.nextStep && this.onGround
                && this.world.getBlockState(new BlockPos(this.posX, this.posY - 0.2D, this.posZ)).getMaterial() != net.minecraft.block.material.Material.AIR) {
            this.nextStep = this.distanceWalkedOnStepModified + 0.55F;
            if (!isInWater()) {
                playSound(WardenContent.STEP, 10.0F, 1.0F);
            }
        }
    }

    @Override
    protected void dropFewItems(boolean wasRecentlyHit, int looting) {
        Item catalyst = WardenContent.sculkCatalyst();
        if (catalyst != null) {
            entityDropItem(new ItemStack(catalyst), 0.0F);
        }
    }

    // --- spawning ------------------------------------------------------------------------------------------------

    @Nullable
    @Override
    public IEntityLivingData onInitialSpawn(DifficultyInstance difficulty, @Nullable IEntityLivingData data) {
        this.brain.setWithExpiry(Memory.DIG_COOLDOWN, Boolean.TRUE, WardenBrain.DIGGING_COOLDOWN);
        if (getEntityData().getBoolean(WardenContent.SUMMONED)) {
            // 1.19's MobSpawnType.TRIGGERED: a shrieker's warden comes up out of the ground.
            setPose(WardenPose.EMERGING);
            this.brain.setWithExpiry(Memory.IS_EMERGING, Boolean.TRUE, WardenBrain.EMERGE_DURATION);
            playSound(WardenContent.AGITATED, 5.0F, 1.0F);
        }
        return super.onInitialSpawn(difficulty, data);
    }

    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        if (!this.world.isRemote) {
            GameEvents.add(this.world, this.listener);
            this.listening = true;
        }
    }

    @Override
    public void onRemovedFromWorld() {
        super.onRemovedFromWorld();
        if (!this.world.isRemote && this.listening) {
            GameEvents.remove(this.world, this.listener);
            this.listening = false;
        }
    }

    // --- ticking -------------------------------------------------------------------------------------------------

    @Override
    public void onUpdate() {
        if (this.world instanceof WorldServer) {
            this.listener.tick(this.world);
            if (this.listening) {
                GameEvents.move(this.world, this.listener);
            }
            if (isNoDespawnRequired() || isRiding()) {
                this.brain.setDigCooldown();
            }
        }
        super.onUpdate();
        if (this.world.isRemote) {
            if (this.ticksExisted % getHeartBeatDelay() == 0) {
                this.heartAnimation = 10;
                if (!isSilent()) {
                    this.world.playSound(this.posX, this.posY, this.posZ, WardenContent.HEARTBEAT, getSoundCategory(), 5.0F, getSoundPitch(), false);
                }
            }
            this.tendrilAnimationO = this.tendrilAnimation;
            if (this.tendrilAnimation > 0) {
                this.tendrilAnimation--;
            }
            this.heartAnimationO = this.heartAnimation;
            if (this.heartAnimation > 0) {
                this.heartAnimation--;
            }
            WardenPose pose = getPose();
            if (pose == WardenPose.EMERGING) {
                clientDiggingParticles(this.emergeAnimationState);
            } else if (pose == WardenPose.DIGGING) {
                clientDiggingParticles(this.diggingAnimationState);
            }
        }
    }

    private void clientDiggingParticles(AnimationState state) {
        if (state.getAccumulatedTime() < 4500L) {
            IBlockState on = this.world.getBlockState(new BlockPos(this.posX, this.posY - 0.2D, this.posZ));
            if (on.getRenderType() != EnumBlockRenderType.INVISIBLE) {
                for (int i = 0; i < 30; i++) {
                    double x = this.posX + (this.rand.nextFloat() * 1.4F - 0.7F);
                    double z = this.posZ + (this.rand.nextFloat() * 1.4F - 0.7F);
                    this.world.spawnParticle(EnumParticleTypes.BLOCK_CRACK, x, this.posY, z, 0.0D, 0.0D, 0.0D, Block.getStateId(on));
                }
            }
        }
    }

    /** 1.19's {@code customServerAiStep}. */
    @Override
    protected void updateAITasks() {
        WorldServer world = (WorldServer) this.world;
        this.brain.tick(world);
        super.updateAITasks();
        if ((this.ticksExisted + getEntityId()) % DARKNESS_INTERVAL == 0) {
            WardenContent.applyDarknessAround(world, getPositionVector(), this, DARKNESS_RADIUS);
        }
        if (this.ticksExisted % 20 == 0) {
            this.angerManagement.tick(world, this::canTargetEntity);
            syncClientAngerLevel();
        }
        this.brain.updateActivity();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void handleStatusUpdate(byte id) {
        if (id == EVENT_ATTACK) {
            this.roarAnimationState.stop();
            this.attackAnimationState.start(this.ticksExisted);
        } else if (id == EVENT_TENDRILS) {
            this.tendrilAnimation = 10;
        } else if (id == EVENT_SONIC_BOOM) {
            this.sonicBoomAnimationState.start(this.ticksExisted);
        } else {
            super.handleStatusUpdate(id);
        }
    }

    public int getClientAngerLevel() {
        return this.dataManager.get(CLIENT_ANGER_LEVEL);
    }

    private void syncClientAngerLevel() {
        this.dataManager.set(CLIENT_ANGER_LEVEL, getActiveAnger());
    }

    /** 40 ticks calm, down to 10 at anger 80. */
    public int getHeartBeatDelay() {
        float f = (float) getClientAngerLevel() / (float) AngerLevel.ANGRY.getMinimumAnger();
        return 40 - MathHelper.floor(MathHelper.clamp(f, 0.0F, 1.0F) * 30.0F);
    }

    public float getTendrilAnimation(float partialTicks) {
        return (this.tendrilAnimationO + (this.tendrilAnimation - this.tendrilAnimationO) * partialTicks) / 10.0F;
    }

    public float getHeartAnimation(float partialTicks) {
        return (this.heartAnimationO + (this.heartAnimation - this.heartAnimationO) * partialTicks) / 10.0F;
    }

    // --- anger and targets -----------------------------------------------------------------------------------------

    /** 1.19's {@code canTargetEntity}. */
    public boolean canTargetEntity(@Nullable Entity e) {
        if (!(e instanceof EntityLivingBase) || e.world != this.world) {
            return false;
        }
        EntityLivingBase living = (EntityLivingBase) e;
        if (e instanceof EntityPlayer && (((EntityPlayer) e).isCreative() || ((EntityPlayer) e).isSpectator())) {
            return false;
        }
        return !isOnSameTeam(e) && !(e instanceof EntityArmorStand) && !(e instanceof EntityWarden) && !e.getIsInvulnerable()
                && living.getHealth() > 0.0F && !e.isDead && this.world.getWorldBorder().contains(e.getEntityBoundingBox());
    }

    public AngerLevel getAngerLevel() {
        return AngerLevel.byAnger(getActiveAnger());
    }

    public int getActiveAnger() {
        return this.angerManagement.getActiveAnger(this.brain.<EntityLivingBase>get(Memory.ATTACK_TARGET));
    }

    public void clearAnger(Entity e) {
        this.angerManagement.clearAnger(e);
    }

    public void increaseAngerAt(@Nullable Entity e) {
        increaseAngerAt(e, DEFAULT_ANGER, true);
    }

    public void increaseAngerAt(@Nullable Entity e, int amount, boolean listeningSound) {
        if (!isAIDisabled() && canTargetEntity(e)) {
            this.brain.setDigCooldown();
            boolean notFightingPlayer = !(this.brain.<EntityLivingBase>get(Memory.ATTACK_TARGET) instanceof EntityPlayer);
            int anger = this.angerManagement.increaseAnger(e, amount);
            if (e instanceof EntityPlayer && notFightingPlayer && AngerLevel.byAnger(anger).isAngry()) {
                this.brain.erase(Memory.ATTACK_TARGET);
            }
            if (listeningSound) {
                playListeningSound();
            }
        }
    }

    private void playListeningSound() {
        if (getPose() != WardenPose.ROARING) {
            SoundEvent sound = getAngerLevel().getListeningSound();
            if (sound != null) {
                playSound(sound, 10.0F, getSoundPitch());
            }
        }
    }

    /** @return the entity the warden is angry at, when angry enough (80) to fight it */
    @Nullable
    public EntityLivingBase getEntityAngryAt() {
        return getAngerLevel().isAngry() ? this.angerManagement.getActiveEntity() : null;
    }

    /** 1.19's {@code setAttackTarget}: fight it now, the sonic boom not for 10 seconds. */
    public void setAttackTargetFromBrain(EntityLivingBase target) {
        this.brain.setAttackTarget(target);
    }

    public boolean closerThan(Entity e, double xz, double y) {
        double dx = e.posX - this.posX;
        double dy = e.posY - this.posY;
        double dz = e.posZ - this.posZ;
        return dx * dx + dz * dz < xz * xz && dy * dy < y * y;
    }

    /** 1.19's melee reach: (2 x width)^2 + the target's width, from feet to feet. */
    public boolean isWithinMeleeAttackRange(EntityLivingBase target) {
        double reach = this.width * 2.0F * this.width * 2.0F + target.width;
        return getDistanceSq(target.posX, target.posY, target.posZ) <= reach;
    }

    // --- combat ----------------------------------------------------------------------------------------------------

    @Override
    public boolean attackEntityAsMob(Entity target) {
        this.world.setEntityState(this, EVENT_ATTACK);
        playSound(WardenContent.ATTACK_IMPACT, 10.0F, getSoundPitch());
        this.brain.setWithExpiry(Memory.SONIC_BOOM_COOLDOWN, Boolean.TRUE, 40L);
        // 1.19's Mob.doHurtTarget with a warden's canDisableShield.
        float damage = (float) getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).getAttributeValue();
        float knockback = 1.5F;
        if (target instanceof EntityLivingBase) {
            damage += EnchantmentHelper.getModifierForCreature(getHeldItemMainhand(), ((EntityLivingBase) target).getCreatureAttribute());
            knockback += EnchantmentHelper.getKnockbackModifier(this);
        }
        boolean shieldUp = target instanceof EntityPlayer && blocks((EntityPlayer) target);
        boolean hurt = target.attackEntityFrom(DamageSource.causeMobDamage(this), damage);
        if (hurt) {
            if (knockback > 0.0F && target instanceof EntityLivingBase) {
                ((EntityLivingBase) target).knockBack(this, knockback * 0.5F, MathHelper.sin(this.rotationYaw * 0.017453292F),
                        -MathHelper.cos(this.rotationYaw * 0.017453292F));
                this.motionX *= 0.6D;
                this.motionZ *= 0.6D;
            }
            int fire = EnchantmentHelper.getFireAspectModifier(this);
            if (fire > 0) {
                target.setFire(fire * 4);
            }
            applyEnchantments(this, target);
        }
        if (shieldUp) {
            ((EntityPlayer) target).disableShield(true);
        }
        return hurt;
    }

    /** 1.12's private {@code canBlockDamageSource} for a melee hit from this warden. */
    private boolean blocks(EntityPlayer player) {
        if (!player.isActiveItemStackBlocking()) {
            return false;
        }
        Vec3d look = player.getLook(1.0F);
        Vec3d rel = new Vec3d(this.posX, this.posY, this.posZ).subtractReverse(new Vec3d(player.posX, player.posY, player.posZ)).normalize();
        rel = new Vec3d(rel.x, 0.0D, rel.z);
        return rel.dotProduct(look) < 0.0D;
    }

    /** The sonic boom landing: 1.19's {@code SonicBoom.tick} when its sound plays. */
    void sonicBoom(WorldServer world, EntityLivingBase target) {
        Vec3d from = getPositionVector().add(0.0D, 1.6F, 0.0D);
        Vec3d to = new Vec3d(target.posX, target.posY + target.getEyeHeight(), target.posZ).subtract(from);
        Vec3d dir = to.normalize();
        SonicBoomPacket.send(world, from, dir, MathHelper.floor(to.length()) + 7);
        playSound(WardenContent.SONIC_BOOM, 3.0F, 1.0F);
        target.attackEntityFrom(WardenContent.sonicBoom(this), 10.0F);
        double resist = target.getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE).getAttributeValue();
        double vertical = 0.5D * (1.0D - resist);
        double horizontal = 2.5D * (1.0D - resist);
        target.addVelocity(dir.x * horizontal, dir.y * vertical, dir.z * horizontal);
        target.velocityChanged = true;
    }

    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        boolean hurt = super.attackEntityFrom(source, amount);
        if (!this.world.isRemote && !isAIDisabled() && !isDiggingOrEmerging()) {
            Entity attacker = source.getTrueSource();
            increaseAngerAt(attacker, AngerLevel.ANGRY.getMinimumAnger() + ON_HURT_ANGER_BOOST, false);
            if (!this.brain.has(Memory.ATTACK_TARGET) && attacker instanceof EntityLivingBase
                    && (!(source instanceof EntityDamageSourceIndirect) || getDistance(attacker) < 5.0F)) {
                setAttackTargetFromBrain((EntityLivingBase) attacker);
            }
        }
        return hurt;
    }

    /** 1.19's {@code doPush}: whatever bumps into the warden is a suspect. */
    @Override
    protected void collideWithEntity(Entity e) {
        if (!this.world.isRemote && !isAIDisabled() && !this.brain.has(Memory.TOUCH_COOLDOWN)) {
            this.brain.setWithExpiry(Memory.TOUCH_COOLDOWN, Boolean.TRUE, TOUCH_COOLDOWN_TICKS);
            increaseAngerAt(e);
            this.brain.setDisturbanceLocation(new BlockPos(e));
        }
        super.collideWithEntity(e);
    }

    // --- vibrations --------------------------------------------------------------------------------------------------

    @Override
    public Set<GameEvent> listenableEvents() {
        return GameEventTags.WARDEN_CAN_LISTEN;
    }

    @Override
    public boolean canTriggerAvoidVibration() {
        return true;
    }

    @Override
    public boolean shouldListen(WorldServer world, VibrationListener l, BlockPos origin, GameEvent event, GameEventContext context) {
        if (isAIDisabled() || getHealth() <= 0.0F || this.brain.has(Memory.VIBRATION_COOLDOWN) || isDiggingOrEmerging()
                || !world.getWorldBorder().contains(origin) || this.isDead || this.world != world) {
            return false;
        }
        return !(context.source instanceof EntityLivingBase) || canTargetEntity(context.source);
    }

    @Override
    public void onSignalReceive(WorldServer world, VibrationListener l, BlockPos origin, GameEvent event, @Nullable Entity source,
                                @Nullable Entity projectileOwner, float distance) {
        if (getHealth() <= 0.0F) {
            return;
        }
        this.brain.setWithExpiry(Memory.VIBRATION_COOLDOWN, Boolean.TRUE, VIBRATION_COOLDOWN_TICKS);
        world.setEntityState(this, EVENT_TENDRILS);
        playSound(WardenContent.TENDRIL_CLICKS, 5.0F, getSoundPitch());
        BlockPos at = origin;
        if (projectileOwner != null) {
            if (getDistance(projectileOwner) < 30.0F) {
                if (this.brain.has(Memory.RECENT_PROJECTILE)) {
                    if (canTargetEntity(projectileOwner)) {
                        at = new BlockPos(projectileOwner);
                    }
                    increaseAngerAt(projectileOwner);
                } else {
                    increaseAngerAt(projectileOwner, PROJECTILE_ANGER, true);
                }
            }
            this.brain.setWithExpiry(Memory.RECENT_PROJECTILE, Boolean.TRUE, 100L);
        } else {
            increaseAngerAt(source);
        }
        if (!getAngerLevel().isAngry()) {
            EntityLivingBase active = this.angerManagement.getActiveEntity();
            if (projectileOwner != null || active == null || active == source) {
                this.brain.setDisturbanceLocation(at);
            }
        }
    }

    // --- saving --------------------------------------------------------------------------------------------------

    @Override
    public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        tag.setTag("anger", this.angerManagement.write());
        tag.setTag("listener", this.listener.writeToNBT());
        tag.setTag("Brain", this.brain.write());
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        if (tag.hasKey("anger", 10)) {
            this.angerManagement.read(tag.getCompoundTag("anger"));
            syncClientAngerLevel();
        }
        if (tag.hasKey("listener", 10)) {
            this.listener.readFromNBT(tag.getCompoundTag("listener"));
        }
        if (tag.hasKey("Brain", 10)) {
            this.brain.read(tag.getCompoundTag("Brain"));
        }
    }

    /** For the tests: the player's view of how angry this warden is at it. */
    public int angerAt(EntityPlayerMP player) {
        return this.angerManagement.anger(player);
    }
}
