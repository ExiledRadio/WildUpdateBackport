package com.exiledradio.wildupdatebackport.deepdark.city;

import com.exiledradio.backportcore.structure.ScatteredPlacement;
import com.exiledradio.backportcore.structure.ScatteredStructure;
import com.exiledradio.backportcore.structure.StructureRandoms;
import com.exiledradio.backportcore.terrain.WorldHeights;
import com.exiledradio.wildupdatebackport.deepdark.DeepDark;
import com.exiledradio.wildupdatebackport.deepdark.DeepDarkContent;
import com.exiledradio.wildupdatebackport.deepdark.QuietGen;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraftforge.event.terraingen.ChunkGeneratorEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.gen.structure.StructureComponent;
import net.minecraft.world.gen.structure.StructureStart;

import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

/**
 * 1.19's ancient city: structure set {@code ancient_cities} ({@code RandomSpreadStructurePlacement(24, 8, LINEAR,
 * 20083232)}), the jigsaw structure of {@link CityLayout} at y -27 (1.19's; moved up by {@link DeepDark#shift} in a
 * world that starts at y 0), valid where the deep dark is at the stub's position (1.19's {@code Structure.isValidBiome}:
 * the noise biome of the stub's quart). Built from the populate step by Backport Core's scattered driver.
 *
 * <p>Terrain: 1.19 shapes it with {@code beard_box}, a cavern the city's boxes cut out of the noise: air through each
 * rigid piece's box above its ground, ground under it, falling off past its sides, top and bottom, added to the
 * terrain's density so that it opens into the caves around it. A terrain that adapts to structures (Backport Core's
 * {@code TerrainAdaptation}, which {@link #startsNear} plans for) carves it in its own noise, as 1.19 does. Elsewhere
 * (1.12.2's terrain, which has no density to add it to) {@link Start#generateStructure} carves the same beard into
 * the blocks before the pieces are placed: {@link CityBeard}.
 */
public final class AncientCityStructure extends ScatteredStructure {

    public static final String SAVE_NAME = "WUB_AncientCity";
    public static final ScatteredPlacement PLACEMENT = new ScatteredPlacement(24, 8, 20083232);

    public AncientCityStructure() {
        super(SAVE_NAME, PLACEMENT);
    }

    /** @return the start height in this world: -27, or 32 where the world starts at y 0 */
    public static int startY(World world) {
        return CityLayout.START_Y_119 + DeepDark.shift(world);
    }

    /** The stub 1.19 checks the biome at, or null if there is no start. Draws from a fresh start random. */
    public static CityLayout.Stub stub(World world, int chunkX, int chunkZ) {
        Random random = StructureRandoms.start(world.getSeed(), chunkX, chunkZ);
        return CityLayout.start(new BlockPos(chunkX << 4, startY(world), chunkZ << 4), random);
    }

    @Override
    protected boolean canStartAt(World world, int chunkX, int chunkZ) {
        if (!DeepDarkContent.cities()) {
            return false;
        }
        CityLayout.Stub stub = stub(world, chunkX, chunkZ);
        return stub != null && DeepDark.noiseAt(world, quartPos(stub.position));
    }

    /** {@code QuartPos.fromBlock}, back to the quart's corner block: where 1.19 samples the noise biome. */
    static BlockPos quartPos(BlockPos p) {
        return new BlockPos(p.getX() >> 2 << 2, p.getY() >> 2 << 2, p.getZ() >> 2 << 2);
    }

    /** How far, in chunks, a city's pieces and their beard reach from its start chunk (116 blocks, 12 more, a piece). */
    private static final int REACH_CHUNKS = 10;

    /** True while this places starts (iterating its start map), during which a new chunk must not add to the map. */
    private boolean building;

    @Override
    public synchronized boolean generateStructure(World world, Random random, ChunkPos chunk) {
        this.building = true;
        try {
            return super.generateStructure(world, random, chunk);
        } finally {
            this.building = false;
        }
    }

