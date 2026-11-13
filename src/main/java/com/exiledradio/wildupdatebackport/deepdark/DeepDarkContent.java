package com.exiledradio.wildupdatebackport.deepdark;

import com.exiledradio.backportcore.locate.StructureLocate;
import com.exiledradio.backportcore.structure.JigsawPools;
import com.exiledradio.backportcore.structure.ModernPalette;
import com.exiledradio.backportcore.structure.ScatteredStructure;
import com.exiledradio.backportcore.structure.Structures;
import com.exiledradio.backportcore.structure.TerrainAdaptation;
import com.exiledradio.wildupdatebackport.ModConfig;
import com.exiledradio.wildupdatebackport.WildUpdateBackport;
import com.exiledradio.wildupdatebackport.common.Content;
import com.exiledradio.wildupdatebackport.common.Owners;
import com.exiledradio.wildupdatebackport.deepdark.city.AncientCityStructure;
import com.exiledradio.wildupdatebackport.deepdark.city.CityPiece;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.structure.MapGenStructureIO;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;

import javax.annotation.Nullable;

/**
 * Stream DD: the deep dark, ancient cities and reinforced deepslate.
 *
 * <ul>
 *   <li><b>Reinforced deepslate</b> ({@link BlockReinforcedDeepslate}): the frame of the city's portal.</li>
 *   <li><b>The deep dark</b> ({@link DeepDark}): 1.18 terrain's own biome where Caves &amp; Cliffs makes it, else a
 *       region under high ground. Its sculk veins and patches ({@link DeepDarkFeatures}), no natural spawns, and a
 *       1.12 biome under 1.19's name ({@link BiomeDeepDark}) that Caves &amp; Cliffs' 1.18 terrain reports there.</li>
 *   <li><b>Ancient cities</b> ({@link AncientCityStructure}): Mojang's 1.19.2 templates and pools, laid out as 1.19.2
 *       lays them out, on 1.19's grid; loot in {@link AncientCityLoot}; {@code /locate structure minecraft:ancient_city}.</li>
 *   <li>The deepslate set the cities are built of is Caves &amp; Cliffs'; without it, {@link DeepslateCopies}.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = WildUpdateBackport.MOD_ID)
public final class DeepDarkContent {

    private static Block reinforcedDeepslate;
    private static Biome biome;

    private DeepDarkContent() {
    }

    public static boolean enabled() {
        return ModConfig.deepDark.enabled;
    }

    /** Ancient cities generate (the stream is on). */
    public static boolean cities() {
        return enabled();
    }

    @Nullable
    public static Block reinforcedDeepslate() {
        return reinforcedDeepslate;
    }

    @Nullable
    public static Biome biome() {
        return biome;
    }

    /** The deepslate the deep dark is made of: Caves &amp; Cliffs', our copy, or stone. */
    public static IBlockState fillBlock() {
        Block b = Owners.block(Owners.CAVES_AND_CLIFFS, "deepslate");
        return b != null ? b.getDefaultState() : Blocks.STONE.getDefaultState();
    }

    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        if (!enabled()) {
            return;
        }
        reinforcedDeepslate = Content.block(event.getRegistry(), "reinforced_deepslate", new BlockReinforcedDeepslate(), null);
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        if (!enabled()) {
            return;
        }
        Content.registerBlockItem(event.getRegistry(), reinforcedDeepslate);
    }

    @SubscribeEvent
    public static void registerBiomes(RegistryEvent.Register<Biome> event) {
        if (!enabled()) {
            return;
        }
        biome = new BiomeDeepDark();
        biome.setRegistryName(Content.id("deep_dark"));
        event.getRegistry().register(biome);
    }

    /** From pre-init. */
    public static void preInit() {
        if (!enabled()) {
            return;
        }
        // minecraft:ancient_city/... pools and processor lists, and the templates, from this mod's assets; our blocks
        // (reinforced deepslate, the sculk sensor, the deepslate copies) under 1.19's names.
        JigsawPools.source(WildUpdateBackport.MOD_ID);
        ModernPalette.source(WildUpdateBackport.MOD_ID);
        ModernPalette.lootTable("minecraft:chests/ancient_city", AncientCityLoot.CITY);
        ModernPalette.lootTable("minecraft:chests/ancient_city_ice_box", AncientCityLoot.ICE_BOX);
        // Stand-ins only when nothing installed provides the block (the owner's is always used when present).
        ModernPalette.fallback("minecraft:soul_lantern", "minecraft:lantern");
        ModernPalette.fallback("minecraft:polished_basalt", "minecraft:smooth_basalt");
        ModernPalette.fallback("minecraft:blue_ice", "minecraft:packed_ice");
    }

    /** From init. */
    public static void init() {
        if (!enabled()) {
            return;
        }
        MinecraftForge.EVENT_BUS.register(new AncientCityLoot());
        MinecraftForge.EVENT_BUS.register(new DeepDarkFeatures.Spawns());
        GameRegistry.registerWorldGenerator(new DeepDarkFeatures(), 1);
        MapGenStructureIO.registerStructure(AncientCityStructure.Start.class, AncientCityStructure.SAVE_NAME);
        MapGenStructureIO.registerStructureComponent(CityPiece.class, "WUB_AncientCityPiece");
        ScatteredStructure.register(Structures.ANCIENT_CITY,
                world -> cities() && world.provider.getDimension() == 0 ? new AncientCityStructure() : null);
        StructureLocate.scattered("minecraft:ancient_cities", Structures.ANCIENT_CITY, null, "minecraft:ancient_city");
        MinecraftForge.TERRAIN_GEN_BUS.register(new AncientCityStructure.PrimerBeard());
        // 1.19's terrain_adaptation beard_box: a terrain that adapts to structures carves the city's cavern.
        TerrainAdaptation.register(Structures.ANCIENT_CITY, TerrainAdaptation.Kind.BEARD_BOX, (world, cx, cz, out) -> {
            ScatteredStructure s = ScatteredStructure.of(world, Structures.ANCIENT_CITY);
            if (s instanceof AncientCityStructure) {
                ((AncientCityStructure) s).startsNear(world, cx, cz, out);
            }
        });
    }

    /** @return the id the templates' loot tables are served under */
    static ResourceLocation id(String path) {
        return Content.id(path);
    }
}
