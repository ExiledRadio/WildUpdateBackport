package net.minecraft.world.level.levelgen.structure;

import com.mojang.datafixers.util.Either;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.data.BuiltinRegistries;
import net.minecraft.data.worldgen.Structures;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.VanillaPackResources;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.structure.pools.FeaturePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.ListPoolElement;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.LevelStorageSource;

import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Vanilla 1.19.2's ancient cities, for Wild Update Backport's deep dark self-test. Run on Mojang's Mojang-named client jar
 * and its libraries (Java 17), G:/MCDownloads/minecraft-1.19.2 (see tools/deepdark/oracle.sh).
 *
 * For each seed, every grid candidate of the ancient_cities set (RandomSpreadStructurePlacement(24, 8, LINEAR, 20083232))
 * in regions -R..R-1: its chunk, the jigsaw's generation stub (where Structure.isValidBiome samples) and the overworld noise
 * biome there ("cand" lines). For the first PIECES candidates of each seed (whatever their biome), every piece of the city
 * vanilla lays out there ("piece" lines: element, rotation, box).
 *
 * Arguments: out.txt regions pieces seeds...
 */
public final class AncientCityOracle119 {
    /**
     * The terrain's beard around a city: Beardifier.forStructuresInChunk's pieces and junctions for a few chunks of
     * it, and compute at points in them, as "beard seed cx cz x y z bits" (Double.doubleToLongBits).
     */
    static void dumpBeard(PrintWriter out, long seed, ChunkPos start, List<StructurePiece> pieces, TerrainAdjustment adjustment) {
        int[][] offsets = {{0, 0}, {2, 1}, {-3, 0}, {1, -4}, {-5, 3}};
        for (int[] o : offsets) {
            ChunkPos chunk = new ChunkPos(start.x + o[0], start.z + o[1]);
            int minX = chunk.getMinBlockX();
            int minZ = chunk.getMinBlockZ();
            it.unimi.dsi.fastutil.objects.ObjectArrayList<net.minecraft.world.level.levelgen.Beardifier.Rigid> rigids =
                    new it.unimi.dsi.fastutil.objects.ObjectArrayList<>();
            it.unimi.dsi.fastutil.objects.ObjectArrayList<net.minecraft.world.level.levelgen.structure.pools.JigsawJunction> junctions =
                    new it.unimi.dsi.fastutil.objects.ObjectArrayList<>();
            for (StructurePiece piece : pieces) {
                if (!piece.isCloseToChunk(chunk, 12)) {
                    continue;
                }
                PoolElementStructurePiece pp = (PoolElementStructurePiece) piece;
                if (pp.getElement().getProjection() == net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool.Projection.RIGID) {
                    rigids.add(new net.minecraft.world.level.levelgen.Beardifier.Rigid(pp.getBoundingBox(), adjustment, pp.getGroundLevelDelta()));
                }
                for (net.minecraft.world.level.levelgen.structure.pools.JigsawJunction j : pp.getJunctions()) {
                    if (j.getSourceX() > minX - 12 && j.getSourceZ() > minZ - 12 && j.getSourceX() < minX + 15 + 12
                            && j.getSourceZ() < minZ + 15 + 12) {
                        junctions.add(j);
                    }
                }
            }
            net.minecraft.world.level.levelgen.Beardifier beard =
                    new net.minecraft.world.level.levelgen.Beardifier(rigids.listIterator(), junctions.listIterator());
            java.util.Random r = new java.util.Random(seed ^ chunk.toLong());
            for (int i = 0; i < 64; i++) {
                int x = minX + r.nextInt(16);
                int y = -64 + r.nextInt(64);
                int z = minZ + r.nextInt(16);
                double v = beard.compute(new net.minecraft.world.level.levelgen.DensityFunction.SinglePointContext(x, y, z));
                out.println("beard " + seed + " " + start.x + " " + start.z + " " + x + " " + y + " " + z + " " + Double.doubleToLongBits(v));
            }
        }
    }

    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        int regions = Integer.parseInt(args[1]);
        int piecesFor = Integer.parseInt(args[2]);
        RegistryAccess access = RegistryAccess.BUILTIN.get();
        Registry<Biome> biomes = access.registryOrThrow(Registry.BIOME_REGISTRY);
        BiomeSource biomeSource = MultiNoiseBiomeSource.Preset.OVERWORLD.biomeSource(biomes);
        NoiseBasedChunkGenerator generator = new NoiseBasedChunkGenerator(access.registryOrThrow(Registry.STRUCTURE_SET_REGISTRY),
                access.registryOrThrow(Registry.NOISE_REGISTRY), biomeSource,
                access.registryOrThrow(Registry.NOISE_GENERATOR_SETTINGS_REGISTRY).getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD));
        Path tmp = Files.createTempDirectory("ancient-city-oracle");
        LevelStorageSource.LevelStorageAccess storage = LevelStorageSource.createDefault(tmp).createAccess("oracle");
        MultiPackResourceManager resources = new MultiPackResourceManager(PackType.SERVER_DATA,
                List.<PackResources>of(new VanillaPackResources(ServerPacksSource.BUILT_IN_METADATA, "minecraft")));
        StructureTemplateManager templates = new StructureTemplateManager(resources, storage, DataFixers.getDataFixer());
        LevelHeightAccessor height = LevelHeightAccessor.create(-64, 384);
        RandomSpreadStructurePlacement placement = new RandomSpreadStructurePlacement(24, 8, RandomSpreadType.LINEAR, 20083232);
        Structure city = Structures.ANCIENT_CITY.value();
        try (PrintWriter out = new PrintWriter(args[0], "UTF-8")) {
            out.println("version 1.19.2");
            for (int s = 3; s < args.length; s++) {
                long seed = Long.parseLong(args[s]);
                RandomState random = RandomState.create(BuiltinRegistries.NOISE_GENERATOR_SETTINGS.getOrThrow(NoiseGeneratorSettings.OVERWORLD),
                        BuiltinRegistries.NOISE, seed);
                int dumped = 0;
                int valid = 0;
                for (int rx = -regions; rx < regions; rx++) {
                    for (int rz = -regions; rz < regions; rz++) {
                        ChunkPos c = placement.getPotentialStructureChunk(seed, rx * 24, rz * 24);
                        Structure.GenerationContext ctx = new Structure.GenerationContext(access, generator, biomeSource, random, templates,
                                seed, c, height, b -> true);
                        Optional<Structure.GenerationStub> stub = city.findGenerationPoint(ctx);
                        if (stub.isEmpty()) {
                            out.println("cand " + seed + " " + c.x + " " + c.z + " none");
                            continue;
                        }
                        BlockPos p = stub.get().position();
                        Holder<Biome> b = biomeSource.getNoiseBiome(QuartPos.fromBlock(p.getX()), QuartPos.fromBlock(p.getY()),
                                QuartPos.fromBlock(p.getZ()), random.sampler());
                        String name = b.unwrapKey().get().location().getPath();
                        if (name.equals("deep_dark")) {
                            valid++;
                        }
                        out.println("cand " + seed + " " + c.x + " " + c.z + " " + p.getX() + " " + p.getY() + " " + p.getZ() + " " + name);
                        if (dumped < piecesFor) {
                            dumped++;
                            List<StructurePiece> pieces = stub.get().getPiecesBuilder().build().pieces();
                            int i = 0;
                            for (StructurePiece piece : pieces) {
                                PoolElementStructurePiece pp = (PoolElementStructurePiece) piece;
                                BoundingBox box = pp.getBoundingBox();
                                out.println("piece " + seed + " " + c.x + " " + c.z + " " + (i++) + " " + describe(pp.getElement()) + " "
                                        + pp.getRotation().name() + " " + box.minX() + " " + box.minY() + " " + box.minZ() + " " + box.maxX()
                                        + " " + box.maxY() + " " + box.maxZ());
                            }
                            dumpBeard(out, seed, c, pieces, city.terrainAdaptation());
                        }
                    }
                }
                System.out.println("seed " + seed + ": " + valid + " ancient cities in " + (4 * regions * regions) + " cells");
            }
        }
        System.out.println("wrote " + args[0]);
        System.exit(0);
    }

    static String describe(StructurePoolElement e) throws Exception {
        if (e instanceof SinglePoolElement) {
            Field f = SinglePoolElement.class.getDeclaredField("template");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            Either<ResourceLocation, ?> either = (Either<ResourceLocation, ?>) f.get(e);
            return either.left().map(ResourceLocation::toString).orElse("?");
        }
        if (e instanceof ListPoolElement) {
            Field f = ListPoolElement.class.getDeclaredField("elements");
            f.setAccessible(true);
            List<?> list = (List<?>) f.get(e);
            return "list:" + describe((StructurePoolElement) list.get(0));
        }
        if (e instanceof FeaturePoolElement) {
            return "feature";
        }
        return "empty";
    }
}
