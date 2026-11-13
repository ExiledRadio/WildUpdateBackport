package com.exiledradio.wildupdatebackport.sculk;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.block.SoundType;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

/**
 * The sculk vein item: placed as 1.19 places multiface blocks. Clicking a vein that has a free face, or a block
 * that can be replaced, works in that block; otherwise in the block in front of the clicked face. The face it
 * sticks to is the first that can hold it of the clicked block's (when not replacing) and then the directions the
 * player looks toward, nearest first.
 */
public class ItemSculkVein extends ItemBlock {

    public ItemSculkVein(BlockSculkVein block) {
        super(block);
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand, EnumFacing facing,
                                      float hitX, float hitY, float hitZ) {
        ItemStack stack = player.getHeldItem(hand);
        if (stack.isEmpty()) {
            return EnumActionResult.FAIL;
        }
        IBlockState clicked = world.getBlockState(pos);
        boolean replaceClicked;
        BlockPos target;
        if (BlockSculkVein.isVein(clicked)) {
            replaceClicked = BlockSculkVein.faces(world, pos) != BlockSculkVein.ALL;
        } else {
            replaceClicked = clicked.getBlock().isReplaceable(world, pos);
        }
        target = replaceClicked ? pos : pos.offset(facing);
        IBlockState at = world.getBlockState(target);
        if (!BlockSculkVein.isVein(at) && !at.getBlock().isReplaceable(world, target)) {
            return EnumActionResult.FAIL;
        }
        if (!player.canPlayerEdit(target, facing, stack)) {
            return EnumActionResult.FAIL;
        }
        for (EnumFacing dir : nearestLookingDirections(player, facing, replaceClicked)) {
            if (VeinSpreader.isValidStateForPlacement(world, at, target, dir)) {
                if (!world.isRemote) {
                    int mask = BlockSculkVein.isVein(at) ? BlockSculkVein.faces(world, target) : 0;
                    BlockSculkVein.set(world, target, mask | BlockSculkVein.bit(dir), 11);
                    if (player instanceof EntityPlayerMP) {
                        CriteriaTriggers.PLACED_BLOCK.trigger((EntityPlayerMP) player, target, stack);
                    }
                }
                SoundType sound = SculkContent.VEIN_SOUND;
                world.playSound(player, target, sound.getPlaceSound(), SoundCategory.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F,
                        sound.getPitch() * 0.8F);
                stack.shrink(1);
                return EnumActionResult.SUCCESS;
            }
        }
        return EnumActionResult.FAIL;
    }

    /** 1.19's {@code BlockPlaceContext.getNearestLookingDirections}. */
    static EnumFacing[] nearestLookingDirections(Entity player, EnumFacing clickedFace, boolean replaceClicked) {
        EnumFacing[] dirs = orderedByNearest(player);
        if (replaceClicked) {
            return dirs;
        }
        EnumFacing first = clickedFace.getOpposite();
        int i = 0;
        while (i < dirs.length && dirs[i] != first) {
            i++;
        }
        if (i > 0) {
            System.arraycopy(dirs, 0, dirs, 1, i);
            dirs[0] = first;
        }
        return dirs;
    }

    /** 1.19's {@code Direction.orderedByNearest}: the six directions, the one the entity looks toward most first. */
    static EnumFacing[] orderedByNearest(Entity entity) {
        float pitch = entity.rotationPitch * ((float) Math.PI / 180F);
        float yaw = -entity.rotationYaw * ((float) Math.PI / 180F);
        float sinP = MathHelper.sin(pitch);
        float cosP = MathHelper.cos(pitch);
        float sinY = MathHelper.sin(yaw);
        float cosY = MathHelper.cos(yaw);
        boolean east = sinY > 0.0F;
        boolean up = sinP < 0.0F;
        boolean south = cosY > 0.0F;
        float ex = east ? sinY : -sinY;
        float uy = up ? -sinP : sinP;
        float sz = south ? cosY : -cosY;
        float hx = ex * cosP;
        float hz = sz * cosP;
        EnumFacing x = east ? EnumFacing.EAST : EnumFacing.WEST;
        EnumFacing y = up ? EnumFacing.UP : EnumFacing.DOWN;
        EnumFacing z = south ? EnumFacing.SOUTH : EnumFacing.NORTH;
        if (ex > sz) {
            if (uy > hx) {
                return make(y, x, z);
            }
            return hz > uy ? make(x, z, y) : make(x, y, z);
        }
        if (uy > hz) {
            return make(y, z, x);
        }
        return hx > uy ? make(z, x, y) : make(z, y, x);
    }

    private static EnumFacing[] make(EnumFacing a, EnumFacing b, EnumFacing c) {
        return new EnumFacing[]{a, b, c, c.getOpposite(), b.getOpposite(), a.getOpposite()};
    }
}
