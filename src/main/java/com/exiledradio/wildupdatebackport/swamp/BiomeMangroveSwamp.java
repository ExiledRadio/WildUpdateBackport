package com.exiledradio.wildupdatebackport.swamp;

import com.exiledradio.wildupdatebackport.mud.MudContent;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeDecorator;
import net.minecraft.world.chunk.ChunkPrimer;
import net.minecraft.world.gen.feature.WorldGenFossils;
import net.minecraftforge.event.terraingen.DecorateBiomeEvent;
import net.minecraftforge.event.terraingen.TerrainGen;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Random;

/**
 * 1.19's mangrove swamp ({@code OverworldBiomes.mangroveSwamp}) as a 1.12.2 biome.
 *
 * <ul>
 *   <li><b>Climate</b>: temperature 0.8, downfall 0.9, rain; sky and fog as 1.19's (the sky from temperature 0.8,
 *       which 1.12.2's formula gives the same; fog 0xC0D8FF, 1.12.2's default).</li>
 *   <li><b>Colors</b>: water {@code 0x3A7A6A}, underwater fog {@code 0x4D7A60}, foliage {@code 0x8DB127}, grass by
 *       the swamp modifier ({@code 0x4C763C} where the biome noise at x and z times 0.0225 is under -0.1, else
 *       {@code 0x6A7039}, as 1.12.2's swamp). 1.12.2's water texture is already blue and the biome color multiplies
 *       it, so the tint is the color divided by 1.13's blue; with 1.13's gray texture in place the exact color is
 *       given instead ({@link SwampContent#waterColor}).</li>
 *   <li><b>Spawns</b>: the common monsters (1.12.2's defaults are the same), one more slime entry (1, 1-1), frogs
 *       (10, 2-5, from the frog stream, by this biome's name), tropical fish (25, 8-8, Update Aquatic's when
 *       installed), bats; no farm animals and no squid.</li>
 *   <li><b>Surface</b> (1.19's {@code SurfaceRuleData}): mud on the floor and under it, above water and below; where
 *       the swamp noise is above 0, a floor at y 60 to 62 becomes water, so the swamp is full of shallow pools.
 *       1.12.2's terrain has no separate swamp noise, so the swamp's own (the biome noise at a quarter of x and z,
 *       as 1.12.2's swamp uses) stands in.</li>
 *   <li><b>Decoration</b>: {@link MangroveSwampDecorator}; and 1.12.2's swamp fossil (1 in 64 chunks), which
 *       stands in for 1.19's fossil features.</li>
 * </ul>
 * Height as 1.12.2's swamp (base -0.2, variation 0.1), since it takes the swamp's place in the hot band.
 */
public class BiomeMangroveSwamp extends Biome {

    /** 1.19's colors. */
    public static final int WATER_COLOR = 0x3A7A6A;
    public static final int WATER_FOG_COLOR = 0x4D7A60;
    public static final int FOLIAGE_COLOR = 0x8DB127;
    public static final int GRASS_DARK = 0x4C763C;
    public static final int GRASS_LIGHT = 0x6A7039;

    /** 1.13's default water color, which the 1.12.2 tint is worked out against. */
    static final int MODERN_DEFAULT_WATER = 0x3F76E4;

    /** The 1.12.2 tint over the blue water texture: 1.19's color divided by 1.13's blue, channel by channel. */
    public static final int WATER_TINT = tint(WATER_COLOR, MODERN_DEFAULT_WATER);

    private static final IBlockState WATER = Blocks.WATER.getDefaultState();

    public BiomeMangroveSwamp() {
        super(new BiomeProperties("Mangrove Swamp").setBaseHeight(-0.2F).setHeightVariation(0.1F)
                .setTemperature(0.8F).setRainfall(0.9F).setWaterColor(WATER_TINT));

        this.spawnableCreatureList.clear();
        this.spawnableWaterCreatureList.clear();
        // The common monsters are 1.12.2's defaults already; 1.19 adds one more slime entry.
        this.spawnableMonsterList.add(new SpawnListEntry(EntitySlime.class, 1, 1, 1));
    }

    static int tint(int color, int base) {
        int r = Math.min(255, ((color >> 16) & 0xFF) * 255 / ((base >> 16) & 0xFF));
        int g = Math.min(255, ((color >> 8) & 0xFF) * 255 / ((base >> 8) & 0xFF));
        int b = Math.min(255, (color & 0xFF) * 255 / (base & 0xFF));
        return r << 16 | g << 8 | b;
    }

    @Override
    public BiomeDecorator createBiomeDecorator() {
        return getModdedBiomeDecorator(new MangroveSwampDecorator());
    }

    /** Mud as top and filler; read when the chunk is made, so mud registered after the biome is seen. */
    private void surfaceBlocks() {
        Block mud = MudContent.mud();
        IBlockState state = mud != null ? mud.getDefaultState() : Blocks.DIRT.getDefaultState();
        this.topBlock = state;
        this.fillerBlock = state;
    }

    @Override
    public void genTerrainBlocks(World world, Random rand, ChunkPrimer primer, int x, int z, double noiseVal) {
        surfaceBlocks();
        double swamp = GRASS_COLOR_NOISE.getValue(x * 0.25D, z * 0.25D);
        if (swamp > 0.0D) {
            int lx = x & 15;
            int lz = z & 15;
            // The floor: the first block from the top that is neither air nor water.
            for (int y = 255; y >= 0; --y) {
                IBlockState s = primer.getBlockState(lz, y, lx);
                if (s.getMaterial() == Material.AIR || s.getMaterial() == Material.WATER) {
                    continue;
                }
                if (y >= 60 && y < 63) {
                    primer.setBlockState(lz, y, lx, WATER);
                }
                break;
            }
        }
        generateBiomeTerrain(world, rand, primer, x, z, noiseVal);
    }

    @Override
    public void decorate(World world, Random rand, BlockPos pos) {
        super.decorate(world, rand, pos);
        if (TerrainGen.decorate(world, rand, new net.minecraft.util.math.ChunkPos(pos), DecorateBiomeEvent.Decorate.EventType.FOSSIL)
                && rand.nextInt(64) == 0) {
            new WorldGenFossils().generate(world, rand, pos);
        }
    }

    /** 1.19's swamp grass modifier, the same noise and colors as 1.12.2's swamp. */
    public static int grassColor(BlockPos pos) {
        double d = GRASS_COLOR_NOISE.getValue(pos.getX() * 0.0225D, pos.getZ() * 0.0225D);
        return d < -0.1D ? GRASS_DARK : GRASS_LIGHT;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public int getGrassColorAtPos(BlockPos pos) {
        return getModdedBiomeGrassColor(grassColor(pos));
    }

    @Override
    @SideOnly(Side.CLIENT)
    public int getFoliageColorAtPos(BlockPos pos) {
        return getModdedBiomeFoliageColor(FOLIAGE_COLOR);
    }

}
