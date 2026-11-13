package com.exiledradio.wildupdatebackport.warden;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.ai.RandomPositionGenerator;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.pathfinding.Path;
import net.minecraft.pathfinding.PathPoint;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The warden's mind: 1.19.2's {@code Brain} with exactly the memories, sensor, activities and behaviors of
 * {@code WardenAi}, on its own tick. No vanilla AI task list is used (nothing here touches {@code entity.tasks}).
 *
 * <h2>How 1.19's brain runs, and so this one</h2>
 * Each tick: memories past their time are forgotten, the sensor runs (every 20 ticks), every stopped behavior of the
 * active activities (core plus one) whose memory conditions hold tries to start, in priority order, then every
 * running behavior ticks, or stops when timed out or no longer usable. Running behaviors keep running when the
 * activity changes. After the tick the activity is the first of emerge, dig, roar, fight, investigate, sniff and idle
 * whose required memory is there; changing activity erases the memories of the activities left.
 *
 * <p>Memories with a time to live are present on every tick up to and including the one their time runs out.
 */
public final class WardenBrain {

    public enum Memory {
        ATTACK_TARGET(false), ATTACK_COOLING_DOWN(false), ROAR_TARGET(false), DISTURBANCE_LOCATION(false),
        RECENT_PROJECTILE(true), IS_SNIFFING(true), IS_EMERGING(true), ROAR_SOUND_DELAY(true), DIG_COOLDOWN(true),
        ROAR_SOUND_COOLDOWN(true), SNIFF_COOLDOWN(true), TOUCH_COOLDOWN(true), VIBRATION_COOLDOWN(true),
        SONIC_BOOM_COOLDOWN(true), SONIC_BOOM_SOUND_COOLDOWN(true), SONIC_BOOM_SOUND_DELAY(true),
        WALK_TARGET(false), LOOK_TARGET(false), NEAREST_ATTACKABLE(false), NEAREST_LIVING_ENTITIES(false),
        NEAREST_VISIBLE_LIVING_ENTITIES(false), CANT_REACH_WALK_TARGET_SINCE(false), PATH(false);

        /** Saved with the warden (1.19's memories that have a codec). */
        final boolean saved;

        Memory(boolean saved) {
            this.saved = saved;
        }
    }

    public enum Activity { CORE, EMERGE, DIG, ROAR, FIGHT, INVESTIGATE, SNIFF, IDLE }

    private static final Activity[] ORDER = {Activity.EMERGE, Activity.DIG, Activity.ROAR, Activity.FIGHT,
            Activity.INVESTIGATE, Activity.SNIFF, Activity.IDLE};

    public static final int DIGGING_DURATION = 100;
    public static final int EMERGE_DURATION = 134;
    public static final int ROAR_DURATION = 84;
    public static final int SNIFFING_DURATION = 84;
    public static final int DIGGING_COOLDOWN = 1200;
    public static final int MELEE_ATTACK_COOLDOWN = 18;
    public static final int SONIC_BOOM_DURATION = 60;
    public static final int SONIC_BOOM_SOUND_TICKS = 34;
    public static final int ROAR_SOUND_TICKS = 25;
    private static final int SENSOR_RADIUS = 24;
    private static final double VISIBLE_RANGE = 16.0D;

    /** Where to walk: a block or an entity, how fast (times the speed attribute) and how close is close enough. */
    public static final class WalkTarget {
        @Nullable
        final EntityLivingBase entity;
        final BlockPos pos;
        final float speed;
        final int closeEnough;

        WalkTarget(BlockPos pos, float speed, int closeEnough) {
            this.entity = null;
            this.pos = pos.toImmutable();
            this.speed = speed;
            this.closeEnough = closeEnough;
        }

        WalkTarget(EntityLivingBase entity, float speed, int closeEnough) {
            this.entity = entity;
            this.pos = BlockPos.ORIGIN;
            this.speed = speed;
            this.closeEnough = closeEnough;
        }

        BlockPos blockPos() {
            return this.entity != null ? new BlockPos(this.entity) : this.pos;
        }
    }

    /** A look target: a block's center or an entity (at its eyes or feet). */
    public static final class LookTarget {
        @Nullable
        final EntityLivingBase entity;
        final boolean eyes;
        final BlockPos pos;

        LookTarget(BlockPos pos) {
            this.entity = null;
            this.eyes = false;
            this.pos = pos.toImmutable();
        }

        LookTarget(EntityLivingBase entity, boolean eyes) {
            this.entity = entity;
            this.eyes = eyes;
            this.pos = BlockPos.ORIGIN;
        }

