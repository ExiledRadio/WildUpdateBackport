package com.exiledradio.wildupdatebackport.allay;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.RandomPositionGenerator;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.pathfinding.Path;
import net.minecraft.pathfinding.PathNodeType;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * 1.19's allay brain ({@code AllayAi}), kept as memories and behaviors run in vanilla's order each tick.
 *
 * <p>Core: swim, panic (2.5x for as long as the last damage is under 2 seconds old), look at the look target
 * for 45-90 ticks, walk to the walk target, count the note block (600) and pickup (60) cooldowns down.
 * Idle, by priority: go to the nearest visible wanted item within 32 blocks (1.75x); go to the deposit
 * target and throw an item at it from within 3 blocks (2.25x, 20-tick runs, current Java); stay within 16
 * blocks of the deposit target unless an item is wanted (2.25x, to within 4); look at a mob within 6 blocks
 * every 30-60 ticks; otherwise one of fly about (10 across, 7 up and down, 2 low, weight 2), fly to the
 * look target (weight 2), wait 30-60 ticks (weight 1).
 *
 * <p>The deposit target is the liked note block (above it) while the allay heard it in the last 600 ticks,
 * else the liked player within 64 blocks.
 */
final class AllayBrain {

    static final int NOTE_BLOCK_COOLDOWN = 600;
    static final int PICKUP_COOLDOWN = 60;
    static final int WANTED_ITEM_DISTANCE = 32;
    static final float SPEED_RETRIEVE = 1.75F;
    static final float SPEED_DEPOSIT = 2.25F;
    static final float SPEED_PANIC = 2.5F;
    static final float SPEED_IDLE = 1.0F;
    static final int GIVE_TIMEOUT = 20;
    /** {@code StayCloseToTarget(…, 4, 16, 2.25)}. */
    static final int CLOSE_ENOUGH = 4;
    static final int TOO_FAR = 16;

    private final EntityAllay allay;

    // memories
    @Nullable
    private UUID likedPlayer;
    @Nullable
    private BlockPos likedNoteBlock;
    private int likedNoteBlockDim;
    private int noteBlockCooldown;
    private int pickupCooldown;
    @Nullable
    private EntityItem wantedItem;
    @Nullable
    private WalkTarget walkTarget;
    @Nullable
    private Tracker lookTarget;

    // behavior state
    private boolean panicking;
    private int giveTicks;
    private int lookTicks;
    private int lookSometimes;
    private int doNothing;
    private int sensorTicks;
    @Nullable
    private BlockPos lastPathTarget;
    private float lastPathSpeed;

    AllayBrain(EntityAllay allay) {
        this.allay = allay;
        this.sensorTicks = allay.getRNG().nextInt(20);
    }

    // ---------------------------------------------------------------------------------------------
    // Memories, for the entity and the tests
    // ---------------------------------------------------------------------------------------------

    @Nullable
    UUID likedPlayer() {
        return this.likedPlayer;
    }

    void setLikedPlayer(@Nullable UUID id) {
        this.likedPlayer = id;
    }

    @Nullable
    BlockPos likedNoteBlock() {
        return this.likedNoteBlock;
    }

    int likedNoteBlockDimension() {
        return this.likedNoteBlockDim;
    }

    int noteBlockCooldown() {
        return this.noteBlockCooldown;
    }

    int pickupCooldown() {
        return this.pickupCooldown;
    }

    boolean isPanicking() {
        return this.panicking;
    }

    @Nullable
    WalkTarget walkTarget() {
        return this.walkTarget;
    }

    /** 1.19's {@code AllayAi.hearNoteblock}. */
    void hearNoteBlock(BlockPos pos) {
        int dim = this.allay.world.provider.getDimension();
        if (this.likedNoteBlock == null) {
            this.likedNoteBlock = pos.toImmutable();
            this.likedNoteBlockDim = dim;
            this.noteBlockCooldown = NOTE_BLOCK_COOLDOWN;
        } else if (this.likedNoteBlock.equals(pos) && this.likedNoteBlockDim == dim) {
            this.noteBlockCooldown = NOTE_BLOCK_COOLDOWN;
        }
    }

