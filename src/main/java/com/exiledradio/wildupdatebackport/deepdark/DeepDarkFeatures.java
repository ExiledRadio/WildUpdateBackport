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
 *   <li>{@code sculk_patch_deep_dark}: 256 tries, spread in the chunk, uniform from the bottom to y 256, kept where
 *       the deep dark is, each placed right there (no environment scan): stream S's {@link SculkPatch#DEEP_DARK},
 *       which starts only in air or water beside a full block.</li>
 * </ul>
 * Heights are 1.19's, moved by {@link DeepDark#shift}. With Caves &amp; Cliffs' 1.18 terrain these run after its own
 * decoration (it places no sculk) rather than at their place in 1.19's feature order. They run, with the city's sculk
 * patches before them, once the chunks they can reach are generated ({@link DeepDarkPending}), from a random of the
 * chunk's own.
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
        DeepDarkPending pending = DeepDarkPending.of(world);
        int x0 = (chunkX << 4) + 8;
        int z0 = (chunkZ << 4) + 8;
        if (DeepDark.modern(world) || DeepDark.highGround(world.getBiomeProvider().getBiome(new BlockPos(x0 + 8, 0, z0 + 8), null))
                || DeepDark.highGround(world.getBiomeProvider().getBiome(new BlockPos(x0, 0, z0), null))
                || DeepDark.highGround(world.getBiomeProvider().getBiome(new BlockPos(x0 + 15, 0, z0 + 15), null))) {
            pending.features(chunkX, chunkZ);
        }
        pending.runReady(world, chunkX, chunkZ, DeepDarkFeatures::decorate);
    }

    /** A populate area's deep dark decoration, its chunks all generated: the city's patches, then the features. */
    static void decorate(World world, int chunkX, int chunkZ, DeepDarkPending.Entry entry) {
        Random random = chunkRandom(world.getSeed(), chunkX, chunkZ);
        int x0 = (chunkX << 4) + 8;
        int z0 = (chunkZ << 4) + 8;
        int shift = DeepDark.shift(world);
        int bottom = WorldHeights.minY(world) < 0 ? WorldHeights.minY(world) : -64;
        try (AutoCloseable full = WorldHeights.enterFullHeight()) {
            QuietGen.run(world, () -> {
                for (BlockPos p : entry.patches) {
                    SculkPatch.ANCIENT_CITY.place(world, random, p);
                }
                if (entry.features) {
                    place(world, random, x0, z0, shift, bottom);
                }
            });
        } catch (Exception e) {
            throw e instanceof RuntimeException ? (RuntimeException) e : new IllegalStateException(e);
        }
    }

    /** A random of the chunk's own (Forge's populate seeding, salted), the same whenever its decoration runs. */
    static Random chunkRandom(long worldSeed, int chunkX, int chunkZ) {
        Random r = new Random(worldSeed);
        long a = (r.nextLong() >> 2) + 1L;
        long b = (r.nextLong() >> 2) + 1L;
        return new Random((a * chunkX + b * chunkZ) ^ worldSeed ^ 0x5C01CDA4L);
    }

    private static void place(World world, Random random, int x0, int z0, int shift, int bottom) {
        int veins = 204 + random.nextInt(250 - 204 + 1);
        for (int i = 0; i < veins; i++) {
            BlockPos p = new BlockPos(x0 + random.nextInt(16), bottom + random.nextInt(256 - bottom + 1) + shift, z0 + random.nextInt(16));
            if (inWorld(world, p) && DeepDark.at(world, p)) {
                placeVein(world, p, random);
            }
        }
        for (int i = 0; i < 256; i++) {
            BlockPos p = new BlockPos(x0 + random.nextInt(16), bottom + random.nextInt(256 - bottom + 1) + shift, z0 + random.nextInt(16));
            if (!inWorld(world, p)) {
                continue;
            }
            if (DeepDark.at(world, p)) {
                SculkPatch.DEEP_DARK.place(world, random, p);
            }
        }
    }

    private static boolean inWorld(World world, BlockPos p) {
        return p.getY() > WorldHeights.minY(world) && p.getY() < WorldHeights.maxYExclusive(world);
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