        Vec3d position() {
            if (this.entity != null) {
                return this.eyes ? new Vec3d(this.entity.posX, this.entity.posY + this.entity.getEyeHeight(), this.entity.posZ)
                        : this.entity.getPositionVector();
            }
            return new Vec3d(this.pos.getX() + 0.5D, this.pos.getY() + 0.5D, this.pos.getZ() + 0.5D);
        }

        boolean isVisibleBy(WardenBrain brain) {
            if (this.entity == null) {
                return true;
            }
            return this.entity.isEntityAlive() && brain.visible().contains(this.entity);
        }
    }

    private static final class Slot {
        Object value;
        long ttl;
        boolean expires;
    }

    private final EntityWarden warden;
    private final Map<Memory, Slot> memories = new EnumMap<Memory, Slot>(Memory.class);
    private final List<Behavior> behaviors = new ArrayList<Behavior>();
    private Activity activity = Activity.IDLE;
    private int sensorTicks;

    WardenBrain(EntityWarden warden) {
        this.warden = warden;
        this.sensorTicks = warden.getRNG().nextInt(20);
        // Core, priority 0.
        add(Activity.CORE, 0, new Swim());
        add(Activity.CORE, 0, new SetWardenLookTarget());
        add(Activity.CORE, 0, new LookAtTargetSink());
        add(Activity.CORE, 0, new MoveToTargetSink());
        add(Activity.EMERGE, 5, new Emerging());
        add(Activity.DIG, 0, new ForceUnmount());
        add(Activity.DIG, 1, new Digging());
        add(Activity.IDLE, 10, new SetRoarTarget());
        add(Activity.IDLE, 10, new TryToSniff());
        add(Activity.IDLE, 10, new IdleRunOne());
        add(Activity.ROAR, 10, new Roar());
        add(Activity.FIGHT, 10, new DigCooldownSetter());
        add(Activity.FIGHT, 10, new StopAttackingIfTargetInvalid());
        add(Activity.FIGHT, 10, new SetEntityLookTarget());
        add(Activity.FIGHT, 10, new SetWalkTargetFromAttackTarget());
        add(Activity.FIGHT, 10, new SonicBoom());
        add(Activity.FIGHT, 10, new MeleeAttack());
        add(Activity.INVESTIGATE, 5, new SetRoarTarget());
        add(Activity.INVESTIGATE, 5, new GoToDisturbance());
        add(Activity.SNIFF, 5, new SetRoarTarget());
        add(Activity.SNIFF, 5, new Sniffing());
        // Stable sort: priority, then the order added (1.19's tree by priority, insertion order within).
        Collections.sort(this.behaviors, (a, b) -> Integer.compare(a.priority, b.priority));
    }

    private void add(Activity activity, int priority, Behavior b) {
        b.activity = activity;
        b.priority = priority;
        this.behaviors.add(b);
    }

    // --- memories -------------------------------------------------------------------------------------------------