    /** 1.19's {@code getItemDepositPosition}. */
    @Nullable
    Tracker depositTarget() {
        if (this.likedNoteBlock != null) {
            if (this.noteBlockCooldown > 0 && this.likedNoteBlockDim == this.allay.world.provider.getDimension()
                    && this.likedNoteBlock.distanceSq(this.allay.getPosition()) <= 1024.0D * 1024.0D
                    && this.allay.world.isBlockLoaded(this.likedNoteBlock)
                    && this.allay.world.getBlockState(this.likedNoteBlock).getBlock() == Blocks.NOTEBLOCK) {
                return new BlockTracker(this.likedNoteBlock.up());
            }
            this.likedNoteBlock = null;
        }
        EntityPlayerMP player = this.allay.likedPlayerInReach();
        return player == null ? null : new EntityTracker(player, true);
    }

    // ---------------------------------------------------------------------------------------------
    // The tick
    // ---------------------------------------------------------------------------------------------

    void tick() {
        if (this.allay.isAIDisabled()) {
            return;
        }
        this.sense();
        this.core();
        if (!this.panicking) {
            this.idle();
        }
        this.moveToTarget();
        this.lookAtTarget();
    }

    /** 1.19's sensors, every 20 ticks: the nearest visible wanted item. */
    private void sense() {
        if (this.wantedItem != null && (this.wantedItem.isDead || !this.allay.wantsToPickUp(this.wantedItem.getItem()))) {
            this.wantedItem = null;
        }
        if (--this.sensorTicks > 0) {
            return;
        }
        this.sensorTicks = 20;
        List<EntityItem> items = this.allay.world.getEntitiesWithinAABB(EntityItem.class,
                this.allay.getEntityBoundingBox().grow(32.0D, 16.0D, 32.0D));
        Collections.sort(items, Comparator.comparingDouble(e -> e.getDistanceSq(this.allay)));
        this.wantedItem = null;
        for (EntityItem item : items) {
            if (this.allay.wantsToPickUp(item.getItem()) && item.getDistanceSq(this.allay) < 32.0D * 32.0D
                    && this.allay.canEntityBeSeen(item)) {
                this.wantedItem = item;
                break;
            }
        }
    }

    private void core() {
        // Swim(0.8)
        if ((this.allay.isInWater() || this.allay.isInLava()) && this.allay.getRNG().nextFloat() < 0.8F) {
            this.allay.getJumpHelper().setJumping();
        }
        // AnimalPanic(2.5): while the last damage source is remembered (40 ticks)
        boolean hurt = this.allay.getLastDamageSource() != null;
        if (hurt) {
            this.panicking = true;
            if (this.allay.getNavigator().noPath()) {
                Vec3d to = RandomPositionGenerator.findRandomTarget(this.allay, 5, 4);
                if (to != null) {
                    this.walkTarget = new WalkTarget(new BlockTracker(new BlockPos(to)), SPEED_PANIC, 0);
                }
            }
        } else {
            this.panicking = false;
        }
        // CountDownCooldownTicks
        if (this.noteBlockCooldown > 0) {
            this.noteBlockCooldown--;
        }
        if (this.pickupCooldown > 0) {
            this.pickupCooldown--;
        }
    }

