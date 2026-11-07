package com.exiledradio.wildupdatebackport.gameevent;

import com.exiledradio.wildupdatebackport.WildUpdateBackport;

import net.minecraft.block.Block;
import net.minecraft.block.BlockButton;
import net.minecraft.block.BlockDirectional;
import net.minecraft.block.BlockPistonBase;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.BlockFenceGate;
import net.minecraft.block.BlockJukebox;
import net.minecraft.block.BlockLever;
import net.minecraft.block.BlockTrapDoor;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityFlying;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IProjectile;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.item.EntityTNTPrimed;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.passive.EntityBat;
import net.minecraft.entity.passive.EntityParrot;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.init.Items;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemArmorStand;
import net.minecraft.item.ItemBoat;
import net.minecraft.item.ItemEndCrystal;
import net.minecraft.item.ItemHangingEntity;
import net.minecraft.item.ItemMinecart;
import net.minecraft.item.ItemMonsterPlacer;
import net.minecraft.item.ItemShears;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.IShearable;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.EnderTeleportEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ExplosionEvent;
import net.minecraftforge.event.world.NoteBlockEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Emits 1.19's game events from what 1.12.2 tells mods. 1.12.2 has no game events, so each one comes from the
 * nearest hook:
 *
 * <table>
 *   <tr><th>Game event</th><th>From</th></tr>
 *   <tr><td>step, swim, flap</td><td>each living entity's update, when its walked distance passes the next whole block
 *       (vanilla's own step-sound rule): in water a swim, flying mobs a flap; the stepped-on block is the context, so
 *       wool and carpets damp steps</td></tr>
 *   <tr><td>splash</td><td>a living entity entering water</td></tr>
 *   <tr><td>elytra_glide</td><td>every 10 ticks of elytra flight</td></tr>
 *   <tr><td>hit_ground</td><td>{@code LivingFallEvent}</td></tr>
 *   <tr><td>block_place / block_destroy</td><td>Forge's place and break events (player-made)</td></tr>
 *   <tr><td>block_open/close, block_activate/deactivate, block_change, jukebox_play/stop, entity_place</td>
 *       <td>a player's right-click: the block is compared with itself at the end of the tick (doors, trapdoors and
 *       gates open or close, buttons and levers activate, anything else changing is a block change); a held boat,
 *       minecart, armor stand, painting, crystal or spawn egg that was used up placed an entity</td></tr>
 *   <tr><td>container_open/close</td><td>Forge's container events, at the clicked block or the player</td></tr>
 *   <tr><td>eat, drink, item_interact_start/finish</td><td>Forge's use-item events</td></tr>
 *   <tr><td>projectile_shoot / projectile_land</td><td>a new arrow or thrown thing with an owner joining the world;
 *       {@code ProjectileImpactEvent}</td></tr>
 *   <tr><td>entity_damage / entity_die</td><td>{@code LivingDamageEvent} (context: the attacker) / {@code LivingDeathEvent}</td></tr>
 *   <tr><td>explode, note_block_play, teleport, fluid_pickup/place, shear, entity_interact,
 *       equip, prime_fuse, lightning_strike</td><td>the matching Forge events; a new primed TNT, a creeper lighting;
 *       new lightning bolts in the world's weather list</td></tr>
 *   <tr><td>piston_extend / piston_contract</td><td>neighbor notifications around piston bases</td></tr>
 * </table>
 *
 * <p>Not emitted (no hook without patches; see {@code docs/streams/vibrations.md}): blocks changed by redstone or
 * other non-player causes, dispense_fail, entity_shake, entity_roar for vanilla mobs, block attach/detach.
 */
@Mod.EventBusSubscriber(modid = WildUpdateBackport.MOD_ID)
public final class GameEventEmitters {

    /** Per living entity: the whole blocks walked so far, and whether it was in water, as of last tick. */
    private static final Map<Entity, int[]> WALKED = new WeakHashMap<Entity, int[]>();

    /** Right-clicked blocks this tick, compared at the tick's end. */
    private static final List<Click> CLICKS = new ArrayList<Click>();

