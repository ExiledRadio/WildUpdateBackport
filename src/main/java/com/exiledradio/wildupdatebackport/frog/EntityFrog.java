package com.exiledradio.wildupdatebackport.frog;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityAgeable;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IEntityLivingData;
import net.minecraft.entity.MoverType;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.MobEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.pathfinding.PathNavigate;
import net.minecraft.pathfinding.PathNavigateGround;
import net.minecraft.pathfinding.PathNavigateSwimmer;
import net.minecraft.pathfinding.PathNodeType;
import net.minecraft.pathfinding.WalkNodeProcessor;
import net.minecraft.stats.StatList;
import net.minecraft.util.DamageSource;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The frog, as 1.19.2's {@code Frog} and {@code FrogAi}.
 *
 * <h2>Numbers</h2>
 * 10 health, 10 attack damage, movement speed 1 (x 0.1 on land and x 0.02 in water through
 * {@link SmoothSwimMoveHelper}), 0.5 x 0.5, steps up a full block, breathes underwater, is not pushed by
 * water, takes 5 less fall damage, never a baby. Breeds with slimeballs: the pair's mother becomes pregnant
 * (no child is born) and lays frogspawn on water next to land. Water costs 4 to path through, trapdoors are
 * avoided.
 *
 * <h2>Behavior</h2>
 * 1.19's brain picks one activity a tick, the first of: <b>tongue</b> (has an attack target), <b>lay spawn</b>
 * (pregnant), <b>long jump</b> (no tempting player, no partner, jump cooldown over, on land), <b>swim</b>
 * (in water), <b>idle</b>. Here the activity is worked out on the entity's tick ({@link #activity()}) and
 * each task in {@link FrogAi} runs only in its own activities. Timers live in {@link FrogMemory} and here.
 *
 * <h2>Two navigators</h2>
 * 1.12.2 has no amphibious navigator. The frog walks (and paddles along the surface) with a ground
 * navigator that may swim, and swims underwater with a second, water-only navigator used by its random
 * swimming; the move helper follows whichever has a path.
 */
public class EntityFrog extends EntityAnimal {

    public static final float WIDTH = 0.5F;
    public static final float HEIGHT = 0.5F;
    public static final int BREEDING_COOLDOWN = 6000;
    public static final int FALL_DAMAGE_REDUCTION = 5;

    public enum Activity { TONGUE, LAY_SPAWN, LONG_JUMP, SWIM, IDLE }

    public enum Pose { STANDING, CROAKING, USING_TONGUE, LONG_JUMPING }

    private static final DataParameter<Byte> VARIANT = EntityDataManager.createKey(EntityFrog.class, DataSerializers.BYTE);
    private static final DataParameter<Byte> POSE = EntityDataManager.createKey(EntityFrog.class, DataSerializers.BYTE);
    private static final DataParameter<Integer> TONGUE_TARGET = EntityDataManager.createKey(EntityFrog.class, DataSerializers.VARINT);

    public final FrogMemory memory = new FrogMemory();
    private final PathNavigateSwimmer swimNavigator;

    private Activity activity = Activity.IDLE;
    /** {@code LONG_JUMP_COOLDOWN_TICKS}: 100 to 140 ticks between long jumps. */
    int longJumpCooldown;
    /** {@code LONG_JUMP_MID_JUMP} and how long it has lasted. */
    boolean midJump;
    int midJumpTicks;
    /** {@code IS_PREGNANT}. */
    boolean pregnant;
    /** {@code BREED_TARGET}. */
    @Nullable
    EntityFrog breedTarget;
    /** {@code NEAREST_ATTACKABLE}, refreshed every 20 ticks as 1.19's sensors are. */
    @Nullable
    EntityLivingBase nearestAttackable;
    /** {@code UNREACHABLE_TONGUE_TARGETS}: up to 5, forgotten 100 ticks after the last was added. */
    final List<UUID> unreachable = new ArrayList<UUID>();
    int unreachableExpires;
    /** A player who used a lead on this frog last tick, for <i>When the Squad Hops into Town</i>. */
    @Nullable
    UUID leashingPlayer;