    private void idle() {
        // 0: GoToWantedItem(1.75, interrupting, 32)
        if (this.wantedItem != null && this.pickupCooldown <= 0 && this.allay.canCollect()
                && this.wantedItem.getDistanceSq(this.allay) < WANTED_ITEM_DISTANCE * WANTED_ITEM_DISTANCE
                && this.allay.world.getWorldBorder().contains(this.wantedItem.getPosition())) {
            this.walkTarget = new WalkTarget(new EntityTracker(this.wantedItem, false), SPEED_RETRIEVE, 0);
            this.setLook(new EntityTracker(this.wantedItem, true));
        }

        // 1: GoAndGiveItemsToTarget(deposit, 2.25, 20 ticks)
        Tracker deposit = this.depositTarget();
        boolean canGive = !this.allay.getInventory().isEmpty() && deposit != null;
        if (this.giveTicks > 0) {
            if (!canGive || --this.giveTicks <= 0) {
                this.giveTicks = 0;
            } else {
                this.giveTick(deposit);
            }
        } else if (canGive) {
            this.giveTicks = GIVE_TIMEOUT;
            this.walkTarget = new WalkTarget(deposit, SPEED_DEPOSIT, 3);
            this.setLook(deposit);
            this.giveTick(deposit);
        }

        // 2: StayCloseToTarget(deposit, no wanted item, 4, 16, 2.25)
        if (deposit != null && this.wantedItem == null
                && deposit.position().squareDistanceTo(this.allay.getPositionVector()) >= TOO_FAR * TOO_FAR) {
            this.walkTarget = new WalkTarget(deposit, SPEED_DEPOSIT, CLOSE_ENOUGH);
            this.setLook(deposit);
        }

        // 3: SetEntityLookTargetSometimes(6, 30-60)
        if (--this.lookSometimes <= 0) {
            this.lookSometimes = 30 + this.allay.getRNG().nextInt(31);
            EntityLivingBase nearest = this.nearestVisibleLiving(6.0D);
            if (nearest != null) {
                this.setLook(new EntityTracker(nearest, true));
            }
        }

        // 4: RunOne(fly about 2, walk to look target 2, do nothing 1)
        if (this.doNothing > 0) {
            this.doNothing--;
        } else if (this.walkTarget == null) {
            List<Integer> order = new ArrayList<Integer>();
            // ShufflingList: each entry drawn with its weight
            List<Integer> bag = new ArrayList<Integer>();
            Collections.addAll(bag, 0, 0, 1, 1, 2);
            while (!bag.isEmpty()) {
                Integer pick = bag.get(this.allay.getRNG().nextInt(bag.size()));
                order.add(pick);
                bag.removeAll(Collections.singleton(pick));
            }
            for (int choice : order) {
                if (choice == 0) {
                    Vec3d to = this.flyingStrollTarget();
                    if (to != null) {
                        this.walkTarget = new WalkTarget(new BlockTracker(new BlockPos(to)), SPEED_IDLE, 0);
                    }
                    break;
                }
                if (choice == 1) {
                    if (this.lookTarget != null) {
                        this.walkTarget = new WalkTarget(this.lookTarget, SPEED_IDLE, 3);
                        break;
                    }
                    continue;
                }
                this.doNothing = 30 + this.allay.getRNG().nextInt(31);
                break;
            }
        }
    }

    private void giveTick(Tracker deposit) {
        if (deposit.position().distanceTo(new Vec3d(this.allay.posX, this.allay.posY + this.allay.getEyeHeight(), this.allay.posZ)) >= 3.0D) {
            return;
        }
        ItemStack one = this.allay.getInventory().decrStackSize(0, 1);
        if (one.isEmpty()) {
            return;
        }
        Vec3d at = deposit.position();
        this.allay.throwItem(one, at.add(0.0D, 1.0D, 0.0D), 0.2F, 0.3F, 0.2F);
        EntityPlayerMP player = this.allay.likedPlayerInReach();
        if (player != null) {
            AllayTriggers.DROP_ITEM_ON_BLOCK.trigger(player, new BlockPos(at).down(), one);
        }
        if (this.allay.world.getTotalWorldTime() % 7L == 0L && this.allay.getRNG().nextDouble() < 0.9D) {
            float pitch = EntityAllay.THROW_SOUND_PITCHES[this.allay.getRNG().nextInt(EntityAllay.THROW_SOUND_PITCHES.length)];
            this.allay.world.playSound(null, this.allay.posX, this.allay.posY, this.allay.posZ, AllayContent.ITEM_THROWN,
                    SoundCategory.NEUTRAL, 1.0F, pitch);
        }
        this.pickupCooldown = PICKUP_COOLDOWN;
    }