    /** Per player: the last block they right-clicked, for container events. */
    private static final Map<EntityPlayer, BlockPos> LAST_CLICK = new WeakHashMap<EntityPlayer, BlockPos>();

    /** Pistons that moved this tick. */
    private static final Set<BlockPos> PISTONS = new HashSet<BlockPos>();

    /** Creepers that have started to swell. */
    private static final Set<Entity> SWELLING = Collections.newSetFromMap(new WeakHashMap<Entity, Boolean>());

    /** Lightning bolts already emitted. */
    private static final Set<Entity> BOLTS = Collections.newSetFromMap(new WeakHashMap<Entity, Boolean>());

    private GameEventEmitters() {
    }

    private static final class Click {
        final World world;
        final EntityPlayer player;
        final BlockPos pos;
        final IBlockState before;
        final ItemStack held;
        final int heldCount;

        Click(World world, EntityPlayer player, BlockPos pos, IBlockState before, ItemStack held) {
            this.world = world;
            this.player = player;
            this.pos = pos;
            this.before = before;
            this.held = held;
            this.heldCount = held.getCount();
        }
    }

    private static void emit(World world, GameEvent event, Vec3d pos, Entity source, IBlockState state) {
        GameEvents.emit(world, event, pos, GameEventContext.of(source, state));
    }

    private static Vec3d at(Entity e) {
        return new Vec3d(e.posX, e.posY, e.posZ);
    }

    // --- movement ----------------------------------------------------------------------------