    public boolean has(Memory m) {
        return this.memories.containsKey(m);
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public <T> T get(Memory m) {
        Slot s = this.memories.get(m);
        return s == null ? null : (T) s.value;
    }

    public void set(Memory m, Object value) {
        Slot s = new Slot();
        s.value = value;
        this.memories.put(m, s);
    }

    public void setWithExpiry(Memory m, Object value, long ttl) {
        Slot s = new Slot();
        s.value = value;
        s.ttl = ttl;
        s.expires = true;
        this.memories.put(m, s);
    }

    public void erase(Memory m) {
        this.memories.remove(m);
        if (m == Memory.ATTACK_TARGET) {
            this.warden.setAttackTarget(null);
        }
    }

    /** @return the ticks a memory has left, or -1 if absent or permanent (for the tests) */
    public long timeToLive(Memory m) {
        Slot s = this.memories.get(m);
        return s == null || !s.expires ? -1 : s.ttl;
    }

    public Activity activity() {
        return this.activity;
    }

    @SuppressWarnings("unchecked")
    Set<EntityLivingBase> visible() {
        Set<EntityLivingBase> v = get(Memory.NEAREST_VISIBLE_LIVING_ENTITIES);
        return v == null ? Collections.<EntityLivingBase>emptySet() : v;
    }

    boolean canSee(EntityLivingBase e) {
        return visible().contains(e);
    }

    // --- the tick -------------------------------------------------------------------------------------------------

    void tick(WorldServer world) {
        long time = world.getTotalWorldTime();
        forgetOutdatedMemories();
        if (--this.sensorTicks <= 0) {
            this.sensorTicks = 20;
            sense(world);
        }
        for (Behavior b : this.behaviors) {
            if (!b.running && (b.activity == Activity.CORE || b.activity == this.activity) && b.canStart(world)) {
                b.running = true;
                int min = b.minDuration;
                b.endTime = time + min + this.warden.getRNG().nextInt(b.maxDuration + 1 - min);
                b.start(world, time);
            }
        }
        for (Behavior b : this.behaviors) {
            if (b.running) {
                if (time <= b.endTime && b.canStillUse(world, time)) {
                    b.tick(world, time);
                } else {
                    b.running = false;
                    b.stop(world, time);
                }
            }
        }
    }

    private void forgetOutdatedMemories() {
        List<Memory> gone = null;
        for (Map.Entry<Memory, Slot> e : this.memories.entrySet()) {
            Slot s = e.getValue();
            if (!s.expires) {
                continue;
            }
            if (s.ttl <= 0) {
                if (gone == null) {
                    gone = new ArrayList<Memory>();
                }
                gone.add(e.getKey());
            }
            s.ttl--;
        }
        if (gone != null) {
            for (Memory m : gone) {
                erase(m);
            }
        }
    }

    /** 1.19's {@code WardenEntitySensor}: living entities within 24, the visible ones, and the nearest attackable. */
    private void sense(WorldServer world) {
        EntityWarden w = this.warden;
        AxisAlignedBB box = w.getEntityBoundingBox().grow(SENSOR_RADIUS, SENSOR_RADIUS, SENSOR_RADIUS);
        List<EntityLivingBase> list = world.getEntitiesWithinAABB(EntityLivingBase.class, box, e -> e != w && e.isEntityAlive());
        list.sort((a, b) -> Double.compare(w.getDistanceSq(a), w.getDistanceSq(b)));
        set(Memory.NEAREST_LIVING_ENTITIES, list);
        Set<EntityLivingBase> visible = new HashSet<EntityLivingBase>();
        for (EntityLivingBase e : list) {
            if (w.getDistanceSq(e) <= VISIBLE_RANGE * VISIBLE_RANGE && targetableForSight(e) && w.canEntityBeSeen(e)) {
                visible.add(e);
            }
        }
        set(Memory.NEAREST_VISIBLE_LIVING_ENTITIES, visible);
        EntityLivingBase nearest = null;
        for (EntityLivingBase e : list) {
            if (e instanceof EntityPlayer && w.canTargetEntity(e)) {
                nearest = e;
                break;
            }
        }
        if (nearest == null) {
            for (EntityLivingBase e : list) {
                if (!(e instanceof EntityPlayer) && w.canTargetEntity(e)) {
                    nearest = e;
                    break;
                }
            }
        }
        if (nearest != null) {
            set(Memory.NEAREST_ATTACKABLE, nearest);
        } else {
            erase(Memory.NEAREST_ATTACKABLE);
        }
    }

    /** 1.19's non-combat targeting conditions: not a spectator or creative player, not on the warden's team. */
    private boolean targetableForSight(EntityLivingBase e) {
        if (e instanceof EntityPlayer && (((EntityPlayer) e).isSpectator() || ((EntityPlayer) e).isCreative())) {
            return false;
        }
        return !this.warden.isOnSameTeam(e);
    }

    /** 1.19's {@code WardenAi.updateActivity}. */
    void updateActivity() {
        for (Activity a : ORDER) {
            if (requirementsMet(a)) {
                if (a != this.activity) {
                    for (Activity other : Activity.values()) {
                        Memory m = erasedWhenStopped(other);
                        if (other != a && m != null) {
                            erase(m);
                        }
                    }
                    this.activity = a;
                }
                return;
            }
        }
    }

    private boolean requirementsMet(Activity a) {
        switch (a) {
            case EMERGE:
                return has(Memory.IS_EMERGING);
            case DIG:
                return !has(Memory.ROAR_TARGET) && !has(Memory.DIG_COOLDOWN);
            case ROAR:
                return has(Memory.ROAR_TARGET);
            case FIGHT:
                return has(Memory.ATTACK_TARGET);
            case INVESTIGATE:
                return has(Memory.DISTURBANCE_LOCATION);
            case SNIFF:
                return has(Memory.IS_SNIFFING);
            default:
                return true;
        }
    }

    @Nullable
    private static Memory erasedWhenStopped(Activity a) {
        switch (a) {
            case EMERGE:
                return Memory.IS_EMERGING;
            case ROAR:
                return Memory.ROAR_TARGET;
            case FIGHT:
                return Memory.ATTACK_TARGET;
            case INVESTIGATE:
                return Memory.DISTURBANCE_LOCATION;
            case SNIFF:
                return Memory.IS_SNIFFING;
            default:
                return null;
        }
    }

    // --- helpers 1.19's WardenAi and BehaviorUtils give ------------------------------------------------------------

    /** 1.19's {@code WardenAi.setDigCooldown}: refreshed to 1200 only while it is still there. */
    void setDigCooldown() {
        if (has(Memory.DIG_COOLDOWN)) {
            setWithExpiry(Memory.DIG_COOLDOWN, Boolean.TRUE, DIGGING_COOLDOWN);
        }
    }

    /** 1.19's {@code WardenAi.setDisturbanceLocation}. */
    void setDisturbanceLocation(BlockPos pos) {
        if (this.warden.world.getWorldBorder().contains(pos) && this.warden.getEntityAngryAt() == null && !has(Memory.ATTACK_TARGET)) {
            setDigCooldown();
            setWithExpiry(Memory.SNIFF_COOLDOWN, Boolean.TRUE, 100L);
            setWithExpiry(Memory.LOOK_TARGET, new LookTarget(pos), 100L);
            setWithExpiry(Memory.DISTURBANCE_LOCATION, pos.toImmutable(), 100L);
            erase(Memory.WALK_TARGET);
        }
    }

    void setAttackTarget(EntityLivingBase target) {
        erase(Memory.ROAR_TARGET);
        set(Memory.ATTACK_TARGET, target);
        this.warden.setAttackTarget(target);
        erase(Memory.CANT_REACH_WALK_TARGET_SINCE);
        setWithExpiry(Memory.SONIC_BOOM_COOLDOWN, Boolean.TRUE, 200L);
    }

    private void lookAt(Vec3d at) {
        this.warden.getLookHelper().setLookPosition(at.x, at.y, at.z, this.warden.getHorizontalFaceSpeed(), this.warden.getVerticalFaceSpeed());
    }

    /** 1.19's {@code BehaviorUtils.lookAtEntity}. */
    private void lookAtEntity(EntityLivingBase e) {
        set(Memory.LOOK_TARGET, new LookTarget(e, true));
    }

    private void setWalkAndLookTarget(BlockPos pos, float speed, int closeEnough) {
        set(Memory.WALK_TARGET, new WalkTarget(pos, speed, closeEnough));
        set(Memory.LOOK_TARGET, new LookTarget(pos));
    }

    // --- saving ---------------------------------------------------------------------------------------------------

    NBTTagCompound write() {
        NBTTagCompound tag = new NBTTagCompound();
        for (Map.Entry<Memory, Slot> e : this.memories.entrySet()) {
            if (e.getKey().saved) {
                NBTTagCompound m = new NBTTagCompound();
                if (e.getValue().expires) {
                    m.setLong("ttl", e.getValue().ttl);
                }
                tag.setTag(e.getKey().name().toLowerCase(java.util.Locale.ROOT), m);
            }
        }
        return tag;
    }

    void read(NBTTagCompound tag) {
        for (Memory m : Memory.values()) {
            String key = m.name().toLowerCase(java.util.Locale.ROOT);
            if (m.saved && tag.hasKey(key, 10)) {
                NBTTagCompound t = tag.getCompoundTag(key);
                if (t.hasKey("ttl")) {
                    setWithExpiry(m, Boolean.TRUE, t.getLong("ttl"));
                } else {
                    set(m, Boolean.TRUE);
                }
            }
        }
    }

    // --- behaviors ------------------------------------------------------------------------------------------------

    /** 1.19's {@code Behavior}: starts when its conditions hold, runs between its min and max duration. */
    abstract class Behavior {
        Activity activity;
        int priority;
        int minDuration = 60;
        int maxDuration = 60;
        boolean running;
        long endTime;

        Behavior duration(int ticks) {
            this.minDuration = ticks;
            this.maxDuration = ticks;
            return this;
        }

        abstract boolean canStart(WorldServer world);

        void start(WorldServer world, long time) {
        }

        boolean canStillUse(WorldServer world, long time) {
            return false;
        }

        void tick(WorldServer world, long time) {
        }

        void stop(WorldServer world, long time) {
        }
    }

    /** {@code Swim(0.8)}. */
    final class Swim extends Behavior {
        @Override
        boolean canStart(WorldServer world) {
            return warden.isInWater() || warden.isInLava();
        }

        @Override
        boolean canStillUse(WorldServer world, long time) {
            return canStart(world);
        }

        @Override
        void tick(WorldServer world, long time) {
            if (warden.getRNG().nextFloat() < 0.8F) {
                warden.getJumpHelper().setJumping();
            }
        }
    }

    final class SetWardenLookTarget extends Behavior {
        @Override
        boolean canStart(WorldServer world) {
            return !has(Memory.ATTACK_TARGET) && (has(Memory.DISTURBANCE_LOCATION) || has(Memory.ROAR_TARGET));
        }

        @Override
        void start(WorldServer world, long time) {
            EntityLivingBase roar = get(Memory.ROAR_TARGET);
            BlockPos pos = roar != null ? new BlockPos(roar) : WardenBrain.this.<BlockPos>get(Memory.DISTURBANCE_LOCATION);
            set(Memory.LOOK_TARGET, new LookTarget(pos));
        }
    }

    /** {@code LookAtTargetSink(45, 90)}. */
    final class LookAtTargetSink extends Behavior {
        LookAtTargetSink() {
            this.minDuration = 45;
            this.maxDuration = 90;
        }

        @Override
        boolean canStart(WorldServer world) {
            return has(Memory.LOOK_TARGET);
        }

        @Override
        boolean canStillUse(WorldServer world, long time) {
            LookTarget t = get(Memory.LOOK_TARGET);
            return t != null && t.isVisibleBy(WardenBrain.this);
        }

        @Override
        void tick(WorldServer world, long time) {
            LookTarget t = get(Memory.LOOK_TARGET);
            if (t != null) {
                lookAt(t.position());
            }
        }

        @Override
        void stop(WorldServer world, long time) {
            erase(Memory.LOOK_TARGET);
        }
    }

    /** {@code MoveToTargetSink(150, 250)}: paths to the walk target with 1.12's navigator. */
    final class MoveToTargetSink extends Behavior {
        private int remainingCooldown;
        @Nullable
        private Path path;
        @Nullable
        private BlockPos lastTargetPos;
        private float speed;

        MoveToTargetSink() {
            this.minDuration = 150;
            this.maxDuration = 250;
        }

        @Override
        boolean canStart(WorldServer world) {
            if (!has(Memory.WALK_TARGET) || has(Memory.PATH)) {
                return false;
            }
            if (this.remainingCooldown > 0) {
                this.remainingCooldown--;
                return false;
            }
            WalkTarget target = get(Memory.WALK_TARGET);
            boolean reached = reached(target);
            if (!reached && tryComputePath(target, world.getTotalWorldTime())) {
                this.lastTargetPos = target.blockPos();
                return true;
            }
            erase(Memory.WALK_TARGET);
            if (reached) {
                erase(Memory.CANT_REACH_WALK_TARGET_SINCE);
            }
            return false;
        }

        @Override
        boolean canStillUse(WorldServer world, long time) {
            if (this.path == null || this.lastTargetPos == null) {
                return false;
            }
            WalkTarget target = get(Memory.WALK_TARGET);
            return !warden.getNavigator().noPath() && target != null && !reached(target);
        }

        @Override
        void stop(WorldServer world, long time) {
            WalkTarget target = get(Memory.WALK_TARGET);
            if (target != null && !reached(target) && isStuck()) {
                this.remainingCooldown = world.rand.nextInt(40);
            }
            warden.getNavigator().clearPath();
            erase(Memory.WALK_TARGET);
            erase(Memory.PATH);
            this.path = null;
        }

        private boolean isStuck() {
            // 1.12's navigator gives up on a stuck path by clearing it: no path left means it stopped short.
            return warden.getNavigator().noPath();
        }

        @Override
        void start(WorldServer world, long time) {
            set(Memory.PATH, this.path);
            warden.getNavigator().setPath(this.path, this.speed);
        }

        @Override
        void tick(WorldServer world, long time) {
            Path current = warden.getNavigator().getPath();
            if (this.path != current) {
                this.path = current;
                if (current != null) {
                    set(Memory.PATH, current);
                } else {
                    erase(Memory.PATH);
                }
            }
            if (current != null && this.lastTargetPos != null) {
                WalkTarget target = get(Memory.WALK_TARGET);
                if (target != null && target.blockPos().distanceSq(this.lastTargetPos) > 4.0D && tryComputePath(target, time)) {
                    this.lastTargetPos = target.blockPos();
                    start(world, time);
                }
            }
        }

        private boolean tryComputePath(WalkTarget target, long time) {
            BlockPos pos = target.blockPos();
            this.path = warden.getNavigator().getPathToPos(pos);
            this.speed = target.speed;
            if (reached(target)) {
                erase(Memory.CANT_REACH_WALK_TARGET_SINCE);
            } else {
                if (this.path != null && canReach(this.path, pos)) {
                    erase(Memory.CANT_REACH_WALK_TARGET_SINCE);
                } else if (!has(Memory.CANT_REACH_WALK_TARGET_SINCE)) {
                    set(Memory.CANT_REACH_WALK_TARGET_SINCE, time);
                }
                if (this.path != null) {
                    return true;
                }
                Vec3d toward = RandomPositionGenerator.findRandomTargetBlockTowards(warden, 10, 7,
                        new Vec3d(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D));
                if (toward != null) {
                    this.path = warden.getNavigator().getPathToXYZ(toward.x, toward.y, toward.z);
                    return this.path != null;
                }
            }
            return false;
        }

        private boolean canReach(Path path, BlockPos pos) {
            PathPoint end = path.getFinalPathPoint();
            return end != null && end.x == pos.getX() && end.y == pos.getY() && end.z == pos.getZ();
        }

        private boolean reached(WalkTarget target) {
            BlockPos a = target.blockPos();
            BlockPos b = new BlockPos(warden);
            int manhattan = Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY()) + Math.abs(a.getZ() - b.getZ());
            return manhattan <= target.closeEnough;
        }
    }

