package com.exiledradio.wildupdatebackport.swamp;

import net.minecraft.init.Biomes;
import net.minecraft.world.biome.Biome;

/**
 * Called from patched vanilla code (patch P9, {@code asm/SwampTransformer}, {@code docs/PATCHES.md}). Keep the
 * signatures: the transformer names them as strings.
 */
public final class SwampHooks {

    private SwampHooks() {
    }

    /**
     * P9, {@code GenLayerShore.getInts}: the biome id its "no beach for the swamp" test compares. The mangrove swamp
     * answers as the swamp, so like it, it meets the ocean with no beach between; every other id is itself. Looked
     * up each time: Forge may give biomes other ids in each world.
     */
    public static int shoreBiome(int id) {
        BiomeMangroveSwamp ours = SwampContent.biome();
        if (ours != null && id >= 0 && Biome.getBiome(id) == ours) {
            return Biome.getIdForBiome(Biomes.SWAMPLAND);
        }
        return id;
    }
}
