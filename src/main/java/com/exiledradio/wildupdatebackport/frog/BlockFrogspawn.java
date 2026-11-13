package com.exiledradio.wildupdatebackport.frog;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.material.MaterialLogic;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityFallingBlock;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.Random;

/**
 * Frogspawn, as 1.19.2's {@code FrogspawnBlock}.
 *
 * <ul>
 *   <li>Sits on a water source with nothing liquid in its own space; anything else around it and it is
 *       gone (no drop).</li>
 *   <li>Hatches 3600 to 11999 ticks after it is placed: it breaks, plays the hatch sound and lets out 2 to 5
 *       tadpoles into the water below, each facing a random way, kept from despawning.</li>
 *   <li>A falling block landing in it breaks it; nothing else does (1.19 has no other entity break it).</li>
 *   <li>Broken instantly, drops nothing even with Silk Touch; no collision, 1.5 pixels high.</li>
 * </ul>
 */
public class BlockFrogspawn extends Block {

    public static final int MIN_TADPOLES = 2;
    public static final int MAX_TADPOLES = 5;
    public static final int MIN_HATCH_DELAY = 3600;
    public static final int MAX_HATCH_DELAY = 12000;

    private static final AxisAlignedBB SHAPE = new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 1.5D / 16.0D, 1.0D);

    /** 1.19's {@code Material.FROGSPAWN}: no collision, not solid, destroyed when pushed. */
    public static final Material MATERIAL = new MaterialLogic(MapColor.WATER) {
        {
            this.setNoPushMobility();
        }
    };

    /** Hatch delay bounds, changeable by the self-test as 1.19's game tests do. */
    static int minHatchDelay = MIN_HATCH_DELAY;
    static int maxHatchDelay = MAX_HATCH_DELAY;

    /** 1.19's {@code setHatchDelay}, for tests. */
    public static void setHatchDelay(int min, int max) {
        minHatchDelay = min;
        maxHatchDelay = max;
    }

    public BlockFrogspawn() {
        super(MATERIAL);
        this.setHardness(0.0F);
        this.setSoundType(FrogSounds.FROGSPAWN);
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return SHAPE;
    }

    @Nullable
    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return NULL_AABB;
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    @Override
    public BlockFaceShape getBlockFaceShape(IBlockAccess world, IBlockState state, BlockPos pos, EnumFacing face) {
        return BlockFaceShape.UNDEFINED;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.TRANSLUCENT;
    }

    /** 1.19's {@code mayPlaceOn}: water source below, no liquid here. */
    public static boolean mayPlaceAt(IBlockAccess world, BlockPos pos) {
        IBlockState below = world.getBlockState(pos.down());
        IBlockState here = world.getBlockState(pos);
        return isWaterSource(below) && !here.getMaterial().isLiquid();
    }

    static boolean isWaterSource(IBlockState state) {
        return state.getMaterial() == Material.WATER && state.getBlock() instanceof BlockLiquid
                && state.getValue(BlockLiquid.LEVEL) == 0;
    }

    @Override
    public boolean canPlaceBlockAt(World world, BlockPos pos) {
        return super.canPlaceBlockAt(world, pos) && mayPlaceAt(world, pos);
    }

    @Override
    public void onBlockAdded(World world, BlockPos pos, IBlockState state) {
        world.scheduleUpdate(pos, this, hatchDelay(world.rand));
    }

    /** 1.19's {@code nextInt(min, max)}: min inclusive, max exclusive. */
    static int hatchDelay(Random random) {
        return minHatchDelay + random.nextInt(Math.max(1, maxHatchDelay - minHatchDelay));
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block block, BlockPos from) {
        if (!mayPlaceAt(world, pos)) {
            world.setBlockToAir(pos);
        }
    }

    @Override
    public void updateTick(World world, BlockPos pos, IBlockState state, Random random) {
        if (!mayPlaceAt(world, pos)) {
            world.destroyBlock(pos, false);
        } else {
            hatch(world, pos, random);
        }
    }

    @Override
    public void onEntityCollision(World world, BlockPos pos, IBlockState state, Entity entity) {
        if (!world.isRemote && entity instanceof EntityFallingBlock) {
            world.destroyBlock(pos, false);
        }
    }

    private void hatch(World world, BlockPos pos, Random random) {
        world.destroyBlock(pos, false);
        world.playSound(null, pos, FrogSounds.FROGSPAWN_HATCH, SoundCategory.BLOCKS, 1.0F, 1.0F);
        int count = MIN_TADPOLES + random.nextInt(MAX_TADPOLES - MIN_TADPOLES + 1);
        for (int i = 1; i <= count; i++) {
            EntityTadpole tadpole = new EntityTadpole(world);
            double x = pos.getX() + offset(random);
            double z = pos.getZ() + offset(random);
            int yaw = 1 + random.nextInt(360);
            tadpole.setLocationAndAngles(x, pos.getY() - 0.5D, z, yaw, 0.0F);
            tadpole.enablePersistence();
            world.spawnEntity(tadpole);
        }
    }

    private static double offset(Random random) {
        double half = EntityTadpole.WIDTH / 2.0F;
        return MathHelper.clamp(random.nextDouble(), half, 1.0D - half);
    }

    @Override
    public Item getItemDropped(IBlockState state, Random rand, int fortune) {
        return Items.AIR;
    }

    @Override
    public int quantityDropped(Random random) {
        return 0;
    }

    @Override
    protected boolean canSilkHarvest() {
        return false;
    }
}
