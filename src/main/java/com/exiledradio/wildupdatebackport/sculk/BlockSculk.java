package com.exiledradio.wildupdatebackport.sculk;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.util.Random;

/**
 * Sculk: 1.19.2's {@code SculkBlock}. Hardness 0.2; with Silk Touch drops itself, otherwise 1 experience.
 *
 * <p>Charges pass through it, and on it they are spent: one in {@code chargeDecayRate} steps, outside the no-growth
 * radius and where fewer than three sensors or shriekers stand within 4 blocks (and 2 above), a charge of at least
 * {@code rand(growthSpawnCost)} grows a sensor (or, one time in 11, a shrieker) on top and pays the cost; otherwise
 * one step in {@code additionalDecayRate} it decays, more the farther it is from the catalyst.
 */
public class BlockSculk extends Block implements SculkBehaviour {

    public BlockSculk() {
        super(SculkContent.SCULK_MATERIAL);
        this.setHardness(0.2F);
        this.setSoundType(SculkContent.SCULK_SOUND);
    }

    @Override
    public int attemptUseCharge(SculkSpreader.ChargeCursor cursor, World world, BlockPos catalyst, Random random,
                                SculkSpreader spreader, boolean spreadVeins) {
        int charge = cursor.getCharge();
        if (charge == 0 || random.nextInt(spreader.chargeDecayRate()) != 0) {
            return charge;
        }
        BlockPos pos = cursor.getPos();
        boolean near = pos.distanceSq(catalyst) < (double) spreader.noGrowthRadius() * spreader.noGrowthRadius();
        if (!near && canPlaceGrowth(world, pos)) {
            int cost = spreader.growthSpawnCost();
            if (random.nextInt(cost) < charge) {
                BlockPos above = pos.up();
                IBlockState growth = randomGrowthState(random, spreader.isWorldGeneration());
                world.setBlockState(above, growth, 3);
                world.playSound(null, pos, growth.getBlock().getSoundType(growth, world, above, null).getPlaceSound(),
                        SoundCategory.BLOCKS, 1.0F, 1.0F);
            }
            return Math.max(0, charge - cost);
        }
        if (random.nextInt(spreader.additionalDecayRate()) != 0) {
            return charge;
        }
        return charge - (near ? 1 : decayPenalty(spreader, pos, catalyst, charge));
    }

    private static int decayPenalty(SculkSpreader spreader, BlockPos pos, BlockPos catalyst, int charge) {
        int r = spreader.noGrowthRadius();
        float d = (float) Math.sqrt(pos.distanceSq(catalyst)) - r;
        float over = d * d;
        int max = (SculkSpreader.MAX_GROWTH_RATE_RADIUS - r) * (SculkSpreader.MAX_GROWTH_RATE_RADIUS - r);
        float f = Math.min(1.0F, over / max);
        return Math.max(1, (int) (charge * f * 0.5F));
    }

    private static IBlockState randomGrowthState(Random random, boolean worldGen) {
        if (random.nextInt(SculkSpreader.SHRIEKER_PLACEMENT_RATE) == 0) {
            return SculkContent.shriekerBlock().getDefaultState().withProperty(BlockSculkShrieker.CAN_SUMMON, worldGen);
        }
        return SculkContent.sensorBlock().getDefaultState();
    }

    /** Air above (1.19 also water; growths are not waterlogged before stream Wl), and at most two growths near. */
    private static boolean canPlaceGrowth(World world, BlockPos pos) {
        if (!world.isAirBlock(pos.up())) {
            return false;
        }
        int count = 0;
        for (BlockPos p : BlockPos.getAllInBoxMutable(pos.add(-4, 0, -4), pos.add(4, 2, 4))) {
            Block b = world.getBlockState(p).getBlock();
            if (b == SculkContent.sensorBlock() || b == SculkContent.shriekerBlock()) {
                count++;
            }
            if (count > 2) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean canChangeBlockStateOnSpread() {
        return false;
    }

    // --- drops: Silk Touch only, else experience -----------------------------------------------

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
        return 1;
    }
}
