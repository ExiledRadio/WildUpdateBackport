package com.exiledradio.wildupdatebackport.mangrove;

import com.exiledradio.backportcore.waterlog.Waterloggable;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRotatedPillar;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Mangrove roots and muddy mangrove roots.
 *
 * <h2>Mangrove roots</h2>
 * 1.19: wood material (axe), hardness and resistance 0.7, podzol map color, see-through like leaves
 * (cutout), never suffocating or blocking sight, and two roots stacked hide the faces between them
 * (vertically only). They still conduct redstone and hold blocks as a full block, as 1.19's do.
 * They hold water ({@link Waterloggable}, with Fluidlogged API), and a tree grown in water keeps it around them.
 * The destroy crack covers every layer of the model, as 1.19's does ({@link RootsCrack}).
 *
 * <h2>Muddy mangrove roots</h2>
 * A rotated pillar of dirt material (shovel), hardness 0.7: mud and roots, crafted shapeless.
 */
public final class BlockMangroveRoots {

    private BlockMangroveRoots() {
    }

    /** {@code mangrove_roots}. */
    public static class Roots extends Block implements Waterloggable {

        public Roots(SoundType sound) {
            super(Material.WOOD, MapColor.OBSIDIAN);
            this.setHardness(0.7F);
            this.setResistance(0.7F * 5.0F / 3.0F);
            this.setSoundType(sound);
            this.setLightOpacity(1);
        }

        @Override
        @SuppressWarnings("deprecation")
        public boolean isOpaqueCube(IBlockState state) {
            return false;
        }

        @Override
        @SideOnly(Side.CLIENT)
        public BlockRenderLayer getRenderLayer() {
            return BlockRenderLayer.CUTOUT_MIPPED;
        }

        @Override
        @SideOnly(Side.CLIENT)
        @SuppressWarnings("deprecation")
        public boolean shouldSideBeRendered(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
            IBlockState other = world.getBlockState(pos.offset(side));
            if (other.getBlock() == this && side.getAxis() == EnumFacing.Axis.Y) {
                return false;
            }
            return super.shouldSideBeRendered(state, world, pos, side);
        }

        @Override
        protected BlockStateContainer createBlockState() {
            return new ExtendedBlockState(this, new IProperty<?>[0], new IUnlistedProperty<?>[]{RootsCrack.VIEW});
        }

        /**
         * The crack pass reads the block through the client world, the chunk render through a chunk cache: only the
         * crack pass gets a {@link RootsCrack.View}, which has the model draw the crack's quads.
         */
        @Override
        public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
            RootsCrack.Viewer viewer = RootsCrack.viewer;
            if (viewer != null && state instanceof IExtendedBlockState && world instanceof World && ((World) world).isRemote) {
                RootsCrack.View view = viewer.view(state, world, pos);
                if (view != null) {
                    return ((IExtendedBlockState) state).withProperty(RootsCrack.VIEW, view);
                }
            }
            return state;
        }

        @Override
        @SuppressWarnings("deprecation")
        public boolean isNormalCube(IBlockState state) {
            // Conducts redstone and supports torches, as 1.19's (it is a full, solid block there).
            return true;
        }

        @Override
        @SuppressWarnings("deprecation")
        public boolean causesSuffocation(IBlockState state) {
            return false;
        }

        @Override
        public boolean canEntitySpawn(IBlockState state, Entity entity) {
            return true;
        }
    }

    /** {@code muddy_mangrove_roots}. */
    public static class Muddy extends BlockRotatedPillar {

        public Muddy(SoundType sound) {
            super(Material.GROUND, MapColor.OBSIDIAN);
            this.setHardness(0.7F);
            this.setResistance(0.7F * 5.0F / 3.0F);
            this.setSoundType(sound);
        }
    }
}