    final class Emerging extends Behavior {
        Emerging() {
            duration(EMERGE_DURATION);
        }

        @Override
        boolean canStart(WorldServer world) {
            return has(Memory.IS_EMERGING) && !has(Memory.WALK_TARGET);
        }

        @Override
        boolean canStillUse(WorldServer world, long time) {
            return true;
        }

        @Override
        void start(WorldServer world, long time) {
            warden.setPose(WardenPose.EMERGING);
            warden.playSound(WardenContent.EMERGE, 5.0F, 1.0F);
        }

        @Override
        void stop(WorldServer world, long time) {
            if (warden.getPose() == WardenPose.EMERGING) {
                warden.setPose(WardenPose.STANDING);
            }
        }
    }

    final class ForceUnmount extends Behavior {
        @Override
        boolean canStart(WorldServer world) {
            return warden.isRiding();
        }

        @Override
        void start(WorldServer world, long time) {
            warden.dismountRidingEntity();
        }
    }

    final class Digging extends Behavior {
        Digging() {
            duration(DIGGING_DURATION);
        }

        @Override
        boolean canStart(WorldServer world) {
            return !has(Memory.ATTACK_TARGET) && !has(Memory.WALK_TARGET)
                    && (warden.onGround || warden.isInWater() || warden.isInLava());
        }

