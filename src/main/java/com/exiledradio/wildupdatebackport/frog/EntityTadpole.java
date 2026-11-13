package com.exiledradio.wildupdatebackport.frog;

import com.exiledradio.wildupdatebackport.common.Owners;

import net.minecraft.entity.MoverType;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.passive.IAnimals;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.pathfinding.PathNavigate;
import net.minecraft.pathfinding.PathNavigateSwimmer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;

/**
 * The tadpole, as 1.19.2's {@code Tadpole} (an {@code AbstractFish}).
 *
 * <ul>
 *   <li>6 health, 0.4 x 0.3, speed 1 (x 0.02 in water); swims at random, follows a player holding a
 *       slimeball, panics when hurt; flops and slowly suffocates out of water (1.12.2's water-mob air, as
 *       1.19's).</li>
 *   <li>Grows into a frog 24000 ticks after it hatched (its {@code Age}); the frog's variant comes from the
 *       biome it grows up in, and it keeps the tadpole's name and NoAI. Feeding a slimeball takes off 10% of
 *       the time left.</li>
 *   <li>A water bucket picks it up into a bucket of tadpole that keeps its age. It never despawns and drops
 *       no experience.</li>
 * </ul>
 */
public class EntityTadpole extends EntityCreature implements IAnimals {

    public static final float WIDTH = 0.4F;
    public static final float HEIGHT = 0.3F;
    /** {@code Tadpole.ticksToBeFrog} */
    public static final int TICKS_TO_BE_FROG = 24000;

    public final FrogMemory memory = new FrogMemory();
    private int age;

    public EntityTadpole(World world) {
        super(world);
        this.setSize(WIDTH, HEIGHT);
        this.moveHelper = new SmoothSwimMoveHelper(this, 85, 10, 0.02F, 0.1F, true);
        // Here, not in initEntityAI, which runs before this class's fields exist.
        FrogAi.addTadpoleTasks(this);
    }

    // --- 1.12.2's water mob (squid), whose air rules are 1.19's WaterAnimal's -----------------------------

    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    @Override
    public boolean isPushedByWater() {
        return false;
    }

    @Override
    public int getTalkInterval() {
        return 120;
    }

    @Override
    public boolean isNotColliding() {
        return this.world.checkNoEntityCollision(this.getEntityBoundingBox(), this);
    }

    /** Out of water it loses a point of air a tick and, from -20, takes 2 drowning damage each second. */
    @Override
    public void onEntityUpdate() {
        int air = this.getAir();
        super.onEntityUpdate();
        if (this.isEntityAlive() && !this.isInWater()) {
            this.setAir(--air);
            if (this.getAir() == -20) {
                this.setAir(0);
                this.attackEntityFrom(DamageSource.DROWN, 2.0F);
            }
        } else {
            this.setAir(300);
        }
    }

    @Override
    protected PathNavigate createNavigator(World world) {
        return new PathNavigateSwimmer(this, world);
    }

    @Override
    protected void initEntityAI() {
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        this.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(6.0D);
        this.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(1.0D);
    }

    public int getAge() {
        return this.age;
    }

    /** Sets the age, growing into a frog at {@link #TICKS_TO_BE_FROG}. */
    public void setAge(int age) {
        this.age = age;
        if (this.age >= TICKS_TO_BE_FROG) {
            growUp();
        }
    }

    public int ticksLeftUntilAdult() {
        return Math.max(0, TICKS_TO_BE_FROG - this.age);
    }

    private void growUp() {
        if (this.world.isRemote || this.isDead) {
            return;
        }
        EntityFrog frog = new EntityFrog(this.world);
        frog.setLocationAndAngles(this.posX, this.posY, this.posZ, this.rotationYaw, this.rotationPitch);
        frog.onInitialSpawn(this.world.getDifficultyForLocation(new BlockPos(frog)), null);
        frog.setNoAI(this.isAIDisabled());
        if (this.hasCustomName()) {
            frog.setCustomNameTag(this.getCustomNameTag());
            frog.setAlwaysRenderNameTag(this.getAlwaysRenderNameTag());
        }
        frog.enablePersistence();
        this.playSound(FrogSounds.TADPOLE_GROW_UP, 0.15F, 1.0F);
        this.world.spawnEntity(frog);
        this.setDead();
    }

    @Override
    public void onLivingUpdate() {
        // AbstractFish: flops about on land.
        if (!this.isInWater() && this.onGround && this.collidedVertically) {
            this.motionY += 0.4D;
            this.motionX += (this.rand.nextFloat() * 2.0F - 1.0F) * 0.05F;
            this.motionZ += (this.rand.nextFloat() * 2.0F - 1.0F) * 0.05F;
            this.onGround = false;
            this.isAirBorne = true;
            this.playSound(FrogSounds.TADPOLE_FLOP, this.getSoundVolume(), this.getSoundPitch());
        }
        if (!this.world.isRemote) {
            this.memory.tick(this, this.rand);
            this.memory.panicking = this.memory.panicking && this.getLastDamageSource() != null;
        }
        super.onLivingUpdate();
        if (!this.world.isRemote) {
            setAge(this.age + 1);
        }
    }