    /** Client animation clocks, 1.19's {@code AnimationState}s. */
    public final AnimationClock jumpAnimation = new AnimationClock();
    public final AnimationClock croakAnimation = new AnimationClock();
    public final AnimationClock tongueAnimation = new AnimationClock();
    public final AnimationClock walkAnimation = new AnimationClock();
    public final AnimationClock swimAnimation = new AnimationClock();
    public final AnimationClock swimIdleAnimation = new AnimationClock();

    public EntityFrog(World world) {
        super(world);
        this.setSize(WIDTH, HEIGHT);
        this.stepHeight = 1.0F;
        this.moveHelper = new SmoothSwimMoveHelper(this, 85, 10, 0.02F, 0.1F, true) {
            @Override
            protected boolean navigating() {
                return !EntityFrog.this.getNavigator().noPath() || !EntityFrog.this.swimNavigator.noPath();
            }
        };
        this.swimNavigator = new PathNavigateSwimmer(this, world);
        this.setPathPriority(PathNodeType.WATER, 4.0F);
        this.setPathPriority(PathNodeType.TRAPDOOR, -1.0F);
        this.longJumpCooldown = FrogAi.longJumpCooldown(this.rand);
        // Not in initEntityAI: 1.12.2 calls that from the superclass constructor, before this class's
        // fields (the memory the tasks read) exist.
        FrogAi.addFrogTasks(this);
    }

    @Override
    protected PathNavigate createNavigator(World world) {
        PathNavigateGround nav = new PathNavigateGround(this, world) {
            @Override
            protected net.minecraft.pathfinding.PathFinder getPathFinder() {
                this.nodeProcessor = new FrogNodeProcessor();
                this.nodeProcessor.setCanEnterDoors(true);
                return new net.minecraft.pathfinding.PathFinder(this.nodeProcessor);
            }
        };
        nav.setCanSwim(true);
        return nav;
    }

    /** The underwater navigator (see the class comment). */
    public PathNavigateSwimmer swimNavigator() {
        return this.swimNavigator;
    }

    @Override
    protected void initEntityAI() {
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        this.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(10.0D);
        this.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(1.0D);
        this.getAttributeMap().registerAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(10.0D);
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        this.dataManager.register(VARIANT, (byte) FrogVariant.TEMPERATE.ordinal());
        this.dataManager.register(POSE, (byte) Pose.STANDING.ordinal());
        this.dataManager.register(TONGUE_TARGET, -1);
    }

    // --- variant, pose, tongue -------------------------------------------------------------------------

    public FrogVariant getVariant() {
        return FrogVariant.byIndex(this.dataManager.get(VARIANT));
    }

    public void setVariant(FrogVariant variant) {
        this.dataManager.set(VARIANT, (byte) variant.ordinal());
    }

    public Pose getPose() {
        int i = this.dataManager.get(POSE);
        return i >= 0 && i < Pose.values().length ? Pose.values()[i] : Pose.STANDING;
    }

    public void setPose(Pose pose) {
        this.dataManager.set(POSE, (byte) pose.ordinal());
    }

    @Nullable
    public Entity getTongueTarget() {
        int id = this.dataManager.get(TONGUE_TARGET);
        return id < 0 ? null : this.world.getEntityByID(id);
    }

    void setTongueTarget(@Nullable Entity target) {
        this.dataManager.set(TONGUE_TARGET, target == null ? -1 : target.getEntityId());
    }

    @Override
    public void notifyDataManagerChange(DataParameter<?> key) {
        if (POSE.equals(key) && this.world.isRemote) {
            Pose pose = getPose();
            restart(this.jumpAnimation, pose == Pose.LONG_JUMPING);
            restart(this.croakAnimation, pose == Pose.CROAKING);
            restart(this.tongueAnimation, pose == Pose.USING_TONGUE);
        }
        super.notifyDataManagerChange(key);
    }

