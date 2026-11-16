package com.exiledradio.wildupdatebackport.sculk;

import com.exiledradio.wildupdatebackport.WildUpdateBackport;
import com.exiledradio.backportcore.gameevent.GameEvent;
import com.exiledradio.backportcore.gameevent.GameEventContext;
import com.exiledradio.backportcore.gameevent.GameEventTags;
import com.exiledradio.backportcore.gameevent.GameEvents;
import com.exiledradio.backportcore.gameevent.PositionSource;
import com.exiledradio.backportcore.gameevent.VibrationListener;

import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.OptionalInt;
import java.util.Set;

/**
 * The sculk shrieker's tile entity: 1.19.2's {@code SculkShriekerBlockEntity}.
 *
 * <p>Listens (radius 8) only for {@code sculk_sensor_tendrils_clicking} caused by a player (directly, riding,
 * by a projectile, or by an item they threw). A shriek: {@code shrieking} for 90 ticks, the shriek particles and
 * sound, and a {@code shriek} game event (heard 32 blocks away). If the shrieker can summon (generated, not
 * peaceful, {@code doWardenSpawning}), the shriek first needs a warning ({@link WardenSpawnTracker}); when it ends,
 * a warning level 1-3 plays a distant warden sound and level 4 summons a warden within 5 blocks across and 6 up
 * or down, and everyone within 40 blocks gets Darkness for 13 seconds.
 *
 * <h2>The warden and Darkness by name</h2>
 * Both belong to stream W: the warden is {@code wildupdatebackport:warden} in the entity registry and Darkness
 * {@code wildupdatebackport:darkness} in the potion registry. Either missing (stream W not built, or switched
 * off) and that part does nothing; a level-4 shriek without a warden plays the angry listening sound instead,
 * as vanilla does when no spot is found. A summoned warden carries {@link #SUMMONED} in its Forge data, so W can
 * play its emerging animation (1.19's {@code MobSpawnType.TRIGGERED}).
 */
public class TileSculkShrieker extends TileEntity implements ITickable, VibrationListener.Config {

    /** Wild Update's own vibrations setting, also while another backport keeps game events running. */
    @Override
    public boolean active() {
        return com.exiledradio.wildupdatebackport.ModConfig.vibrations.enabled;
    }

    public static final ResourceLocation WARDEN = new ResourceLocation(WildUpdateBackport.MOD_ID, "warden");
    public static final ResourceLocation DARKNESS = new ResourceLocation(WildUpdateBackport.MOD_ID, "darkness");
    /** Set to true in a warden's {@code getEntityData()} when a shrieker summoned it. */
    public static final String SUMMONED = "wildupdatebackport:summoned_by_shrieker";

    private static final int LISTENER_RADIUS = 8;
    private static final int WARNING_SOUND_RADIUS = 10;
    private static final int WARDEN_SPAWN_ATTEMPTS = 20;
    private static final int WARDEN_SPAWN_RANGE_XZ = 5;
    private static final int WARDEN_SPAWN_RANGE_Y = 6;
    private static final int DARKNESS_RADIUS = 40;

    private int warningLevel;
    private VibrationListener listener;
    private boolean listening;

    public VibrationListener listener() {
        if (this.listener == null) {
            this.listener = new VibrationListener(new PositionSource.Block(this.pos), LISTENER_RADIUS, this);
        }
        return this.listener;
    }

    public int warningLevel() {
        return this.warningLevel;
    }

    @Override
    public Set<GameEvent> listenableEvents() {
        return GameEventTags.SHRIEKER_CAN_LISTEN;
    }

    @Override
    public boolean shouldListen(WorldServer world, VibrationListener l, BlockPos origin, GameEvent event, GameEventContext context) {
        IBlockState state = world.getBlockState(this.pos);
        return !this.isInvalid() && state.getBlock() instanceof BlockSculkShrieker && !state.getValue(BlockSculkShrieker.SHRIEKING)
                && tryGetPlayer(context.source) != null;
    }

    @Override
    public void onSignalReceive(WorldServer world, VibrationListener l, BlockPos origin, GameEvent event, @Nullable Entity source,
                                @Nullable Entity projectileOwner, float distance) {
        tryShriek(world, tryGetPlayer(projectileOwner != null ? projectileOwner : source));
    }

    @Override
    public void onSignalSchedule() {
        this.markDirty();
    }