    /** {@code AbstractFish.travel}: swims at 0.01, slowed by 0.9 a tick, sinking slightly. */
    @Override
    public void travel(float strafe, float vertical, float forward) {
        if (this.isServerWorld() && this.isInWater()) {
            this.moveRelative(strafe, vertical, forward, 0.01F);
            this.move(MoverType.SELF, this.motionX, this.motionY, this.motionZ);
            this.motionX *= 0.9D;
            this.motionY *= 0.9D;
            this.motionZ *= 0.9D;
            if (this.getAttackTarget() == null) {
                this.motionY -= 0.005D;
            }
        } else {
            super.travel(strafe, vertical, forward);
        }
    }

    @Override
    public boolean processInteract(EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (FrogMemory.tempts(stack)) {
            if (!player.capabilities.isCreativeMode) {
                stack.shrink(1);
            }
            int seconds = (int) ((float) (ticksLeftUntilAdult() / 20) * 0.1F);
            setAge(this.age + seconds * 20);
            if (this.world.isRemote) {
                this.world.spawnParticle(EnumParticleTypes.VILLAGER_HAPPY, this.posX + (this.rand.nextDouble() - 0.5D) * this.width,
                        this.posY + this.rand.nextDouble() * this.height + 0.5D, this.posZ + (this.rand.nextDouble() - 0.5D) * this.width,
                        0.0D, 0.0D, 0.0D);
            }
            return true;
        }
        if (stack.getItem() == Items.WATER_BUCKET && this.isEntityAlive()) {
            Item bucket = Owners.ours("tadpole_bucket");
            if (bucket == null) {
                return false;
            }
            this.playSound(FrogSounds.BUCKET_FILL_TADPOLE, 1.0F, 1.0F);
            ItemStack filled = new ItemStack(bucket);
            saveToBucketTag(filled);
            if (!this.world.isRemote) {
                if (player.capabilities.isCreativeMode) {
                    if (!player.inventory.hasItemStack(filled)) {
                        player.inventory.addItemStackToInventory(filled.copy());
                    }
                } else {
                    stack.shrink(1);
                    if (stack.isEmpty()) {
                        player.setHeldItem(hand, filled);
                    } else if (!player.inventory.addItemStackToInventory(filled)) {
                        player.dropItem(filled, false);
                    }
                }
                if (player instanceof EntityPlayerMP) {
                    FilledBucketTrigger.INSTANCE.trigger((EntityPlayerMP) player, filled);
                }
            }
            this.setDead();
            return true;
        }
        return super.processInteract(player, hand);
    }

    /** 1.19's {@code Bucketable.saveDefaultDataToBucketTag} and the tadpole's age. */
    public void saveToBucketTag(ItemStack stack) {
        if (this.hasCustomName()) {
            stack.setStackDisplayName(this.getCustomNameTag());
        }
        NBTTagCompound tag = stack.getTagCompound() == null ? new NBTTagCompound() : stack.getTagCompound();
        if (this.isAIDisabled()) {
            tag.setBoolean("NoAI", true);
        }
        if (this.isSilent()) {
            tag.setBoolean("Silent", true);
        }
        if (this.hasNoGravity()) {
            tag.setBoolean("NoGravity", true);
        }
        if (this.isGlowing()) {
            tag.setBoolean("Glowing", true);
        }
        if (this.getIsInvulnerable()) {
            tag.setBoolean("Invulnerable", true);
        }
        tag.setFloat("Health", this.getHealth());
        tag.setInteger("Age", this.age);
        stack.setTagCompound(tag);
    }

    /** 1.19's {@code Bucketable.loadDefaultDataFromBucketTag} and the age. */
    public void loadFromBucketTag(NBTTagCompound tag) {
        if (tag.hasKey("NoAI")) {
            this.setNoAI(tag.getBoolean("NoAI"));
        }
        if (tag.hasKey("Silent")) {
            this.setSilent(tag.getBoolean("Silent"));
        }
        if (tag.hasKey("NoGravity")) {
            this.setNoGravity(tag.getBoolean("NoGravity"));
        }
        if (tag.hasKey("Glowing")) {
            this.setGlowing(tag.getBoolean("Glowing"));
        }
        if (tag.hasKey("Invulnerable")) {
            this.setEntityInvulnerable(tag.getBoolean("Invulnerable"));
        }
        if (tag.hasKey("Health", 99)) {
            this.setHealth(tag.getFloat("Health"));
        }
        if (tag.hasKey("Age")) {
            this.setAge(tag.getInteger("Age"));
        }
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        tag.setInteger("Age", this.age);
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        this.setAge(tag.getInteger("Age"));
    }

    /** Always "from a bucket" in 1.19: it never despawns. */
    @Override
    protected boolean canDespawn() {
        return false;
    }

    @Override
    protected int getExperiencePoints(EntityPlayer player) {
        return 0;
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return FrogSounds.TADPOLE_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return FrogSounds.TADPOLE_DEATH;
    }
}