        @Override
        boolean canStillUse(WorldServer world, long time) {
            return !warden.isDead;
        }

        @Override
        void start(WorldServer world, long time) {
            if (warden.onGround) {
                warden.setPose(WardenPose.DIGGING);
                warden.playSound(WardenContent.DIG, 5.0F, 1.0F);
            } else {
                warden.playSound(WardenContent.AGITATED, 5.0F, 1.0F);
                stop(world, time);
            }
        }

        @Override
        void stop(WorldServer world, long time) {
            if (!warden.isDead) {
                warden.setDead();
            }
        }
    }

    /** {@code SetRoarTarget(Warden::getEntityAngryAt)}. */
    final class SetRoarTarget extends Behavior {
        @Override
        boolean canStart(WorldServer world) {
            if (has(Memory.ROAR_TARGET) || has(Memory.ATTACK_TARGET)) {
                return false;
            }
            EntityLivingBase angryAt = warden.getEntityAngryAt();
            return angryAt != null && warden.canTargetEntity(angryAt);
        }

        @Override
        void start(WorldServer world, long time) {
            EntityLivingBase angryAt = warden.getEntityAngryAt();
            if (angryAt != null) {
                set(Memory.ROAR_TARGET, angryAt);
                erase(Memory.CANT_REACH_WALK_TARGET_SINCE);
            }
        }
    }

