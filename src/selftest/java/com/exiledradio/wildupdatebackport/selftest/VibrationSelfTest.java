package com.exiledradio.wildupdatebackport.selftest;

import com.exiledradio.wildupdatebackport.ModConfig;
import com.exiledradio.backportcore.gameevent.GameEvent;
import com.exiledradio.backportcore.gameevent.GameEventContext;
import com.exiledradio.backportcore.gameevent.GameEventEmitters;
import com.exiledradio.backportcore.gameevent.GameEvents;
import com.exiledradio.backportcore.gameevent.PositionSource;
import com.exiledradio.backportcore.gameevent.VibrationListener;

import net.minecraft.block.BlockDoor;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/** Stream G: game events and vibrations. */
public final class VibrationSelfTest implements SelfTest {

    @Override
    public String name() {
        return "vibrations";
    }

    /** A listener that records what reaches it. */
    static final class Recorder implements VibrationListener.Config {
        final List<GameEvent> heard = new ArrayList<GameEvent>();
        final List<Float> distances = new ArrayList<Float>();
        Entity lastSource;

        @Override
        public boolean shouldListen(WorldServer world, VibrationListener l, BlockPos origin, GameEvent event, GameEventContext context) {
            return true;
        }

        @Override
        public void onSignalReceive(WorldServer world, VibrationListener l, BlockPos origin, GameEvent event, @Nullable Entity source,
                                    @Nullable Entity owner, float distance) {
            this.heard.add(event);
            this.distances.add(distance);
            this.lastSource = source;
        }
    }

    /** Hands out the tick's messages and lets the vibration arrive. */
    private static void deliver(WorldServer world, VibrationListener l) {
        GameEvents.flush(world);
        for (int i = 0; i < 40; i++) {
            l.tick(world);
        }
    }

