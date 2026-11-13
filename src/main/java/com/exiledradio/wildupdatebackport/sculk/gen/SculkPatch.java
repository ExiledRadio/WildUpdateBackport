package com.exiledradio.wildupdatebackport.sculk.gen;

import com.exiledradio.wildupdatebackport.sculk.BlockSculkShrieker;
import com.exiledradio.wildupdatebackport.sculk.SculkBehaviour;
import com.exiledradio.wildupdatebackport.sculk.SculkContent;
import com.exiledradio.wildupdatebackport.sculk.SculkSpreader;

import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Random;

/**
 * 1.19.2's {@code SculkPatchFeature}, the one place sculk is generated: world generation's spreader
 * ({@link SculkSpreader#createWorldGenSpreader()}) run from one origin, then maybe a catalyst on it and rare
 * shriekers (that can summon) around it.
 *
 * <p>Placement belongs to the deep dark (stream DD), which calls {@link #place} with vanilla's configurations
 * below: {@code sculk_patch_deep_dark} 256 times per chunk, spread in the chunk, from the bottom to the top of
 * the terrain, on the floor of caves; {@code sculk_patch_ancient_city} from the city's pieces. Any terrain can
 * call it (1.18 extended-height worlds included): it needs only a world and a position.
 */
public final class SculkPatch {

    /** 1.19's {@code sculk_patch_deep_dark}: 10 charges of 32, 64 attempts, 0 growth rounds, 1 spread round, no shriekers, catalyst 1 in 2. */
    public static final SculkPatch DEEP_DARK = new SculkPatch(10, 32, 64, 0, 1, 0, 0, 0.5F);
    /** 1.19's {@code sculk_patch_ancient_city}: as the deep dark's, with 1 to 3 shrieker attempts. */
    public static final SculkPatch ANCIENT_CITY = new SculkPatch(10, 32, 64, 0, 1, 1, 3, 0.5F);

    private final int chargeCount;
    private final int amountPerCharge;
    private final int spreadAttempts;
    private final int growthRounds;
    private final int spreadRounds;
    private final int extraRareGrowthsMin;
    private final int extraRareGrowthsMax;
    private final float catalystChance;

    public SculkPatch(int chargeCount, int amountPerCharge, int spreadAttempts, int growthRounds, int spreadRounds,
                      int extraRareGrowthsMin, int extraRareGrowthsMax, float catalystChance) {
        this.chargeCount = chargeCount;
        this.amountPerCharge = amountPerCharge;
        this.spreadAttempts = spreadAttempts;
        this.growthRounds = growthRounds;
        this.spreadRounds = spreadRounds;
        this.extraRareGrowthsMin = extraRareGrowthsMin;
        this.extraRareGrowthsMax = extraRareGrowthsMax;
        this.catalystChance = catalystChance;
    }

    /** @return true if the patch was placed (false when sculk is off or the origin cannot spread) */
    public boolean place(World world, Random random, BlockPos origin) {
        if (!SculkContent.enabled() || SculkContent.sculkBlock() == null || !canSpreadFrom(world, origin)) {
            return false;
        }
        SculkSpreader spreader = SculkSpreader.createWorldGenSpreader();
        int rounds = this.spreadRounds + this.growthRounds;
        for (int round = 0; round < rounds; round++) {
            for (int i = 0; i < this.chargeCount; i++) {
                spreader.addCursors(origin, this.amountPerCharge);
            }
            boolean spreadVeins = round < this.spreadRounds;
            for (int i = 0; i < this.spreadAttempts; i++) {
                spreader.updateCursors(world, origin, random, spreadVeins);
            }
            spreader.clear();
        }
        BlockPos below = origin.down();
        if (random.nextFloat() <= this.catalystChance && fullBlock(world, below)) {
            world.setBlockState(origin, SculkContent.catalystBlock().getDefaultState(), 3);
        }
        int growths = this.extraRareGrowthsMin + random.nextInt(this.extraRareGrowthsMax - this.extraRareGrowthsMin + 1);
        for (int i = 0; i < growths; i++) {
            BlockPos p = origin.add(random.nextInt(5) - 2, 0, random.nextInt(5) - 2);
            BlockPos down = p.down();
            if (world.isAirBlock(p) && world.getBlockState(down).getBlockFaceShape(world, down, EnumFacing.UP) == BlockFaceShape.SOLID) {
                world.setBlockState(p, SculkContent.shriekerBlock().getDefaultState().withProperty(BlockSculkShrieker.CAN_SUMMON, true), 3);
            }
        }
        return true;
    }

    private static boolean canSpreadFrom(World world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof SculkBehaviour) {
            return true;
        }
        if (!world.isAirBlock(pos)) {
            return false;
        }
        for (EnumFacing f : EnumFacing.VALUES) {
            if (fullBlock(world, pos.offset(f))) {
                return true;
            }
        }
        return false;
    }

    private static boolean fullBlock(World world, BlockPos pos) {
        IBlockState s = world.getBlockState(pos);
        return s.isFullCube() && s.getCollisionBoundingBox(world, pos) != null;
    }
}
