package com.exiledradio.wildupdatebackport.allay;

import com.exiledradio.backportcore.gameevent.GameEvent;
import com.exiledradio.backportcore.gameevent.GameEventContext;
import com.exiledradio.backportcore.gameevent.GameEventListener;
import com.exiledradio.backportcore.gameevent.GameEventTags;
import com.exiledradio.backportcore.gameevent.GameEvents;
import com.exiledradio.backportcore.gameevent.PositionSource;
import com.exiledradio.backportcore.gameevent.VibrationListener;

import net.minecraft.block.BlockJukebox;
import net.minecraft.block.state.IBlockState;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.pathfinding.PathNavigate;
import net.minecraft.pathfinding.PathNavigateFlying;
import net.minecraft.potion.PotionUtils;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.block.BlockJukebox.TileEntityJukebox;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.Set;
import java.util.UUID;

/**
 * The allay: 1.19's {@code Allay}, current Java's rules.
 *
 * <p>It flies; given an item it keeps one in its hand and collects matching items (up to a stack, in a
 * one-slot inventory), and delivers them to the player who gave it the item, or to a note block it heard
 * played in the last 30 seconds. It dances while a jukebox within 10 blocks plays, and a dancing allay
 * given an amethyst shard duplicates (6000-tick cooldown). It heals 1 health every 10 ticks, never
 * despawns, takes no fall damage and cannot be hurt by the player it likes.
 *
 * <p>Its AI is 1.19's brain in small: {@link AllayBrain}.
 */
public class EntityAllay extends EntityCreature {

    public static final int DUPLICATION_COOLDOWN = 6000;
    public static final int LISTENER_RADIUS = 16;
    /** 1.19's {@code GameEvent.JUKEBOX_PLAY.getNotificationRadius()}. */
    public static final int JUKEBOX_RADIUS = 10;
    static final float DANCING_LOOP = 55.0F;
    static final float SPINNING_DURATION = 15.0F;
    static final float HOLDING_DURATION = 5.0F;

    /** 1.19's {@code Allay.THROW_SOUND_PITCHES}. */
    static final float[] THROW_SOUND_PITCHES = {0.5625F, 0.625F, 0.75F, 0.9375F, 1.0F, 1.0F, 1.125F, 1.25F, 1.5F,
            1.875F, 2.0F, 2.25F, 2.5F, 3.0F, 3.75F, 4.0F};

    private static final DataParameter<Boolean> DANCING = EntityDataManager.createKey(EntityAllay.class, DataSerializers.BOOLEAN);
    private static final DataParameter<Boolean> CAN_DUPLICATE = EntityDataManager.createKey(EntityAllay.class, DataSerializers.BOOLEAN);

    private final InventoryBasic inventory = new InventoryBasic("Items", false, 1);
    final AllayBrain brain = new AllayBrain(this);
    private final VibrationListener vibrationListener;
    private final GameEventListener jukeboxListener;
    private boolean listening;

    @Nullable
    private BlockPos jukeboxPos;
    private long duplicationCooldown;

    // client animation state
    private float holdingTicks;
    private float holdingTicks0;
    private float dancingTicks;
    private float spinningTicks;
    private float spinningTicks0;

    public EntityAllay(World world) {
        super(world);
        this.setSize(0.35F, 0.6F);
        this.moveHelper = new AllayMoveHelper(this);
        this.experienceValue = 0;
        // The held item always drops on death, undamaged, unless it carries Curse of Vanishing.
        this.setDropChance(EntityEquipmentSlot.MAINHAND, 2.0F);
        PositionSource eyes = new PositionSource.EntitySource(this, this.height * 0.6F);
        this.vibrationListener = new VibrationListener(eyes, LISTENER_RADIUS, new NoteBlockEars());
        this.jukeboxListener = new JukeboxEars(eyes);
    }

