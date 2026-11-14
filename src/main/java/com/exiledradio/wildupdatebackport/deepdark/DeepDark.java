package com.exiledradio.wildupdatebackport.deepdark;

import com.exiledradio.backportcore.terrain.WorldHeights;
import com.exiledradio.backportcore.worldgen.FlatWorlds;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.BiomeDictionary;

import javax.annotation.Nullable;
import java.lang.reflect.Method;

/**
 * Where the deep dark is (the plan's <i>Decided</i> 1).
 *
 * <ul>
 *   <li><b>1.18 terrain</b> (Caves &amp; Cliffs' {@code cavesandcliffs} worlds, through Backport Core 1.2.0's
 *       {@code ModernGeneration}): 1.19's own 3D biome, {@code minecraft:deep_dark}, at real heights.</li>
 *   <li><b>Any other overworld</b>: 1.19 picks the deep dark as a bottom biome where erosion is low (the
 *       mountains' and high ground's erosion levels 0 and 1, {@code OverworldBiomeBuilder}). 1.12.2 has no erosion,
 *       so the high ground stands in: a column whose 1.12 biome is a hills, mountain or plateau biome (Forge's
 *       {@code HILLS} or {@code MOUNTAIN}, or a base height of 0.45 and up), never an ocean, river or
 *       beach. Heights are 1.19's moved up by {@link #shift} (59 in a 0-255 world): y 1 to 40, 1.19's -58 to -19.</li>
 *   <li><b>Superflat</b>: as today's, the preset's one biome everywhere, so the deep dark only where that biome is
 *       the deep dark ({@link #flat}), at any height.</li>
 * </ul>
 */
public final class DeepDark {

    /** 1.19's deep dark heights this mod uses without 1.18 terrain, in 1.19's coordinates. */
    public static final int MIN_Y_119 = -58, MAX_Y_119 = -19;
    /** How far 1.19's heights move up in a world that starts at y 0. */
    public static final int SHIFT = 59;

    public static final String BIOME = "minecraft:deep_dark";

    private static Method biomeId;
    private static boolean looked;

    private DeepDark() {
    }

    /** The 1.18 terrain's Beardifier, whose BEARD_BOX field says it carves 1.19's beard_box (DD-5). */
    private static final String TERRAIN_BEARDIFIER = "com.exiledradio.cavesandcliffsbackport.terrain.core.Beardifier";
    private static Boolean beardBox;

    /**
     * @return true where the world's terrain carves an ancient city's {@code beard_box} in its own noise: 1.18 terrain
     *     whose Beardifier has it (Backport Core's {@code TerrainAdaptation} has no way to ask yet). Elsewhere the city
     *     carves its beard into the blocks itself.
     */
    public static boolean terrainCarvesCities(World world) {
        if (!modern(world)) {
            return false;
        }
        if (beardBox == null) {
            boolean found;
            try {
                Class.forName(TERRAIN_BEARDIFIER, false, DeepDark.class.getClassLoader()).getField("BEARD_BOX");
                found = true;
            } catch (ReflectiveOperationException | LinkageError e) {
                found = false;
            }
            beardBox = found;
        }
        return beardBox;
    }

    /** @return how far 1.19's y moves in this world: 0 where the world goes down to y -64, else 59 */
    public static int shift(World world) {
        return WorldHeights.minY(world) < 0 ? 0 : SHIFT;
    }

    /** @return true when 1.18 terrain answers this world's 3D biomes */
    public static boolean modern(World world) {
        return modernBiome(world, BlockPos.ORIGIN) != null;
    }

