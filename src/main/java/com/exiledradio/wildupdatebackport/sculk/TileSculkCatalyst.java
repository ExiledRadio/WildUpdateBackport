package com.exiledradio.wildupdatebackport.sculk;

import com.exiledradio.wildupdatebackport.gameevent.GameEvent;
import com.exiledradio.wildupdatebackport.gameevent.GameEventContext;
import com.exiledradio.wildupdatebackport.gameevent.GameEventListener;
import com.exiledradio.wildupdatebackport.gameevent.GameEvents;
import com.exiledradio.wildupdatebackport.gameevent.PositionSource;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * The sculk catalyst's tile entity: 1.19.2's {@code SculkCatalystBlockEntity}. An immediate game-event listener
 * (radius 8) that hears {@code entity_die}: a living thing that has experience to give (and is not a baby) gives
 * it all to a new charge at its feet instead of dropping it (see {@link SculkExperience}), the player who last hurt
 * it earns <i>It Spreads</i>, and the catalyst blooms. Every tick the charges move ({@link SculkSpreader}).
 */
public class TileSculkCatalyst extends TileEntity implements ITickable, GameEventListener {

    private final SculkSpreader spreader = SculkSpreader.createLevelSpreader();
    private PositionSource source;
    private boolean listening;

    public SculkSpreader spreader() {
        return this.spreader;
    }

    @Override
    public PositionSource position() {
        if (this.source == null) {
            this.source = new PositionSource.Block(this.pos);
        }
        return this.source;
    }

    @Override
    public int radius() {
        return 8;
    }

    @Override
    public boolean immediate() {
        return true;
    }

    @Override
    public boolean handle(WorldServer world, GameEvent event, Vec3d origin, GameEventContext context) {
        if (this.isInvalid() || event != GameEvent.ENTITY_DIE || !(context.source instanceof EntityLivingBase)) {
            return false;
        }
        EntityLivingBase dying = (EntityLivingBase) context.source;
        if (!SculkExperience.consumed(dying)) {
            int xp = SculkExperience.reward(dying);
            if (SculkExperience.shouldDrop(dying) && xp > 0) {
                this.spreader.addCursors(new BlockPos(origin.add(0.0D, 0.5D, 0.0D)), xp);
                if (dying.getRevengeTarget() instanceof EntityPlayerMP) {
                    KillMobNearCatalystTrigger.INSTANCE.trigger((EntityPlayerMP) dying.getRevengeTarget());
                }
            }
            SculkExperience.consume(dying);
            IBlockState state = world.getBlockState(this.pos);
            if (state.getBlock() instanceof BlockSculkCatalyst) {
                BlockSculkCatalyst.bloom(world, this.pos, state, world.rand);
            }
            this.markDirty();
        }
        return true;
    }

    @Override
    public void update() {
        if (this.world.isRemote) {
            return;
        }
        if (!this.listening) {
            GameEvents.add(this.world, this);
            this.listening = true;
        }
        if (!this.spreader.getCursors().isEmpty()) {
            this.spreader.updateCursors(this.world, this.pos, this.world.rand, true);
            this.markDirty();
        }
    }

    @Override
    public void onLoad() {
        if (this.world != null && !this.world.isRemote) {
            GameEvents.add(this.world, this);
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
        if (this.world != null && !this.world.isRemote) {
            GameEvents.remove(this.world, this);
        }
        this.listening = false;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        this.spreader.load(tag);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        this.spreader.save(tag);
        return super.writeToNBT(tag);
    }

    @Override
    public boolean shouldRefresh(World world, BlockPos pos, IBlockState oldState, IBlockState newState) {
        return oldState.getBlock() != newState.getBlock();
    }
}
