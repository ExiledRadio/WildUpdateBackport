package com.exiledradio.wildupdatebackport.deepdark.city;

import com.exiledradio.backportcore.structure.ScatteredPlacement;
import com.exiledradio.backportcore.structure.ScatteredStructure;
import com.exiledradio.backportcore.structure.StructureRandoms;
import com.exiledradio.backportcore.terrain.WorldHeights;
import com.exiledradio.wildupdatebackport.deepdark.DeepDark;
import com.exiledradio.wildupdatebackport.deepdark.DeepDarkContent;
import com.exiledradio.wildupdatebackport.deepdark.QuietGen;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.gen.structure.StructureComponent;
import net.minecraft.world.gen.structure.StructureStart;

import java.util.List;
import java.util.Random;

/**
 * 1.19's ancient city: structure set {@code ancient_cities} ({@code RandomSpreadStructurePlacement(24, 8, LINEAR,
 * 20083232)}), the jigsaw structure of {@link CityLayout} at y -27 (1.19's; moved up by {@link DeepDark#shift} in a
 * world that starts at y 0), valid where the deep dark is at the stub's position (1.19's {@code Structure.isValidBiome}:
 * the noise biome of the stub's quart). Built from the populate step by Backport Core's scattered driver.
 *
 * <p>Terrain: 1.19 shapes it with {@code beard_box}, a cavern the city's boxes cut out of the noise. Neither 1.12.2's
 * terrain nor Caves &amp; Cliffs' 1.18.2 has it, so each chunk of the city is cleared to air in every rigid piece's box
 * above its floor and filled below it ({@link Start#generateStructure}) before the pieces are placed; the beard's
 * twelve-block falloff around the boxes is left out.
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
                    beardBox(world, chunk);
                    super.generateStructure(world, random, chunk);
                });
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        /** The stand-in for {@code beard_box} described on the class. */
        void beardBox(World world, StructureBoundingBox chunk) {
            IBlockState air = Blocks.AIR.getDefaultState();
            IBlockState fill = DeepDarkContent.fillBlock();
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (StructureComponent c : this.components) {
                if (!(c instanceof CityPiece) || ((CityPiece) c).isFeature() || !((CityPiece) c).element().rigid()) {
                    continue;
                }
                StructureBoundingBox b = c.getBoundingBox();
                if (!b.intersectsWith(chunk)) {
                    continue;
                }
                int floor = b.minY;
                int x0 = Math.max(b.minX, chunk.minX), x1 = Math.min(b.maxX, chunk.maxX);
                int z0 = Math.max(b.minZ, chunk.minZ), z1 = Math.min(b.maxZ, chunk.maxZ);
                int bottom = Math.max(WorldHeights.minY(world) + 1, floor - 2);
                for (int x = x0; x <= x1; x++) {
                    for (int z = z0; z <= z1; z++) {
                        for (int y = bottom; y <= b.maxY; y++) {
                            p.setPos(x, y, z);
                            IBlockState s = world.getBlockState(p);
                            if (CityProcessors.cannotReplace(s.getBlock())) {
                                continue;
                            }
                            if (y >= floor) {
                                if (s.getMaterial() != net.minecraft.block.material.Material.AIR) {
                                    world.setBlockState(p, air, 2);
                                }
                            } else if (!s.isFullCube()) {
                                world.setBlockState(p, fill, 2);
                            }
                        }
                    }
                }
            }
        }
    }

    /** The pieces of the city starting in this chunk. Loads nothing. */
    public static List<CityPiece> layout(World world, int chunkX, int chunkZ) {
        Random random = StructureRandoms.start(world.getSeed(), chunkX, chunkZ);
        return CityLayout.assemble(new BlockPos(chunkX << 4, startY(world), chunkZ << 4), random);
    }
}