    /** @return the 1.18 terrain's biome id at a block, or null in a world without it */
    @Nullable
    public static String modernBiome(World world, BlockPos pos) {
        Method m = method();
        if (m == null) {
            return null;
        }
        try {
            return (String) m.invoke(null, world, pos);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    @Nullable
    private static synchronized Method method() {
        if (!looked) {
            looked = true;
            try {
                biomeId = Class.forName("com.exiledradio.backportcore.terrain.ModernGeneration")
                        .getMethod("biomeId", World.class, BlockPos.class);
            } catch (ReflectiveOperationException | LinkageError e) {
                biomeId = null;
            }
        }
        return biomeId;
    }

    /**
     * 1.19's {@code Structure.isValidBiome}: the noise biome of the quart (not the zoomed biome a block reports, which
     * may be a neighbor's). Caves &amp; Cliffs' generator keeps its terrain core's {@code BiomeSource} behind its 3D
     * biomes; reached by reflection until Backport Core's {@code ModernGeneration} offers it
     * ({@code docs/CORE_REQUESTS.md} DD-1). Anywhere else, {@link #at}.
     */
    public static boolean noiseAt(World world, BlockPos pos) {
        String id = noiseBiome(world, pos.getX() >> 2, pos.getY() >> 2, pos.getZ() >> 2);
        return id != null ? BIOME.equals(id) : at(world, pos);
    }

    private static boolean noiseLooked;
    private static java.lang.reflect.Field biomes3d, source;
    private static Method sourceBiome, biomeName;

    /** @return the 1.18 noise biome at a quart, or null when this world has none we can reach */
    @Nullable
    static String noiseBiome(World world, int qx, int qy, int qz) {
        if (!(world instanceof net.minecraft.world.WorldServer)) {
            return null;
        }
        Object generator = ((net.minecraft.world.WorldServer) world).getChunkProvider().chunkGenerator;
        if (generator == null || !generator.getClass().getName().endsWith(".terrain.ModernGenerator")) {
            return null;
        }
        try {
            synchronized (DeepDark.class) {
                if (!noiseLooked) {
                    noiseLooked = true;
                    biomes3d = generator.getClass().getDeclaredField("biomes3d");
                    biomes3d.setAccessible(true);
                    Class<?> chunkBiomes = biomes3d.getType();
                    source = chunkBiomes.getDeclaredField("source");
                    source.setAccessible(true);
                    sourceBiome = source.getType().getMethod("biome", int.class, int.class, int.class);
                    biomeName = Class.forName(source.getType().getPackage().getName() + ".Biomes").getMethod("name", int.class);
                }
            }
            if (sourceBiome == null) {
                return null;
            }
            Object chunks = biomes3d.get(generator);
            synchronized (chunks) {
                Object src = source.get(chunks);
                int id = (Integer) sourceBiome.invoke(src, qx, qy, qz);
                return "minecraft:" + biomeName.invoke(null, id);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            synchronized (DeepDark.class) {
                if (sourceBiome != null) {
                    com.exiledradio.wildupdatebackport.WildUpdateBackport.LOGGER.warn(
                            "Caves & Cliffs' noise biome is out of reach; ancient cities check its zoomed biome instead.", e);
                }
                sourceBiome = null;
            }
            return null;
        }
    }

    /** @return true if this block is in the deep dark */
    public static boolean at(World world, BlockPos pos) {
        if (world.provider.getDimension() != 0 || !DeepDarkContent.enabled()) {
            return false;
        }
        String modern = modernBiome(world, pos);
        if (modern != null) {
            return BIOME.equals(modern);
        }
        if (FlatWorlds.isFlat(world)) {
            return flat(world.getBiomeProvider().getBiome(pos, null));
        }
        int y119 = pos.getY() - shift(world);
        return y119 >= MIN_Y_119 && y119 <= MAX_Y_119 && highGround(world.getBiomeProvider().getBiome(pos, null));
    }

    /** @return whether a superflat world's biome is the deep dark: only the deep dark biome itself */
    static boolean flat(@Nullable Biome biome) {
        return biome != null && biome == DeepDarkContent.biome();
    }

    /** The stand-in for 1.19's low erosion in a 1.12 biome map. */
    static boolean highGround(@Nullable Biome biome) {
        if (biome == null) {
            return false;
        }
        if (BiomeDictionary.hasType(biome, BiomeDictionary.Type.OCEAN) || BiomeDictionary.hasType(biome, BiomeDictionary.Type.RIVER)
                || BiomeDictionary.hasType(biome, BiomeDictionary.Type.BEACH)) {
            return false;
        }
        return biome.getBaseHeight() >= 0.45F || BiomeDictionary.hasType(biome, BiomeDictionary.Type.HILLS)
                || BiomeDictionary.hasType(biome, BiomeDictionary.Type.MOUNTAIN);
    }
}
