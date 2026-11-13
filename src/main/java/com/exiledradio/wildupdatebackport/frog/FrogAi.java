package com.exiledradio.wildupdatebackport.frog;

import com.exiledradio.wildupdatebackport.common.Owners;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.ai.RandomPositionGenerator;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.pathfinding.Path;
import net.minecraft.pathfinding.PathNodeType;
import net.minecraft.pathfinding.PathPoint;
import net.minecraft.pathfinding.WalkNodeProcessor;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Frogs' and tadpoles' behaviors: 1.19.2's {@code FrogAi}, {@code TadpoleAi} and the brain behaviors they
 * use, as 1.12.2 tasks. Each frog task runs only in its own activities ({@link EntityFrog#activity()}).
 *
 * <p>Mutex bits: 1 movement, 2 looking.
 */
public final class FrogAi {

    /** {@code TIME_BETWEEN_LONG_JUMPS}: uniform 100 to 140 ticks. */
    public static final int MIN_JUMP_COOLDOWN = 100;
    public static final int MAX_JUMP_COOLDOWN = 140;
    public static final int MAX_LONG_JUMP_HEIGHT = 2;
    public static final int MAX_LONG_JUMP_WIDTH = 4;
    public static final float MAX_JUMP_VELOCITY = 1.5F;
    public static final float PREFERRED_BLOCK_CHANCE = 0.5F;
    /** {@code FrogAttackablesSensor.TARGET_DETECTION_DISTANCE} */
    public static final double TARGET_DISTANCE = 10.0D;
    /** {@code ShootTongue.EATING_DISTANCE} */
    public static final float EATING_DISTANCE = 1.75F;

    private FrogAi() {
    }

    static int longJumpCooldown(Random random) {
        return MIN_JUMP_COOLDOWN + random.nextInt(MAX_JUMP_COOLDOWN - MIN_JUMP_COOLDOWN + 1);
    }

    /** 1.19's {@code #frog_prefer_jump_to}: lily pads and big dripleaf. */
    static boolean preferJumpTo(Block block) {
        return block == Blocks.WATERLILY || block == Owners.block(Owners.CAVES_AND_CLIFFS, "big_dripleaf");
    }

    static void addFrogTasks(EntityFrog frog) {
        frog.tasks.addTask(0, new Panic(frog, frog.memory, 2.0D));
        frog.tasks.addTask(1, new Tongue(frog));
        frog.tasks.addTask(2, new MakeLove(frog));
        frog.tasks.addTask(2, new LaySpawn(frog));
        frog.tasks.addTask(3, new Tempt(frog, frog.memory, 1.25D) {
            @Override
            boolean allowed() {
                EntityFrog.Activity a = frog.activity();
                return (a == EntityFrog.Activity.IDLE || a == EntityFrog.Activity.SWIM) && frog.breedTarget == null;
            }
        });
        frog.tasks.addTask(4, new LongJump(frog));
        frog.tasks.addTask(5, new FindLand(frog));
        frog.tasks.addTask(6, new FrogWander(frog));
    }

    static void addTadpoleTasks(EntityTadpole tadpole) {
        tadpole.tasks.addTask(0, new Panic(tadpole, tadpole.memory, 2.0D));
        tadpole.tasks.addTask(1, new Tempt(tadpole, tadpole.memory, 1.25D));
        tadpole.tasks.addTask(2, new TadpoleWander(tadpole));
    }

    // --- shared helpers ---------------------------------------------------------------------------------

    static boolean isWater(World world, BlockPos pos) {
        return world.getBlockState(pos).getMaterial() == Material.WATER;
    }

    /** 1.19's {@code BehaviorUtils.getRandomSwimmablePos}: a random water position within reach. */
    @Nullable
    static Vec3d randomSwimPos(EntityCreature mob, int xz, int y) {
        for (int i = 0; i < 10; i++) {
            Vec3d v = RandomPositionGenerator.findRandomTarget(mob, xz, y);
            if (v != null && isWater(mob.world, new BlockPos(v))) {
                return v;
            }
        }
        return null;
    }

    /** 1.19's {@code RandomSwim} tiers: keeps going out the same way while the water lasts. */
    @Nullable
    static Vec3d swimTarget(EntityCreature mob) {
        int[][] tiers = {{1, 1}, {3, 3}, {5, 5}, {6, 5}, {7, 7}, {10, 7}};
        Vec3d found = null;
        Vec3d here = mob.getPositionVector();
        for (int[] t : tiers) {
            Vec3d next;
            if (found == null) {
                next = randomSwimPos(mob, t[0], t[1]);
            } else {
                Vec3d dir = found.subtract(here).normalize();
                next = here.add(dir.x * t[0], dir.y * t[1], dir.z * t[0]);
            }
            if (next == null || !isWater(mob.world, new BlockPos(next))) {
                return found;
            }
            found = next;
        }
        return found;
    }

    static boolean idle(EntityLiving mob) {
        return mob.getNavigator().noPath() && (!(mob instanceof EntityFrog) || ((EntityFrog) mob).swimNavigator().noPath());
    }

    /** 1.19's {@code NearestVisibleLivingEntitySensor} for frogs: the nearest small slime it can see within 10. */
    @Nullable
    static EntityLivingBase findAttackable(EntityFrog frog) {
        AxisAlignedBB box = frog.getEntityBoundingBox().grow(16.0D, 16.0D, 16.0D);
        EntityLivingBase best = null;
        double bestD = TARGET_DISTANCE * TARGET_DISTANCE;
        for (EntityLivingBase e : frog.world.getEntitiesWithinAABB(EntityLivingBase.class, box)) {
            if (e == frog || !e.isEntityAlive() || !EntityFrog.canEat(e) || frog.unreachable.contains(e.getUniqueID())) {
                continue;
            }
            double d = e.getDistanceSq(frog);
            if (d < bestD && frog.getEntitySenses().canSee(e)) {
                best = e;
                bestD = d;
            }
        }
        return best;
    }

    // --- tasks ------------------------------------------------------------------------------------------

    /** 1.19's {@code AnimalPanic}: while hurt in the last 40 ticks, runs to random spots at double speed. */
    static class Panic extends EntityAIBase {
        private final EntityCreature mob;
        private final FrogMemory memory;
        private final double speed;

        Panic(EntityCreature mob, FrogMemory memory, double speed) {
            this.mob = mob;
            this.memory = memory;
            this.speed = speed;
            this.setMutexBits(1);
        }

        @Override
        public boolean shouldExecute() {
            return this.mob.getLastDamageSource() != null;
        }

        @Override
        public void startExecuting() {
            this.memory.panicking = true;
            move();
        }

        @Override
        public void updateTask() {
            this.memory.panicking = true;
            if (idle(this.mob)) {
                move();
            }
        }

        private void move() {
            Vec3d v = this.mob.isInWater() && this.mob instanceof EntityTadpole ? randomSwimPos(this.mob, 5, 4)
                    : RandomPositionGenerator.findRandomTarget(this.mob, 5, 4);
            if (v != null) {
                this.mob.getNavigator().tryMoveToXYZ(v.x, v.y, v.z, this.speed);
            }
        }

        @Override
        public boolean shouldContinueExecuting() {
            return this.mob.getLastDamageSource() != null;
        }

        @Override
        public void resetTask() {
            this.memory.panicking = false;
        }
    }

    /** 1.19's {@code FollowTemptation} at 1.25 times speed, stopping within 2.5 blocks; 100-tick cooldown after. */
    static class Tempt extends EntityAIBase {
        private final EntityCreature mob;
        private final FrogMemory memory;
        private final double speed;

        Tempt(EntityCreature mob, FrogMemory memory, double speed) {
            this.mob = mob;
            this.memory = memory;
            this.speed = speed;
            this.setMutexBits(1 | 2);
        }

        boolean allowed() {
            return true;
        }

        @Override
        public boolean shouldExecute() {
            return this.memory.temptingPlayer != null && this.memory.temptCooldown <= 0 && !this.memory.panicking && allowed();
        }

        @Override
        public boolean shouldContinueExecuting() {
            return this.memory.temptingPlayer != null && !this.memory.panicking && allowed();
        }

        @Override
        public void startExecuting() {
            this.memory.tempted = true;
        }

        @Override
        public void updateTask() {
            EntityPlayer p = this.memory.temptingPlayer;
            if (p == null) {
                return;
            }
            this.memory.look(p, this.mob.getRNG());
            if (this.mob.getDistanceSq(p) < 6.25D) {
                this.mob.getNavigator().clearPath();
            } else {
                this.mob.getNavigator().tryMoveToEntityLiving(p, this.speed);
            }
        }

        @Override
        public void resetTask() {
            this.memory.temptCooldown = FrogMemory.TEMPT_COOLDOWN;
            this.memory.tempted = false;
            this.mob.getNavigator().clearPath();
            this.memory.lookTarget = null;
        }
    }

    /** 1.19's {@code ShootTongue}: walks up to a small slime, catches it with its tongue and eats it. */
    static class Tongue extends EntityAIBase {
        private enum State { MOVE_TO_TARGET, CATCH_ANIMATION, EAT_ANIMATION, DONE }

        private final EntityFrog frog;
        private State state = State.DONE;
        private int eatTimer;
        private int pathCounter;
        private int ticks;

        Tongue(EntityFrog frog) {
            this.frog = frog;
            this.setMutexBits(1 | 2);
        }

        @Override
        public boolean shouldExecute() {
            EntityLivingBase target = this.frog.getAttackTarget();
            if (this.frog.activity() != EntityFrog.Activity.TONGUE || target == null || this.frog.memory.panicking) {
                return false;
            }
            if (!canPathTo(target)) {
                this.frog.setAttackTarget(null);
                markUnreachable(target);
                return false;
            }
            return this.frog.getPose() != EntityFrog.Pose.CROAKING && EntityFrog.canEat(target);
        }

        private boolean canPathTo(EntityLivingBase target) {
            Path path = this.frog.getNavigator().getPathToEntityLiving(target);
            if (path == null || path.getFinalPathPoint() == null) {
                return false;
            }
            PathPoint end = path.getFinalPathPoint();
            double dx = end.x + 0.5D - target.posX;
            double dy = end.y - target.posY;
            double dz = end.z + 0.5D - target.posZ;
            return Math.sqrt(dx * dx + dy * dy + dz * dz) < EATING_DISTANCE;
        }

        private void markUnreachable(EntityLivingBase target) {
            boolean fresh = !this.frog.unreachable.contains(target.getUniqueID());
            if (this.frog.unreachable.size() == 5 && fresh) {
                this.frog.unreachable.remove(0);
            }
            if (fresh) {
                this.frog.unreachable.add(target.getUniqueID());
            }
            this.frog.unreachableExpires = 100;
        }

        @Override
        public void startExecuting() {
            EntityLivingBase target = this.frog.getAttackTarget();
            this.frog.getLookHelper().setLookPositionWithEntity(target, 30.0F, 30.0F);
            this.frog.setTongueTarget(target);
            this.frog.getNavigator().tryMoveToXYZ(target.posX, target.posY, target.posZ, 2.0D);
            this.pathCounter = 10;
            this.state = State.MOVE_TO_TARGET;
            this.ticks = 0;
        }

        @Override
        public boolean shouldContinueExecuting() {
            return this.frog.getAttackTarget() != null && this.state != State.DONE && !this.frog.memory.panicking && this.ticks < 100;
        }

        @Override
        public void updateTask() {
            this.ticks++;
            EntityLivingBase target = this.frog.getAttackTarget();
            if (target == null) {
                return;
            }
            this.frog.setTongueTarget(target);
            switch (this.state) {
                case MOVE_TO_TARGET:
                    if (target.getDistance(this.frog) < EATING_DISTANCE) {
                        this.frog.world.playSound(null, this.frog.posX, this.frog.posY, this.frog.posZ, FrogSounds.FROG_TONGUE,
                                SoundCategory.NEUTRAL, 2.0F, 1.0F);
                        this.frog.setPose(EntityFrog.Pose.USING_TONGUE);
                        Vec3d pull = new Vec3d(this.frog.posX - target.posX, this.frog.posY - target.posY, this.frog.posZ - target.posZ)
                                .normalize().scale(0.75D);
                        target.motionX = pull.x;
                        target.motionY = pull.y;
                        target.motionZ = pull.z;
                        target.velocityChanged = true;
                        this.eatTimer = 0;
                        this.state = State.CATCH_ANIMATION;
                    } else if (this.pathCounter <= 0) {
                        this.frog.getNavigator().tryMoveToXYZ(target.posX, target.posY, target.posZ, 2.0D);
                        this.pathCounter = 10;
                    } else {
                        this.pathCounter--;
                    }
                    break;
                case CATCH_ANIMATION:
                    if (this.eatTimer++ >= 6) {
                        this.state = State.EAT_ANIMATION;
                        eat(target);
                    }
                    break;
                case EAT_ANIMATION:
                    if (this.eatTimer >= 10) {
                        this.state = State.DONE;
                    } else {
                        this.eatTimer++;
                    }
                    break;
                default:
                    break;
            }
        }

        private void eat(EntityLivingBase target) {
            this.frog.world.playSound(null, this.frog.posX, this.frog.posY, this.frog.posZ, FrogSounds.FROG_EAT, SoundCategory.NEUTRAL, 2.0F, 1.0F);
            if (target.isEntityAlive()) {
                this.frog.attackEntityAsMob(target);
                if (!target.isEntityAlive()) {
                    target.setDead();
                }
            }
        }

        @Override
        public void resetTask() {
            this.frog.setAttackTarget(null);
            this.frog.setTongueTarget(null);
            this.frog.setPose(EntityFrog.Pose.STANDING);
            this.state = State.DONE;
        }
    }

    /** 1.19's {@code AnimalMakeLove}: two frogs in love walk together; after 60 to 109 ticks within 3 blocks, one becomes pregnant. */
    static class MakeLove extends EntityAIBase {
        private final EntityFrog frog;
        private long spawnAt;
        private int ticks;

        MakeLove(EntityFrog frog) {
            this.frog = frog;
            this.setMutexBits(1 | 2);
        }

        @Nullable
        private EntityFrog partner() {
            EntityFrog best = null;
            double bestD = Double.MAX_VALUE;
            for (EntityFrog f : this.frog.world.getEntitiesWithinAABB(EntityFrog.class, this.frog.getEntityBoundingBox().grow(16.0D))) {
                if (f != this.frog && this.frog.canMateWith(f) && this.frog.getEntitySenses().canSee(f)) {
                    double d = f.getDistanceSq(this.frog);
                    if (d < bestD) {
                        best = f;
                        bestD = d;
                    }
                }
            }
            return best;
        }

        @Override
        public boolean shouldExecute() {
            return this.frog.activity() == EntityFrog.Activity.IDLE && this.frog.breedTarget == null && this.frog.isInLove() && partner() != null;
        }

        @Override
        public void startExecuting() {
            EntityFrog p = partner();
            this.frog.breedTarget = p;
            p.breedTarget = this.frog;
            walkTogether(p);
            this.spawnAt = this.frog.world.getTotalWorldTime() + 60 + this.frog.getRNG().nextInt(50);
            this.ticks = 0;
        }

        private void walkTogether(EntityFrog p) {
            this.frog.getLookHelper().setLookPositionWithEntity(p, 30.0F, 30.0F);
            p.getLookHelper().setLookPositionWithEntity(this.frog, 30.0F, 30.0F);
            this.frog.getNavigator().tryMoveToEntityLiving(p, 1.0D);
            p.getNavigator().tryMoveToEntityLiving(this.frog, 1.0D);
        }

        @Override
        public boolean shouldContinueExecuting() {
            EntityFrog p = this.frog.breedTarget;
            return p != null && p.isEntityAlive() && this.frog.canMateWith(p) && this.frog.getEntitySenses().canSee(p)
                    && this.frog.world.getTotalWorldTime() <= this.spawnAt && this.ticks < 110;
        }

        @Override
        public void updateTask() {
            this.ticks++;
            EntityFrog p = this.frog.breedTarget;
            if (p == null) {
                return;
            }
            walkTogether(p);
            if (this.frog.getDistance(p) < 3.0F && this.frog.world.getTotalWorldTime() >= this.spawnAt) {
                this.frog.spawnChildFromBreeding(p);
                this.frog.breedTarget = null;
                p.breedTarget = null;
            }
        }

        @Override
        public void resetTask() {
            if (this.frog.breedTarget != null) {
                this.frog.breedTarget.breedTarget = null;
            }
            this.frog.breedTarget = null;
            this.frog.getNavigator().clearPath();
            this.frog.memory.lookTarget = null;
            this.spawnAt = 0L;
        }
    }

    /** Every position within a Manhattan distance box, nearest first, as {@code BlockPos.withinManhattan}. */
    static List<BlockPos> manhattan(int range) {
        List<BlockPos> list = new ArrayList<BlockPos>();
        for (int x = -range; x <= range; x++) {
            for (int y = -range; y <= range; y++) {
                for (int z = -range; z <= range; z++) {
                    list.add(new BlockPos(x, y, z));
                }
            }
        }
        Collections.sort(list, new Comparator<BlockPos>() {
            @Override
            public int compare(BlockPos a, BlockPos b) {
                return Integer.compare(Math.abs(a.getX()) + Math.abs(a.getY()) + Math.abs(a.getZ()),
                        Math.abs(b.getX()) + Math.abs(b.getY()) + Math.abs(b.getZ()));
            }
        });
        return list;
    }

    private static final List<BlockPos> MANHATTAN_8 = manhattan(8);
    private static final List<BlockPos> MANHATTAN_6 = manhattan(6);

    /** No collision at all at this position (1.19's {@code getCollisionShape(...).isEmpty()}). */
    static boolean noCollision(World world, BlockPos pos) {
        IBlockState s = world.getBlockState(pos);
        return s.getCollisionBoundingBox(world, pos) == null;
    }

    /**
     * 1.19's {@code TryFindLandNearWater} (range 8, speed 1) and {@code TryLaySpawnOnWaterNearLand}: a pregnant
     * frog walks to land next to water; on the ground beside open water with air above it, it lays frogspawn
     * there, with the lay-spawn sound, and is no longer pregnant.
     */
    static class LaySpawn extends EntityAIBase {
        private final EntityFrog frog;
        private long nextOkStart;
        private int ticks;

        LaySpawn(EntityFrog frog) {
            this.frog = frog;
            this.setMutexBits(1);
        }

        @Override
        public boolean shouldExecute() {
            return this.frog.activity() == EntityFrog.Activity.LAY_SPAWN && this.frog.getAttackTarget() == null;
        }

        @Override
        public boolean shouldContinueExecuting() {
            return shouldExecute() && this.frog.isPregnant();
        }

        @Override
        public void startExecuting() {
            this.ticks = 0;
        }

        @Override
        public void updateTask() {
            this.ticks++;
            World world = this.frog.world;
            long now = world.getTotalWorldTime();
            if (!idle(this.frog)) {
                if (!this.frog.isInWater() && this.frog.onGround) {
                    tryLay(world);
                }
                return;
            }
            if (now >= this.nextOkStart && !isWater(world, new BlockPos(this.frog))) {
                BlockPos here = new BlockPos(this.frog);
                for (BlockPos off : MANHATTAN_8) {
                    BlockPos p = here.add(off);
                    if ((p.getX() == here.getX() && p.getZ() == here.getZ()) || !noCollision(world, p) || noCollision(world, p.down())) {
                        continue;
                    }
                    for (EnumFacing f : EnumFacing.Plane.HORIZONTAL) {
                        BlockPos side = p.offset(f);
                        if (world.isAirBlock(side) && world.getBlockState(side.down()).getBlock() == Blocks.WATER) {
                            this.nextOkStart = now + 40L;
                            this.frog.getNavigator().tryMoveToXYZ(p.getX() + 0.5D, p.getY(), p.getZ() + 0.5D, 1.0D);
                            return;
                        }
                    }
                }
                this.nextOkStart = now + 40L;
            }
            FrogWander.idleWander(this.frog, new int[] {2, 1, 2, 1});
        }

        /** {@code TryLaySpawnOnWaterNearLand.start}. */
        private void tryLay(World world) {
            BlockPos below = new BlockPos(this.frog).down();
            for (EnumFacing f : EnumFacing.Plane.HORIZONTAL) {
                BlockPos water = below.offset(f);
                IBlockState s = world.getBlockState(water);
                AxisAlignedBB box = s.getCollisionBoundingBox(world, water);
                if ((box == null || box.maxY < 1.0D) && s.getMaterial() == Material.WATER) {
                    BlockPos up = water.up();
                    if (world.isAirBlock(up)) {
                        Block spawn = Owners.ourBlock("frogspawn");
                        if (spawn == null) {
                            return;
                        }
                        world.setBlockState(up, spawn.getDefaultState(), 3);
                        world.playSound(null, this.frog.posX, this.frog.posY, this.frog.posZ, FrogSounds.FROG_LAY_SPAWN,
                                SoundCategory.BLOCKS, 1.0F, 1.0F);
                        this.frog.pregnant = false;
                        return;
                    }
                }
            }
        }
    }

    /**
     * 1.19's {@code TryFindLand}: in water, heads for the nearest dry spot with solid ground under it
     * (range 8 at 1.5 times speed while swimming, 6 at normal speed otherwise), at most every 60 ticks.
     */
    static class FindLand extends EntityAIBase {
        private final EntityFrog frog;
        private long nextOkStart;

        FindLand(EntityFrog frog) {
            this.frog = frog;
            this.setMutexBits(1);
        }

        @Override
        public boolean shouldExecute() {
            EntityFrog.Activity a = this.frog.activity();
            return (a == EntityFrog.Activity.SWIM || a == EntityFrog.Activity.IDLE) && this.frog.getAttackTarget() == null
                    && idle(this.frog) && isWater(this.frog.world, new BlockPos(this.frog))
                    && this.frog.world.getTotalWorldTime() >= this.nextOkStart;
        }

        @Override
        public boolean shouldContinueExecuting() {
            return false;
        }

        @Override
        public void startExecuting() {
            boolean swim = this.frog.activity() == EntityFrog.Activity.SWIM;
            World world = this.frog.world;
            BlockPos here = new BlockPos(this.frog);
            long now = world.getTotalWorldTime();
            this.nextOkStart = now + 60L;
            for (BlockPos off : swim ? MANHATTAN_8 : MANHATTAN_6) {
                BlockPos p = here.add(off);
                if (p.getX() == here.getX() && p.getZ() == here.getZ()) {
                    continue;
                }
                IBlockState s = world.getBlockState(p);
                if (s.getMaterial() != Material.WATER && !s.getMaterial().isLiquid() && noCollision(world, p)
                        && world.getBlockState(p.down()).isSideSolid(world, p.down(), EnumFacing.UP)) {
                    this.frog.getNavigator().tryMoveToXYZ(p.getX() + 0.5D, p.getY(), p.getZ() + 0.5D, swim ? 1.5D : 1.0D);
                    return;
                }
            }
        }
    }

    /**
     * 1.19's {@code LongJumpToPreferredBlock} (and {@code LongJumpToRandomPos}): on land, every 100 to 140
     * ticks, the frog picks a spot up to 4 blocks across and 2 up or down (half the time looking for a lily pad
     * or big dripleaf first) that it cannot simply walk to, crouches for 40 ticks and leaps there on a
     * computed arc (65 to 80 degrees, at most 1.5 blocks a tick, a clear flight).
     */
    static class LongJump extends EntityAIBase {
        private static final int[] ANGLES = {65, 70, 75, 80};

        private final EntityFrog frog;
        private final List<BlockPos> candidates = new ArrayList<BlockPos>();
        private final List<BlockPos> notPreferred = new ArrayList<BlockPos>();
        private boolean wantPreferred;
        @Nullable
        private Vec3d initialPosition;
        @Nullable
        private Vec3d chosenJump;
        private int findTries;
        private long prepareStart;
        private int ticks;
        private boolean jumped;

        LongJump(EntityFrog frog) {
            this.frog = frog;
            this.setMutexBits(1 | 2);
        }

        @Override
        public boolean shouldExecute() {
            if (this.frog.activity() != EntityFrog.Activity.LONG_JUMP || this.frog.isMidJump() || this.frog.longJumpCooldown > 0) {
                return false;
            }
            boolean ok = this.frog.onGround && !this.frog.isInWater() && !this.frog.isInLava();
            if (!ok) {
                this.frog.longJumpCooldown = longJumpCooldown(this.frog.getRNG()) / 2;
            }
            return ok;
        }

        @Override
        public void startExecuting() {
            this.chosenJump = null;
            this.jumped = false;
            this.findTries = 20;
            this.ticks = 0;
            this.initialPosition = this.frog.getPositionVector();
            BlockPos here = new BlockPos(this.frog);
            this.candidates.clear();
            for (int x = -MAX_LONG_JUMP_WIDTH; x <= MAX_LONG_JUMP_WIDTH; x++) {
                for (int y = -MAX_LONG_JUMP_HEIGHT; y <= MAX_LONG_JUMP_HEIGHT; y++) {
                    for (int z = -MAX_LONG_JUMP_WIDTH; z <= MAX_LONG_JUMP_WIDTH; z++) {
                        if (x != 0 || y != 0 || z != 0) {
                            this.candidates.add(here.add(x, y, z));
                        }
                    }
                }
            }
            this.notPreferred.clear();
            this.wantPreferred = this.frog.getRNG().nextFloat() < PREFERRED_BLOCK_CHANCE;
            this.frog.getNavigator().clearPath();
        }

        @Override
        public boolean shouldContinueExecuting() {
            boolean ok = !this.jumped && this.initialPosition != null && this.initialPosition.equals(this.frog.getPositionVector())
                    && this.findTries > 0 && !this.frog.isInWater() && (this.chosenJump != null || !this.candidates.isEmpty())
                    && this.ticks < 200;
            if (!ok && !this.frog.isMidJump()) {
                this.frog.longJumpCooldown = longJumpCooldown(this.frog.getRNG()) / 2;
                this.frog.memory.lookTarget = null;
            }
            return ok;
        }

        @Override
        public void updateTask() {
            this.ticks++;
            long now = this.frog.world.getTotalWorldTime();
            if (this.chosenJump != null) {
                if (now - this.prepareStart >= 40L) {
                    this.frog.rotationYaw = this.frog.renderYawOffset;
                    double len = this.chosenJump.length();
                    double scaled = len + this.frog.jumpBoostPower();
                    Vec3d v = this.chosenJump.scale(scaled / len);
                    this.frog.motionX = v.x;
                    this.frog.motionY = v.y;
                    this.frog.motionZ = v.z;
                    this.frog.velocityChanged = true;
                    this.frog.midJump = true;
                    this.frog.midJumpTicks = 0;
                    this.frog.setPose(EntityFrog.Pose.LONG_JUMPING);
                    this.jumped = true;
                    this.frog.world.playSound(null, this.frog.posX, this.frog.posY, this.frog.posZ, FrogSounds.FROG_LONG_JUMP,
                            SoundCategory.NEUTRAL, 1.0F, 1.0F);
                }
            } else {
                this.findTries--;
                pickCandidate(now);
            }
        }

        /** Weighted by the squared distance, rounded up, as 1.19's {@code PossibleJump}. */
        @Nullable
        private BlockPos randomCandidate() {
            if (this.candidates.isEmpty()) {
                return null;
            }
            BlockPos here = new BlockPos(this.frog);
            int total = 0;
            for (BlockPos p : this.candidates) {
                total += weight(here, p);
            }
            int r = this.frog.getRNG().nextInt(total);
            for (int i = 0; i < this.candidates.size(); i++) {
                r -= weight(here, this.candidates.get(i));
                if (r < 0) {
                    return this.candidates.remove(i);
                }
            }
            return this.candidates.remove(this.candidates.size() - 1);
        }

        private static int weight(BlockPos a, BlockPos b) {
            return MathHelper.ceil(a.distanceSq(b));
        }

        @Nullable
        private BlockPos nextCandidate() {
            if (!this.wantPreferred) {
                return randomCandidate();
            }
            while (!this.candidates.isEmpty()) {
                BlockPos p = randomCandidate();
                if (p != null) {
                    if (preferJumpTo(this.frog.world.getBlockState(p.down()).getBlock())) {
                        return p;
                    }
                    this.notPreferred.add(p);
                }
            }
            return this.notPreferred.isEmpty() ? null : this.notPreferred.remove(0);
        }

        private void pickCandidate(long now) {
            while (!this.candidates.isEmpty() || (this.wantPreferred && !this.notPreferred.isEmpty())) {
                BlockPos target = nextCandidate();
                if (target == null) {
                    return;
                }
                if (!acceptable(target)) {
                    continue;
                }
                Vec3d jump = optimalJump(new Vec3d(target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D));
                if (jump == null) {
                    continue;
                }
                this.frog.getLookHelper().setLookPosition(target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D, 30.0F, 30.0F);
                if (!walkable(target)) {
                    this.chosenJump = jump;
                    this.prepareStart = now;
                    return;
                }
            }
        }

        /** {@code createPath(target, 0, 8)} reaches it: a path to the spot that stays within 8 blocks. */
        private boolean walkable(BlockPos target) {
            Path path = this.frog.getNavigator().getPathToPos(target);
            if (path == null || path.getFinalPathPoint() == null) {
                return false;
            }
            PathPoint end = path.getFinalPathPoint();
            if (end.x != target.getX() || end.y != target.getY() || end.z != target.getZ()) {
                return false;
            }
            for (int i = 0; i < path.getCurrentPathLength(); i++) {
                PathPoint pt = path.getPathPointFromIndex(i);
                if (this.frog.getDistanceSq(pt.x + 0.5D, pt.y, pt.z + 0.5D) > 64.0D) {
                    return false;
                }
            }
            return true;
        }

        /** {@code isAcceptableLandingPosition} with {@code willNotLandInFluid}. */
        private boolean acceptable(BlockPos target) {
            BlockPos here = new BlockPos(this.frog);
            if (here.getX() == target.getX() && here.getZ() == target.getZ()) {
                return false;
            }
            World world = this.frog.world;
            Block below = world.getBlockState(target.down()).getBlock();
            boolean stable = world.getBlockState(target.down()).isOpaqueCube();
            if (!stable && !EntityFrog.isLilyPad(below)) {
                return false;
            }
            PathNodeType type = new WalkNodeProcessor().getPathNodeType(world, target.getX(), target.getY(), target.getZ());
            if (this.frog.getPathPriority(type) != 0.0F) {
                return false;
            }
            return !world.getBlockState(target).getMaterial().isLiquid() && !world.getBlockState(target.down()).getMaterial().isLiquid();
        }

        @Nullable
        private Vec3d optimalJump(Vec3d target) {
            List<Integer> angles = new ArrayList<Integer>();
            for (int a : ANGLES) {
                angles.add(a);
            }
            Collections.shuffle(angles, this.frog.getRNG());
            for (int a : angles) {
                Vec3d v = jumpForAngle(target, a);
                if (v != null) {
                    return v;
                }
            }
            return null;
        }

        /** {@code calculateJumpVectorForAngle}: the launch velocity for an arc at this angle, or null. */
        @Nullable
        private Vec3d jumpForAngle(Vec3d target, int angle) {
            Vec3d from = this.frog.getPositionVector();
            Vec3d back = new Vec3d(target.x - from.x, 0.0D, target.z - from.z).normalize().scale(0.5D);
            target = target.subtract(back);
            Vec3d d = target.subtract(from);
            float rad = angle * (float) Math.PI / 180.0F;
            double yawToward = Math.atan2(d.z, d.x);
            double hSq = d.x * d.x + d.z * d.z;
            double h = Math.sqrt(hSq);
            double dy = d.y;
            double sin2 = Math.sin(2.0F * rad);
            double cosSq = Math.pow(Math.cos(rad), 2.0D);
            double sin = Math.sin(rad);
            double cos = Math.cos(rad);
            double sinYaw = Math.sin(yawToward);
            double cosYaw = Math.cos(yawToward);
            double vSq = hSq * 0.08D / (h * sin2 - 2.0D * dy * cosSq);
            if (vSq < 0.0D) {
                return null;
            }
            double v = Math.sqrt(vSq);
            if (v > MAX_JUMP_VELOCITY) {
                return null;
            }
            double vh = v * cos;
            double vy = v * sin;
            int steps = MathHelper.ceil(h / vh) * 2;
            double dist = 0.0D;
            Vec3d last = null;
            for (int i = 0; i < steps - 1; i++) {
                dist += h / steps;
                double y = sin / cos * dist - Math.pow(dist, 2.0D) * 0.08D / (2.0D * vSq * Math.pow(cos, 2.0D));
                Vec3d p = new Vec3d(from.x + dist * cosYaw, from.y + y, from.z + dist * sinYaw);
                if (last != null && !clear(last, p)) {
                    return null;
                }
                last = p;
            }
            return new Vec3d(vh * cosYaw, vy, vh * sinYaw).scale(0.95F);
        }

        /** {@code isClearTransition}: the frog's box fits all along this step of the flight. */
        private boolean clear(Vec3d a, Vec3d b) {
            Vec3d d = b.subtract(a);
            double size = Math.min(EntityFrog.WIDTH, EntityFrog.HEIGHT);
            int n = MathHelper.ceil(d.length() / size);
            Vec3d dir = d.normalize();
            Vec3d p = a;
            for (int i = 0; i < n; i++) {
                p = i == n - 1 ? b : p.add(dir.scale(size * 0.9F));
                double hw = EntityFrog.WIDTH / 2.0D;
                AxisAlignedBB box = new AxisAlignedBB(p.x - hw, p.y, p.z - hw, p.x + hw, p.y + EntityFrog.HEIGHT, p.z + hw);
                if (!this.frog.world.getCollisionBoxes(this.frog, box).isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public void resetTask() {
            this.chosenJump = null;
            this.initialPosition = null;
        }
    }

    /**
     * The idle and swim activities' wandering: 1.19's {@code RunOne} on land (stroll, walk to what it looks
     * at, croak, rest 5 to 20 ticks; weights 1, 1, 3, 2) and its {@code GateBehavior} in water (random swim at
     * 0.75 times speed, else a stroll, then 30 to 60 ticks of drifting).
     */
    static class FrogWander extends EntityAIBase {
        private final EntityFrog frog;
        private int croakTicks = -1;

        FrogWander(EntityFrog frog) {
            this.frog = frog;
            this.setMutexBits(1);
        }

        @Override
        public boolean shouldExecute() {
            EntityFrog.Activity a = this.frog.activity();
            return (a == EntityFrog.Activity.IDLE || a == EntityFrog.Activity.SWIM) && this.frog.getAttackTarget() == null && idle(this.frog);
        }

        @Override
        public boolean shouldContinueExecuting() {
            return this.croakTicks >= 0 || (shouldExecute() && this.frog.memory.idleTicks > 0);
        }

        @Override
        public void startExecuting() {
            if (this.frog.activity() == EntityFrog.Activity.SWIM) {
                swimWander();
                return;
            }
            int pick = idleWander(this.frog, new int[] {1, 1, 3, 2});
            if (pick == 2) {
                this.croakTicks = 0;
            }
        }

        private void swimWander() {
            Vec3d v = swimTarget(this.frog);
            if (v != null) {
                this.frog.swimNavigator().tryMoveToXYZ(v.x, v.y, v.z, 0.75D);
            } else {
                Vec3d land = RandomPositionGenerator.getLandPos(this.frog, 10, 7);
                if (land != null) {
                    this.frog.getNavigator().tryMoveToXYZ(land.x, land.y, land.z, 1.0D);
                } else if (this.frog.memory.lookTarget != null && this.frog.getDistance(this.frog.memory.lookTarget) > 3.0F) {
                    this.frog.getNavigator().tryMoveToEntityLiving(this.frog.memory.lookTarget, 1.0D);
                }
            }
            if (this.frog.isInWater()) {
                this.frog.memory.idleTicks = 30 + this.frog.getRNG().nextInt(31);
            }
        }

        @Override
        public void updateTask() {
            if (this.croakTicks >= 0) {
                // Croak: 60 ticks in the croaking pose, on land, while not moving.
                if (this.croakTicks == 0) {
                    if (this.frog.isInWater() || this.frog.isInLava() || this.frog.getPose() != EntityFrog.Pose.STANDING) {
                        this.croakTicks = -1;
                        return;
                    }
                    this.frog.setPose(EntityFrog.Pose.CROAKING);
                }
                if (++this.croakTicks >= 60 || !idle(this.frog)) {
                    this.croakTicks = -1;
                    this.frog.setPose(EntityFrog.Pose.STANDING);
                }
            }
        }

        @Override
        public void resetTask() {
            if (this.croakTicks >= 0) {
                this.frog.setPose(EntityFrog.Pose.STANDING);
                this.croakTicks = -1;
            }
        }

        /**
         * One weighted pick of stroll / walk to look target / croak / rest, falling to the next when one
         * cannot start, as {@code RunOne} does.
         *
         * @param weights stroll, look-walk, croak, rest
         * @return the index started (2 = croak, the caller runs it), or -1
         */
        static int idleWander(EntityFrog frog, int[] weights) {
            List<Integer> order = new ArrayList<Integer>();
            List<Integer> pool = new ArrayList<Integer>();
            for (int i = 0; i < weights.length; i++) {
                for (int w = 0; w < weights[i]; w++) {
                    pool.add(i);
                }
            }
            Random r = frog.getRNG();
            while (!pool.isEmpty()) {
                Integer i = pool.get(r.nextInt(pool.size()));
                order.add(i);
                while (pool.remove(i)) {
                    // drop every copy of this choice
                }
            }
            for (int i : order) {
                switch (i) {
                    case 0: {
                        Vec3d v = RandomPositionGenerator.getLandPos(frog, 10, 7);
                        if (v != null && frog.getNavigator().tryMoveToXYZ(v.x, v.y, v.z, 1.0D)) {
                            return 0;
                        }
                        break;
                    }
                    case 1:
                        if (frog.memory.lookTarget instanceof EntityLivingBase && frog.getDistance(frog.memory.lookTarget) > 3.0F
                                && frog.getNavigator().tryMoveToEntityLiving(frog.memory.lookTarget, 1.0D)) {
                            return 1;
                        }
                        break;
                    case 2:
                        if (frog.getPose() == EntityFrog.Pose.STANDING && !frog.isInWater()) {
                            return 2;
                        }
                        break;
                    default:
                        if (frog.onGround) {
                            frog.memory.idleTicks = 5 + r.nextInt(16);
                            return 3;
                        }
                        break;
                }
            }
            return -1;
        }
    }

    /** 1.19's {@code TadpoleAi} idle: random swim at half speed (weight 2), toward what it looks at (3), drift (5). */
    static class TadpoleWander extends EntityAIBase {
        private final EntityTadpole tadpole;

        TadpoleWander(EntityTadpole tadpole) {
            this.tadpole = tadpole;
            this.setMutexBits(1);
        }

        @Override
        public boolean shouldExecute() {
            return idle(this.tadpole) && this.tadpole.memory.idleTicks <= 0;
        }

        @Override
        public boolean shouldContinueExecuting() {
            return false;
        }

        @Override
        public void startExecuting() {
            Vec3d v = this.tadpole.isInWater() ? swimTarget(this.tadpole) : null;
            if (v != null) {
                this.tadpole.getNavigator().tryMoveToXYZ(v.x, v.y, v.z, 0.5D);
            } else if (this.tadpole.memory.lookTarget != null && this.tadpole.getDistance(this.tadpole.memory.lookTarget) > 3.0F) {
                this.tadpole.getNavigator().tryMoveToEntityLiving(this.tadpole.memory.lookTarget, 0.5D);
            }
            if (this.tadpole.isInWater()) {
                this.tadpole.memory.idleTicks = 30 + this.tadpole.getRNG().nextInt(31);
            }
        }
    }
}
