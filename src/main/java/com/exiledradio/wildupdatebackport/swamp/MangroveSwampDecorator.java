package com.exiledradio.wildupdatebackport.swamp;

import com.exiledradio.wildupdatebackport.mangrove.BlockPropagule;
import com.exiledradio.wildupdatebackport.mangrove.MangroveContent;
import com.exiledradio.wildupdatebackport.mangrove.WorldGenMangroveTree;
import com.exiledradio.wildupdatebackport.mud.MudContent;

import net.minecraft.block.Block;
import net.minecraft.block.BlockDirt;
import net.minecraft.block.BlockTallGrass;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeDecorator;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.feature.WorldGenLiquids;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.terraingen.DecorateBiomeEvent;
import net.minecraftforge.event.terraingen.TerrainGen;

import java.util.Random;

/**
 * The mangrove swamp's decoration: 1.19.2's placed features for the biome, with their numbers, on 1.12.2's
 * decoration area (the chunk offset by 8, so nothing reaches past the chunks that are already there).
 *
 * <table>
 *   <tr><th>Step</th><th>1.19.2 placed feature</th><th>Here</th></tr>
 *   <tr><td>ores</td><td>the default ores and underground variety</td><td>1.12.2's ({@code generateOres})</td></tr>
 *   <tr><td>ores</td><td>{@code disk_grass}: once, on the top solid block if it is mud</td><td>disk of radius 2-6,
 *       2 up and down: dirt and mud become grass where nothing solid and no water is above, else dirt</td></tr>
 *   <tr><td>ores</td><td>{@code disk_clay}: once, on the top solid block under water</td><td>radius 2-3, 1 up and
 *       down: dirt and clay become clay</td></tr>
 *   <tr><td>fluid springs</td><td>the default springs</td><td>1.12.2's (50 water, 20 lava)</td></tr>
 *   <tr><td>vegetation</td><td>{@code trees_mangrove}: 25, water at most 5 deep, on the ocean floor where a
 *       propagule would stand</td><td>85% tall mangroves, else mangroves ({@link WorldGenMangroveTree})</td></tr>
 *   <tr><td>vegetation</td><td>{@code patch_grass_normal}: 5 on the world surface</td><td>32 tries each, 7 across
 *       and 3 up or down, grass where there is air and it can stand</td></tr>
 *   <tr><td>vegetation</td><td>{@code patch_dead_bush}: 1 on the world surface</td><td>4 tries, dead bushes</td></tr>
 *   <tr><td>vegetation</td><td>{@code patch_waterlily}: 4 on the world surface</td><td>10 tries, lily pads</td></tr>
 *   <tr><td>vegetation</td><td>{@code seagrass_swamp}: 64, 60% tall</td><td>Update Aquatic's, which places it in
 *       every swamp-type biome</td></tr>
 * </table>
 * 1.12.2's own swamp decoration (oak swamp trees, blue orchids, mushrooms, sugar cane, sand and gravel patches,
 * pumpkins) is not placed: 1.19's mangrove swamp has none of it.
 *
 * <p>The heightmaps are 1.19's: the <i>world surface</i> is the first block above the highest one that is not air
 * (plants and water included), the <i>ocean floor</i> the first above the highest one that blocks movement.
 */
public class MangroveSwampDecorator extends BiomeDecorator {

    /** 1.19.2's numbers. */
    public static final int TREE_COUNT = 25;
    public static final int TREE_MAX_WATER_DEPTH = 5;
    public static final float TALL_CHANCE = 0.85F;
    public static final int GRASS_PATCHES = 5;
    public static final int GRASS_TRIES = 32;
    public static final int DEAD_BUSH_TRIES = 4;
    public static final int LILY_PATCHES = 4;
    public static final int LILY_TRIES = 10;