    @SubscribeEvent
    public static void livingUpdate(LivingEvent.LivingUpdateEvent event) {
        EntityLivingBase e = event.getEntityLiving();
        World world = e.world;
        if (world.isRemote) {
            return;
        }
        int walked = (int) e.distanceWalkedOnStepModified;
        int[] last = WALKED.get(e);
        if (last == null) {
            WALKED.put(e, new int[] {walked, e.isInWater() ? 1 : 0});
            return;
        }
        boolean inWater = e.isInWater();
        if (inWater && last[1] == 0 && e.ticksExisted > 1) {
            emit(world, GameEvent.SPLASH, at(e), e, null);
        }
        last[1] = inWater ? 1 : 0;
        if (walked > last[0]) {
            last[0] = walked;
            if (!e.isRiding()) {
                if (e instanceof EntityFlying || e instanceof EntityBat || (e instanceof EntityParrot && !e.onGround)) {
                    emit(world, GameEvent.FLAP, at(e), e, null);
                } else if (inWater) {
                    emit(world, GameEvent.SWIM, at(e), e, null);
                } else if (e.onGround) {
                    BlockPos below = new BlockPos(e.posX, e.posY - 0.2D, e.posZ);
                    IBlockState ground = world.getBlockState(below);
                    if (ground.getMaterial() != Material.AIR) {
                        emit(world, GameEvent.STEP, at(e), e, ground);
                    }
                }
            }
        } else if (walked < last[0]) {
            last[0] = walked;
        }
        if (e.isElytraFlying() && e.getTicksElytraFlying() % 10 == 0) {
            emit(world, GameEvent.ELYTRA_GLIDE, at(e), e, null);
        }
        if (e instanceof EntityCreeper) {
            boolean swelling = ((EntityCreeper) e).getCreeperState() > 0;
            if (swelling && SWELLING.add(e)) {
                emit(world, GameEvent.PRIME_FUSE, at(e), e, null);
            } else if (!swelling) {
                SWELLING.remove(e);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void fall(LivingFallEvent event) {
        EntityLivingBase e = event.getEntityLiving();
        if (!event.isCanceled() && event.getDistance() > 0.0F && !e.world.isRemote) {
            emit(e.world, GameEvent.HIT_GROUND, at(e), e, null);
        }
    }

    // --- blocks ------------------------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void place(BlockEvent.PlaceEvent event) {
        if (!event.isCanceled()) {
            GameEvents.emit(event.getWorld(), GameEvent.BLOCK_PLACE, event.getPos(), GameEventContext.of(event.getPlayer(), event.getPlacedBlock()));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void breakBlock(BlockEvent.BreakEvent event) {
        if (!event.isCanceled()) {
            GameEvents.emit(event.getWorld(), GameEvent.BLOCK_DESTROY, event.getPos(), GameEventContext.of(event.getPlayer(), event.getState()));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void rightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        World world = event.getWorld();
        if (world.isRemote || event.isCanceled()) {
            return;
        }
        BlockPos pos = event.getPos();
        CLICKS.add(new Click(world, event.getEntityPlayer(), pos, world.getBlockState(pos), event.getItemStack()));
        LAST_CLICK.put(event.getEntityPlayer(), pos);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void rightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!event.getWorld().isRemote && !event.isCanceled() && placesEntity(event.getItemStack())) {
            EntityPlayer p = event.getEntityPlayer();
            CLICKS.add(new Click(event.getWorld(), p, new BlockPos(p), event.getWorld().getBlockState(new BlockPos(p)), event.getItemStack()));
        }
    }

    private static boolean placesEntity(ItemStack stack) {
        return !stack.isEmpty() && (stack.getItem() instanceof ItemBoat || stack.getItem() instanceof ItemMinecart
                || stack.getItem() instanceof ItemArmorStand || stack.getItem() instanceof ItemHangingEntity
                || stack.getItem() instanceof ItemEndCrystal || stack.getItem() instanceof ItemMonsterPlacer
                || stack.getItem().getClass().getName().startsWith("com.exiledradio.wildupdatebackport.boat."));
    }

    /** Compares the tick's right-clicked blocks with themselves, then flushes nothing else. */
    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        PISTONS.clear();
        if (CLICKS.isEmpty()) {
            return;
        }
        List<Click> clicks = new ArrayList<Click>(CLICKS);
        CLICKS.clear();
        Set<BlockPos> done = new HashSet<BlockPos>();
        for (Click c : clicks) {
            IBlockState now = c.world.getBlockState(c.pos);
            boolean used = c.held.getCount() < c.heldCount;
            if (placesEntity(c.held) && (used || c.player.capabilities.isCreativeMode)) {
                emit(c.world, GameEvent.ENTITY_PLACE, new Vec3d(c.pos).add(0.5D, 1.0D, 0.5D), c.player, null);
                continue;
            }
            if (now == c.before || !done.add(c.pos)) {
                continue;
            }
            if (now.getBlock() != c.before.getBlock()) {
                continue; // placed or broken: Forge's own events covered it
            }
            GameEvent event2 = changeEvent(c.before, now);
            if (event2 != null) {
                GameEvents.emit(c.world, event2, c.pos, GameEventContext.of(c.player, now));
            }
        }
    }

    /** @return what a player's right-click that changed a block's state is, as 1.19 names it */
    static GameEvent changeEvent(IBlockState before, IBlockState now) {
        Block block = now.getBlock();
        if (block instanceof BlockDoor || block instanceof BlockTrapDoor || block instanceof BlockFenceGate) {
            Boolean open = bool(now, "open");
            if (open != null && !open.equals(bool(before, "open"))) {
                return open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE;
            }
        }
        if (block instanceof BlockButton || block instanceof BlockLever) {
            Boolean powered = bool(now, "powered");
            if (powered != null && !powered.equals(bool(before, "powered"))) {
                return powered ? GameEvent.BLOCK_ACTIVATE : GameEvent.BLOCK_DEACTIVATE;
            }
        }
        if (block instanceof BlockJukebox) {
            return now.getValue(BlockJukebox.HAS_RECORD) ? GameEvent.JUKEBOX_PLAY : GameEvent.JUKEBOX_STOP_PLAY;
        }
        return GameEvent.BLOCK_CHANGE;
    }

    private static Boolean bool(IBlockState state, String name) {
        for (IProperty<?> p : state.getPropertyKeys()) {
            if (p instanceof PropertyBool && p.getName().equals(name)) {
                return state.getValue((PropertyBool) p);
            }
        }
        return null;
    }

    @SubscribeEvent
    public static void containerOpen(PlayerContainerEvent.Open event) {
        EntityPlayer p = event.getEntityPlayer();
        if (!p.world.isRemote) {
            containerEvent(p, GameEvent.CONTAINER_OPEN);
        }
    }

    @SubscribeEvent
    public static void containerClose(PlayerContainerEvent.Close event) {
        EntityPlayer p = event.getEntityPlayer();
        if (!p.world.isRemote && event.getContainer() != p.inventoryContainer) {
            containerEvent(p, GameEvent.CONTAINER_CLOSE);
        }
    }

    private static void containerEvent(EntityPlayer p, GameEvent event) {
        BlockPos clicked = LAST_CLICK.get(p);
        TileEntity tile = clicked != null && clicked.distanceSq(p.getPosition()) < 100 ? p.world.getTileEntity(clicked) : null;
        if (tile != null) {
            GameEvents.emit(p.world, event, clicked, GameEventContext.of(p, p.world.getBlockState(clicked)));
        } else {
            emit(p.world, event, at(p), p, null);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void note(NoteBlockEvent.Play event) {
        if (!event.isCanceled()) {
            GameEvents.emit(event.getWorld(), GameEvent.NOTE_BLOCK_PLAY, event.getPos(), GameEventContext.NONE);
        }
    }

    /**
     * Pistons (Forge 14.23.5.2847 has no piston event): a piston base whose own state change notifies its
     * neighbors with {@code extended=true} has extended; a block next to a retracted piston that faces it
     * changing (the head going) means it contracted. Each piston once per tick.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void neighborNotify(BlockEvent.NeighborNotifyEvent event) {
        World world = event.getWorld();
        if (world.isRemote || event.isCanceled()) {
            return;
        }
        BlockPos pos = event.getPos();
        IBlockState state = event.getState();
        if (state.getBlock() instanceof BlockPistonBase) {
            if (state.getValue(BlockPistonBase.EXTENDED) && PISTONS.add(pos.toImmutable())) {
                GameEvents.emit(world, GameEvent.PISTON_EXTEND, pos, GameEventContext.NONE);
            }
            return;
        }
        for (EnumFacing f : EnumFacing.values()) {
            BlockPos base = pos.offset(f);
            IBlockState s = world.getBlockState(base);
            if (s.getBlock() instanceof BlockPistonBase && s.getValue(BlockDirectional.FACING) == f.getOpposite()
                    && !s.getValue(BlockPistonBase.EXTENDED) && PISTONS.add(base.toImmutable())) {
                GameEvents.emit(world, GameEvent.PISTON_CONTRACT, base, GameEventContext.NONE);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void explode(ExplosionEvent.Detonate event) {
        Vec3d p = event.getExplosion().getPosition();
        GameEvents.emit(event.getWorld(), GameEvent.EXPLODE, p, GameEventContext.of(event.getExplosion().getExplosivePlacedBy()));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void bucket(FillBucketEvent event) {
        RayTraceResult hit = event.getTarget();
        if (event.isCanceled() || hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK || event.getWorld().isRemote) {
            return;
        }
        boolean empty = event.getEmptyBucket().getItem() == Items.BUCKET;
        BlockPos pos = empty ? hit.getBlockPos() : hit.getBlockPos().offset(hit.sideHit);
        GameEvents.emit(event.getWorld(), empty ? GameEvent.FLUID_PICKUP : GameEvent.FLUID_PLACE, pos, GameEventContext.of(event.getEntityPlayer()));
    }

    // --- entities ----------------------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void interactEntity(PlayerInteractEvent.EntityInteract event) {
        if (event.isCanceled() || event.getWorld().isRemote) {
            return;
        }
        Entity target = event.getTarget();
        ItemStack held = event.getItemStack();
        if (held.getItem() instanceof ItemShears && target instanceof IShearable
                && ((IShearable) target).isShearable(held, event.getWorld(), new BlockPos(target))) {
            emit(event.getWorld(), GameEvent.SHEAR, at(target), event.getEntityPlayer(), null);
        } else {
            emit(event.getWorld(), GameEvent.ENTITY_INTERACT, at(target), event.getEntityPlayer(), null);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void damage(LivingDamageEvent event) {
        EntityLivingBase e = event.getEntityLiving();
        if (!event.isCanceled() && event.getAmount() > 0.0F && !e.world.isRemote) {
            emit(e.world, GameEvent.ENTITY_DAMAGE, at(e), event.getSource().getTrueSource(), null);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void death(LivingDeathEvent event) {
        EntityLivingBase e = event.getEntityLiving();
        if (!event.isCanceled() && !e.world.isRemote) {
            emit(e.world, GameEvent.ENTITY_DIE, at(e), e, null);
        }
    }

    @SubscribeEvent
    public static void useStart(LivingEntityUseItemEvent.Start event) {
        EntityLivingBase e = event.getEntityLiving();
        if (!e.world.isRemote) {
            emit(e.world, GameEvent.ITEM_INTERACT_START, at(e), e, null);
        }
    }

    @SubscribeEvent
    public static void useFinish(LivingEntityUseItemEvent.Finish event) {
        EntityLivingBase e = event.getEntityLiving();
        if (e.world.isRemote) {
            return;
        }
        EnumAction action = event.getItem().getItemUseAction();
        if (action == EnumAction.EAT) {
            emit(e.world, GameEvent.EAT, at(e), e, null);
        } else if (action == EnumAction.DRINK) {
            emit(e.world, GameEvent.DRINK, at(e), e, null);
        }
        emit(e.world, GameEvent.ITEM_INTERACT_FINISH, at(e), e, null);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void equip(LivingEquipmentChangeEvent event) {
        EntityLivingBase e = event.getEntityLiving();
        if (e.world.isRemote || e.ticksExisted < 2 || event.getSlot().getSlotType() != EntityEquipmentSlot.Type.ARMOR
                || event.getTo().isEmpty()) {
            return;
        }
        emit(e.world, GameEvent.EQUIP, at(e), e, null);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void teleport(EnderTeleportEvent event) {
        EntityLivingBase e = event.getEntityLiving();
        if (!event.isCanceled() && !e.world.isRemote) {
            emit(e.world, GameEvent.TELEPORT, at(e), e, null);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void join(EntityJoinWorldEvent event) {
        Entity e = event.getEntity();
        World world = event.getWorld();
        if (event.isCanceled() || world.isRemote || e.ticksExisted > 0) {
            return;
        }
        if (e instanceof IProjectile) {
            Entity owner = e instanceof EntityArrow ? ((EntityArrow) e).shootingEntity
                    : e instanceof EntityThrowable ? ((EntityThrowable) e).getThrower() : null;
            if (owner != null) {
                emit(world, GameEvent.PROJECTILE_SHOOT, at(e), owner, null);
            }
        } else if (e instanceof EntityTNTPrimed && ((EntityTNTPrimed) e).getFuse() >= 79) {
            emit(world, GameEvent.PRIME_FUSE, at(e), ((EntityTNTPrimed) e).getTntPlacedBy(), null);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void impact(ProjectileImpactEvent event) {
        Entity e = event.getEntity();
        if (!event.isCanceled() && !e.world.isRemote && event.getRayTraceResult() != null) {
            Vec3d hit = event.getRayTraceResult().hitVec;
            emit(e.world, GameEvent.PROJECTILE_LAND, hit != null ? hit : at(e), e, null);
        }
    }

    @SubscribeEvent
    public static void worldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !(event.world instanceof WorldServer) || event.world.weatherEffects.isEmpty()) {
            return;
        }
        for (Entity bolt : event.world.weatherEffects) {
            if (bolt instanceof EntityLightningBolt && BOLTS.add(bolt)) {
                emit(event.world, GameEvent.LIGHTNING_STRIKE, at(bolt), null, null);
            }
        }
    }
}