    /** 1.19's {@code tryGetPlayer}: the player behind an entity, if any. */
    @Nullable
    public static EntityPlayerMP tryGetPlayer(@Nullable Entity entity) {
        if (entity instanceof EntityPlayerMP) {
            return (EntityPlayerMP) entity;
        }
        if (entity != null && entity.getControllingPassenger() instanceof EntityPlayerMP) {
            return (EntityPlayerMP) entity.getControllingPassenger();
        }
        Entity owner = null;
        if (entity instanceof EntityArrow) {
            owner = ((EntityArrow) entity).shootingEntity;
        } else if (entity instanceof EntityThrowable) {
            owner = ((EntityThrowable) entity).getThrower();
        } else if (entity instanceof EntityFireball) {
            owner = ((EntityFireball) entity).shootingEntity;
        }
        if (owner instanceof EntityPlayerMP) {
            return (EntityPlayerMP) owner;
        }
        if (entity instanceof EntityItem && ((EntityItem) entity).getThrower() != null) {
            EntityPlayer thrower = entity.world.getPlayerEntityByName(((EntityItem) entity).getThrower());
            if (thrower instanceof EntityPlayerMP) {
                return (EntityPlayerMP) thrower;
            }
        }
        return null;
    }

    public void tryShriek(WorldServer world, @Nullable EntityPlayerMP player) {
        if (player == null) {
            return;
        }
        IBlockState state = world.getBlockState(this.pos);
        if (!(state.getBlock() instanceof BlockSculkShrieker) || state.getValue(BlockSculkShrieker.SHRIEKING)) {
            return;
        }
        this.warningLevel = 0;
        if (!canRespond(world, state) || tryToWarn(world, player)) {
            shriek(world, state, player);
        }
    }

    private boolean tryToWarn(WorldServer world, EntityPlayerMP player) {
        OptionalInt level = WardenSpawnTracker.tryWarn(world, this.pos, player);
        if (level.isPresent()) {
            this.warningLevel = level.getAsInt();
            this.markDirty();
        }
        return level.isPresent();
    }

    private void shriek(WorldServer world, IBlockState state, @Nullable Entity source) {
        world.setBlockState(this.pos, state.withProperty(BlockSculkShrieker.SHRIEKING, true), 2);
        world.scheduleUpdate(this.pos, state.getBlock(), BlockSculkShrieker.SHRIEKING_TICKS);
        SculkEffectPacket.send(world, this.pos, Collections.singletonList(new SculkEffectPacket.Entry(SculkEffectPacket.SHRIEK, this.pos, 0)));
        GameEvents.emit(world, GameEvent.SHRIEK, this.pos, GameEventContext.of(source));
    }

    static boolean canRespond(WorldServer world, IBlockState state) {
        return state.getBlock() instanceof BlockSculkShrieker && state.getValue(BlockSculkShrieker.CAN_SUMMON)
                && world.getDifficulty() != EnumDifficulty.PEACEFUL && world.getGameRules().getBoolean(SculkContent.DO_WARDEN_SPAWNING);
    }

    /** At the end of a shriek (or when a shrieking one is broken): the response, and Darkness. */
    public void tryRespond(WorldServer world, IBlockState state) {
        if (canRespond(world, state) && this.warningLevel > 0) {
            if (!trySummonWarden(world)) {
                playWardenReplySound(world);
            }
            applyDarknessAround(world, new Vec3d(this.pos.getX() + 0.5D, this.pos.getY() + 0.5D, this.pos.getZ() + 0.5D), null, DARKNESS_RADIUS);
        }
    }

    private void playWardenReplySound(WorldServer world) {
        SoundEvent sound = SculkContent.wardenReply(this.warningLevel);
        if (sound != null) {
            int x = this.pos.getX() + world.rand.nextInt(2 * WARNING_SOUND_RADIUS + 1) - WARNING_SOUND_RADIUS;
            int y = this.pos.getY() + world.rand.nextInt(2 * WARNING_SOUND_RADIUS + 1) - WARNING_SOUND_RADIUS;
            int z = this.pos.getZ() + world.rand.nextInt(2 * WARNING_SOUND_RADIUS + 1) - WARNING_SOUND_RADIUS;
            world.playSound(null, x, y, z, sound, SoundCategory.HOSTILE, 5.0F, 1.0F);
        }
    }