    /** {@code AirAndWaterRandomPos.getPos(mob, 10, 7, -2, look.x, look.z, pi/2)}: ten tries, the first that works. */
    @Nullable
    private Vec3d flyingStrollTarget() {
        Vec3d look = this.allay.getLook(0.0F);
        for (int i = 0; i < 10; i++) {
            BlockPos p = this.randomAirPos(10, 7, -2, look.x, look.z, Math.PI / 2.0D);
            if (p != null) {
                return new Vec3d(p.getX() + 0.5D, p.getY(), p.getZ() + 0.5D);
            }
        }
        return null;
    }

    @Nullable
    private BlockPos randomAirPos(int horizontal, int vertical, int yOffset, double dirX, double dirZ, double spread) {
        java.util.Random r = this.allay.getRNG();
        double angle = MathHelper.atan2(dirZ, dirX) - Math.PI / 2.0D + (2.0F * r.nextFloat() - 1.0F) * spread;
        double dist = Math.sqrt(r.nextDouble()) * Math.sqrt(2.0D) * horizontal;
        double dx = -dist * Math.sin(angle);
        double dz = dist * Math.cos(angle);
        if (Math.abs(dx) > horizontal || Math.abs(dz) > horizontal) {
            return null;
        }
        int dy = r.nextInt(2 * vertical + 1) - vertical + yOffset;
        BlockPos p = new BlockPos(this.allay.posX + dx, this.allay.posY + dy, this.allay.posZ + dz);
        if (p.getY() < 0 || p.getY() > this.allay.world.getHeight() - 1) {
            return null;
        }
        while (p.getY() < this.allay.world.getHeight() && this.allay.world.getBlockState(p).getMaterial().isSolid()) {
            p = p.up();
        }
        PathNodeType type = this.allay.getNavigator().getNodeProcessor().getPathNodeType(this.allay.world, p.getX(), p.getY(), p.getZ());
        return this.allay.getPathPriority(type) != 0.0F ? null : p;
    }

    @Nullable
    private EntityLivingBase nearestVisibleLiving(double range) {
        EntityLivingBase best = null;
        double bestSq = range * range;
        for (EntityLivingBase e : this.allay.world.getEntitiesWithinAABB(EntityLivingBase.class,
                this.allay.getEntityBoundingBox().grow(range, range, range))) {
            if (e == this.allay || !e.isEntityAlive()) {
                continue;
            }
            double d = e.getDistanceSq(this.allay);
            if (d <= bestSq && this.allay.canEntityBeSeen(e)) {
                bestSq = d;
                best = e;
            }
        }
        return best;
    }

    private void setLook(Tracker target) {
        this.lookTarget = target;
        this.lookTicks = 0;
    }

    /** {@code MoveToTargetSink}: paths to the walk target; forgets it once within its distance. */
    private void moveToTarget() {
        if (this.walkTarget == null) {
            return;
        }
        if (!this.walkTarget.target.valid()) {
            this.walkTarget = null;
            this.allay.getNavigator().clearPath();
            return;
        }
        BlockPos at = this.walkTarget.target.block();
        BlockPos me = new BlockPos(this.allay);
        int manhattan = Math.abs(at.getX() - me.getX()) + Math.abs(at.getY() - me.getY()) + Math.abs(at.getZ() - me.getZ());
        if (manhattan <= this.walkTarget.closeEnough) {
            this.walkTarget = null;
            this.lastPathTarget = null;
            this.allay.getNavigator().clearPath();
            return;
        }
        if (this.lastPathTarget == null || this.allay.getNavigator().noPath() || at.distanceSq(this.lastPathTarget) > 4.0D
                || this.walkTarget.speed != this.lastPathSpeed) {
            this.lastPathSpeed = this.walkTarget.speed;
            Vec3d p = this.walkTarget.target.position();
            Path path = this.allay.getNavigator().getPathToXYZ(p.x, p.y, p.z);
            if (path == null) {
                // CANT_REACH_WALK_TARGET_SINCE: given up on
                this.walkTarget = null;
                this.lastPathTarget = null;
                return;
            }
            this.allay.getNavigator().setPath(path, this.walkTarget.speed);
            this.lastPathTarget = at;
        }
    }