    @Override
    protected void genDecorations(Biome biome, World world, Random random) {
        ChunkPos chunk = new ChunkPos(this.chunkPos);
        MinecraftForge.EVENT_BUS.post(new DecorateBiomeEvent.Pre(world, random, chunk));
        generateOres(world, random);

        if (!SwampContent.generatorPlaces(world, "disk_grass")
                && TerrainGen.decorate(world, random, chunk, DecorateBiomeEvent.Decorate.EventType.CUSTOM)) {
            BlockPos floor = oceanFloor(world, inSquare(random)).down();
            Block mud = MudContent.mud();
            if (mud != null && world.getBlockState(floor).getBlock() == mud) {
                diskGrass(world, random, floor);
            }
        }
        if (!SwampContent.generatorPlaces(world, "disk_clay")
                && TerrainGen.decorate(world, random, chunk, DecorateBiomeEvent.Decorate.EventType.CLAY)) {
            BlockPos above = oceanFloor(world, inSquare(random));
            if (world.getBlockState(above).getMaterial() == Material.WATER) {
                diskClay(world, random, above);
            }
        }

        springs(world, random, chunk);

        if (!SwampContent.generatorPlaces(world, "trees_mangrove")
                && TerrainGen.decorate(world, random, chunk, DecorateBiomeEvent.Decorate.EventType.TREE)) {
            for (int i = 0; i < TREE_COUNT; i++) {
                BlockPos column = inSquare(random);
                BlockPos floor = oceanFloor(world, column);
                if (worldSurface(world, column).getY() - floor.getY() > TREE_MAX_WATER_DEPTH || !propaguleSurvives(world, floor)) {
                    continue;
                }
                // mangrove_vegetation: tall_mangrove_checked at 0.85, else mangrove_checked; each checks the
                // propagule again.
                boolean tall = random.nextFloat() < TALL_CHANCE;
                if (propaguleSurvives(world, floor)) {
                    new WorldGenMangroveTree(false, tall).generate(world, random, floor);
                }
            }
        }

        if (TerrainGen.decorate(world, random, chunk, DecorateBiomeEvent.Decorate.EventType.GRASS)) {
            IBlockState grass = Blocks.TALLGRASS.getDefaultState().withProperty(BlockTallGrass.TYPE, BlockTallGrass.EnumType.GRASS);
            for (int i = 0; i < GRASS_PATCHES; i++) {
                patch(world, random, worldSurface(world, inSquare(random)), grass, GRASS_TRIES);
            }
        }
        if (TerrainGen.decorate(world, random, chunk, DecorateBiomeEvent.Decorate.EventType.DEAD_BUSH)) {
            patch(world, random, worldSurface(world, inSquare(random)), Blocks.DEADBUSH.getDefaultState(), DEAD_BUSH_TRIES);
        }
        if (TerrainGen.decorate(world, random, chunk, DecorateBiomeEvent.Decorate.EventType.LILYPAD)) {
            for (int i = 0; i < LILY_PATCHES; i++) {
                patch(world, random, worldSurface(world, inSquare(random)), Blocks.WATERLILY.getDefaultState(), LILY_TRIES);
            }
        }

        MinecraftForge.EVENT_BUS.post(new DecorateBiomeEvent.Post(world, random, chunk));
    }

    /** {@code InSquarePlacement}: a random column of the decoration area. */
    private BlockPos inSquare(Random random) {
        return this.chunkPos.add(random.nextInt(16) + 8, 0, random.nextInt(16) + 8);
    }

    /** As 1.12.2's own springs (1.19's are the same features). */
    private void springs(World world, Random random, ChunkPos chunk) {
        if (!this.generateFalls) {
            return;
        }
        if (TerrainGen.decorate(world, random, chunk, DecorateBiomeEvent.Decorate.EventType.LAKE_WATER)) {
            for (int i = 0; i < 50; ++i) {
                int x = random.nextInt(16) + 8;
                int z = random.nextInt(16) + 8;
                int y = random.nextInt(248) + 8;
                if (y > 0) {
                    new WorldGenLiquids(Blocks.FLOWING_WATER).generate(world, random, this.chunkPos.add(x, random.nextInt(y), z));
                }
            }
        }
        if (TerrainGen.decorate(world, random, chunk, DecorateBiomeEvent.Decorate.EventType.LAKE_LAVA)) {
            for (int i = 0; i < 20; ++i) {
                int x = random.nextInt(16) + 8;
                int z = random.nextInt(16) + 8;
                int y = random.nextInt(random.nextInt(random.nextInt(240) + 8) + 8);
                new WorldGenLiquids(Blocks.FLOWING_LAVA).generate(world, random, this.chunkPos.add(x, y, z));
            }
        }
    }

    // --- heightmaps -----------------------------------------------------------------------------