    private void restart(AnimationClock clock, boolean on) {
        if (on) {
            clock.start(this.ticksExisted);
        } else {
            clock.stop();
        }
    }

    private void keep(AnimationClock clock, boolean on) {
        if (on) {
            clock.startIfStopped(this.ticksExisted);
        } else {
            clock.stop();
        }
    }

    // --- activity ---------------------------------------------------------------------------------------

    public Activity activity() {
        return this.activity;
    }

    /** 1.19's {@code FrogAi.updateActivity}: the first valid of tongue, lay spawn, long jump, swim, idle. */
    private Activity pickActivity() {
        boolean inWater = this.isInWater();
        if (this.getAttackTarget() != null) {
            return Activity.TONGUE;
        }
        if (this.pregnant && !this.midJump) {
            return Activity.LAY_SPAWN;
        }
        if (this.memory.temptingPlayer == null && this.breedTarget == null && this.longJumpCooldown <= 0 && !inWater) {
            return Activity.LONG_JUMP;
        }
        if (!this.midJump && inWater) {
            return Activity.SWIM;
        }
        if (!this.midJump && !inWater) {
            return Activity.IDLE;
        }
        return this.activity;
    }

    public boolean isMidJump() {
        return this.midJump;
    }

    public boolean isPregnant() {
        return this.pregnant;
    }

    public void setPregnant(boolean pregnant) {
        this.pregnant = pregnant;
    }

    public int getLongJumpCooldown() {
        return this.longJumpCooldown;
    }

    public void setLongJumpCooldown(int ticks) {
        this.longJumpCooldown = ticks;
    }

    @Override
    public void onLivingUpdate() {
        if (!this.world.isRemote) {
            serverBrainTick();
        }
        super.onLivingUpdate();
    }

    /** Sensors, timers and the activity, before the tasks run. */
    private void serverBrainTick() {
        this.memory.tick(this, this.rand);
        if (this.longJumpCooldown > 0) {
            this.longJumpCooldown--;
        }
        if (this.unreachableExpires > 0 && --this.unreachableExpires == 0) {
            this.unreachable.clear();
        }
        this.memory.panicking = this.getLastDamageSource() != null && this.memory.panicking;
        if (this.breedTarget != null && (!this.breedTarget.isEntityAlive() || this.breedTarget.breedTarget != this)) {
            this.breedTarget = null;
        }
        // StopAttackingIfTargetInvalid
        EntityLivingBase target = this.getAttackTarget();
        if (target != null && (!target.isEntityAlive() || target.world != this.world)) {
            this.setAttackTarget(null);
        }
        if (this.ticksExisted % 20 == 0) {
            this.nearestAttackable = FrogAi.findAttackable(this);
        }
        this.activity = pickActivity();
        if (this.activity != Activity.TONGUE && this.getAttackTarget() != null) {
            this.setAttackTarget(null);
        }
        // StartAttacking, in the idle, swim and lay spawn activities, when not breeding.
        if ((this.activity == Activity.IDLE || this.activity == Activity.SWIM || this.activity == Activity.LAY_SPAWN)
                && this.breedTarget == null && this.getAttackTarget() == null && this.nearestAttackable != null
                && this.nearestAttackable.isEntityAlive()) {
            this.setAttackTarget(this.nearestAttackable);
            this.activity = Activity.TONGUE;
        }
        // LongJumpMidJump: lands when back on the ground.
        if (this.midJump) {
            this.midJumpTicks++;
            if (this.getPose() != Pose.LONG_JUMPING) {
                this.setPose(Pose.LONG_JUMPING);
            }
            if ((this.onGround && this.midJumpTicks > 1) || this.midJumpTicks > 100 || this.isInWater()) {
                if (this.onGround) {
                    this.motionX *= 0.1F;
                    this.motionZ *= 0.1F;
                    this.playSound(FrogSounds.FROG_STEP, 2.0F, 1.0F);
                }
                this.midJump = false;
                this.midJumpTicks = 0;
                this.setPose(Pose.STANDING);
                this.longJumpCooldown = FrogAi.longJumpCooldown(this.rand);
            }
        }
        if (this.leashingPlayer != null) {
            if (this.getLeashed() && this.getLeashHolder() instanceof EntityPlayerMP
                    && this.leashingPlayer.equals(this.getLeashHolder().getUniqueID())) {
                FrogLeashTrigger.INSTANCE.trigger((EntityPlayerMP) this.getLeashHolder(), getVariant());
            }
            this.leashingPlayer = null;
        }
    }

