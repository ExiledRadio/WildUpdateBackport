package com.exiledradio.wildupdatebackport.deepdark.city;

import com.exiledradio.backportcore.structure.ScatteredStructure;
import com.exiledradio.backportcore.structure.Structures;
import com.exiledradio.wildupdatebackport.deepdark.DeepDark;
import com.exiledradio.wildupdatebackport.deepdark.DeepDarkContent;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkPrimer;
import net.minecraft.world.gen.MapGenBase;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.gen.structure.StructureComponent;
import net.minecraftforge.event.terraingen.InitMapGenEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * No liquid in an ancient city's space, as in 26.3. There the city's {@code beard_box} is in the noise, the deep
 * dark's aquifer holds no fluid, its biome has no lakes and no springs, and the carvers fill with lava only at and
 * below y -56. 1.12.2's generation around a city in 1.12 terrain (vanilla's and RTG's alike) has three things 26.3
 * does not:
 *
 * <ul>
 *   <li>1.12.2's carvers ({@code MapGenCaves}, {@code MapGenRavine}) fill with lava up to y 10, which is 1.19's -49
 *       moved up by {@link DeepDark#SHIFT}: the bottom of every city (y 8 here) stands in it. Around a city, the
 *       carvers' lava above y {@link #LAVA_LEVEL} (1.19's -56) becomes the air a 1.19 carver leaves there
 *       ({@link Carvers}, after each carver, in the chunk's primer).</li>
 *   <li>Springs ({@code WorldGenLiquids}) and lakes ({@code WorldGenLakes}), 1.12.2's and RTG's: none whose liquid
 *       could reach a city (patches P19 and P20, {@link #springBlock} and {@link #lakeHeight}).</li>
 *   <li>Anything else that left liquid in the cavern before the city is built (another mod's pond): the populate-time
 *       carve clears it with the rock ({@link CityBeard}).</li>
 * </ul>
 *
 * <p>1.18 terrain (Caves &amp; Cliffs') has 1.18's carvers and features, so none of this applies there.
 */
public final class CityLiquids {

    /** 1.19's carver lava level (above_bottom 8: y -56), moved up by {@link DeepDark#SHIFT}: lava at and below this. */
    static final int LAVA_LEVEL = -56 + DeepDark.SHIFT;
    /** 1.12.2's carvers put lava where {@code y - 1 < 10}. */
    static final int LAVA_TOP_112 = 10;
    /** How far past a piece the beard carves: the city's space ends here. */
    static final int BEARD = 12;
    /** How far past the city's space a spring's water runs (7 blocks, and lava 3), with a block to spare. */
    static final int FLOW = 8;
    /** Lava's run in the Overworld (3), with a block to spare. */
    static final int LAVA_FLOW = 4;

    private CityLiquids() {
    }

    // ---- the city's pieces near a chunk

    /** By world: recently asked chunks' piece boxes ({minX, minY, minZ, maxX, maxY, maxZ} each), most recent last. */
    private static final Map<World, Map<Long, int[][]>> BOXES = new WeakHashMap<World, Map<Long, int[][]>>();
    private static final int[][] NONE = new int[0][];
    private static final int CACHE = 512;

    /**
     * @return the boxes of the city pieces whose beard reaches chunk (cx, cz) (feature elements, which take no room,
     *     left out); laid out as the populate step lays them out, and kept, as {@link AncientCityStructure#startsNear}
     */
    static int[][] boxes(World world, int cx, int cz) {
        Map<Long, int[][]> known;
        synchronized (BOXES) {
            known = BOXES.get(world);
            if (known == null) {
                known = new LinkedHashMap<Long, int[][]>(CACHE, 0.75F, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<Long, int[][]> eldest) {
                        return size() > CACHE;
                    }
                };
                BOXES.put(world, known);
            }
            int[][] b = known.get(ChunkPos.asLong(cx, cz));
            if (b != null) {
                return b;
            }
        }
        int[][] b = NONE;
        if (world.provider.getDimension() == 0 && DeepDarkContent.cities()) {
            ScatteredStructure s = ScatteredStructure.of(world, Structures.ANCIENT_CITY);
            if (s instanceof AncientCityStructure) {
                List<StructureComponent> pieces = new ArrayList<StructureComponent>();
                ((AncientCityStructure) s).startsNear(world, cx, cz, start -> pieces.addAll(start.getComponents()));
                List<int[]> out = new ArrayList<int[]>();
                for (StructureComponent c : pieces) {
                    if (c instanceof CityPiece && !((CityPiece) c).isFeature()) {
                        StructureBoundingBox x = c.getBoundingBox();
                        out.add(new int[] {x.minX, x.minY, x.minZ, x.maxX, x.maxY, x.maxZ});
                    }
                }
                b = out.isEmpty() ? NONE : out.toArray(new int[0][]);
            }
        }
        synchronized (BOXES) {
            known.put(ChunkPos.asLong(cx, cz), b);
        }
        return b;
    }

    /**
     * @return true if the box from (x0, y0, z0) to (x1, y1, z1) comes within {@code grow} blocks (sideways, and below) or
     *     {@code up} blocks (above) of a city piece
     */
    static boolean near(World world, int x0, int y0, int z0, int x1, int y1, int z1, int grow, int up) {
        for (int cx = (x0 - grow) >> 4; cx <= (x1 + grow) >> 4; cx++) {
            for (int cz = (z0 - grow) >> 4; cz <= (z1 + grow) >> 4; cz++) {
                for (int[] b : boxes(world, cx, cz)) {
                    if (x1 >= b[0] - grow && x0 <= b[3] + grow && z1 >= b[2] - grow && z0 <= b[5] + grow
                            && y1 >= b[1] - grow && y0 <= b[4] + up) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // ---- P19 and P20

    /**
     * P19: {@code WorldGenLiquids.generate}'s first block read (the stone it needs above the spring) goes through here.
     * Where the spring's water or lava could run into a city, the answer is air, and the spring is not placed: 26.3's
     * deep dark has no springs.
     */
    public static IBlockState springBlock(World world, BlockPos above) {
        if (!world.isRemote && above.getY() < 128) {
            int x = above.getX(), y = above.getY() - 1, z = above.getZ();
            try {
                // Water runs 7 out and falls; lava runs 3.
                if (near(world, x, y, z, x, y, z, BEARD + FLOW, BEARD + FLOW)) {
                    return Blocks.AIR.getDefaultState();
                }
            } catch (RuntimeException e) {
                com.exiledradio.wildupdatebackport.WildUpdateBackport.LOGGER.warn("Could not look for ancient cities near a spring at {}.", above, e);
            }
        }
        return world.getBlockState(above);
    }

    /**
     * P20: {@code WorldGenLakes.generate}'s height test ({@code position.getY() <= 4}: no lake), once it has gone down
     * through the air, goes through here with the lake's corner. A lake whose 16 x 8 x 16 could reach a city answers
     * 0, and is not placed: 26.3's deep dark has no lakes.
     */
    public static int lakeHeight(int y, BlockPos corner, World world) {
        if (!world.isRemote && y > 4 && y < 128) {
            try {
                int x = corner.getX(), z = corner.getZ();
                // The lake fills from y - 4 to y + 3, then its lava or water edge runs.
                if (near(world, x, y - 4, z, x + 15, y + 3, z + 15, BEARD + FLOW, BEARD + FLOW)) {
                    return 0;
                }
            } catch (RuntimeException e) {
                com.exiledradio.wildupdatebackport.WildUpdateBackport.LOGGER.warn("Could not look for ancient cities near a lake at {}.", corner, e);
            }
        }
        return y;
    }

    // ---- the carvers' lava

    /**
     * Wraps the cave and ravine carvers of 1.12 terrain (vanilla's, RTG's, or whatever another mod put there before
     * us) so that around a city, each leaves 1.19's lava level: lava from {@link #LAVA_LEVEL} + 1 to 10 becomes air.
     */
    public static final class Carvers {
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void initMapGen(InitMapGenEvent event) {
            if ((event.getType() == InitMapGenEvent.EventType.CAVE || event.getType() == InitMapGenEvent.EventType.RAVINE)
                    && event.getNewGen() != null && !(event.getNewGen() instanceof Carver)) {
                event.setNewGen(new Carver(event.getNewGen()));
            }
        }
    }

    /** A carver, then 1.19's lava level around a city. */
    static final class Carver extends MapGenBase {
        private final MapGenBase carver;

        Carver(MapGenBase carver) {
            this.carver = carver;
        }

        @Override
        public void generate(World world, int chunkX, int chunkZ, ChunkPrimer primer) {
            this.carver.generate(world, chunkX, chunkZ, primer);
            if (world != null && !world.isRemote && world.provider.getDimension() == 0 && DeepDarkContent.cities()
                    && DeepDark.shift(world) == DeepDark.SHIFT && !DeepDark.modern(world)) {
                lavaLevel(primer, chunkX, chunkZ, boxes(world, chunkX, chunkZ));
            }
        }
    }

    /** @return how many lava blocks over 1.19's lava level, within the city's space and a flow of it, became air */
    static int lavaLevel(ChunkPrimer primer, int chunkX, int chunkZ, int[][] boxes) {
        if (boxes.length == 0) {
            return 0;
        }
        int changed = 0;
        int bx = chunkX << 4, bz = chunkZ << 4;
        int grow = BEARD + LAVA_FLOW;
        IBlockState air = Blocks.AIR.getDefaultState();
        for (int[] b : boxes) {
            if (b[1] - grow > LAVA_TOP_112 || b[4] + grow <= LAVA_LEVEL) {
                continue;
            }
            int x0 = Math.max(bx, b[0] - grow), x1 = Math.min(bx + 15, b[3] + grow);
            int z0 = Math.max(bz, b[2] - grow), z1 = Math.min(bz + 15, b[5] + grow);
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    for (int y = LAVA_LEVEL + 1; y <= LAVA_TOP_112; y++) {
                        IBlockState s = primer.getBlockState(x - bx, y, z - bz);
                        if (s.getMaterial() == Material.LAVA) {
                            primer.setBlockState(x - bx, y, z - bz, air);
                            changed++;
                        }
                    }
                }
            }
        }
        return changed;
    }
}
