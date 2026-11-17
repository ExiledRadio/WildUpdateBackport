package com.exiledradio.wildupdatebackport.deepdark.city;

import com.exiledradio.wildupdatebackport.deepdark.DeepDark;
import com.exiledradio.wildupdatebackport.deepdark.DeepDarkContent;
import com.exiledradio.wildupdatebackport.sculk.BlockSculkVein;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkPrimer;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.gen.structure.StructureComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * 1.19.2's {@code beard_box} for a terrain that cannot add it to its own density (1.12.2's): the Beardifier's sum over
 * the city's rigid pieces ({@code getBeardContribution(dx, distance outside the box vertically, dz, y - ground) * 0.8})
 * and its junctions ({@code * 0.4}), exactly as 1.19.2 computes it, added to a density read off the blocks already
 * there: {@link #ROCK} for rock, minus it for open space. That is the final density of solid rock deep underground in
 * 1.18 and later ({@code squeeze} of a clamped 1) and of open cave, so the beard carves and fills where vanilla's would
 * in rock and in cave: air through each box above its ground and about a block past its sides and top, ground a block
 * or so under it, junctions sloping into their neighbors.
 */
public final class CityBeard {

    /** {@code squeeze(1)}: 1/2 - 1/24. */
    static final double ROCK = 0.5 - 1.0 / 24.0;

    /** 1.19.2's BEARD_KERNEL: exp(-lengthSquared(x, y + 0.5, z) / 16), x, y and z -12..11, as floats. */
    private static final float[] KERNEL = new float[24 * 24 * 24];

    static {
        for (int i = 0; i < 24; i++) {
            for (int j = 0; j < 24; j++) {
                for (int k = 0; k < 24; k++) {
                    double x = j - 12;
                    double y = (double) (k - 12) + 0.5D;
                    double z = i - 12;
                    KERNEL[i * 24 * 24 + j * 24 + k] = (float) Math.pow(Math.E, -(x * x + y * y + z * z) / 16.0D);
                }
            }
        }
    }

    private CityBeard() {
    }

    /**
     * 1.19.2's terrain beard at a block: {@code Beardifier.compute} of the chunk holding it over a city's pieces (every
     * rigid one {@code beard_box}), as the 1.18 terrain adds it to its density.
     */
    public static double density(List<? extends StructureComponent> components, int x, int y, int z) {
        List<int[]> rigids = new ArrayList<int[]>();
        List<int[]> junctions = new ArrayList<int[]>();
        for (StructureComponent c : components) {
            if (c instanceof CityPiece) {
                CityPiece piece = (CityPiece) c;
                StructureBoundingBox b = piece.getBoundingBox();
                if (piece.rigid()) {
                    rigids.add(new int[] {b.minX, b.minY + piece.groundLevelDelta(), b.minZ, b.maxX, b.maxY, b.maxZ});
                }
                int[] j = piece.junctions();
                for (int n = 0; n < j.length; n += 3) {
                    junctions.add(new int[] {j[n], j[n + 1], j[n + 2]});
                }
            }
        }
        return compute(rigids, junctions, x, y, z);
    }

    /** Where a carve reads and writes blocks: the world, or a chunk's primer. */
    interface Blocks2 {
        IBlockState get(int x, int y, int z);

        void set(int x, int y, int z, IBlockState state);
    }

    /**
     * Carves the city's beard into a chunk as it is generated (Forge's {@code ReplaceBiomeBlocks}, before 1.12.2's
     * carvers): the nearest 1.12 has to 1.19 adding it to the noise, so it is there before any chunk around is
     * decorated and no sculk grows on rock it takes away. Heights 0 to 255.
     */
    public static void carvePrimer(World world, ChunkPrimer primer, int chunkX, int chunkZ, List<StructureComponent> components) {
        int bx = chunkX << 4;
        int bz = chunkZ << 4;
        StructureBoundingBox chunk = new StructureBoundingBox(bx, 0, bz, bx + 15, 255, bz + 15);
        carve(components, chunk, 0, 255, new Blocks2() {
            @Override
            public IBlockState get(int x, int y, int z) {
                return primer.getBlockState(x - bx, y, z - bz);
            }

            @Override
            public void set(int x, int y, int z, IBlockState state) {
                primer.setBlockState(x - bx, y, z - bz, state);
            }
        });
    }

    /**
     * Carves the beard of a city's pieces into one populate area (x and z of {@code chunk}), for a chunk generator
     * that does not let it into the primer; where it did, this finds the beard already there.
     */
    static void carve(World world, List<StructureComponent> components, StructureBoundingBox chunk) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        carve(components, chunk, DeepDark.minY(world) + 1, DeepDark.maxYExclusive(world) - 1, new Blocks2() {
            @Override
            public IBlockState get(int x, int y, int z) {
                return world.getBlockState(p.setPos(x, y, z));
            }

            @Override
            public void set(int x, int y, int z, IBlockState state) {
                world.setBlockState(p.setPos(x, y, z), state, 2);
            }
        });
    }

    private static void carve(List<StructureComponent> components, StructureBoundingBox chunk, int minY, int maxY, Blocks2 blocks) {
        List<int[]> rigids = new ArrayList<int[]>();
        List<int[]> junctions = new ArrayList<int[]>();
        int lowY = Integer.MAX_VALUE;
        int highY = Integer.MIN_VALUE;
        for (StructureComponent c : components) {
            if (!(c instanceof CityPiece)) {
                continue;
            }
            CityPiece piece = (CityPiece) c;
            StructureBoundingBox b = piece.getBoundingBox();
            if (b.maxX + 12 < chunk.minX || b.minX - 12 > chunk.maxX || b.maxZ + 12 < chunk.minZ || b.minZ - 12 > chunk.maxZ) {
                continue;
            }
            if (piece.rigid()) {
                int ground = b.minY + piece.groundLevelDelta();
                rigids.add(new int[] {b.minX, ground, b.minZ, b.maxX, b.maxY, b.maxZ});
                lowY = Math.min(lowY, Math.min(ground, b.maxY) - 11);
                highY = Math.max(highY, Math.max(ground, b.maxY) + 11);
            }
            int[] j = piece.junctions();
            for (int n = 0; n < j.length; n += 3) {
                junctions.add(new int[] {j[n], j[n + 1], j[n + 2]});
                lowY = Math.min(lowY, j[n + 1] - 12);
                highY = Math.max(highY, j[n + 1] + 11);
            }
        }
        if (rigids.isEmpty() && junctions.isEmpty()) {
            return;
        }
        lowY = Math.max(lowY, minY);
        highY = Math.min(highY, maxY);
        IBlockState air = Blocks.AIR.getDefaultState();
        IBlockState fill = DeepDarkContent.fillBlock();
        for (int x = chunk.minX; x <= chunk.maxX; x++) {
            for (int z = chunk.minZ; z <= chunk.maxZ; z++) {
                for (int y = lowY; y <= highY; y++) {
                    double beard = compute(rigids, junctions, x, y, z);
                    if (beard == 0.0D) {
                        continue;
                    }
                    IBlockState s = blocks.get(x, y, z);
                    // Veins a neighbor's decoration grew in the cavern already carved (the primer's) stay.
                    if (CityProcessors.cannotReplace(s.getBlock()) || BlockSculkVein.isVein(s)) {
                        continue;
                    }
                    Material m = s.getMaterial();
                    boolean open = m == Material.AIR || m.isLiquid();
                    if (!open && ROCK + beard < 0.0D) {
                        blocks.set(x, y, z, air);
                    } else if (open && beard - ROCK > 0.0D && s != fill) {
                        blocks.set(x, y, z, fill);
                    }
                }
            }
        }
    }

    /**
     * 1.19.2's {@code Beardifier.compute} at a block, every piece {@code beard_box}: the pieces, then the junctions
     * that its chunk's Beardifier keeps (sources inside the chunk grown by 12, exclusive).
     */
    static double compute(List<int[]> rigids, List<int[]> junctions, int x, int y, int z) {
        double d = 0.0D;
        for (int[] r : rigids) {
            int dx = Math.max(0, Math.max(r[0] - x, x - r[3]));
            int dz = Math.max(0, Math.max(r[2] - z, z - r[5]));
            int toGround = y - r[1];
            int dy = Math.max(0, Math.max(r[1] - y, y - r[4]));
            d += contribution(dx, dy, dz, toGround) * 0.8D;
        }
        int cx = x >> 4 << 4;
        int cz = z >> 4 << 4;
        for (int[] j : junctions) {
            if (j[0] > cx - 12 && j[2] > cz - 12 && j[0] < cx + 15 + 12 && j[2] < cz + 15 + 12) {
                int dy = y - j[1];
                d += contribution(x - j[0], dy, z - j[2], dy) * 0.4D;
            }
        }
        return d;
    }

    /** 1.19.2's {@code getBeardContribution}. */
    static double contribution(int x, int y, int z, int yToGround) {
        int i = x + 12;
        int j = y + 12;
        int k = z + 12;
        if (i < 0 || i >= 24 || j < 0 || j >= 24 || k < 0 || k >= 24) {
            return 0.0D;
        }
        double d = (double) yToGround + 0.5D;
        double e = (double) x * (double) x + d * d + (double) z * (double) z;
        double f = -d * fastInvSqrt(e / 2.0D) / 2.0D;
        return f * (double) KERNEL[k * 24 * 24 + i * 24 + j];
    }

    /** {@code Mth.fastInvSqrt}. */
    private static double fastInvSqrt(double v) {
        double half = 0.5D * v;
        long i = Double.doubleToRawLongBits(v);
        i = 6910469410427058090L - (i >> 1);
        v = Double.longBitsToDouble(i);
        return v * (1.5D - half * v * v);
    }
}