    @Override
    protected void updateAITasks() {
        this.swimNavigator.onUpdateNavigation();
        super.updateAITasks();
    }

    @Override
    public void onUpdate() {
        if (this.world.isRemote) {
            double h = this.motionX * this.motionX + this.motionZ * this.motionZ;
            keep(this.walkAnimation, this.onGround && h > 1.0E-6D && !this.isInWater());
            boolean swimming = h > 1.0E-6D && this.isInWater();
            keep(this.swimAnimation, swimming);
            keep(this.swimIdleAnimation, !swimming && this.isInWater());
        }
        super.onUpdate();
    }

    /** 1.19's {@code Frog.travel}, plus 1.19's frictionless flight during a long jump. */
    @Override
    public void travel(float strafe, float vertical, float forward) {
        if (this.isServerWorld() && this.isInWater()) {
            this.moveRelative(strafe, vertical, forward, this.getAIMoveSpeed());
            this.move(MoverType.SELF, this.motionX, this.motionY, this.motionZ);
            this.motionX *= 0.9D;
            this.motionY *= 0.9D;
            this.motionZ *= 0.9D;
        } else if (this.midJump && !this.isInLava() && !this.onGround) {
            this.moveRelative(strafe, vertical, forward, this.jumpMovementFactor);
            this.move(MoverType.SELF, this.motionX, this.motionY, this.motionZ);
            if (!this.hasNoGravity()) {
                this.motionY -= 0.08D;
            }
        } else {
            super.travel(strafe, vertical, forward);
        }
    }

    /** {@code getJumpBoostPower}: 0.1 per Jump Boost level. */
    double jumpBoostPower() {
        return this.isPotionActive(MobEffects.JUMP_BOOST) ? 0.1D * (this.getActivePotionEffect(MobEffects.JUMP_BOOST).getAmplifier() + 1) : 0.0D;
    }

    // --- rules ------------------------------------------------------------------------------------------

    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    @Override
    public boolean isPushedByWater() {
        return false;
    }

    @Override
    public void fall(float distance, float damageMultiplier) {
        // 1.19's calculateFallDamage: 5 less than usual. 1.12.2 counts 3 blocks free, so 5 more are added.
        super.fall(Math.max(0.0F, distance - FALL_DAMAGE_REDUCTION / Math.max(damageMultiplier, 1.0E-4F)), damageMultiplier);
    }

    @Override
    public boolean isChild() {
        return false;
    }

    @Override
    public boolean isBreedingItem(ItemStack stack) {
        return FrogMemory.tempts(stack);
    }

    /** Frogs breed into frogspawn, never into a child. */
    @Nullable
    @Override
    public EntityAgeable createChild(EntityAgeable partner) {
        return null;
    }

