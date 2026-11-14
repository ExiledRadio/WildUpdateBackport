package com.exiledradio.wildupdatebackport.sculk;

import com.exiledradio.backportcore.waterlog.Waterloggable;

import net.minecraft.block.Block;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import javax.annotation.Nullable;
import java.util.Random;

/**
 * The sculk shrieker: 1.19.2's {@code SculkShriekerBlock}. Collision half a block high, hardness 3, resistance 3;
 * with Silk Touch drops itself, otherwise 5 experience.
 *
 * <p>{@code shrieking} for 90 ticks after a player steps on it or a nearby sculk sensor clicked because of one
 * ({@link TileSculkShrieker}); {@code can_summon} only for shriekers made by world generation (placed and
 * catalyst-grown ones never summon). Breaking a shrieking one still gives its response. It holds a water source
 * ({@link Waterloggable}, with Fluidlogged API).
 */
public class BlockSculkShrieker extends Block implements Waterloggable {

    public static final PropertyBool SHRIEKING = PropertyBool.create("shrieking");
    public static final PropertyBool CAN_SUMMON = PropertyBool.create("can_summon");
    public static final int SHRIEKING_TICKS = 90;
    public static final double TOP_Y = 0.5D;
    private static final AxisAlignedBB COLLIDER = new AxisAlignedBB(0, 0, 0, 1, TOP_Y, 1);

    public BlockSculkShrieker() {
        super(SculkContent.SCULK_MATERIAL, MapColor.BLACK);
        this.setHardness(3.0F);
        this.setResistance(3.0F * 5.0F / 3.0F);
        this.setSoundType(SculkContent.SHRIEKER_SOUND);
        this.setDefaultState(this.blockState.getBaseState().withProperty(SHRIEKING, false).withProperty(CAN_SUMMON, false));
    }

    @Override
    public void onEntityWalk(World world, BlockPos pos, Entity entity) {
        stepOn(world, pos, entity);
    }

    /** 1.19's {@code stepOn}; also reached for sneaking players (see SculkContent). */
    static void stepOn(World world, BlockPos pos, Entity entity) {
        if (world instanceof WorldServer) {
            EntityPlayerMP player = TileSculkShrieker.tryGetPlayer(entity);
            TileEntity te = world.getTileEntity(pos);
            if (player != null && te instanceof TileSculkShrieker) {
                ((TileSculkShrieker) te).tryShriek((WorldServer) world, player);
            }
        }
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (world instanceof WorldServer && state.getValue(SHRIEKING)) {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof TileSculkShrieker) {
                ((TileSculkShrieker) te).tryRespond((WorldServer) world, state);
            }
        }
        super.breakBlock(world, pos, state);
    }

    @Override
    public void updateTick(World world, BlockPos pos, IBlockState state, Random rand) {
        if (state.getValue(SHRIEKING)) {
            world.setBlockState(pos, state.withProperty(SHRIEKING, false), 3);
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof TileSculkShrieker && world instanceof WorldServer) {
                ((TileSculkShrieker) te).tryRespond((WorldServer) world, world.getBlockState(pos));
            }
        }
    }

    @Override
    @Nullable
    @SuppressWarnings("deprecation")
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return COLLIDER;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockFaceShape getBlockFaceShape(IBlockAccess world, IBlockState state, BlockPos pos, EnumFacing face) {
        return face == EnumFacing.DOWN ? BlockFaceShape.SOLID : BlockFaceShape.UNDEFINED;
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, CAN_SUMMON, SHRIEKING);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return (state.getValue(SHRIEKING) ? 1 : 0) | (state.getValue(CAN_SUMMON) ? 2 : 0);
    }

    @Override
    @SuppressWarnings("deprecation")
    public IBlockState getStateFromMeta(int meta) {
        return this.getDefaultState().withProperty(SHRIEKING, (meta & 1) != 0).withProperty(CAN_SUMMON, (meta & 2) != 0);
    }

    /** The item places a shrieker that cannot summon, whatever state it was picked from. */
    @Override
    public int damageDropped(IBlockState state) {
        return 0;
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileSculkShrieker();
    }

    @Override
    public Item getItemDropped(IBlockState state, Random rand, int fortune) {
        return Items.AIR;
    }

    @Override
    public boolean canSilkHarvest(World world, BlockPos pos, IBlockState state, EntityPlayer player) {
        return true;
    }

    @Override
    public int getExpDrop(IBlockState state, IBlockAccess world, BlockPos pos, int fortune) {
        return 5;
    }
}
