package com.exiledradio.wildupdatebackport.mangrove;

import net.minecraft.block.BlockLog;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/**
 * Mangrove log, mangrove wood, stripped mangrove log and stripped mangrove wood.
 *
 * <p>All four extend vanilla's {@link BlockLog}, so leaves next to them do not decay, breaking one starts
 * nearby leaves decaying, and every mod that looks for logs finds them. 1.19 puts all four in its
 * {@code logs} tag, as here.
 *
 * <p>One property, {@code axis} ({@code x}, {@code y}, {@code z}, and 1.12.2's {@code none}, which nothing
 * in survival reaches), in vanilla logs' metadata bits 2-3.
 *
 * <p>1.19's numbers: hardness 2 and blast resistance 2 (vanilla log's); map color red, except the
 * unstripped log's bark sides, which are podzol (1.12.2's {@link MapColor#OBSIDIAN}). 1.19's mangrove
 * wood block is red all round.
 */
public class BlockMangroveLog extends BlockLog {

    /** True for the wood blocks, which show bark (or stripped bark) on every face. */
    public final boolean wood;

    /** True for the stripped log and the stripped wood. */
    public final boolean stripped;

    public BlockMangroveLog(boolean wood, boolean stripped) {
        this.wood = wood;
        this.stripped = stripped;
        this.setDefaultState(this.blockState.getBaseState().withProperty(LOG_AXIS, EnumAxis.Y));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, new IProperty[] {LOG_AXIS});
    }

    @Override
    @SuppressWarnings("deprecation")
    public IBlockState getStateFromMeta(int meta) {
        EnumAxis axis;
        switch (meta & 12) {
            case 4:
                axis = EnumAxis.X;
                break;
            case 8:
                axis = EnumAxis.Z;
                break;
            case 12:
                axis = EnumAxis.NONE;
                break;
            default:
                axis = EnumAxis.Y;
                break;
        }
        return this.getDefaultState().withProperty(LOG_AXIS, axis);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        switch (state.getValue(LOG_AXIS)) {
            case X:
                return 4;
            case Z:
                return 8;
            case NONE:
                return 12;
            default:
                return 0;
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public MapColor getMapColor(IBlockState state, IBlockAccess world, BlockPos pos) {
        if (this.stripped || this.wood) {
            return MapColor.RED;
        }
        return state.getValue(LOG_AXIS) == EnumAxis.Y ? MapColor.RED : MapColor.OBSIDIAN;
    }
}