    /** 1.19's {@code Frog.spawnChildFromBreeding}: the mother becomes pregnant, both rest 6000 ticks. */
    void spawnChildFromBreeding(EntityFrog partner) {
        EntityPlayerMP player = this.getLoveCause();
        if (player == null) {
            player = partner.getLoveCause();
        }
        if (player != null) {
            player.addStat(StatList.ANIMALS_BRED);
            CriteriaTriggers.BRED_ANIMALS.trigger(player, this, partner, null);
        }
        this.setGrowingAge(BREEDING_COOLDOWN);
        partner.setGrowingAge(BREEDING_COOLDOWN);
        this.resetInLove();
        partner.resetInLove();
        this.pregnant = true;
        this.world.setEntityState(this, (byte) 18);
        if (this.world.getGameRules().getBoolean("doMobLoot")) {
            this.world.spawnEntity(new EntityXPOrb(this.world, this.posX, this.posY, this.posZ, this.rand.nextInt(7) + 1));
        }
    }

    /** 1.19's {@code Frog.canEat}: slimes and magma cubes of size 1 (and nothing else). */
    public static boolean canEat(EntityLivingBase target) {
        return target instanceof EntitySlime && ((EntitySlime) target).getSlimeSize() == 1;
    }

    @Override
    public boolean attackEntityAsMob(Entity target) {
        float damage = (float) this.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).getAttributeValue();
        boolean hit = target.attackEntityFrom(DamageSource.causeMobDamage(this), damage);
        if (hit) {
            this.applyEnchantments(this, target);
        }
        this.setLastAttackedEntity(target);
        return hit;
    }

    @Override
    public boolean getCanSpawnHere() {
        int x = MathHelper.floor(this.posX);
        int y = MathHelper.floor(this.getEntityBoundingBox().minY);
        int z = MathHelper.floor(this.posZ);
        return FrogSpawns.canSpawnAt(this.world, new BlockPos(x, y, z)) && this.world.getBlockState(new BlockPos(x, y - 1, z)).canEntitySpawn(this);
    }

    /** 1.19's {@code finalizeSpawn}: the variant from the biome it is created in. */
    @Nullable
    @Override
    public IEntityLivingData onInitialSpawn(DifficultyInstance difficulty, @Nullable IEntityLivingData data) {
        Biome biome = this.world.getBiome(new BlockPos(this));
        this.setVariant(FrogSpawns.variantFor(biome));
        this.longJumpCooldown = FrogAi.longJumpCooldown(this.rand);
        return super.onInitialSpawn(difficulty, data);
    }

    // --- sounds -----------------------------------------------------------------------------------------

    @Override
    protected SoundEvent getAmbientSound() {
        return FrogSounds.FROG_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return FrogSounds.FROG_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return FrogSounds.FROG_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, Block block) {
        this.playSound(FrogSounds.FROG_STEP, 0.15F, 1.0F);
    }

    // --- saving -----------------------------------------------------------------------------------------

    @Override
    public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        tag.setString("variant", getVariant().id);
        if (this.pregnant) {
            tag.setBoolean("IsPregnant", true);
        }
        tag.setInteger("LongJumpCooldown", this.longJumpCooldown);
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        if (tag.hasKey("variant")) {
            this.setVariant(FrogVariant.byId(tag.getString("variant")));
        }
        this.pregnant = tag.getBoolean("IsPregnant");
        if (tag.hasKey("LongJumpCooldown")) {
            this.longJumpCooldown = tag.getInteger("LongJumpCooldown");
        }
    }

    /**
     * 1.19's {@code FrogNodeEvaluator}: a spot above a lily pad (or Caves &amp; Cliffs' big dripleaf) counts as
     * open, never as ground to walk on: frogs reach lily pads by long jumps.
     */
    static class FrogNodeProcessor extends WalkNodeProcessor {
        @Override
        public PathNodeType getPathNodeType(IBlockAccess world, int x, int y, int z) {
            Block below = world.getBlockState(new BlockPos(x, y - 1, z)).getBlock();
            return FrogAi.preferJumpTo(below) ? PathNodeType.OPEN : super.getPathNodeType(world, x, y, z);
        }
    }

    static boolean isLilyPad(Block block) {
        return block == Blocks.WATERLILY;
    }
}