    @Override
    public void run(Check c) {
        WorldServer world = c.world;
        BlockPos area = c.area().add(16, 0, 16);
        Recorder rec = new Recorder();
        BlockPos at = area.add(0, 1, 0);
        VibrationListener listener = new VibrationListener(new PositionSource.Block(at), 8, rec);
        GameEvents.flush(world);
        GameEvents.add(world, listener);
        c.ok(GameEvents.listenerCount(world) >= 1, "a listener is registered");

        c.eq(1, GameEvent.STEP.frequency(), "step's frequency is 1");
        c.eq(15, GameEvent.EXPLODE.frequency(), "explode's frequency is 15");
        c.eq(32, GameEvent.SHRIEK.radius(), "the shriek carries 32 blocks");

        // 1. closest first, travel time, one at a time
        GameEvents.emit(world, GameEvent.BLOCK_CHANGE, at.add(6, 0, 0), GameEventContext.NONE);
        GameEvents.emit(world, GameEvent.EAT, at.add(3, 0, 0), GameEventContext.NONE);
        GameEvents.flush(world);
        c.ok(listener.isReceiving(), "a vibration is on its way");
        for (int i = 0; i < 2; i++) {
            listener.tick(world);
        }
        c.eq(0, rec.heard.size(), "it has not arrived after 2 ticks (3 blocks away)");
        listener.tick(world);
        c.eq(1, rec.heard.size(), "it arrives after 3 ticks");
        c.eq(GameEvent.EAT, rec.heard.isEmpty() ? null : rec.heard.get(0), "the closer of two vibrations in a tick wins");
        deliver(world, listener);
        c.eq(1, rec.heard.size(), "the farther one is not heard later");

        // 2. radius, sneaking, dampening, occlusion, and vibrations switched off
        rec.heard.clear();
        GameEvents.emit(world, GameEvent.EAT, at.add(10, 0, 0), GameEventContext.NONE);
        deliver(world, listener);
        c.eq(0, rec.heard.size(), "nothing heard beyond the listener's 8 blocks");
        GameEvents.emit(world, GameEvent.BLOCK_PLACE, at.add(2, 0, 0), GameEventContext.of(Blocks.WOOL.getDefaultState()));
        deliver(world, listener);
        c.eq(0, rec.heard.size(), "placing wool makes no vibration");
        EntityPlayerMP sneaker = Players.real(world, "sneaker");
        sneaker.setPosition(at.getX() + 2.5, at.getY(), at.getZ() + 0.5);
        sneaker.setSneaking(true);
        GameEvents.emit(world, GameEvent.STEP, new Vec3d(sneaker.posX, sneaker.posY, sneaker.posZ), GameEventContext.of(sneaker));
        deliver(world, listener);
        c.eq(0, rec.heard.size(), "a sneaking player's step is not heard");
        GameEvents.emit(world, GameEvent.BLOCK_PLACE, new Vec3d(sneaker.posX, sneaker.posY, sneaker.posZ), GameEventContext.of(sneaker));
        deliver(world, listener);
        c.eq(1, rec.heard.size(), "a sneaking player placing a block is heard");
        rec.heard.clear();
        BlockPos wallFrom = at.add(4, 0, 0);
        c.fill(at.add(2, -2, -2), at.add(2, 2, 2), Blocks.WOOL.getDefaultState());
        GameEvents.emit(world, GameEvent.EAT, wallFrom, GameEventContext.NONE);
        deliver(world, listener);
        c.eq(0, rec.heard.size(), "a wool wall between occludes the vibration");
        c.fill(at.add(2, -2, -2), at.add(2, 2, 2), Blocks.AIR.getDefaultState());
        ModConfig.vibrations.enabled = false;
        GameEvents.emit(world, GameEvent.EAT, at.add(2, 0, 0), GameEventContext.NONE);
        deliver(world, listener);
        ModConfig.vibrations.enabled = true;
        c.eq(0, rec.heard.size(), "with vibrations off nothing is heard");

        // 3. saving a vibration in flight
        GameEvents.emit(world, GameEvent.EXPLODE, at.add(5, 0, 0), GameEventContext.NONE);
        GameEvents.flush(world);
        NBTTagCompound tag = listener.writeToNBT();
        Recorder rec2 = new Recorder();
        VibrationListener copy = new VibrationListener(new PositionSource.Block(at), 8, rec2);
        copy.readFromNBT(tag);
        c.ok(copy.isReceiving(), "a vibration in flight is saved and read back");
        for (int i = 0; i < 10; i++) {
            copy.tick(world);
            listener.tick(world);
        }
        c.eq(GameEvent.EXPLODE, rec2.heard.isEmpty() ? null : rec2.heard.get(0), "and still arrives");
        rec.heard.clear();

        // 4. emitters: steps, death, block place, doors, containers
        EntityPig pig = new EntityPig(world);
        pig.setPosition(at.getX() + 3.5, at.getY() - 1 + 0.0, at.getZ() + 0.5);
        world.setBlockState(at.add(3, -2, 0), Blocks.STONE.getDefaultState());
        world.spawnEntity(pig);
        pig.onGround = true;
        MinecraftForge.EVENT_BUS.post(new LivingEvent.LivingUpdateEvent(pig));
        pig.distanceWalkedOnStepModified += 1.2F;
        MinecraftForge.EVENT_BUS.post(new LivingEvent.LivingUpdateEvent(pig));
        deliver(world, listener);
        c.eq(GameEvent.STEP, rec.heard.isEmpty() ? null : rec.heard.get(0), "a pig walking a block makes a step");
        c.ok(rec.lastSource == pig, "the step's source is the pig");
        rec.heard.clear();
        world.setBlockState(at.add(3, -2, 0), Blocks.WOOL.getDefaultState());
        pig.distanceWalkedOnStepModified += 1.2F;
        MinecraftForge.EVENT_BUS.post(new LivingEvent.LivingUpdateEvent(pig));
        deliver(world, listener);
        c.eq(0, rec.heard.size(), "a step on wool is not heard");
        pig.attackEntityFrom(DamageSource.GENERIC, 100.0F);
        deliver(world, listener);
        c.ok(rec.heard.contains(GameEvent.ENTITY_DAMAGE) || rec.heard.contains(GameEvent.ENTITY_DIE), "killing a pig is heard: " + rec.heard);
        rec.heard.clear();
        deliver(world, listener);
        rec.heard.clear();

        EntityPlayerMP player = Players.real(world, "vibrations");
        player.interactionManager.setGameType(GameType.SURVIVAL);
        player.setPosition(at.getX() + 4.5, at.getY(), at.getZ() + 2.5);
        BlockPos floor = at.add(4, -2, 4);
        world.setBlockState(floor, Blocks.STONE.getDefaultState());
        ItemStack stone = new ItemStack(Blocks.STONE);
        player.setHeldItem(EnumHand.MAIN_HAND, stone);
        player.interactionManager.processRightClickBlock(player, world, stone, EnumHand.MAIN_HAND, floor, EnumFacing.UP, 0.5F, 1.0F, 0.5F);
        endTick();
        deliver(world, listener);
        c.eq(GameEvent.BLOCK_PLACE, rec.heard.isEmpty() ? null : rec.heard.get(0), "a player placing a block is heard");
        rec.heard.clear();

        BlockPos doorAt = at.add(-3, -1, 0);
        world.setBlockState(doorAt.down(), Blocks.STONE.getDefaultState());
        world.setBlockState(doorAt, Blocks.OAK_DOOR.getDefaultState().withProperty(BlockDoor.HALF, BlockDoor.EnumDoorHalf.LOWER), 2);
        world.setBlockState(doorAt.up(), Blocks.OAK_DOOR.getDefaultState().withProperty(BlockDoor.HALF, BlockDoor.EnumDoorHalf.UPPER), 2);
        player.setHeldItem(EnumHand.MAIN_HAND, ItemStack.EMPTY);
        player.interactionManager.processRightClickBlock(player, world, ItemStack.EMPTY, EnumHand.MAIN_HAND, doorAt, EnumFacing.EAST, 0.5F, 0.5F, 0.5F);
        c.ok(world.getBlockState(doorAt).getValue(BlockDoor.OPEN), "the door opened");
        endTick();
        deliver(world, listener);
        c.eq(GameEvent.BLOCK_OPEN, rec.heard.isEmpty() ? null : rec.heard.get(0), "opening a door is a block_open");
        rec.heard.clear();

        BlockPos chestAt = at.add(0, -1, 3);
        world.setBlockState(chestAt, Blocks.CHEST.getDefaultState());
        player.interactionManager.processRightClickBlock(player, world, ItemStack.EMPTY, EnumHand.MAIN_HAND, chestAt, EnumFacing.UP, 0.5F, 1.0F, 0.5F);
        c.ok(player.openContainer instanceof ContainerChest, "the chest opened");
        endTick();
        deliver(world, listener);
        c.eq(GameEvent.CONTAINER_OPEN, rec.heard.isEmpty() ? null : rec.heard.get(0), "opening a chest is a container_open");
        player.closeScreen();
        rec.heard.clear();
        deliver(world, listener);
        rec.heard.clear();

        GameEvents.remove(world, listener);
        GameEvents.emit(world, GameEvent.EAT, at.add(1, 0, 0), GameEventContext.NONE);
        deliver(world, listener);
        c.eq(0, rec.heard.size(), "a removed listener hears nothing");
        c.ok(Items.APPLE != null, "done");
    }

    private static void endTick() {
        GameEventEmitters.serverTick(new TickEvent.ServerTickEvent(TickEvent.Phase.END));
    }
}
