package com.exiledradio.wildupdatebackport.goat;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityAgeable;
import net.minecraft.entity.IEntityLivingData;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIFollowParent;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAIMate;
import net.minecraft.entity.ai.EntityAIPanic;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAITempt;
import net.minecraft.entity.ai.EntityAIWanderAvoidWater;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.pathfinding.PathNavigateGround;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;

/**
 * The goat, Caves &amp; Cliffs' copied unchanged (1.17's, with current Java's numbers), plus 1.19's horn snapping.
 *
 * <h2>What it does</h2>
 * <ul>
 *   <li><b>Rams</b> a player, armor stand or mob that stands still ({@link EntityAIGoatRam}).</li>
 *   <li><b>Long jumps</b> to places it cannot walk to ({@link EntityAIGoatLongJump}); the flight itself is here
 *       ({@link #startLongJump}, {@link #travel}), because nothing may slow it down.</li>
 *   <li>Takes 10 less fall damage, gives milk, breeds with wheat. One in fifty is a screaming goat.</li>
 * </ul>
 *
 * <h2>Saved data</h2>
 * Current Java's tags: {@code IsScreamingGoat}, {@code HasLeftHorn}, {@code HasRightHorn} (a horn is lost to a
 * ram into a {@code #snaps_goat_horn} block) and the two cooldowns
 * where its brain keeps them ({@code Brain.memories}).
 */
public class EntityGoat extends EntityAnimal {

    /** Entity states (1.17's numbers): the head goes down before a ram and back up after. */
    public static final byte STATE_LOWER_HEAD = 58, STATE_RAISE_HEAD = 59;

    public static final float WIDTH = 0.9F, HEIGHT = 1.3F;
    /** While long jumping the goat is 0.7 of its size. */
    public static final float JUMP_SCALE = 0.7F;
    public static final int FALL_DAMAGE_REDUCTION = 10;
    public static final double SCREAMING_CHANCE = 0.02D;
    /** A long jump that has not landed after this many ticks ends anyway. */
    private static final int MID_JUMP_TIMEOUT = 100;

    private static final DataParameter<Boolean> SCREAMING = EntityDataManager.createKey(EntityGoat.class, DataSerializers.BOOLEAN);
    private static final DataParameter<Boolean> LEFT_HORN = EntityDataManager.createKey(EntityGoat.class, DataSerializers.BOOLEAN);
    private static final DataParameter<Boolean> RIGHT_HORN = EntityDataManager.createKey(EntityGoat.class, DataSerializers.BOOLEAN);

    private static final String MEMORY_RAM = "minecraft:ram_cooldown_ticks", MEMORY_JUMP = "minecraft:long_jump_cooling_down";

    /** Ticks until the next ram attempt and the next long jump. Counted down every tick, whatever the goat does. */
    private int ramCooldown, jumpCooldown;

    private boolean loweringHead;
    private int lowerHeadTick;

    private boolean midJump;
    private int midJumpTicks;
    private BlockPos jumpTarget;

    public EntityGoat(World world) {
        super(world);
        this.setSize(WIDTH, HEIGHT);
        ((PathNavigateGround) this.getNavigator()).setCanSwim(true);
    }