    final class TryToSniff extends Behavior {
        @Override
        boolean canStart(WorldServer world) {
            return !has(Memory.SNIFF_COOLDOWN) && has(Memory.NEAREST_ATTACKABLE) && !has(Memory.DISTURBANCE_LOCATION);
        }

        @Override
        void start(WorldServer world, long time) {
            set(Memory.IS_SNIFFING, Boolean.TRUE);
            setWithExpiry(Memory.SNIFF_COOLDOWN, Boolean.TRUE, 100 + world.rand.nextInt(101));
            erase(Memory.WALK_TARGET);
            warden.setPose(WardenPose.SNIFFING);
        }
    }

    /**
     * {@code RunOne} of {@code RandomStroll(0.5)} (weight 2) and {@code DoNothing(30, 60)} (weight 1), while not
     * sniffing: the order is shuffled by weight and the first one that can start runs.
     */
    final class IdleRunOne extends Behavior {
        private int doNothingLeft = -1;

        @Override
        boolean canStart(WorldServer world) {
            return !has(Memory.IS_SNIFFING);
        }

        @Override
        void start(WorldServer world, long time) {
            boolean strollFirst = warden.getRNG().nextInt(3) < 2;
            if (strollFirst && tryStroll()) {
                return;
            }
            // Do nothing for 30 to 60 ticks (it can always start), unless stroll came second and nothing is next.
            this.doNothingLeft = 30 + warden.getRNG().nextInt(31);
        }

        private boolean tryStroll() {
            if (has(Memory.WALK_TARGET)) {
                return false;
            }
            Vec3d to = RandomPositionGenerator.findRandomTarget(warden, 10, 7);
            if (to != null) {
                set(Memory.WALK_TARGET, new WalkTarget(new BlockPos(to), 0.5F, 0));
            }
            return true;
        }

