package com.exiledradio.wildupdatebackport.common;

import com.exiledradio.backportcore.waterlog.Waterloggable;

import net.minecraft.block.Block;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.BlockWall;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.util.Random;

/**
 * Stairs, slabs and walls made from one of this mod's blocks, shared by every stream that has them.
 *
 * <p>Each is vanilla's class with the least added to make a new one exist: protected constructors, a slab's
 * one-value variant, a wall's cobblestone variant pinned. The blockstate files leave the pinned properties
 * out ({@link Content#ignore}).
 *
 * <p>All three are {@link Waterloggable}, as 1.19 makes every stair, slab and wall (a double slab holds no water).
 */
public final class ModBuilding {

    private ModBuilding() {
    }

    /** Stairs of a block; strength and sound come from the block's state, as vanilla's. */
    public static class Stairs extends BlockStairs implements Waterloggable {
        public Stairs(IBlockState base) {
            super(base);
            this.useNeighborBrightness = true;
        }
    }

    /**
     * A wall of a block. Extending {@link BlockWall} is what makes it join vanilla's walls and fences as
     * walls do. Vanilla's {@code variant} (cobblestone or mossy) cannot be removed, so it stays at its first
     * value and the blockstate file ignores it.
     */
    public static class Wall extends BlockWall implements Waterloggable {
        private final MapColor color;

        public Wall(Block base, MapColor color) {
            super(base);
            this.color = color;
        }

        @Override
        @SuppressWarnings("deprecation")
        public String getLocalizedName() {
            return net.minecraft.util.text.translation.I18n.translateToLocal(getTranslationKey() + ".name");
        }

        @Override
        public void getSubBlocks(CreativeTabs tab, NonNullList<ItemStack> items) {
            items.add(new ItemStack(this));
        }

        @Override
        public int damageDropped(IBlockState state) {
            return 0;
        }

        @Override
        @SuppressWarnings("deprecation")
        public IBlockState getStateFromMeta(int meta) {
            return getDefaultState();
        }

        @Override
        public int getMetaFromState(IBlockState state) {
            return 0;
        }

        @Override
        @SuppressWarnings("deprecation")
        public MapColor getMapColor(IBlockState state, IBlockAccess world, BlockPos pos) {
            return this.color;
        }
    }

    /**
     * A slab, in 1.12.2's two-block form: {@link Half} and its {@link Double}. {@link BlockSlab} insists on a
     * variant property; here it has one value. {@link #isDouble()} is answered by the subclass, because the
     * superclass constructor asks before any field is set.
     */
    public abstract static class Slab extends BlockSlab implements Waterloggable {

        /** 1.19: a slab holds water, a double slab (a full block) does not. */
        @Override
        public boolean canHoldWater(IBlockState state, IBlockAccess world, BlockPos pos) {
            return !isDouble();
        }

        public static final PropertyEnum<Variant> VARIANT = PropertyEnum.create("variant", Variant.class);

        /** The only variant. */
        public enum Variant implements IStringSerializable {
            DEFAULT;

            @Override
            public String getName() {
                return "default";
            }
        }

        protected Block half;

        protected Slab(Material material, MapColor color, float hardness, float resistance, SoundType sound) {
            super(material, color);
            this.setHardness(hardness);
            // Stored tripled and reported fifthed: this makes getExplosionResistance return 1.19's number.
            this.setResistance(resistance * 5.0F / 3.0F);
            this.setSoundType(sound);
            IBlockState state = this.blockState.getBaseState().withProperty(VARIANT, Variant.DEFAULT);
            if (!isDouble()) {
                state = state.withProperty(HALF, EnumBlockHalf.BOTTOM);
            }
            this.setDefaultState(state);
            this.useNeighborBrightness = !isDouble();
        }

        @Override
        protected BlockStateContainer createBlockState() {
            return isDouble() ? new BlockStateContainer(this, VARIANT) : new BlockStateContainer(this, HALF, VARIANT);
        }

        @Override
        public String getTranslationKey(int meta) {
            return getTranslationKey();
        }

        @Override
        public IProperty<?> getVariantProperty() {
            return VARIANT;
        }

        @Override
        public Comparable<?> getTypeForItem(ItemStack stack) {
            return Variant.DEFAULT;
        }

        @Override
        @SuppressWarnings("deprecation")
        public IBlockState getStateFromMeta(int meta) {
            IBlockState state = getDefaultState();
            if (!isDouble()) {
                state = state.withProperty(HALF, (meta & 8) != 0 ? EnumBlockHalf.TOP : EnumBlockHalf.BOTTOM);
            }
            return state;
        }

        @Override
        public int getMetaFromState(IBlockState state) {
            return !isDouble() && state.getValue(HALF) == EnumBlockHalf.TOP ? 8 : 0;
        }

        @Override
        public Item getItemDropped(IBlockState state, Random rand, int fortune) {
            return Item.getItemFromBlock(this.half);
        }

        @Override
        public ItemStack getItem(World world, BlockPos pos, IBlockState state) {
            return new ItemStack(this.half);
        }

        @Override
        public int damageDropped(IBlockState state) {
            return 0;
        }

        /** The slab itself. */
        public static class Half extends Slab {
            public Half(Material material, MapColor color, float hardness, float resistance, SoundType sound) {
                super(material, color, hardness, resistance, sound);
                this.half = this;
            }

            @Override
            public boolean isDouble() {
                return false;
            }
        }

        /** Two slabs in one space; no item of its own, drops two halves. */
        public static class Double extends Slab {
            public Double(Half half, Material material, MapColor color, float hardness, float resistance, SoundType sound) {
                super(material, color, hardness, resistance, sound);
                this.half = half;
            }

            @Override
            public boolean isDouble() {
                return true;
            }
        }
    }
}
