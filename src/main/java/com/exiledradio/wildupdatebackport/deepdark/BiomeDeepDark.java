package com.exiledradio.wildupdatebackport.deepdark;

import net.minecraft.world.biome.Biome;

/**
 * 1.19's deep dark as a 1.12.2 biome ({@code OverworldBiomes.deepDark}: temperature 0.8, downfall 0.4, rain, no mob
 * spawns). It is never placed in a 1.12.2 biome map: Caves &amp; Cliffs' 1.18 terrain picks it by name
 * ({@code BiomeMap.modern}) for the deep dark's blocks, so its F3 screen and spawner read it there.
 */
public class BiomeDeepDark extends Biome {

    public BiomeDeepDark() {
        super(new BiomeProperties("Deep Dark").setTemperature(0.8F).setRainfall(0.4F));
        this.spawnableCreatureList.clear();
        this.spawnableMonsterList.clear();
        this.spawnableWaterCreatureList.clear();
        this.spawnableCaveCreatureList.clear();
    }
}