    /**
     * Backport Core's terrain adaptation planner: every start whose box, grown by 12 blocks, reaches chunk (cx, cz),
     * laid out (and kept) as the populate step will lay it out. Loads no chunks: the biome check reads the terrain's
     * noise biome.
     */
    public void startsNear(World world, int cx, int cz, Consumer<StructureStart> out) {
        this.world = world;
        if (!DeepDarkContent.cities()) {
            return;
        }
        ScatteredPlacement grid = placement();
        int s = grid.spacing();
        for (int gx = Math.floorDiv(cx - REACH_CHUNKS, s); gx <= Math.floorDiv(cx + REACH_CHUNKS, s); gx++) {
            for (int gz = Math.floorDiv(cz - REACH_CHUNKS, s); gz <= Math.floorDiv(cz + REACH_CHUNKS, s); gz++) {
                ChunkPos c = grid.candidate(world.getSeed(), gx * s, gz * s);
                if (Math.abs(c.x - cx) > REACH_CHUNKS || Math.abs(c.z - cz) > REACH_CHUNKS) {
                    continue;
                }
                long key = ChunkPos.asLong(c.x, c.z);
                StructureStart start = this.structureMap.get(key);
                if (start == null) {
                    if (!canSpawnStructureAtCoords(c.x, c.z)) {
                        continue;
                    }
                    if (this.building) {
                        // A chunk generated while starts are placed (another mod's populate): the same layout, not kept.
                        start = getStructureStart(c.x, c.z);
                    } else {
                        // Lays out every start within eight chunks, each from its own seed, as the populate step does.
                        generate(world, c.x, c.z, null);
                        start = this.structureMap.get(key);
                    }
                }
                if (start != null && start.isSizeableStructure() && reaches(start.getBoundingBox(), cx, cz)) {
                    out.accept(start);
                }
            }
        }
    }

    /** Whether a box grown by 12 blocks (the beard's reach) reaches chunk (cx, cz). */
    static boolean reaches(StructureBoundingBox b, int cx, int cz) {
        int x = cx << 4;
        int z = cz << 4;
        return b != null && b.maxX + 12 >= x && b.minX - 12 <= x + 15 && b.maxZ + 12 >= z && b.minZ - 12 <= z + 15;
    }

    @Override
    protected StructureStart getStructureStart(int chunkX, int chunkZ) {
        return new Start(this.world, chunkX, chunkZ);
    }

    public static final class Start extends StructureStart {
        /** For loading. */
        public Start() {
        }

        Start(World world, int chunkX, int chunkZ) {
            super(chunkX, chunkZ);
            this.components.addAll(layout(world, chunkX, chunkZ));
            updateBoundingBox();
        }

        @Override
        public void generateStructure(World world, Random random, StructureBoundingBox box) {
            // 1.12.2's MapGenStructure clips each chunk to y 1-255; a world that goes down to -64 builds the whole height.
            StructureBoundingBox chunk = new StructureBoundingBox(box.minX, Math.min(box.minY, WorldHeights.minY(world)), box.minZ,
                    box.maxX, Math.max(box.maxY, WorldHeights.maxYExclusive(world) - 1), box.maxZ);
            try (AutoCloseable full = WorldHeights.enterFullHeight()) {
                QuietGen.run(world, () -> {
                    // Where the chunk generator let the beard into its primer (PrimerBeard), this finds it there.
                    if (!DeepDark.terrainCarvesCities(world)) {
                        CityBeard.carve(world, this.components, chunk);
                    }
                    super.generateStructure(world, random, chunk);
                });
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }

    /**
     * 1.12.2's terrain: each new chunk gets the beard of the cities that reach it in its primer, before the carvers
     * and before anything is decorated ({@link CityBeard#carvePrimer}). Not in 1.18 terrain, which has no 1.12 primer
     * for the depths and carves the beard itself.
     */
    public static final class PrimerBeard {
        @SubscribeEvent
        public void replaceBiomeBlocks(ChunkGeneratorEvent.ReplaceBiomeBlocks event) {
            World world = event.getWorld();
            if (world == null || world.isRemote || event.getPrimer() == null || world.provider.getDimension() != 0
                    || !DeepDarkContent.cities() || DeepDark.modern(world)) {
                return;
            }
            ScatteredStructure s = ScatteredStructure.of(world, com.exiledradio.backportcore.structure.Structures.ANCIENT_CITY);
            if (!(s instanceof AncientCityStructure)) {
                return;
            }
            List<StructureComponent> pieces = new java.util.ArrayList<StructureComponent>();
            ((AncientCityStructure) s).startsNear(world, event.getX(), event.getZ(), start -> pieces.addAll(start.getComponents()));
            if (!pieces.isEmpty()) {
                CityBeard.carvePrimer(world, event.getPrimer(), event.getX(), event.getZ(), pieces);
            }
        }
    }

    /** The pieces of the city starting in this chunk. Loads nothing. */
    public static List<CityPiece> layout(World world, int chunkX, int chunkZ) {
        Random random = StructureRandoms.start(world.getSeed(), chunkX, chunkZ);
        return CityLayout.assemble(new BlockPos(chunkX << 4, startY(world), chunkZ << 4), random);
    }
}
