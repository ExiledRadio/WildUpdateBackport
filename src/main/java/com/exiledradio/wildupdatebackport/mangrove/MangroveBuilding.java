package com.exiledradio.wildupdatebackport.mangrove;

import net.minecraft.block.Block;
import net.minecraft.block.BlockButtonWood;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockFenceGate;
import net.minecraft.block.BlockPlanks;
import net.minecraft.block.BlockPressurePlate;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.BlockTrapDoor;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.util.Random;

/**
 * The mangrove wood set's building blocks: each a subclass of the vanilla class oak's is, built with
 * oak's material, hardness and sounds, so everything they do is vanilla's own code. 1.19 colors them
 * all red on maps (mangrove planks' color). Their constructors are protected, hence one small subclass
 * each.
 */
public final class MangroveBuilding {

    private MangroveBuilding() {
    }

    /** Mangrove planks: hardness 2, resistance 3. */
    public static class Planks extends Block {
        public Planks() {
            super(Material.WOOD, MapColor.RED);
            this.setHardness(2.0F);
            this.setResistance(5.0F);
            this.setSoundType(SoundType.WOOD);
        }
    }

    /** {@code mangrove_stairs}, from the planks. */
    public static class Stairs extends BlockStairs {
        public Stairs(IBlockState planks) {
            super(planks);
            this.useNeighborBrightness = true;
        }
    }

    /** {@code mangrove_fence}: hardness 2, resistance 3. */
    public static class Fence extends BlockFence {
        public Fence() {
            super(Material.WOOD, MapColor.RED);
            this.setHardness(2.0F);
            this.setResistance(5.0F);
            this.setSoundType(SoundType.WOOD);
        }
    }

    /** {@code mangrove_fence_gate}: hardness 2, resistance 3. */
    public static class FenceGate extends BlockFenceGate {
        public FenceGate() {
            super(BlockPlanks.EnumType.OAK);
            this.setHardness(2.0F);
            this.setResistance(5.0F);
            this.setSoundType(SoundType.WOOD);
        }

        @Override
        @SuppressWarnings("deprecation")
        public MapColor getMapColor(IBlockState state, IBlockAccess world, BlockPos pos) {
            return MapColor.RED;
        }
    }

    /** {@code mangrove_door}: hardness 3; drops and is picked as our door item. */
    public static class Door extends BlockDoor {
        public Door() {
            super(Material.WOOD);
            this.setHardness(3.0F);
            this.setSoundType(SoundType.WOOD);
            this.disableStats();
        }

        @Override
        @SuppressWarnings("deprecation")
        public MapColor getMapColor(IBlockState state, IBlockAccess world, BlockPos pos) {
            return MapColor.RED;
        }

        @Override
        public Item getItemDropped(IBlockState state, Random rand, int fortune) {
            return state.getValue(HALF) == EnumDoorHalf.UPPER ? Items.AIR : MangroveContent.doorItem();
        }

        @Override
        public ItemStack getItem(World world, BlockPos pos, IBlockState state) {
            return new ItemStack(MangroveContent.doorItem());
        }
    }

    /** {@code mangrove_trapdoor}: hardness 3. */
    public static class Trapdoor extends BlockTrapDoor {
        public Trapdoor() {
            super(Material.WOOD);
            this.setHardness(3.0F);
            this.setSoundType(SoundType.WOOD);
            this.disableStats();
        }

        @Override
        @SuppressWarnings("deprecation")
        public MapColor getMapColor(IBlockState state, IBlockAccess world, BlockPos pos) {
            return MapColor.RED;
        }
    }

    /** {@code mangrove_button}: hardness 0.5. */
    public static class Button extends BlockButtonWood {
        public Button() {
            this.setHardness(0.5F);
            this.setSoundType(SoundType.WOOD);
        }
    }

    /** {@code mangrove_pressure_plate}: hardness 0.5, everything presses it. */
    public static class PressurePlate extends BlockPressurePlate {
        public PressurePlate() {
            super(Material.WOOD, Sensitivity.EVERYTHING);
            this.setHardness(0.5F);
            this.setSoundType(SoundType.WOOD);
        }

        @Override
        @SuppressWarnings("deprecation")
        public MapColor getMapColor(IBlockState state, IBlockAccess world, BlockPos pos) {
            return MapColor.RED;
        }
    }

    /**
     * {@code mangrove_slab} and its double, in 1.12.2's two-block form.
     *
     * <p>{@link BlockSlab} insists on a variant property (it is how vanilla packs eight stone slabs into
     * one block); here it has one value and the blockstate files leave it out. {@link #isDouble()} is
     * answered by the subclass, because {@code BlockSlab}'s constructor calls it before fields are set.
     * Hardness 2, resistance 3, as 1.19's.
     */
    public abstract static class Slab extends BlockSlab {

        public static final PropertyEnum<Variant> VARIANT = PropertyEnum.create("variant", Variant.class);

        /** The only variant. */
        public enum Variant implements IStringSerializable {
            DEFAULT;

            @Override
            public String getName() {
                return "default";
            }
        }

        Block half;

        Slab() {
            super(Material.WOOD, MapColor.RED);
            this.setHardness(2.0F);
            this.setResistance(5.0F);
            this.setSoundType(SoundType.WOOD);
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
        public static final class Half extends Slab {
            public Half() {
                this.half = this;
            }

            @Override
            public boolean isDouble() {
                return false;
            }
        }

        /** Two slabs in one space; no item of its own, drops two halves. */
        public static final class Double extends Slab {
            public Double(Half half) {
                this.half = half;
            }

            @Override
            public boolean isDouble() {
                return true;
            }
        }
    }
}