    // ---------------------------------------------------------------------------------------------
    // Attributes, movement
    // ---------------------------------------------------------------------------------------------

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        this.getAttributeMap().registerAttribute(SharedMonsterAttributes.FLYING_SPEED);
        this.getAttributeMap().registerAttribute(SharedMonsterAttributes.ATTACK_DAMAGE);
        this.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(20.0D);
        this.getEntityAttribute(SharedMonsterAttributes.FLYING_SPEED).setBaseValue(0.1F);
        this.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.1F);
        this.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(2.0D);
        // 1.19.2's 48: the allay paths to items 32 blocks away and to its player up to 64.
        this.getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE).setBaseValue(48.0D);
    }

    @Override
    protected PathNavigate createNavigator(World world) {
        PathNavigateFlying nav = new PathNavigateFlying(this, world);
        nav.setCanOpenDoors(false);
        nav.setCanFloat(true);
        nav.setCanEnterDoors(true);
        return nav;
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        this.dataManager.register(DANCING, false);
        this.dataManager.register(CAN_DUPLICATE, true);
    }

    /** 1.19's {@code Allay.travel}: flying drag 0.91, water 0.8, lava 0.5. */
    @Override
    public void travel(float strafe, float vertical, float forward) {
        if (this.isServerWorld() || this.canPassengerSteer()) {
            if (this.isInWater()) {
                this.moveRelative(strafe, vertical, forward, 0.02F);
                this.move(net.minecraft.entity.MoverType.SELF, this.motionX, this.motionY, this.motionZ);
                this.motionX *= 0.8F;
                this.motionY *= 0.8F;
                this.motionZ *= 0.8F;
            } else if (this.isInLava()) {
                this.moveRelative(strafe, vertical, forward, 0.02F);
                this.move(net.minecraft.entity.MoverType.SELF, this.motionX, this.motionY, this.motionZ);
                this.motionX *= 0.5D;
                this.motionY *= 0.5D;
                this.motionZ *= 0.5D;
            } else {
                this.moveRelative(strafe, vertical, forward, this.getAIMoveSpeed());
                this.move(net.minecraft.entity.MoverType.SELF, this.motionX, this.motionY, this.motionZ);
                this.motionX *= 0.91F;
                this.motionY *= 0.91F;
                this.motionZ *= 0.91F;
            }
        }
        // 1.19's calculateEntityAnimation(this, false): horizontal movement only
        this.prevLimbSwingAmount = this.limbSwingAmount;
        double dx = this.posX - this.prevPosX;
        double dz = this.posZ - this.prevPosZ;
        float f = MathHelper.sqrt(dx * dx + dz * dz) * 4.0F;
        if (f > 1.0F) {
            f = 1.0F;
        }
        this.limbSwingAmount += (f - this.limbSwingAmount) * 0.4F;
        this.limbSwing += this.limbSwingAmount;
    }

    @Override
    public float getEyeHeight() {
        return this.height * 0.6F;
    }

    @Override
    public void fall(float distance, float damageMultiplier) {
    }

    @Override
    protected void updateFallState(double y, boolean onGround, IBlockState state, BlockPos pos) {
    }

    @Override
    protected void playStepSound(BlockPos pos, net.minecraft.block.Block block) {
    }

    @Override
    protected boolean canDespawn() {
        return false;
    }

    /** 1.19's {@code isFlapping}: the wings flap whenever it is off the ground. */
    public boolean isFlapping() {
        return !this.onGround;
    }

    // ---------------------------------------------------------------------------------------------
    // Sounds
    // ---------------------------------------------------------------------------------------------

    @Override
    protected SoundEvent getAmbientSound() {
        return this.getHeldItemMainhand().isEmpty() ? AllayContent.AMBIENT_WITHOUT_ITEM : AllayContent.AMBIENT_WITH_ITEM;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return AllayContent.HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return AllayContent.DEATH;
    }

    @Override
    protected float getSoundVolume() {
        return 0.4F;
    }

    // ---------------------------------------------------------------------------------------------
    // Ticking
    // ---------------------------------------------------------------------------------------------

    @Override
    protected void updateAITasks() {
        super.updateAITasks();
        this.brain.tick();
    }

    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();
        if (!this.world.isRemote && this.isEntityAlive() && this.ticksExisted % 10 == 0) {
            this.heal(1.0F);
        }
        if (this.isDancing() && this.ticksExisted % 20 == 0 && this.shouldStopDancing()) {
            this.setDancing(false);
            this.jukeboxPos = null;
        }
        this.updateDuplicationCooldown();
        if (!this.world.isRemote) {
            // Current Java's jukebox tells listeners it is playing every second, so an allay that arrives
            // after the disc went in still dances. 1.12.2's jukebox keeps no song length: a jukebox
            // holding a disc is playing.
            if (!this.isDancing() && this.ticksExisted % 20 == 0) {
                BlockPos playing = this.findPlayingJukebox();
                if (playing != null) {
                    this.setJukeboxPlaying(playing, true);
                }
            }
            if (this.canCollect() && !this.dead && ForgeEventFactory.getMobGriefingEvent(this.world, this)) {
                // 1.19's pickup reach (1, 1, 1) around the bounding box.
                for (EntityItem item : this.world.getEntitiesWithinAABB(EntityItem.class, this.getEntityBoundingBox().grow(1.0D, 1.0D, 1.0D))) {
                    if (!item.isDead && !item.getItem().isEmpty() && !item.cannotPickup() && this.wantsToPickUp(item.getItem())) {
                        this.pickUp(item);
                    }
                }
            }
        }
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (this.world.isRemote) {
            this.holdingTicks0 = this.holdingTicks;
            if (!this.getHeldItemMainhand().isEmpty()) {
                this.holdingTicks = MathHelper.clamp(this.holdingTicks + 1.0F, 0.0F, HOLDING_DURATION);
            } else {
                this.holdingTicks = MathHelper.clamp(this.holdingTicks - 1.0F, 0.0F, HOLDING_DURATION);
            }
            if (this.isDancing()) {
                this.dancingTicks++;
                this.spinningTicks0 = this.spinningTicks;
                if (this.isSpinning()) {
                    this.spinningTicks++;
                } else {
                    this.spinningTicks--;
                }
                this.spinningTicks = MathHelper.clamp(this.spinningTicks, 0.0F, SPINNING_DURATION);
            } else {
                this.dancingTicks = 0.0F;
                this.spinningTicks = 0.0F;
                this.spinningTicks0 = 0.0F;
            }
        } else {
            this.vibrationListener.tick(this.world);
            if (this.listening) {
                GameEvents.move(this.world, this.vibrationListener);
                GameEvents.move(this.world, this.jukeboxListener);
            }
            if (this.brain.isPanicking()) {
                this.setDancing(false);
            }
        }
    }

    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        if (!this.world.isRemote) {
            GameEvents.add(this.world, this.vibrationListener);
            GameEvents.add(this.world, this.jukeboxListener);
            this.listening = true;
        }
    }

    @Override
    public void onRemovedFromWorld() {
        super.onRemovedFromWorld();
        if (!this.world.isRemote) {
            GameEvents.remove(this.world, this.vibrationListener);
            GameEvents.remove(this.world, this.jukeboxListener);
            this.listening = false;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Damage and death
    // ---------------------------------------------------------------------------------------------

    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        if (this.isLikedPlayer(source.getTrueSource())) {
            return false;
        }
        return super.attackEntityFrom(source, amount);
    }

    /** Current Java: the liked player counts as an ally. */
    @Override
    public boolean isOnSameTeam(Entity other) {
        return this.isLikedPlayer(other) || super.isOnSameTeam(other);
    }

    boolean isLikedPlayer(@Nullable Entity entity) {
        UUID liked = this.brain.likedPlayer();
        return entity instanceof EntityPlayer && liked != null && liked.equals(entity.getUniqueID());
    }

    /** 1.19's {@code dropEquipment}: the inventory always drops (whatever {@code doMobLoot} says). */
    @Override
    public void onDeath(DamageSource cause) {
        if (!this.world.isRemote && !this.dead) {
            for (int i = 0; i < this.inventory.getSizeInventory(); i++) {
                ItemStack stack = this.inventory.removeStackFromSlot(i);
                if (!stack.isEmpty()) {
                    this.entityDropItem(stack, 0.0F);
                }
            }
            ItemStack held = this.getHeldItemMainhand();
            if (!held.isEmpty() && !EnchantmentHelper.hasVanishingCurse(held)) {
                this.entityDropItem(held, 0.0F);
                this.setHeldItem(EnumHand.MAIN_HAND, ItemStack.EMPTY);
            }
        }
        super.onDeath(cause);
    }

    // ---------------------------------------------------------------------------------------------
    // Items
    // ---------------------------------------------------------------------------------------------

    public InventoryBasic getInventory() {
        return this.inventory;
    }

    /** 1.19's {@code canPickUpLoot}: holding an item and not just after throwing one. */
    public boolean canCollect() {
        return this.brain.pickupCooldown() <= 0 && !this.getHeldItemMainhand().isEmpty();
    }

    /** The allay takes no armor or weapons the vanilla way. */
    @Override
    public boolean canPickUpLoot() {
        return false;
    }

    /** 1.19's {@code wantsToPickUp}, with current Java's potion rule. */
    public boolean wantsToPickUp(ItemStack stack) {
        ItemStack held = this.getHeldItemMainhand();
        return !held.isEmpty() && ForgeEventFactory.getMobGriefingEvent(this.world, this) && this.canAdd(stack)
                && considersEqual(held, stack);
    }

    /**
     * Current Java's {@code allayConsidersItemEqual}: the same item (and, in 1.12.2, the same subtype, since
     * 1.19's wool colors and the like are items of their own), damage ignored, and the same potion.
     */
    static boolean considersEqual(ItemStack held, ItemStack other) {
        return ItemStack.areItemsEqualIgnoreDurability(held, other)
                && PotionUtils.getPotionFromItem(held) == PotionUtils.getPotionFromItem(other);
    }

    /** 1.19's {@code SimpleContainer.canAddItem}. */
    boolean canAdd(ItemStack stack) {
        for (int i = 0; i < this.inventory.getSizeInventory(); i++) {
            ItemStack in = this.inventory.getStackInSlot(i);
            if (in.isEmpty() || (ItemStack.areItemsEqual(in, stack) && ItemStack.areItemStackTagsEqual(in, stack)
                    && in.getCount() < in.getMaxStackSize())) {
                return true;
            }
        }
        return false;
    }

    /** 1.19's {@code InventoryCarrier.pickUpItem}. */
    void pickUp(EntityItem entity) {
        ItemStack stack = entity.getItem();
        if (!this.canAdd(stack)) {
            return;
        }
        int before = stack.getCount();
        ItemStack left = this.inventory.addItem(stack.copy());
        int taken = before - left.getCount();
        if (taken <= 0) {
            return;
        }
        this.onItemPickup(entity, taken);
        if (left.isEmpty()) {
            entity.setDead();
        } else {
            stack.setCount(left.getCount());
            entity.setItem(stack);
        }
    }

    @Override
    protected boolean processInteract(EntityPlayer player, EnumHand hand) {
        ItemStack held = player.getHeldItem(hand);
        ItemStack mine = this.getHeldItemMainhand();
        if (this.isDancing() && AllayContent.isDuplicationItem(held) && this.canDuplicate()) {
            if (!this.world.isRemote) {
                this.duplicate();
                this.world.setEntityState(this, (byte) 18);
                SoundEvent chime = AllayContent.chime();
                if (chime != null) {
                    this.world.playSound(null, this.posX, this.posY, this.posZ, chime, SoundCategory.NEUTRAL, 2.0F, 1.0F);
                }
                consume(player, held);
            }
            return true;
        }
        if (mine.isEmpty() && !held.isEmpty()) {
            if (!this.world.isRemote) {
                ItemStack one = held.copy();
                one.setCount(1);
                this.setHeldItem(EnumHand.MAIN_HAND, one);
                consume(player, held);
                this.world.playSound(null, this.posX, this.posY, this.posZ, AllayContent.ITEM_GIVEN, SoundCategory.NEUTRAL, 2.0F, 1.0F);
                this.brain.setLikedPlayer(player.getUniqueID());
            }
            return true;
        }
        if (!mine.isEmpty() && hand == EnumHand.MAIN_HAND && held.isEmpty()) {
            if (!this.world.isRemote) {
                this.setHeldItem(EnumHand.MAIN_HAND, ItemStack.EMPTY);
                this.world.playSound(null, this.posX, this.posY, this.posZ, AllayContent.ITEM_TAKEN, SoundCategory.NEUTRAL, 2.0F, 1.0F);
                this.swingArm(EnumHand.MAIN_HAND);
                for (int i = 0; i < this.inventory.getSizeInventory(); i++) {
                    ItemStack stack = this.inventory.removeStackFromSlot(i);
                    if (!stack.isEmpty()) {
                        // BehaviorUtils.throwItem(this, stack, position()): no direction, so no push.
                        this.throwItem(stack, new Vec3d(this.posX, this.posY, this.posZ), 0.3F, 0.3F, 0.3F);
                    }
                }
                this.brain.setLikedPlayer(null);
                // As 1.19: with a full inventory the item is not kept.
                player.inventory.addItemStackToInventory(mine);
            }
            return true;
        }
        return super.processInteract(player, hand);
    }

    private static void consume(EntityPlayer player, ItemStack held) {
        if (!player.capabilities.isCreativeMode) {
            held.shrink(1);
        }
    }

    /**
     * 1.19's {@code BehaviorUtils.throwItem}: from just under the eyes, toward a target, with the given
     * speed on each axis and the default pickup delay. Marks the item as the allay's, for
     * <i>You've Got a Friend in Me</i>.
     */
    EntityItem throwItem(ItemStack stack, Vec3d target, float vx, float vy, float yOffset) {
        double y = this.posY + this.getEyeHeight() - yOffset;
        EntityItem item = new EntityItem(this.world, this.posX, y, this.posZ, stack);
        item.getEntityData().setUniqueId(AllayContent.THROWER_TAG, this.getUniqueID());
        Vec3d dir = target.subtract(this.posX, this.posY, this.posZ).normalize();
        item.motionX = dir.x * vx;
        item.motionY = dir.y * vy;
        item.motionZ = dir.z * vx;
        item.setDefaultPickupDelay();
        this.world.spawnEntity(item);
        return item;
    }

    // ---------------------------------------------------------------------------------------------
    // Dancing and duplication
    // ---------------------------------------------------------------------------------------------

    public boolean isDancing() {
        return this.dataManager.get(DANCING);
    }

    public void setDancing(boolean dancing) {
        if (!this.world.isRemote && (!dancing || !this.brain.isPanicking())) {
            this.dataManager.set(DANCING, dancing);
        }
    }

    public void setJukeboxPlaying(BlockPos pos, boolean playing) {
        if (playing) {
            if (!this.isDancing()) {
                this.jukeboxPos = pos.toImmutable();
                this.setDancing(true);
            }
        } else if (pos.equals(this.jukeboxPos) || this.jukeboxPos == null) {
            this.jukeboxPos = null;
            this.setDancing(false);
        }
    }

    @Nullable
    public BlockPos getJukeboxPos() {
        return this.jukeboxPos;
    }

    private boolean shouldStopDancing() {
        return this.jukeboxPos == null || !closerToCenter(this.jukeboxPos, JUKEBOX_RADIUS)
                || !isPlayingJukebox(this.world.getBlockState(this.jukeboxPos));
    }

    private boolean closerToCenter(BlockPos pos, double distance) {
        double dx = pos.getX() + 0.5D - this.posX;
        double dy = pos.getY() + 0.5D - this.posY;
        double dz = pos.getZ() + 0.5D - this.posZ;
        return dx * dx + dy * dy + dz * dz < distance * distance;
    }

    private static boolean isPlayingJukebox(IBlockState state) {
        return state.getBlock() == Blocks.JUKEBOX && state.getValue(BlockJukebox.HAS_RECORD);
    }

    /** @return the nearest jukebox holding a disc within 10 blocks, from the loaded chunks' tile entities */
    @Nullable
    private BlockPos findPlayingJukebox() {
        BlockPos best = null;
        double bestSq = Double.MAX_VALUE;
        int x0 = MathHelper.floor(this.posX - JUKEBOX_RADIUS) >> 4;
        int x1 = MathHelper.floor(this.posX + JUKEBOX_RADIUS) >> 4;
        int z0 = MathHelper.floor(this.posZ - JUKEBOX_RADIUS) >> 4;
        int z1 = MathHelper.floor(this.posZ + JUKEBOX_RADIUS) >> 4;
        for (int cx = x0; cx <= x1; cx++) {
            for (int cz = z0; cz <= z1; cz++) {
                if (!this.world.isBlockLoaded(new BlockPos(cx << 4, 0, cz << 4))) {
                    continue;
                }
                Chunk chunk = this.world.getChunk(cx, cz);
                for (TileEntity te : chunk.getTileEntityMap().values()) {
                    if (!(te instanceof TileEntityJukebox) || te.isInvalid()) {
                        continue;
                    }
                    BlockPos p = te.getPos();
                    if (!closerToCenter(p, JUKEBOX_RADIUS) || !isPlayingJukebox(this.world.getBlockState(p))) {
                        continue;
                    }
                    double d = p.distanceSqToCenter(this.posX, this.posY, this.posZ);
                    if (d < bestSq) {
                        bestSq = d;
                        best = p;
                    }
                }
            }
        }
        return best;
    }

    public boolean canDuplicate() {
        return this.dataManager.get(CAN_DUPLICATE);
    }

    public long getDuplicationCooldown() {
        return this.duplicationCooldown;
    }

    private void setDuplicationCooldown(long ticks) {
        this.duplicationCooldown = ticks;
        this.dataManager.set(CAN_DUPLICATE, ticks == 0L);
    }

    private void updateDuplicationCooldown() {
        if (!this.world.isRemote && this.duplicationCooldown > 0L) {
            this.setDuplicationCooldown(this.duplicationCooldown - 1L);
        }
    }

    /** 1.19.1's duplication: a new persistent allay in the same spot, both on cooldown. */
    @Nullable
    EntityAllay duplicate() {
        EntityAllay copy = new EntityAllay(this.world);
        copy.setLocationAndAngles(this.posX, this.posY, this.posZ, this.rotationYaw, this.rotationPitch);
        copy.enablePersistence();
        copy.setDuplicationCooldown(DUPLICATION_COOLDOWN);
        this.setDuplicationCooldown(DUPLICATION_COOLDOWN);
        this.world.spawnEntity(copy);
        return copy;
    }

    /** Current Java's renderer draws the allay at full block light. */
    @Override
    @SideOnly(Side.CLIENT)
    public int getBrightnessForRender() {
        return (super.getBrightnessForRender() & 0xFFFF0000) | (15 << 4);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void handleStatusUpdate(byte id) {
        if (id == 18) {
            for (int i = 0; i < 3; i++) {
                double vx = this.rand.nextGaussian() * 0.02D;
                double vy = this.rand.nextGaussian() * 0.02D;
                double vz = this.rand.nextGaussian() * 0.02D;
                // 1.19's getRandomX(1.0), getRandomY() + 0.5, getRandomZ(1.0)
                this.world.spawnParticle(EnumParticleTypes.HEART,
                        this.posX + (2.0D * this.rand.nextDouble() - 1.0D) * this.width,
                        this.posY + this.height * this.rand.nextDouble() + 0.5D,
                        this.posZ + (2.0D * this.rand.nextDouble() - 1.0D) * this.width, vx, vy, vz);
            }
        } else {
            super.handleStatusUpdate(id);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Client animation
    // ---------------------------------------------------------------------------------------------

    public float getHoldingItemAnimationProgress(float partial) {
        return (this.holdingTicks0 + (this.holdingTicks - this.holdingTicks0) * partial) / HOLDING_DURATION;
    }

    public boolean isSpinning() {
        return this.dancingTicks % DANCING_LOOP < SPINNING_DURATION;
    }

    public float getSpinningProgress(float partial) {
        return (this.spinningTicks0 + (this.spinningTicks - this.spinningTicks0) * partial) / SPINNING_DURATION;
    }

    // ---------------------------------------------------------------------------------------------
    // Saving
    // ---------------------------------------------------------------------------------------------

    @Override
    public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        NBTTagList items = new NBTTagList();
        for (int i = 0; i < this.inventory.getSizeInventory(); i++) {
            ItemStack stack = this.inventory.getStackInSlot(i);
            if (!stack.isEmpty()) {
                items.appendTag(stack.writeToNBT(new NBTTagCompound()));
            }
        }
        tag.setTag("Inventory", items);
        tag.setTag("listener", this.vibrationListener.writeToNBT());
        tag.setLong("DuplicationCooldown", this.duplicationCooldown);
        tag.setTag("Brain", this.brain.writeToNBT());
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        NBTTagList items = tag.getTagList("Inventory", 10);
        this.inventory.clear();
        for (int i = 0; i < items.tagCount(); i++) {
            ItemStack stack = new ItemStack(items.getCompoundTagAt(i));
            if (!stack.isEmpty()) {
                this.inventory.addItem(stack);
            }
        }
        if (tag.hasKey("listener", 10)) {
            this.vibrationListener.readFromNBT(tag.getCompoundTag("listener"));
        }
        this.setDuplicationCooldown(tag.getInteger("DuplicationCooldown"));
        this.brain.readFromNBT(tag.getCompoundTag("Brain"));
    }

    // ---------------------------------------------------------------------------------------------
    // Hearing
    // ---------------------------------------------------------------------------------------------

    /** 1.19's {@code AllayVibrationListenerConfig}: note blocks only, and only the liked one once it has one. */
    private final class NoteBlockEars implements VibrationListener.Config {

    /** Wild Update's own vibrations setting, also while another backport keeps game events running. */
    @Override
    public boolean active() {
        return com.exiledradio.wildupdatebackport.ModConfig.vibrations.enabled;
    }
        @Override
        public Set<GameEvent> listenableEvents() {
            return GameEventTags.ALLAY_CAN_LISTEN;
        }

        @Override
        public boolean shouldListen(WorldServer world, VibrationListener listener, BlockPos origin, GameEvent event,
                                    GameEventContext context) {
            if (EntityAllay.this.world != world || EntityAllay.this.isDead || EntityAllay.this.isAIDisabled()) {
                return false;
            }
            BlockPos liked = EntityAllay.this.brain.likedNoteBlock();
            return liked == null || (EntityAllay.this.brain.likedNoteBlockDimension() == world.provider.getDimension()
                    && liked.equals(origin) && liked.distanceSq(EntityAllay.this.getPosition()) <= 1024.0D * 1024.0D);
        }

        @Override
        public void onSignalReceive(WorldServer world, VibrationListener listener, BlockPos origin, GameEvent event,
                                    @Nullable Entity source, @Nullable Entity projectileOwner, float distance) {
            if (event == GameEvent.NOTE_BLOCK_PLAY) {
                EntityAllay.this.brain.hearNoteBlock(origin);
            }
        }
    }

    /** 1.19's {@code Allay.JukeboxListener}. */
    private final class JukeboxEars implements GameEventListener {
        private final PositionSource position;

        JukeboxEars(PositionSource position) {
            this.position = position;
        }

        @Override
        public PositionSource position() {
            return this.position;
        }

        @Override
        public int radius() {
            return JUKEBOX_RADIUS;
        }

        @Override
        public boolean handle(WorldServer world, GameEvent event, Vec3d origin, GameEventContext context) {
            if (event == GameEvent.JUKEBOX_PLAY) {
                EntityAllay.this.setJukeboxPlaying(new BlockPos(origin), true);
                return true;
            }
            if (event == GameEvent.JUKEBOX_STOP_PLAY) {
                EntityAllay.this.setJukeboxPlaying(new BlockPos(origin), false);
                return true;
            }
            return false;
        }
    }

    /** @return the liked player if they can be delivered to: alive, not a spectator, within 64 blocks */
    @Nullable
    EntityPlayerMP likedPlayerInReach() {
        UUID id = this.brain.likedPlayer();
        if (id == null || this.world.isRemote) {
            return null;
        }
        EntityPlayer p = this.world.getPlayerEntityByUUID(id);
        if (p instanceof EntityPlayerMP && !p.isSpectator() && p.isEntityAlive() && p.getDistanceSq(this) < 64.0D * 64.0D) {
            return (EntityPlayerMP) p;
        }
        return null;
    }
}