    /** 1.19's WORLD_SURFACE: the position above the highest block that is not air. */
    public static BlockPos worldSurface(World world, BlockPos column) {
        Chunk chunk = world.getChunk(column);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(column.getX(), chunk.getTopFilledSegment() + 16, column.getZ());
        while (pos.getY() > 0) {
            pos.setY(pos.getY() - 1);
            if (chunk.getBlockState(pos).getMaterial() != Material.AIR) {
                return pos.up();
            }
        }
        return new BlockPos(column.getX(), 0, column.getZ());
    }

    /** 1.19's OCEAN_FLOOR: the position above the highest block that blocks movement. */
    public static BlockPos oceanFloor(World world, BlockPos column) {
        Chunk chunk = world.getChunk(column);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(column.getX(), chunk.getTopFilledSegment() + 16, column.getZ());
        while (pos.getY() > 0) {
            pos.setY(pos.getY() - 1);
            if (chunk.getBlockState(pos).getMaterial().blocksMovement()) {
                return pos.up();
            }
        }
        return new BlockPos(column.getX(), 0, column.getZ());
    }

    /** {@code BlockPredicate.wouldSurvive(mangrove_propagule)}: a standing propagule's soil below. */
    static boolean propaguleSurvives(World world, BlockPos pos) {
        BlockPropagule propagule = MangroveContent.propagule();
        return propagule != null && propagule.canBlockStay(world, pos, propagule.getDefaultState());
    }

    // --- features -------------------------------------------------------------------------------

    /** {@code RandomPatchFeature} of a {@code SimpleBlockFeature} only where empty. @return how many were placed */
    static int patch(World world, Random random, BlockPos origin, IBlockState state, int tries) {
        Block block = state.getBlock();
        int placed = 0;
        for (int i = 0; i < tries; i++) {
            BlockPos p = origin.add(random.nextInt(8) - random.nextInt(8), random.nextInt(4) - random.nextInt(4),
                    random.nextInt(8) - random.nextInt(8));
            if (p.getY() <= 0 || p.getY() >= 255 || !world.isAirBlock(p)) {
                continue;
            }
            if (block instanceof net.minecraft.block.BlockBush && !((net.minecraft.block.BlockBush) block).canBlockStay(world, p, state)) {
                continue;
            }
            world.setBlockState(p, state, 2);
            placed++;
        }
        return placed;
    }

    /** 1.19.2's {@code disk_grass}. */
    static void diskGrass(World world, Random random, BlockPos center) {
        Block mud = MudContent.mud();
        int radius = 2 + random.nextInt(5);
        disk(world, center, radius, 2, new Disk() {
            @Override
            public boolean target(IBlockState state) {
                return isPlainDirt(state) || state.getBlock() == mud;
            }

            @Override
            public IBlockState place(World world, BlockPos pos) {
                IBlockState above = world.getBlockState(pos.up());
                boolean covered = above.getMaterial().isSolid() || above.getMaterial() == Material.WATER;
                return covered ? Blocks.DIRT.getDefaultState() : Blocks.GRASS.getDefaultState();
            }
        });
    }

    /** 1.19.2's {@code disk_clay}. */
    static void diskClay(World world, Random random, BlockPos center) {
        int radius = 2 + random.nextInt(2);
        disk(world, center, radius, 1, new Disk() {
            @Override
            public boolean target(IBlockState state) {
                return isPlainDirt(state) || state.getBlock() == Blocks.CLAY;
            }

            @Override
            public IBlockState place(World world, BlockPos pos) {
                return Blocks.CLAY.getDefaultState();
            }
        });
    }

    private static boolean isPlainDirt(IBlockState state) {
        return state.getBlock() == Blocks.DIRT && state.getValue(BlockDirt.VARIANT) == BlockDirt.DirtType.DIRT;
    }

    private interface Disk {
        boolean target(IBlockState state);

        IBlockState place(World world, BlockPos pos);
    }

    /** 1.19's {@code DiskFeature}: every column within the radius, from {@code halfHeight} above to below, top down. */
    private static void disk(World world, BlockPos center, int radius, int halfHeight, Disk disk) {
        int top = center.getY() + halfHeight;
        int bottom = center.getY() - halfHeight - 1;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) {
                    continue;
                }
                for (int y = top; y > bottom; y--) {
                    pos.setPos(center.getX() + dx, y, center.getZ() + dz);
                    if (disk.target(world.getBlockState(pos))) {
                        world.setBlockState(pos, disk.place(world, pos), 2);
                    }
                }
            }
        }
    }
}
