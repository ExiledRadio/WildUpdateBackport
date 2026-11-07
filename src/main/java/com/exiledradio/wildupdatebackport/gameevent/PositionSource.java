package com.exiledradio.wildupdatebackport.gameevent;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import javax.annotation.Nullable;

/** Where a listener is: a block's center or a point on an entity. 1.19's {@code PositionSource}. */
public interface PositionSource {

    /** @return the position now, or null when it is gone (an unloaded or dead entity) */
    @Nullable
    Vec3d position(World world);

    /** For the vibration particle: written to its packet so the client can follow the target. */
    void write(NBTTagCompound tag);

    /** A block's center. */
    final class Block implements PositionSource {
        public final BlockPos pos;

        public Block(BlockPos pos) {
            this.pos = pos.toImmutable();
        }

        @Override
        public Vec3d position(World world) {
            return new Vec3d(this.pos.getX() + 0.5D, this.pos.getY() + 0.5D, this.pos.getZ() + 0.5D);
        }

        @Override
        public void write(NBTTagCompound tag) {
            tag.setString("type", "block");
            tag.setInteger("x", this.pos.getX());
            tag.setInteger("y", this.pos.getY());
            tag.setInteger("z", this.pos.getZ());
        }
    }

    /** A point {@code yOffset} above an entity's feet. */
    final class EntitySource implements PositionSource {
        public final Entity entity;
        public final float yOffset;

        public EntitySource(Entity entity, float yOffset) {
            this.entity = entity;
            this.yOffset = yOffset;
        }

        @Override
        @Nullable
        public Vec3d position(World world) {
            if (this.entity.isDead || this.entity.world != world) {
                return null;
            }
            return new Vec3d(this.entity.posX, this.entity.posY + this.yOffset, this.entity.posZ);
        }

        @Override
        public void write(NBTTagCompound tag) {
            tag.setString("type", "entity");
            tag.setInteger("id", this.entity.getEntityId());
            tag.setFloat("y", this.yOffset);
        }
    }
}