        @Override
        boolean canStillUse(WorldServer world, long time) {
            return this.doNothingLeft > 0;
        }

        @Override
        void tick(WorldServer world, long time) {
            this.doNothingLeft--;
        }

        @Override
        void stop(WorldServer world, long time) {
            this.doNothingLeft = -1;
        }

        @Override
        Behavior duration(int ticks) {
            return this;
        }
    }

    final class Roar extends Behavior {
        Roar() {
            duration(ROAR_DURATION);
        }

        @Override
        boolean canStart(WorldServer world) {
            return has(Memory.ROAR_TARGET) && !has(Memory.ATTACK_TARGET);
        }

        @Override
        boolean canStillUse(WorldServer world, long time) {
            return true;
        }

        @Override
        void start(WorldServer world, long time) {
            setWithExpiry(Memory.ROAR_SOUND_DELAY, Boolean.TRUE, ROAR_SOUND_TICKS);
            erase(Memory.WALK_TARGET);
            EntityLivingBase target = get(Memory.ROAR_TARGET);
            lookAtEntity(target);
            warden.setPose(WardenPose.ROARING);
            warden.increaseAngerAt(target, 20, false);
        }

        @Override
        void tick(WorldServer world, long time) {
            if (!has(Memory.ROAR_SOUND_DELAY) && !has(Memory.ROAR_SOUND_COOLDOWN)) {
                setWithExpiry(Memory.ROAR_SOUND_COOLDOWN, Boolean.TRUE, ROAR_DURATION - ROAR_SOUND_TICKS);
                warden.playSound(WardenContent.ROAR, 3.0F, 1.0F);
            }
        }

        @Override
        void stop(WorldServer world, long time) {
            if (warden.getPose() == WardenPose.ROARING) {
                warden.setPose(WardenPose.STANDING);
            }
            EntityLivingBase target = get(Memory.ROAR_TARGET);
            if (target != null) {
                warden.setAttackTargetFromBrain(target);
            }
            erase(Memory.ROAR_TARGET);
        }
    }

    final class DigCooldownSetter extends Behavior {
        @Override
        boolean canStart(WorldServer world) {
            return true;
        }

        @Override
        void start(WorldServer world, long time) {
            setDigCooldown();
        }
    }

    /** {@code StopAttackingIfTargetInvalid(!angry || !canTarget, onTargetInvalid, false)}. */
    final class StopAttackingIfTargetInvalid extends Behavior {
        @Override
        boolean canStart(WorldServer world) {
            return has(Memory.ATTACK_TARGET);
        }

        @Override
        void start(WorldServer world, long time) {
            EntityLivingBase target = get(Memory.ATTACK_TARGET);
            boolean invalid = !warden.canAttackClass(target.getClass()) || !target.isEntityAlive() || target.world != warden.world
                    || !warden.getAngerLevel().isAngry() || !warden.canTargetEntity(target);
            if (invalid) {
                if (!warden.canTargetEntity(target)) {
                    warden.clearAnger(target);
                }
                setDigCooldown();
                erase(Memory.ATTACK_TARGET);
            }
        }
    }

    /** {@code SetEntityLookTarget(isTarget, followRange)}. */
    final class SetEntityLookTarget extends Behavior {
        @Override
        boolean canStart(WorldServer world) {
            if (has(Memory.LOOK_TARGET)) {
                return false;
            }
            EntityLivingBase target = get(Memory.ATTACK_TARGET);
            double range = warden.getEntityAttribute(net.minecraft.entity.SharedMonsterAttributes.FOLLOW_RANGE).getAttributeValue();
            return target != null && canSee(target) && warden.getDistanceSq(target) <= range * range;
        }

        @Override
        void start(WorldServer world, long time) {
            set(Memory.LOOK_TARGET, new LookTarget(WardenBrain.this.<EntityLivingBase>get(Memory.ATTACK_TARGET), true));
        }
    }

    /** {@code SetWalkTargetFromAttackTargetIfTargetOutOfReach(1.2)}. */
    final class SetWalkTargetFromAttackTarget extends Behavior {
        @Override
        boolean canStart(WorldServer world) {
            return has(Memory.ATTACK_TARGET);
        }

        @Override
        void start(WorldServer world, long time) {
            EntityLivingBase target = get(Memory.ATTACK_TARGET);
            if (canSee(target) && warden.isWithinMeleeAttackRange(target)) {
                erase(Memory.WALK_TARGET);
            } else {
                set(Memory.LOOK_TARGET, new LookTarget(target, true));
                set(Memory.WALK_TARGET, new WalkTarget(target, 1.2F, 0));
            }
        }
    }

    final class SonicBoom extends Behavior {
        SonicBoom() {
            duration(SONIC_BOOM_DURATION);
        }

