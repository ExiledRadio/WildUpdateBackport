package com.exiledradio.wildupdatebackport.frog;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.dispenser.BehaviorDefaultDispenseItem;
import net.minecraft.dispenser.IBlockSource;
import net.minecraft.block.BlockDispenser;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemBucket;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.stats.StatList;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;

import javax.annotation.Nullable;

/**
 * The bucket of tadpole: 1.19's {@code MobBucketItem}. Emptied like a water bucket, it places water and lets
 * out the tadpole it holds, with its age and the data a bucket keeps (name, NoAI, Silent, NoGravity, Glowing,
 * Invulnerable, Health). Stacks to 1. A dispenser empties it the same way and keeps the empty bucket.
 */
public class ItemTadpoleBucket extends ItemBucket {

    public ItemTadpoleBucket() {
        super(Blocks.FLOWING_WATER);
        this.setMaxStackSize(1);
        this.setContainerItem(Items.BUCKET);
    }

    /** Not a fluid container: emptying it into a tank would lose the tadpole (1.19's is not one either). */
    @Nullable
    @Override
    public net.minecraftforge.common.capabilities.ICapabilityProvider initCapabilities(ItemStack stack, @Nullable NBTTagCompound nbt) {
        return null;
    }

    /** Registers the dispenser behavior: water out, tadpole out, empty bucket back. */
    void registerDispenser() {
        BlockDispenser.DISPENSE_BEHAVIOR_REGISTRY.putObject(this, new BehaviorDefaultDispenseItem() {
            @Override
            protected ItemStack dispenseStack(IBlockSource source, ItemStack stack) {
                EnumFacing facing = source.getBlockState().getValue(BlockDispenser.FACING);
                BlockPos pos = source.getBlockPos().offset(facing);
                if (place(null, source.getWorld(), pos)) {
                    release(source.getWorld(), stack, pos);
                    return new ItemStack(Items.BUCKET);
                }
                return super.dispenseStack(source, stack);
            }
        });
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        RayTraceResult hit = this.rayTrace(world, player, false);
        if (hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK) {
            return new ActionResult<ItemStack>(EnumActionResult.PASS, stack);
        }
        BlockPos at = hit.getBlockPos();
        if (!world.isBlockModifiable(player, at)) {
            return new ActionResult<ItemStack>(EnumActionResult.FAIL, stack);
        }
        boolean replaceable = world.getBlockState(at).getBlock().isReplaceable(world, at);
        BlockPos pos = replaceable && hit.sideHit == EnumFacing.UP ? at : at.offset(hit.sideHit);
        if (!player.canPlayerEdit(pos, hit.sideHit, stack) || !place(player, world, pos)) {
            return new ActionResult<ItemStack>(EnumActionResult.FAIL, stack);
        }
        release(world, stack, pos);
        if (player instanceof EntityPlayerMP) {
            CriteriaTriggers.PLACED_BLOCK.trigger((EntityPlayerMP) player, pos, stack);
        }
        player.addStat(StatList.getObjectUseStats(this));
        return new ActionResult<ItemStack>(EnumActionResult.SUCCESS,
                player.capabilities.isCreativeMode ? stack : new ItemStack(Items.BUCKET));
    }

    /**
     * Places a water source as vanilla's water bucket does (evaporating in the Nether), with the tadpole
     * bucket's own emptying sound.
     *
     * @return false if the water could not go there
     */
    boolean place(@Nullable EntityPlayer player, World world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        Material material = state.getMaterial();
        boolean solid = material.isSolid();
        boolean replaceable = state.getBlock().isReplaceable(world, pos);
        if (!world.isAirBlock(pos) && solid && !replaceable) {
            return false;
        }
        if (world.provider.doesWaterVaporize()) {
            world.playSound(player, pos, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 0.5F,
                    2.6F + (world.rand.nextFloat() - world.rand.nextFloat()) * 0.8F);
            for (int i = 0; i < 8; i++) {
                world.spawnParticle(EnumParticleTypes.SMOKE_LARGE, pos.getX() + Math.random(), pos.getY() + Math.random(),
                        pos.getZ() + Math.random(), 0.0D, 0.0D, 0.0D);
            }
            return true;
        }
        if (!world.isRemote && (!solid || replaceable) && !material.isLiquid()) {
            world.destroyBlock(pos, true);
        }
        world.playSound(player, pos, FrogSounds.BUCKET_EMPTY_TADPOLE, SoundCategory.NEUTRAL, 1.0F, 1.0F);
        world.setBlockState(pos, Blocks.FLOWING_WATER.getDefaultState(), 11);
        return true;
    }

    /** Lets the bucket's tadpole out at a position. */
    static void release(World world, ItemStack stack, BlockPos pos) {
        if (world.isRemote) {
            return;
        }
        EntityTadpole tadpole = new EntityTadpole(world);
        tadpole.setLocationAndAngles(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, world.rand.nextFloat() * 360.0F, 0.0F);
        if (stack.hasDisplayName()) {
            tadpole.setCustomNameTag(stack.getDisplayName());
        }
        NBTTagCompound tag = stack.getTagCompound();
        if (tag != null) {
            tadpole.loadFromBucketTag(tag);
        }
        world.spawnEntity(tadpole);
    }
}
