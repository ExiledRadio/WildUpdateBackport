package com.exiledradio.wildupdatebackport.mangrove;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.block.BlockStandingSign;
import net.minecraft.block.BlockWallSign;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemSign;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.util.Random;

/**
 * The mangrove sign: standing and wall blocks, the item, and the tile.
 *
 * <p>Both blocks extend vanilla's sign blocks, so placement, popping off, rotation and the outline are an
 * oak sign's. The tile is a vanilla {@link TileEntitySign} under its own class: the editing screen, its
 * packets, commands in sign text and the family's sign dyeing all check {@code instanceof TileEntitySign},
 * and a tile class can have only one renderer, which is how this one draws the mangrove texture.
 *
 * <p>1.19's numbers: hardness 1, wood sounds, mangrove log's map color (podzol); the item stacks to 16.
 */
public final class MangroveSign {

    private MangroveSign() {
    }

    /** The tile behind both mangrove sign blocks. Nothing but a sign under its own class. */
    public static class Tile extends TileEntitySign {
    }

    /** On top of a block, in any of sixteen directions. */
    public static class Standing extends BlockStandingSign {
        public Standing() {
            this.setHardness(1.0F);
            this.setSoundType(SoundType.WOOD);
            this.disableStats();
        }

        @Override
        public TileEntity createNewTileEntity(World world, int meta) {
            return new Tile();
        }

        @Override
        public Item getItemDropped(IBlockState state, Random rand, int fortune) {
            return MangroveContent.signItem();
        }

        @Override
        public ItemStack getItem(World world, BlockPos pos, IBlockState state) {
            return new ItemStack(MangroveContent.signItem());
        }

        @Override
        @SuppressWarnings("deprecation")
        public MapColor getMapColor(IBlockState state, IBlockAccess world, BlockPos pos) {
            return MapColor.OBSIDIAN;
        }
    }

    /** On the side of a block. */
    public static class Wall extends BlockWallSign {
        public Wall() {
            this.setHardness(1.0F);
            this.setSoundType(SoundType.WOOD);
            this.disableStats();
        }

        @Override
        public TileEntity createNewTileEntity(World world, int meta) {
            return new Tile();
        }

        @Override
        public Item getItemDropped(IBlockState state, Random rand, int fortune) {
            return MangroveContent.signItem();
        }

        @Override
        public ItemStack getItem(World world, BlockPos pos, IBlockState state) {
            return new ItemStack(MangroveContent.signItem());
        }

        @Override
        @SuppressWarnings("deprecation")
        public MapColor getMapColor(IBlockState state, IBlockAccess world, BlockPos pos) {
            return MapColor.OBSIDIAN;
        }
    }

    /**
     * The mangrove sign as held: vanilla's {@link ItemSign} with its two blocks swapped for ours. Placing on
     * top stands it facing the player, on a side hangs it there, then the editing screen opens.
     */
    public static class SignItem extends ItemSign {

        @Override
        public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand,
                                          EnumFacing facing, float hitX, float hitY, float hitZ) {
            IBlockState clicked = world.getBlockState(pos);
            boolean replaceable = clicked.getBlock().isReplaceable(world, pos);
            if (facing == EnumFacing.DOWN || !(clicked.getMaterial().isSolid() || replaceable)
                    || (replaceable && facing != EnumFacing.UP)) {
                return EnumActionResult.FAIL;
            }
            if (!replaceable) {
                pos = pos.offset(facing);
            }
            ItemStack stack = player.getHeldItem(hand);
            Standing standing = MangroveContent.standingSign();
            Wall wall = MangroveContent.wallSign();
            if (!player.canPlayerEdit(pos, facing, stack) || !standing.canPlaceBlockAt(world, pos)) {
                return EnumActionResult.FAIL;
            }
            if (world.isRemote) {
                return EnumActionResult.SUCCESS;
            }
            if (facing == EnumFacing.UP) {
                int rotation = MathHelper.floor((double) ((player.rotationYaw + 180.0F) * 16.0F / 360.0F) + 0.5D) & 15;
                world.setBlockState(pos, standing.getDefaultState().withProperty(BlockStandingSign.ROTATION, rotation), 11);
            } else {
                world.setBlockState(pos, wall.getDefaultState().withProperty(BlockWallSign.FACING, facing), 11);
            }
            TileEntity tile = world.getTileEntity(pos);
            if (tile instanceof TileEntitySign && !ItemBlock.setTileEntityNBT(world, player, pos, stack)) {
                player.openEditSign((TileEntitySign) tile);
            }
            if (player instanceof EntityPlayerMP) {
                CriteriaTriggers.PLACED_BLOCK.trigger((EntityPlayerMP) player, pos, stack);
            }
            stack.shrink(1);
            return EnumActionResult.SUCCESS;
        }
    }
}