        @Override
        boolean canStart(WorldServer world) {
            EntityLivingBase target = get(Memory.ATTACK_TARGET);
            return target != null && !has(Memory.SONIC_BOOM_COOLDOWN) && warden.closerThan(target, 15.0D, 20.0D);
        }

        @Override
        boolean canStillUse(WorldServer world, long time) {
            return true;
        }

        @Override
        void start(WorldServer world, long time) {
            setWithExpiry(Memory.ATTACK_COOLING_DOWN, Boolean.TRUE, SONIC_BOOM_DURATION);
            setWithExpiry(Memory.SONIC_BOOM_SOUND_DELAY, Boolean.TRUE, SONIC_BOOM_SOUND_TICKS);
            world.setEntityState(warden, EntityWarden.EVENT_SONIC_BOOM);
            warden.playSound(WardenContent.SONIC_CHARGE, 3.0F, 1.0F);
        }

        @Override
        void tick(WorldServer world, long time) {
            EntityLivingBase target = get(Memory.ATTACK_TARGET);
            if (target != null) {
                warden.getLookHelper().setLookPosition(target.posX, target.posY, target.posZ,
                        warden.getHorizontalFaceSpeed(), warden.getVerticalFaceSpeed());
            }
            if (!has(Memory.SONIC_BOOM_SOUND_DELAY) && !has(Memory.SONIC_BOOM_SOUND_COOLDOWN)) {
                setWithExpiry(Memory.SONIC_BOOM_SOUND_COOLDOWN, Boolean.TRUE, SONIC_BOOM_DURATION - SONIC_BOOM_SOUND_TICKS);
                if (target != null && warden.canTargetEntity(target) && warden.closerThan(target, 15.0D, 20.0D)) {
                    warden.sonicBoom(world, target);
                }
            }
        }

        @Override
        void stop(WorldServer world, long time) {
            setWithExpiry(Memory.SONIC_BOOM_COOLDOWN, Boolean.TRUE, 40L);
        }
    }

    /** {@code MeleeAttack(18)}. */
    final class MeleeAttack extends Behavior {
        @Override
        boolean canStart(WorldServer world) {
            EntityLivingBase target = get(Memory.ATTACK_TARGET);
            return target != null && !has(Memory.ATTACK_COOLING_DOWN) && canSee(target) && warden.isWithinMeleeAttackRange(target);
        }

        @Override
        void start(WorldServer world, long time) {
            EntityLivingBase target = get(Memory.ATTACK_TARGET);
            lookAtEntity(target);
            warden.swingArm(net.minecraft.util.EnumHand.MAIN_HAND);
            warden.attackEntityAsMob(target);
            setWithExpiry(Memory.ATTACK_COOLING_DOWN, Boolean.TRUE, MELEE_ATTACK_COOLDOWN);
        }
    }

    /** {@code GoToTargetLocation(DISTURBANCE_LOCATION, 2, 0.7)}. */
    final class GoToDisturbance extends Behavior {
        @Override
        boolean canStart(WorldServer world) {
            return has(Memory.DISTURBANCE_LOCATION) && !has(Memory.ATTACK_TARGET) && !has(Memory.WALK_TARGET);
        }

        @Override
        void start(WorldServer world, long time) {
            BlockPos pos = get(Memory.DISTURBANCE_LOCATION);
            if (pos.distanceSq(new BlockPos(warden)) >= 2 * 2) {
                BlockPos near = pos.add(world.rand.nextInt(3) - 1, 0, world.rand.nextInt(3) - 1);
                setWalkAndLookTarget(near, 0.7F, 2);
            }
        }
    }

    final class Sniffing extends Behavior {
        Sniffing() {
            duration(SNIFFING_DURATION);
        }

        @Override
        boolean canStart(WorldServer world) {
            return has(Memory.IS_SNIFFING) && !has(Memory.ATTACK_TARGET) && !has(Memory.WALK_TARGET);
        }

        @Override
        boolean canStillUse(WorldServer world, long time) {
            return true;
        }

        @Override
        void start(WorldServer world, long time) {
            warden.playSound(WardenContent.SNIFF, 5.0F, 1.0F);
        }

        @Override
        void stop(WorldServer world, long time) {
            if (warden.getPose() == WardenPose.SNIFFING) {
                warden.setPose(WardenPose.STANDING);
            }
            erase(Memory.IS_SNIFFING);
            EntityLivingBase nearest = get(Memory.NEAREST_ATTACKABLE);
            if (nearest != null && warden.canTargetEntity(nearest)) {
                if (warden.closerThan(nearest, 6.0D, 20.0D)) {
                    warden.increaseAngerAt(nearest);
                }
                if (!has(Memory.DISTURBANCE_LOCATION)) {
                    setDisturbanceLocation(new BlockPos(nearest));
                }
            }
        }
    }
}