    /** 1.19's {@code SpawnUtil.trySpawnMob} with {@code ON_TOP_OF_COLLIDER}: 20 tries, 5 across, 6 up and down. */
    private boolean trySummonWarden(WorldServer world) {
        if (this.warningLevel < WardenSpawnTracker.MAX_WARNING_LEVEL || !EntityList.isRegistered(WARDEN)) {
            return false;
        }
        for (int i = 0; i < WARDEN_SPAWN_ATTEMPTS; i++) {
            int dx = world.rand.nextInt(2 * WARDEN_SPAWN_RANGE_XZ + 1) - WARDEN_SPAWN_RANGE_XZ;
            int dz = world.rand.nextInt(2 * WARDEN_SPAWN_RANGE_XZ + 1) - WARDEN_SPAWN_RANGE_XZ;
            BlockPos start = this.pos.add(dx, WARDEN_SPAWN_RANGE_Y, dz);
            if (!world.getWorldBorder().contains(start)) {
                continue;
            }
            BlockPos at = spawnPosition(world, start, WARDEN_SPAWN_RANGE_Y);
            if (at == null) {
                continue;
            }
            Entity e = EntityList.createEntityByIDFromName(WARDEN, world);
            if (!(e instanceof EntityLiving)) {
                return false;
            }
            EntityLiving warden = (EntityLiving) e;
            warden.setLocationAndAngles(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, world.rand.nextFloat() * 360.0F, 0.0F);
            warden.getEntityData().setBoolean(SUMMONED, true);
            warden.onInitialSpawn(world.getDifficultyForLocation(at), null);
            if (world.checkNoEntityCollision(warden.getEntityBoundingBox(), warden)) {
                world.spawnEntity(warden);
                return true;
            }
        }
        return false;
    }

    /** Walks down from {@code start} to the first block whose top is full with nothing solid above it. */
    @Nullable
    public static BlockPos spawnPosition(World world, BlockPos start, int range) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(start);
        IBlockState above = world.getBlockState(p);
        for (int y = range; y >= -range; y--) {
            p.move(EnumFacing.DOWN);
            IBlockState here = world.getBlockState(p);
            BlockPos up = p.up();
            if (above.getCollisionBoundingBox(world, up) == null
                    && here.getBlockFaceShape(world, p, EnumFacing.UP) == BlockFaceShape.SOLID) {
                return up;
            }
            above = here;
        }
        return null;
    }

    /** 1.19's {@code Warden.applyDarknessAround}: Darkness 260 ticks to survival and adventure players in range. */
    public static void applyDarknessAround(WorldServer world, Vec3d at, @Nullable Entity source, int radius) {
        Potion darkness = ForgeRegistries.POTIONS.getValue(DARKNESS);
        if (darkness == null) {
            return;
        }
        for (EntityPlayer player : world.playerEntities) {
            if (!(player instanceof EntityPlayerMP) || ((EntityPlayerMP) player).interactionManager.getGameType().isCreative()
                    || player.isSpectator()) {
                continue;
            }
            if (source != null && source.isOnSameTeam(player)) {
                continue;
            }
            if (player.getPositionVector().squareDistanceTo(at) >= (double) radius * radius) {
                continue;
            }
            PotionEffect current = player.getActivePotionEffect(darkness);
            if (current == null || current.getAmplifier() < 0 || current.getDuration() < 200) {
                player.addPotionEffect(new PotionEffect(darkness, 260, 0, false, false));
            }
        }
    }

    @Override
    public void update() {
        if (this.world.isRemote) {
            return;
        }
        if (!this.listening) {
            GameEvents.add(this.world, listener());
            this.listening = true;
        }
        listener().tick(this.world);
    }

    @Override
    public void onLoad() {
        if (this.world != null && !this.world.isRemote) {
            GameEvents.add(this.world, listener());
            this.listening = true;
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();
        stopListening();
    }

    @Override
    public void onChunkUnload() {
        stopListening();
    }

    private void stopListening() {
        if (this.world != null && !this.world.isRemote && this.listener != null) {
            GameEvents.remove(this.world, this.listener);
        }
        this.listening = false;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        if (tag.hasKey("warning_level", 99)) {
            this.warningLevel = tag.getInteger("warning_level");
        }
        this.listener = null;
        if (tag.hasKey("listener", 10)) {
            listener().readFromNBT(tag.getCompoundTag("listener"));
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setInteger("warning_level", this.warningLevel);
        if (this.listener != null) {
            tag.setTag("listener", this.listener.writeToNBT());
        }
        return tag;
    }

    @Override
    public boolean shouldRefresh(World world, BlockPos pos, IBlockState oldState, IBlockState newState) {
        return oldState.getBlock() != newState.getBlock();
    }
}
