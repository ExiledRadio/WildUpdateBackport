package com.exiledradio.wildupdatebackport.frog;

import com.exiledradio.wildupdatebackport.WildUpdateBackport;
import com.exiledradio.wildupdatebackport.common.Owners;

import net.minecraft.block.Block;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Every rule about where frogs come from, in one place, so other terrain (the mangrove swamp stream, or a
 * mod that replaces the overworld's biomes with 1.18's) can hook in by calling {@link #addSpawns(Biome)}
 * for its own biomes.
 *
 * <h2>Variants</h2>
 * 1.19 picks a frog's variant from the biome it is created in (spawned, hatched from a spawn egg, or grown
 * from a tadpole): its {@code #spawns_cold_variant_frogs} biome tag gives cold frogs, then
 * {@code #spawns_warm_variant_frogs} warm ones, everything else temperate. Biomes are matched by their name
 * in any namespace, so 1.12.2's names (ice_flats, mesa, hell...) and 1.19's (snowy_slopes, grove,
 * mangrove_swamp...) both work, whichever mod registers them. A biome in neither list falls back to Forge's
 * biome dictionary: {@code SNOWY} or {@code END} is cold, {@code HOT} or {@code NETHER} warm.
 *
 * <h2>Spawning</h2>
 * As 1.19's swamp and mangrove swamp: creature category, weight 10, groups of 2 to 5, standing on grass,
 * mud, mangrove roots or muddy mangrove roots, at a light level above 8.
 */
public final class FrogSpawns {

    public static final int WEIGHT = 10;
    public static final int MIN_GROUP = 2;
    public static final int MAX_GROUP = 5;

    /** 1.19's {@code #spawns_cold_variant_frogs}, plus the 1.12.2 biomes 1.18's data fixer turns into them. */
    private static final Set<String> COLD = new HashSet<String>(Arrays.asList(
            // 1.19
            "snowy_plains", "ice_spikes", "frozen_peaks", "jagged_peaks", "snowy_slopes", "frozen_ocean",
            "deep_frozen_ocean", "grove", "deep_dark", "frozen_river", "snowy_taiga", "snowy_beach",
            "the_end", "small_end_islands", "end_midlands", "end_highlands", "end_barrens",
            // 1.12.2
            "ice_flats", "mutated_ice_flats", "ice_mountains", "cold_beach", "taiga_cold", "taiga_cold_hills",
            "mutated_taiga_cold", "sky", "sky_island_low", "sky_island_medium", "sky_island_high", "sky_island_barren"));

    /** 1.19's {@code #spawns_warm_variant_frogs}, the same way. */
    private static final Set<String> WARM = new HashSet<String>(Arrays.asList(
            // 1.19
            "desert", "warm_ocean", "jungle", "sparse_jungle", "bamboo_jungle", "savanna", "savanna_plateau",
            "windswept_savanna", "nether_wastes", "soul_sand_valley", "crimson_forest", "warped_forest",
            "basalt_deltas", "badlands", "eroded_badlands", "wooded_badlands", "mangrove_swamp",
            // 1.12.2
            "desert_hills", "mutated_desert", "jungle_hills", "jungle_edge", "mutated_jungle", "mutated_jungle_edge",
            "savanna_rock", "mutated_savanna", "mutated_savanna_rock", "hell", "mesa", "mesa_rock", "mesa_clear_rock",
            "mutated_mesa", "mutated_mesa_rock", "mutated_mesa_clear_rock"));

    /** Biomes frogs spawn in: 1.19's swamp (1.12.2's swampland and its hills) and the mangrove swamp. */
    private static final Set<String> SPAWN_BIOMES = new HashSet<String>(Arrays.asList(
            "swamp", "swampland", "mutated_swampland", "mangrove_swamp"));

    private static final List<Biome> ADDED = new ArrayList<Biome>();

    private FrogSpawns() {
    }

    /** @return the variant a frog created in this biome gets */
    public static FrogVariant variantFor(Biome biome) {
        String path = biome.getRegistryName() == null ? "" : biome.getRegistryName().getPath();
        if (COLD.contains(path)) {
            return FrogVariant.COLD;
        }
        if (WARM.contains(path)) {
            return FrogVariant.WARM;
        }
        if (BiomeDictionary.hasType(biome, BiomeDictionary.Type.SNOWY) || BiomeDictionary.hasType(biome, BiomeDictionary.Type.END)) {
            return FrogVariant.COLD;
        }
        if (BiomeDictionary.hasType(biome, BiomeDictionary.Type.HOT) || BiomeDictionary.hasType(biome, BiomeDictionary.Type.NETHER)) {
            return FrogVariant.WARM;
        }
        return FrogVariant.TEMPERATE;
    }

    /** @return true if frogs spawn naturally in this biome (by name, in any namespace) */
    public static boolean spawnsIn(Biome biome) {
        return biome.getRegistryName() != null && SPAWN_BIOMES.contains(biome.getRegistryName().getPath());
    }

    /**
     * Adds frogs to a biome's creature spawns (once). The hook for other terrain: call it for a biome that
     * should have 1.19's swamp frogs.
     */
    public static void addSpawns(Biome biome) {
        if (!FrogContent.enabled() || ADDED.contains(biome)) {
            return;
        }
        ADDED.add(biome);
        biome.getSpawnableList(EnumCreatureType.CREATURE).add(new Biome.SpawnListEntry(EntityFrog.class, WEIGHT, MIN_GROUP, MAX_GROUP));
    }

    /** From init: every registered biome whose name is a swamp's. */
    static void addAll() {
        for (Biome biome : ForgeRegistries.BIOMES) {
            if (spawnsIn(biome)) {
                addSpawns(biome);
                WildUpdateBackport.LOGGER.debug("Frogs spawn in {}", biome.getRegistryName());
            }
        }
    }

    /** @return the biomes frogs were added to */
    public static List<Biome> spawnBiomes() {
        return Collections.unmodifiableList(ADDED);
    }

    /** 1.19's {@code #frogs_spawnable_on}: grass, mud, mangrove roots, muddy mangrove roots. */
    public static boolean spawnableOn(Block block) {
        return block == Blocks.GRASS || block == Owners.ourBlock("mud") || block == Owners.ourBlock("mangrove_roots")
                || block == Owners.ourBlock("muddy_mangrove_roots");
    }

    /** 1.19's {@code Frog.checkFrogSpawnRules}: a spawnable block below and raw light above 8. */
    public static boolean canSpawnAt(World world, BlockPos pos) {
        return spawnableOn(world.getBlockState(pos.down()).getBlock()) && world.getLight(pos) > 8;
    }
}