    @Override
    protected void initEntityAI() {
        // 1.17's order: panic always wins; a goat that is breeding or tempted does not ram; ramming comes
        // before jumping and both before idling.
        this.tasks.addTask(0, new EntityAISwimming(this));
        this.tasks.addTask(1, new EntityAIPanic(this, 2.0D));
        this.tasks.addTask(2, new EntityAIMate(this, 1.0D));
        this.tasks.addTask(3, new EntityAITempt(this, 1.25D, Items.WHEAT, false));
        this.tasks.addTask(4, new EntityAIGoatRam(this));
        this.tasks.addTask(5, new EntityAIGoatLongJump(this));
        this.tasks.addTask(6, new EntityAIFollowParent(this, 1.25D));
        this.tasks.addTask(7, new EntityAIWanderAvoidWater(this, 1.0D));
        this.tasks.addTask(8, new EntityAIWatchClosest(this, EntityPlayer.class, 6.0F));
        this.tasks.addTask(9, new EntityAILookIdle(this));
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        this.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(10.0D);
        this.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.2D);
        this.getAttributeMap().registerAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(2.0D);
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        this.dataManager.register(SCREAMING, false);
        this.dataManager.register(LEFT_HORN, true);
        this.dataManager.register(RIGHT_HORN, true);
    }

    // ------------------------------------------------------------------------------------------
    // Screaming, horns
    // ------------------------------------------------------------------------------------------

    public boolean isScreamingGoat() {
        return this.dataManager.get(SCREAMING);
    }

    public void setScreamingGoat(boolean screaming) {
        this.dataManager.set(SCREAMING, screaming);
    }

    public boolean hasLeftHorn() {
        return this.dataManager.get(LEFT_HORN);
    }

    public boolean hasRightHorn() {
        return this.dataManager.get(RIGHT_HORN);
    }

    /** Sets the horns; a ram into a {@code #snaps_goat_horn} block takes one ({@link EntityAIGoatRam}). */
    public void setHorns(boolean left, boolean right) {
        this.dataManager.set(LEFT_HORN, left);
        this.dataManager.set(RIGHT_HORN, right);
    }

    private SoundEvent pick(SoundEvent[] pair) {
        return pair[this.isScreamingGoat() ? 1 : 0];
    }

    /** A ram's damage: 2 from an adult, 1 from a kid. */
    public double ramDamage() {
        this.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(this.isChild() ? 1.0D : 2.0D);
        return this.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).getAttributeValue();
    }

    // ------------------------------------------------------------------------------------------
    // Cooldowns
    // ------------------------------------------------------------------------------------------

    public int getRamCooldown() {
        return this.ramCooldown;
    }

    public void setRamCooldown(int ticks) {
        this.ramCooldown = Math.max(0, ticks);
    }

    public int getJumpCooldown() {
        return this.jumpCooldown;
    }

    public void setJumpCooldown(int ticks) {
        this.jumpCooldown = Math.max(0, ticks);
    }

    /** 30 seconds to 5 minutes between rams; 5 to 15 seconds for a screaming goat. */
    int nextRamCooldown() {
        return this.isScreamingGoat() ? 100 + this.rand.nextInt(201) : 600 + this.rand.nextInt(5401);
    }

    /** The wait after a ram attempt that found nobody to ram: the shortest cooldown. */
    int failedRamCooldown() {
        return this.isScreamingGoat() ? 100 : 600;
    }

    /** 30 to 60 seconds between long jumps. */
    int nextJumpCooldown() {
        return 600 + this.rand.nextInt(601);
    }

    @Override
    protected void updateAITasks() {
        super.updateAITasks();
        // Timers run here and never inside a goal: a goal that another goal keeps out is never asked.
        if (this.ramCooldown > 0) {
            this.ramCooldown--;
        }
        if (this.jumpCooldown > 0) {
            this.jumpCooldown--;
        }
        // The first tick of a jump is the tick it starts on, still on the ground.
        if (this.midJump && this.midJumpTicks++ > 0 && (this.onGround || this.midJumpTicks > MID_JUMP_TIMEOUT)) {
            this.endLongJump();
        }
    }

    // ------------------------------------------------------------------------------------------
    // Ramming: the head
    // ------------------------------------------------------------------------------------------

    /** Lowers or raises the head, here and on every client watching. */
    void setLoweringHead(boolean lowering) {
        this.loweringHead = lowering;
        this.world.setEntityState(this, lowering ? STATE_LOWER_HEAD : STATE_RAISE_HEAD);
    }

    public boolean isLoweringHead() {
        return this.loweringHead;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void handleStatusUpdate(byte id) {
        if (id == STATE_LOWER_HEAD) {
            this.loweringHead = true;
        } else if (id == STATE_RAISE_HEAD) {
            this.loweringHead = false;
        } else {
            super.handleStatusUpdate(id);
        }
    }

    /** @return the head's pitch while it is lowered for a ram, in radians: 30 degrees after a second */
    public float getRammingXHeadRot() {
        return this.lowerHeadTick / 20.0F * 30.0F * ((float) Math.PI / 180F);
    }

    @Override
    public void onLivingUpdate() {
        if (this.loweringHead) {
            this.lowerHeadTick++;
        } else {
            this.lowerHeadTick -= 2;
        }
        this.lowerHeadTick = MathHelper.clamp(this.lowerHeadTick, 0, 20);
        super.onLivingUpdate();
        // A goat's head turns at most 15 degrees from its body.
        float turn = MathHelper.wrapDegrees(this.rotationYawHead - this.renderYawOffset);
        this.rotationYawHead = this.renderYawOffset + MathHelper.clamp(turn, -15.0F, 15.0F);

        if (!this.world.isRemote && this.ticksExisted % 20 == 0 && this.getRidingEntity() instanceof EntityBoat) {
            for (Entity passenger : this.getRidingEntity().getPassengers()) {
                if (passenger instanceof EntityPlayer) {
                    GoatContent.grant((EntityPlayer) passenger, GoatContent.BOAT_ADVANCEMENT);
                }
            }
        }
    }

    void playRamSound(SoundEvent[] pair, float pitch) {
        this.world.playSound(null, this.posX, this.posY, this.posZ, this.pick(pair), SoundCategory.HOSTILE, 1.0F, pitch);
    }

    float voicePitch() {
        return this.getSoundPitch();
    }

    // ------------------------------------------------------------------------------------------
    // Long jumps: the flight
    // ------------------------------------------------------------------------------------------

    public boolean isMidJump() {
        return this.midJump;
    }

    /** @return where the last long jump aimed, or null before the first */
    @Nullable
    public BlockPos getJumpTarget() {
        return this.jumpTarget;
    }

    /** @return the extra jump speed Jump Boost gives, as vanilla's jump */
    double jumpBoostPower() {
        PotionEffect boost = this.getActivePotionEffect(MobEffects.JUMP_BOOST);
        return boost == null ? 0.0D : 0.1D * (boost.getAmplifier() + 1);
    }

    /** Leaves the ground with this velocity (blocks per tick) for a block to land in, which {@link #travel} then keeps free of drag. */
    void startLongJump(Vec3d velocity, BlockPos target) {
        this.jumpTarget = target;
        this.rotationYaw = this.renderYawOffset;
        double length = velocity.length();
        double scale = (length + this.jumpBoostPower()) / length;
        this.motionX = velocity.x * scale;
        this.motionY = velocity.y * scale;
        this.motionZ = velocity.z * scale;
        this.velocityChanged = true;
        this.midJump = true;
        this.midJumpTicks = 0;
        this.getNavigator().clearPath();
        this.resize(WIDTH * JUMP_SCALE, HEIGHT * JUMP_SCALE);
        this.world.playSound(null, this.posX, this.posY, this.posZ, this.pick(GoatContent.LONG_JUMP), SoundCategory.NEUTRAL, 1.0F, 1.0F);
    }

    private void endLongJump() {
        if (this.onGround) {
            this.motionX *= 0.1D;
            this.motionY *= 0.1D;
            this.motionZ *= 0.1D;
            this.world.playSound(null, this.posX, this.posY, this.posZ, GoatContent.STEP, SoundCategory.NEUTRAL, 2.0F, 1.0F);
        }
        this.midJump = false;
        this.resize(WIDTH, HEIGHT);
        this.jumpCooldown = this.nextJumpCooldown();
    }

    /** An ageable mob's size only takes effect when its age scale is applied again. */
    private void resize(float width, float height) {
        this.setSize(width, height);
        this.setScaleForAge(this.isChild());
    }

    /**
     * A long jump is a plain throw: 1.17 switches off friction for it, so the jump the goat worked out lands
     * where it aimed. 1.12 has no such switch, so the air drag its movement applies is undone here.
     */
    @Override
    public void travel(float strafe, float vertical, float forward) {
        if (!this.midJump || this.world.isRemote || this.isInWater() || this.isInLava()) {
            super.travel(strafe, vertical, forward);
            return;
        }
        double x = this.motionX, y = this.motionY, z = this.motionZ;
        super.travel(0.0F, 0.0F, 0.0F);
        if (!this.collidedHorizontally) {
            this.motionX = x;
            this.motionZ = z;
        }
        if (!this.onGround && !this.collidedVertically) {
            this.motionY = y - 0.08D;
        }
    }

    /** 10 less fall damage than anything else: a goat walks away from a 13-block fall. */
    @Override
    public void fall(float distance, float damageMultiplier) {
        super.fall(damageMultiplier > 0.0F ? distance - FALL_DAMAGE_REDUCTION / damageMultiplier : distance, damageMultiplier);
    }

    // ------------------------------------------------------------------------------------------
    // Sounds
    // ------------------------------------------------------------------------------------------

    @Override
    protected SoundEvent getAmbientSound() {
        return this.pick(GoatContent.AMBIENT);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return this.pick(GoatContent.HURT);
    }

    @Override
    protected SoundEvent getDeathSound() {
        return this.pick(GoatContent.DEATH);
    }

    @Override
    protected void playStepSound(BlockPos pos, Block block) {
        this.playSound(GoatContent.STEP, 0.15F, 1.0F);
    }

    // ------------------------------------------------------------------------------------------
    // Milk, wheat, kids
    // ------------------------------------------------------------------------------------------

    @Override
    public boolean processInteract(EntityPlayer player, EnumHand hand) {
        ItemStack held = player.getHeldItem(hand);
        if (held.getItem() == Items.BUCKET && !this.isChild()) {
            player.playSound(this.pick(GoatContent.MILK), 1.0F, 1.0F);
            ItemStack milk = new ItemStack(Items.MILK_BUCKET);
            if (player.capabilities.isCreativeMode) {
                // Current Java: creative keeps the bucket and gets one milk bucket if it has none.
                if (!player.inventory.hasItemStack(milk)) {
                    player.inventory.addItemStackToInventory(milk);
                }
            } else {
                held.shrink(1);
                if (held.isEmpty()) {
                    player.setHeldItem(hand, milk);
                } else if (!player.inventory.addItemStackToInventory(milk)) {
                    player.dropItem(milk, false);
                }
            }
            return true;
        }
        boolean food = this.isBreedingItem(held);
        boolean used = super.processInteract(player, hand);
        if (used && food && !this.world.isRemote) {
            this.world.playSound(null, this.posX, this.posY, this.posZ, this.pick(GoatContent.EAT), SoundCategory.NEUTRAL, 1.0F,
                    0.8F + this.rand.nextFloat() * 0.4F);
        }
        return used;
    }

    /** As vanilla: the kid screams if the other parent does, and 1 in 50 otherwise. */
    @Override
    public EntityGoat createChild(EntityAgeable mate) {
        EntityGoat kid = new EntityGoat(this.world);
        kid.initCooldowns();
        boolean screamingMate = mate instanceof EntityGoat && ((EntityGoat) mate).isScreamingGoat();
        kid.setScreamingGoat(screamingMate || this.rand.nextDouble() < SCREAMING_CHANCE);
        return kid;
    }

    private void initCooldowns() {
        this.jumpCooldown = this.nextJumpCooldown();
        this.ramCooldown = 600 + this.rand.nextInt(5401);
    }

    // ------------------------------------------------------------------------------------------
    // Spawning
    // ------------------------------------------------------------------------------------------

    /** Counts a spawned group, so its first goat is never a kid (as 1.14 and later spawn animals). */
    private static final class Group implements IEntityLivingData {
        int size;
    }

    @Nullable
    @Override
    public IEntityLivingData onInitialSpawn(DifficultyInstance difficulty, @Nullable IEntityLivingData data) {
        data = super.onInitialSpawn(difficulty, data);
        this.initCooldowns();
        this.setScreamingGoat(this.rand.nextDouble() < SCREAMING_CHANCE);
        if (!(data instanceof Group)) {
            data = new Group();
        }
        Group group = (Group) data;
        if (group.size > 0 && this.rand.nextFloat() <= 0.05F) {
            this.setGrowingAge(-24000);
        }
        group.size++;
        return data;
    }

    /**
     * @return true for the blocks goats spawn on: stone, snow, packed ice, gravel (and powder snow, which is
     * Caves &amp; Cliffs' and never present while this copy is registered)
     */
    public static boolean spawnableOn(IBlockState state) {
        Block block = state.getBlock();
        return block == Blocks.STONE || block == Blocks.SNOW || block == Blocks.SNOW_LAYER || block == Blocks.PACKED_ICE
                || block == Blocks.GRAVEL;
    }

    @Override
    public float getBlockPathWeight(BlockPos pos) {
        return spawnableOn(this.world.getBlockState(pos.down())) ? 10.0F : this.world.getLightBrightness(pos) - 0.5F;
    }

    @Override
    public boolean getCanSpawnHere() {
        BlockPos pos = new BlockPos(MathHelper.floor(this.posX), MathHelper.floor(this.getEntityBoundingBox().minY), MathHelper.floor(this.posZ));
        return spawnableOn(this.world.getBlockState(pos.down())) && this.world.getLight(pos) > 8;
    }

    // ------------------------------------------------------------------------------------------
    // Saving
    // ------------------------------------------------------------------------------------------

    @Override
    public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        tag.setBoolean("IsScreamingGoat", this.isScreamingGoat());
        tag.setBoolean("HasLeftHorn", this.hasLeftHorn());
        tag.setBoolean("HasRightHorn", this.hasRightHorn());
        NBTTagCompound memories = new NBTTagCompound();
        memory(memories, MEMORY_RAM, this.ramCooldown);
        memory(memories, MEMORY_JUMP, this.jumpCooldown);
        NBTTagCompound brain = new NBTTagCompound();
        brain.setTag("memories", memories);
        tag.setTag("Brain", brain);
    }

    private static void memory(NBTTagCompound memories, String name, int value) {
        if (value > 0) {
            NBTTagCompound memory = new NBTTagCompound();
            memory.setInteger("value", value);
            memories.setTag(name, memory);
        }
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        this.setScreamingGoat(tag.getBoolean("IsScreamingGoat"));
        // A goat saved without the horn tags (none exists, but a summon command may leave them out) has both.
        this.setHorns(!tag.hasKey("HasLeftHorn") || tag.getBoolean("HasLeftHorn"), !tag.hasKey("HasRightHorn") || tag.getBoolean("HasRightHorn"));
        NBTTagCompound memories = tag.getCompoundTag("Brain").getCompoundTag("memories");
        this.ramCooldown = memories.getCompoundTag(MEMORY_RAM).getInteger("value");
        this.jumpCooldown = memories.getCompoundTag(MEMORY_JUMP).getInteger("value");
    }
}
