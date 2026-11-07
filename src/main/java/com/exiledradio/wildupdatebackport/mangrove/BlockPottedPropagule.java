package com.exiledradio.wildupdatebackport.mangrove;

import net.minecraft.block.Block;
import net.minecraft.block.BlockFlowerPot;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.stats.StatList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityFlowerPot;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * {@code potted_mangrove_propagule}: a flower pot holding a propagule.
 *
 * <p>1.12.2's flower pot keeps its plant in a tile whose contents are drawn from a fixed list, so it cannot
 * show a new plant. As in 1.13+, where every potted plant is its own block, a propagule put into an empty
 * vanilla pot turns the pot into this block; taking it out (an empty hand) turns it back into the empty
 * vanilla pot. Breaking it drops the pot and the propagule.
 */
public class BlockPottedPropagule extends Block {

    /** Vanilla's flower pot outline: 6 x 6 pixels, 6 high. */
    private static final AxisAlignedBB POT = new AxisAlignedBB(0.3125D, 0.0D, 0.3125D, 0.6875D, 0.375D, 0.6875D);

    public BlockPottedPropagule() {
        super(Material.CIRCUITS);
        this.setHardness(0.0F);
        this.setSoundType(SoundType.STONE);
    }

    /**
     * Puts a held propagule into an empty vanilla flower pot. Called from the right-click event.
     *
     * @return true if it did (the event is then cancelled)
     */
    static boolean tryPot(PlayerInteractEvent.RightClickBlock event, BlockPottedPropagule potted, Block propagule) {
        World world = event.getWorld();
        BlockPos pos = event.getPos();
        ItemStack held = event.getItemStack();
        if (held.isEmpty() || held.getItem() != net.minecraft.item.Item.getItemFromBlock(propagule)
                || world.getBlockState(pos).getBlock() != Blocks.FLOWER_POT) {
            return false;
        }
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof TileEntityFlowerPot) || !((TileEntityFlowerPot) tile).getFlowerItemStack().isEmpty()) {
            return false;
        }
        if (!world.isRemote) {
            world.setBlockState(pos, potted.getDefaultState(), 3);
            EntityPlayer player = event.getEntityPlayer();
            player.addStat(StatList.FLOWER_POTTED);
            if (!player.capabilities.isCreativeMode) {
                held.shrink(1);
            }
        }
        return true;
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
                                    EnumFacing facing, float hitX, float hitY, float hitZ) {
        ItemStack held = player.getHeldItem(hand);
        if (!held.isEmpty()) {
            // As 1.13+: a full pot only gives its plant back to an empty hand.
            return false;
        }
        if (!world.isRemote) {
            Block propagule = MangroveContent.propagule();
            if (propagule != null) {
                ItemStack plant = new ItemStack(propagule);
                if (!player.addItemStackToInventory(plant)) {
                    player.dropItem(plant, false);
                }
            }
            world.setBlockState(pos, Blocks.FLOWER_POT.getDefaultState(), 3);
        }
        return true;
    }

    @Override
    public void getDrops(NonNullList<ItemStack> drops, IBlockAccess world, BlockPos pos, IBlockState state, int fortune) {
        drops.add(new ItemStack(Items.FLOWER_POT));
        Block propagule = MangroveContent.propagule();
        if (propagule != null) {
            drops.add(new ItemStack(propagule));
        }
    }

    @Override
    public ItemStack getItem(World world, BlockPos pos, IBlockState state) {
        Block propagule = MangroveContent.propagule();
        return propagule != null ? new ItemStack(propagule) : new ItemStack(Items.FLOWER_POT);
    }

    @Override
    public boolean canPlaceBlockAt(World world, BlockPos pos) {
        return super.canPlaceBlockAt(world, pos) && Blocks.FLOWER_POT.canPlaceBlockAt(world, pos);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block block, BlockPos from) {
        IBlockState below = world.getBlockState(pos.down());
        if (below.getBlockFaceShape(world, pos.down(), EnumFacing.UP) != BlockFaceShape.SOLID && !below.isTopSolid()) {
            this.dropBlockAsItem(world, pos, state, 0);
            world.setBlockToAir(pos);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return POT;
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
        return BlockFaceShape.UNDEFINED;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT;
    }

    /** Vanilla's own empty pot, for the tests. */
    static boolean isEmptyPot(World world, BlockPos pos) {
        return world.getBlockState(pos).getBlock() instanceof BlockFlowerPot;
    }
}
