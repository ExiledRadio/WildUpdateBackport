package com.exiledradio.wildupdatebackport.sculk;

import com.exiledradio.backportcore.gameevent.GameEvent;
import com.exiledradio.backportcore.gameevent.GameEventContext;
import com.exiledradio.backportcore.gameevent.GameEvents;
import com.exiledradio.backportcore.gameevent.PositionSource;
import com.exiledradio.backportcore.gameevent.VibrationListener;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import javax.annotation.Nullable;

/**
 * The sculk sensor's tile entity: 1.19.2's {@code SculkSensorBlockEntity}. A vibration listener of radius 8 that
 * hears everything in {@code #vibrations} (except its own block being placed or broken), only while inactive, and
 * lets a sneaking player earn <i>Sneak 100</i>. Also holds the sensor's redstone power and its last vibration's
 * frequency ({@code last_vibration_frequency}).
 */
public class TileSculkSensor extends TileEntity implements ITickable, VibrationListener.Config {

    /** Wild Update's own vibrations setting, also while another backport keeps game events running. */
    @Override
    public boolean active() {
        return com.exiledradio.wildupdatebackport.ModConfig.vibrations.enabled;
    }

    private VibrationListener listener;
    private int lastVibrationFrequency;
    private int power;
    private boolean listening;

    public VibrationListener listener() {
        if (this.listener == null) {
            this.listener = new VibrationListener(new PositionSource.Block(this.pos), BlockSculkSensor.RANGE, this);
        }
        return this.listener;
    }

    public int lastVibrationFrequency() {
        return this.lastVibrationFrequency;
    }

    public void setLastVibrationFrequency(int frequency) {
        this.lastVibrationFrequency = frequency;
        this.markDirty();
    }

    public int power() {
        return this.power;
    }

    public void setPower(int power) {
        this.power = power;
        this.markDirty();
    }

    /** 1.19's {@code getRedstoneStrengthForDistance}: 15 at the sensor, down to 1 at the edge of its range. */
    public static int redstoneStrengthForDistance(float distance, int radius) {
        double d = (double) distance / (double) radius;
        return Math.max(1, 15 - MathHelper.floor(d * 15.0D));
    }

    @Override
    public boolean canTriggerAvoidVibration() {
        return true;
    }

    @Override
    public boolean shouldListen(WorldServer world, VibrationListener l, BlockPos origin, GameEvent event, GameEventContext context) {
        if (this.isInvalid()) {
            return false;
        }
        if (origin.equals(this.pos) && (event == GameEvent.BLOCK_DESTROY || event == GameEvent.BLOCK_PLACE)) {
            return false;
        }
        return BlockSculkSensor.canActivate(world.getBlockState(this.pos));
    }

    @Override
    public void onSignalReceive(WorldServer world, VibrationListener l, BlockPos origin, GameEvent event, @Nullable Entity source,
                                @Nullable Entity projectileOwner, float distance) {
        IBlockState state = world.getBlockState(this.pos);
        if (BlockSculkSensor.canActivate(state)) {
            // Current Java's frequencies (GameEvent.frequency), not 1.19.2's older table.
            this.lastVibrationFrequency = event.frequency();
            BlockSculkSensor.activate(source, world, this.pos, state, redstoneStrengthForDistance(distance, l.radius()));
            this.markDirty();
        }
    }

    @Override
    public void onSignalSchedule() {
        this.markDirty();
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
        this.lastVibrationFrequency = tag.getInteger("last_vibration_frequency");
        this.power = tag.getInteger("power");
        // super has set the position, so the listener is made at the right block.
        this.listener = null;
        if (tag.hasKey("listener", 10)) {
            listener().readFromNBT(tag.getCompoundTag("listener"));
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setInteger("last_vibration_frequency", this.lastVibrationFrequency);
        tag.setInteger("power", this.power);
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
