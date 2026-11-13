package com.exiledradio.wildupdatebackport.deepdark;

import com.exiledradio.backportcore.terrain.WorldHeights;
import com.exiledradio.wildupdatebackport.common.Owners;
import com.exiledradio.wildupdatebackport.sculk.BlockSculkVein;
import com.exiledradio.wildupdatebackport.sculk.SculkContent;
import com.exiledradio.wildupdatebackport.sculk.gen.SculkPatch;
import net.minecraft.block.Block;
import net.minecraft.block.BlockStone;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.fml.common.IWorldGenerator;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The deep dark's two features of its own (1.19.2's {@code CavePlacements}, step {@code underground_decoration}), placed
 * in each chunk from the populate step, a half chunk in as 1.12.2 decorates:
 *
 * <ul>
 *   <li>{@code sculk_vein}: 204 to 250 tries, spread in the chunk, uniform from the bottom of the world to y 256, kept
 *       where the deep dark is; each a {@code MultifaceGrowthFeature} of sculk vein, search range 20, on floors, walls
 *       and ceilings of stone, andesite, diorite, granite, dripstone, calcite, tuff and deepslate, always spreading.</li>
 *   <li>{@code sculk_patch_deep_dark}: 256 tries, spread in the chunk, uniform from the bottom to y 256, scanned down up
 *       to 12 blocks through air to a solid block, then up one, kept where the deep dark is: stream S's
 *       {@link SculkPatch#DEEP_DARK}.</li>
 * </ul>
 * Heights are 1.19's, moved by {@link DeepDark#shift}. With Caves &amp; Cliffs' 1.18 terrain these run after its own
 * decoration (it places no sculk) rather than at their place in 1.19's feature order, with this populate random.
 *
 * <p>{@link Spawns}: nothing spawns naturally in the deep dark (1.19's empty {@code MobSpawnSettings}). Spawners and
 * shriekers' wardens are not natural spawns.
 */
public final class DeepDarkFeatures implements IWorldGenerator {

    static final int SEARCH_RANGE = 20;

    @Override
    public void generate(Random random, int chunkX, int chunkZ, World world, IChunkGenerator generator, IChunkProvider provider) {
        if (world.provider.getDimension() != 0 || !DeepDarkContent.enabled() || !SculkContent.enabled() || SculkContent.veinBlock() == null) {
            return;
        }
        int x0 = (chunkX << 4) + 8;
        int z0 = (chunkZ << 4) + 8;
        boolean modern = DeepDark.modern(world);
        if (!modern && !DeepDark.highGround(world.getBiomeProvider().getBiome(new BlockPos(x0 + 8, 0, z0 + 8), null))
                && !DeepDark.highGround(world.getBiomeProvider().getBiome(new BlockPos(x0, 0, z0), null))
                && !DeepDark.highGround(world.getBiomeProvider().getBiome(new BlockPos(x0 + 15, 0, z0 + 15), null))) {
            return;
        }
        int shift = DeepDark.shift(world);
        int bottom = WorldHeights.minY(world) < 0 ? WorldHeights.minY(world) : -64;
        try (AutoCloseable full = WorldHeights.enterFullHeight()) {
            QuietGen.run(world, () -> place(world, random, x0, z0, shift, bottom));
        } catch (Exception e) {
            throw e instanceof RuntimeException ? (RuntimeException) e : new IllegalStateException(e);
        }
    }

    private static void place(World world, Random random, int x0, int z0, int shift, int bottom) {
        int veins = 204 + random.nextInt(250 - 204 + 1);
        for (int i = 0; i < veins; i++) {
            BlockPos p = new BlockPos(x0 + random.nextInt(16), bottom + random.nextInt(256 - bottom + 1) + shift, z0 + random.nextInt(16));
            if (inWorld(world, p) && generated(world, p, VEIN_REACH) && DeepDark.at(world, p)) {
                placeVein(world, p, random);
            }
        }
        for (int i = 0; i < 256; i++) {
            BlockPos p = new BlockPos(x0 + random.nextInt(16), bottom + random.nextInt(256 - bottom + 1) + shift, z0 + random.nextInt(16));
            if (!inWorld(world, p) || !generated(world, p, PATCH_REACH)) {
                continue;
            }
            BlockPos floor = scanDown(world, p);
            if (floor != null) {
                BlockPos origin = floor.up();
                if (DeepDark.at(world, origin)) {
                    SculkPatch.DEEP_DARK.place(world, random, origin);
                }
            }
        }
    }

    /** How far a vein's search and spread reach: the search range and one more. */
    static final int VEIN_REACH = SEARCH_RANGE + 2;
    /** How far a patch's charges wander in practice (they spread a cursor at a time, 32 to a charge). */
    public static final int PATCH_REACH = 16;

    /**
     * 1.19 decorates inside a 3 x 3 chunk region; 1.12.2 populates a half chunk in, with only the 2 x 2 chunks below
     * it sure to exist. A try whose reach would generate a new chunk (cascading generation) is left out.
     */
    public static boolean generated(World world, BlockPos p, int reach) {
        net.minecraft.world.chunk.IChunkProvider provider = world.getChunkProvider();
        for (int cx = (p.getX() - reach) >> 4; cx <= (p.getX() + reach) >> 4; cx++) {
            for (int cz = (p.getZ() - reach) >> 4; cz <= (p.getZ() + reach) >> 4; cz++) {
                if (!provider.isChunkGeneratedAt(cx, cz)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean inWorld(World world, BlockPos p) {
        return p.getY() > WorldHeights.minY(world) && p.getY() < WorldHeights.maxYExclusive(world);
    }

    /** {@code EnvironmentScanPlacement.scanningFor(DOWN, solid, ONLY_IN_AIR, 12)}: the solid block, or null. */
    static BlockPos scanDown(World world, BlockPos start) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(start);
        if (!world.isAirBlock(p)) {
            return null;
        }
        for (int i = 0; i < 12; i++) {
            if (world.getBlockState(p).getMaterial().isSolid()) {
                return p.toImmutable();
            }
            p.move(EnumFacing.DOWN);
            if (p.getY() <= WorldHeights.minY(world)) {
                return null;
            }
            if (!world.isAirBlock(p) && !world.getBlockState(p).getMaterial().isSolid()) {
                return null;
            }
        }
        return world.getBlockState(p).getMaterial().isSolid() ? p.toImmutable() : null;
    }

    // ---- MultifaceGrowthFeature

    static void placeVein(World world, BlockPos origin, Random random) {
        if (!airOrWater(world.getBlockState(origin))) {
            return;
        }
        List<EnumFacing> dirs = shuffled(EnumFacing.VALUES, random, null);
        if (grow(world, origin, random, dirs)) {
            return;
        }
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (EnumFacing dir : dirs) {
            p.setPos(origin);
            List<EnumFacing> others = shuffled(EnumFacing.VALUES, random, dir.getOpposite());
            for (int i = 0; i < SEARCH_RANGE; i++) {
                p.move(dir);
                IBlockState state = world.getBlockState(p);
                if (!airOrWater(state) && !BlockSculkVein.isVein(state)) {
                    break;
                }
                if (grow(world, p.toImmutable(), random, others)) {
                    return;
                }
            }
        }
    }

    private static boolean grow(World world, BlockPos pos, Random random, List<EnumFacing> dirs) {
        for (EnumFacing dir : dirs) {
            BlockPos support = pos.offset(dir);
            if (!canBePlacedOn(world.getBlockState(support))) {
                continue;
            }
            // MultifaceBlock.getStateForPlacement: the face must be free and its support sturdy.
            int mask = BlockSculkVein.isVein(world.getBlockState(pos)) ? BlockSculkVein.faces(world, pos) : 0;
            if (BlockSculkVein.has(mask, dir) || !BlockSculkVein.canAttachTo(world, dir, support)) {
                return false;
            }
            BlockSculkVein.set(world, pos, mask | BlockSculkVein.bit(dir), 2);
            // chance_of_spreading 1.0: random.nextFloat() < 1 always holds, but is still drawn.
            random.nextFloat();
            SculkContent.veinBlock().spreader().spreadFromFaceTowardRandomDirection(world, pos, dir, random);
            return true;
        }
        return false;
    }

    private static boolean airOrWater(IBlockState state) {
        return state.getMaterial() == Material.AIR || state.getBlock() == Blocks.WATER || state.getBlock() == Blocks.FLOWING_WATER;
    }

    /** 1.19's {@code can_be_placed_on}: stone, andesite, diorite, granite, dripstone block, calcite, tuff, deepslate. */
    static boolean canBePlacedOn(IBlockState state) {
        Block b = state.getBlock();
        if (b == Blocks.STONE) {
            BlockStone.EnumType t = state.getValue(BlockStone.VARIANT);
            return t == BlockStone.EnumType.STONE || t == BlockStone.EnumType.ANDESITE || t == BlockStone.EnumType.DIORITE
                    || t == BlockStone.EnumType.GRANITE;
        }
        for (String name : new String[] {"deepslate", "tuff", "calcite", "dripstone_block"}) {
            if (b != Blocks.AIR && b == Owners.block(Owners.CAVES_AND_CLIFFS, name)) {
                return true;
            }
        }
        return false;
    }

    static List<EnumFacing> shuffled(EnumFacing[] values, Random random, EnumFacing except) {
        List<EnumFacing> out = new ArrayList<EnumFacing>(Arrays.asList(values));
        if (except != null) {
            out.remove(except);
        }
        Collections.shuffle(out, random);
        return out;
    }

    /** No natural spawns in the deep dark. */
    public static final class Spawns {
        @SubscribeEvent
        public void checkSpawn(LivingSpawnEvent.CheckSpawn event) {
            if (event.isSpawner() || event.getWorld().isRemote) {
                return;
            }
            World world = event.getWorld();
            // Caves & Cliffs' 1.18 terrain spawns from our biome there, whose lists are empty.
            if (DeepDark.modern(world)) {
                return;
            }
            if (DeepDark.at(world, new BlockPos(event.getX(), event.getY(), event.getZ()))) {
                event.setResult(Event.Result.DENY);
            }
        }
    }
}
