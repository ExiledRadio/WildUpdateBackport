package com.exiledradio.wildupdatebackport.frog;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.stats.StatList;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.ForgeEventFactory;

/**
 * 1.19's {@code PlaceOnWaterBlockItem} for frogspawn: aimed at a water source with air above, it places
 * the frogspawn on the water, as a lily pad is placed. Used on a block it does nothing.
 */
public class ItemFrogspawn extends ItemBlock {

    public ItemFrogspawn(Block block) {
        super(block);
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand, EnumFacing facing,
                                      float hitX, float hitY, float hitZ) {
        return EnumActionResult.PASS;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        RayTraceResult hit = this.rayTrace(world, player, true);
        if (hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK) {
            return new ActionResult<ItemStack>(EnumActionResult.PASS, stack);
        }
        BlockPos water = hit.getBlockPos();
        if (!world.isBlockModifiable(player, water) || !player.canPlayerEdit(water.offset(hit.sideHit), hit.sideHit, stack)) {
            return new ActionResult<ItemStack>(EnumActionResult.FAIL, stack);
        }
        BlockPos above = water.up();
        if (!BlockFrogspawn.isWaterSource(world.getBlockState(water)) || !world.isAirBlock(above)) {
            return new ActionResult<ItemStack>(EnumActionResult.FAIL, stack);
        }
        BlockSnapshot snapshot = BlockSnapshot.getBlockSnapshot(world, above);
        world.setBlockState(above, this.block.getDefaultState());
        if (ForgeEventFactory.onPlayerBlockPlace(player, snapshot, EnumFacing.UP, hand).isCanceled()) {
            snapshot.restore(true, false);
            return new ActionResult<ItemStack>(EnumActionResult.FAIL, stack);
        }
        world.setBlockState(above, this.block.getDefaultState(), 11);
        if (player instanceof EntityPlayerMP) {
            CriteriaTriggers.PLACED_BLOCK.trigger((EntityPlayerMP) player, above, stack);
        }
        if (!player.capabilities.isCreativeMode) {
            stack.shrink(1);
        }
        player.addStat(StatList.getObjectUseStats(this));
        SoundType sound = FrogSounds.FROGSPAWN;
        world.playSound(player, above, sound.getPlaceSound(), SoundCategory.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
        return new ActionResult<ItemStack>(EnumActionResult.SUCCESS, stack);
    }
}