    /** {@code LookAtTargetSink(45, 90)}. */
    private void lookAtTarget() {
        if (this.lookTarget == null) {
            return;
        }
        if (this.lookTicks == 0) {
            this.lookTicks = 45 + this.allay.getRNG().nextInt(46);
        }
        if (!this.lookTarget.valid() || --this.lookTicks <= 0) {
            this.lookTarget = null;
            this.lookTicks = 0;
            return;
        }
        Vec3d p = this.lookTarget.position();
        this.allay.getLookHelper().setLookPosition(p.x, p.y, p.z, this.allay.getHorizontalFaceSpeed(), this.allay.getVerticalFaceSpeed());
    }

    // ---------------------------------------------------------------------------------------------
    // Saving: the memories 1.19 saves
    // ---------------------------------------------------------------------------------------------

    NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        if (this.likedPlayer != null) {
            tag.setUniqueId("LikedPlayer", this.likedPlayer);
        }
        if (this.likedNoteBlock != null) {
            NBTTagCompound nb = new NBTTagCompound();
            nb.setInteger("X", this.likedNoteBlock.getX());
            nb.setInteger("Y", this.likedNoteBlock.getY());
            nb.setInteger("Z", this.likedNoteBlock.getZ());
            nb.setInteger("Dimension", this.likedNoteBlockDim);
            tag.setTag("LikedNoteBlock", nb);
        }
        if (this.noteBlockCooldown > 0) {
            tag.setInteger("LikedNoteBlockCooldown", this.noteBlockCooldown);
        }
        if (this.pickupCooldown > 0) {
            tag.setInteger("ItemPickupCooldown", this.pickupCooldown);
        }
        return tag;
    }

    void readFromNBT(NBTTagCompound tag) {
        this.likedPlayer = tag.hasUniqueId("LikedPlayer") ? tag.getUniqueId("LikedPlayer") : null;
        if (tag.hasKey("LikedNoteBlock", 10)) {
            NBTTagCompound nb = tag.getCompoundTag("LikedNoteBlock");
            this.likedNoteBlock = new BlockPos(nb.getInteger("X"), nb.getInteger("Y"), nb.getInteger("Z"));
            this.likedNoteBlockDim = nb.getInteger("Dimension");
        } else {
            this.likedNoteBlock = null;
        }
        this.noteBlockCooldown = tag.getInteger("LikedNoteBlockCooldown");
        this.pickupCooldown = tag.getInteger("ItemPickupCooldown");
    }

    // ---------------------------------------------------------------------------------------------
    // Targets
    // ---------------------------------------------------------------------------------------------

    /** 1.19's {@code PositionTracker}. */
    interface Tracker {
        Vec3d position();

        BlockPos block();

        boolean valid();
    }

    static final class EntityTracker implements Tracker {
        final Entity entity;
        private final boolean eyes;

        EntityTracker(Entity entity, boolean eyes) {
            this.entity = entity;
            this.eyes = eyes;
        }

        @Override
        public Vec3d position() {
            return new Vec3d(this.entity.posX, this.entity.posY + (this.eyes ? this.entity.getEyeHeight() : 0.0D), this.entity.posZ);
        }

        @Override
        public BlockPos block() {
            return new BlockPos(this.entity);
        }

        @Override
        public boolean valid() {
            return this.entity.isEntityAlive();
        }
    }

    static final class BlockTracker implements Tracker {
        final BlockPos pos;

        BlockTracker(BlockPos pos) {
            this.pos = pos.toImmutable();
        }

        @Override
        public Vec3d position() {
            return new Vec3d(this.pos.getX() + 0.5D, this.pos.getY() + 0.5D, this.pos.getZ() + 0.5D);
        }

        @Override
        public BlockPos block() {
            return this.pos;
        }

        @Override
        public boolean valid() {
            return true;
        }
    }

    static final class WalkTarget {
        final Tracker target;
        final float speed;
        final int closeEnough;

        WalkTarget(Tracker target, float speed, int closeEnough) {
            this.target = target;
            this.speed = speed;
            this.closeEnough = closeEnough;
        }
    }
}
