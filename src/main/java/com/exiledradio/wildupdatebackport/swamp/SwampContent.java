package com.exiledradio.wildupdatebackport.swamp;

import com.exiledradio.wildupdatebackport.ModConfig;
import com.exiledradio.wildupdatebackport.WildUpdateBackport;
import com.exiledradio.wildupdatebackport.common.Content;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.monster.EntityMagmaCube;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.common.BiomeManager;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.event.terraingen.BiomeEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Stream Sw: the mangrove swamp ({@link BiomeMangroveSwamp}), registered as {@code wildupdatebackport:mangrove_swamp}.
 *
 * <h2>Where it generates</h2>
 * <ul>
 *   <li><b>1.12.2's terrain</b>: 1.19 puts the mangrove swamp in the swamp's slot of the hot climates, so it joins
 *       Forge's hot band ({@code BiomeType.DESERT}: desert 30, savanna 20, plains 10) at the swamp's weight, 10. Like
 *       1.12.2's swamp it meets the ocean without a beach (patch P9, {@code GenLayerShore}). {@code swamp.enabled}
 *       decides it for each world as it is created or loaded ({@link #applyPlacement}), so turning it off stops new
 *       mangrove swamps without touching chunks already made.</li>
 *   <li><b>Caves &amp; Cliffs' 1.18 terrain</b>: that terrain chooses its biomes by 1.19's climate parameters itself
 *       and takes a biome a mod registers under the modern name, so its mangrove swamps are this biome (spawns,
 *       colors, frogs). It places 1.19's features there itself, our blocks by name; this biome's own decorator does
 *       not run in those worlds, and it steps aside for any generator that says it places a feature
 *       ({@link #generatorPlaces}).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = WildUpdateBackport.MOD_ID)
public final class SwampContent {

    public static final String NAME = "mangrove_swamp";

    /** Forge's hot band, at the swamp's own weight. */
    public static final int WEIGHT = 10;

    /** 1.19's tropical fish in the mangrove swamp: water ambient, 25, groups of 8. */
    public static final int FISH_WEIGHT = 25;
    public static final int FISH_GROUP = 8;

    private static BiomeMangroveSwamp biome;
    private static BiomeManager.BiomeEntry entry;
    private static boolean placed;

    private SwampContent() {
    }

    public static boolean enabled() {
        return ModConfig.swamp.enabled;
    }

    @Nullable
    public static BiomeMangroveSwamp biome() {
        return biome;
    }

    /**
     * Always registered, so a world that has mangrove swamps loads them whatever the setting. Before the deep dark
     * (high priority), so the two get the same ids at every launch (a superflat preset names its biome by id).
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void registerBiomes(RegistryEvent.Register<Biome> event) {
        biome = new BiomeMangroveSwamp();
        biome.setRegistryName(Content.id(NAME));
        event.getRegistry().register(biome);
        BiomeDictionary.addTypes(biome, BiomeDictionary.Type.WET, BiomeDictionary.Type.SWAMP);
        entry = new BiomeManager.BiomeEntry(biome, WEIGHT);
        applyPlacement();
    }

    /**
     * Puts the biome in Forge's hot band, or takes it out, as {@code swamp.enabled} says. Called at registration and
     * before each server starts, since 1.12.2 copies the band when a world's biome layers are made.
     */
    public static void applyPlacement() {
        if (biome == null) {
            return;
        }
        boolean want = enabled();
        if (want && !placed) {
            BiomeManager.addBiome(BiomeManager.BiomeType.DESERT, entry);
            placed = true;
        } else if (!want && placed) {
            BiomeManager.removeBiome(BiomeManager.BiomeType.DESERT, entry);
            placed = false;
        }
    }

    /** @return whether the biome is in the hot band now */
    public static boolean placed() {
        return placed;
    }

    /** P9's hook value: the biome's numeric id, or -1. */
    static int id() {
        return biome == null ? -1 : Biome.getIdForBiome(biome);
    }

    // --- water ----------------------------------------------------------------------------------

    /**
     * 1.19's water color once something has put 1.13's gray water texture in place: the biome's 1.12.2 tint is then
     * replaced (Update Aquatic does that for every biome and gives other mods' a filtered default blue), so this
     * runs last and gives the exact color.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void waterColor(BiomeEvent.GetWaterColor event) {
        if (biome != null && event.getBiome() == biome && event.getNewColor() != BiomeMangroveSwamp.WATER_TINT) {
            event.setNewColor(BiomeMangroveSwamp.WATER_COLOR);
        }
    }

    // --- slimes ---------------------------------------------------------------------------------

    /**
     * 1.19's {@code #allows_surface_slime_spawns} holds the mangrove swamp, so slimes spawn on its surface as in the
     * swamp: between y 50 and 70, half the time, by the moon's brightness, in light no brighter than a random 0-7.
     * 1.12.2's {@code EntitySlime} names its swamp outright, so the same rule is given here; the slime chunk rule
     * below y 40 stays the slime's own.
     */
    @SubscribeEvent
    public static void checkSpawn(LivingSpawnEvent.CheckSpawn event) {
        if (biome == null || event.isSpawner() || event.getResult() != Event.Result.DEFAULT) {
            return;
        }
        EntityLiving living = (EntityLiving) event.getEntityLiving();
        if (!(living instanceof EntitySlime) || living instanceof EntityMagmaCube) {
            return;
        }
        World world = event.getWorld();
        BlockPos column = new BlockPos(MathHelper.floor(living.posX), 0, MathHelper.floor(living.posZ));
        if (world.getBiome(column) != biome || world.getDifficulty() == EnumDifficulty.PEACEFUL) {
            return;
        }
        if (surfaceRule(world, living) && living.world.getBlockState(new BlockPos(living).down()).canEntitySpawn(living)
                && living.isNotColliding()) {
            event.setResult(Event.Result.ALLOW);
        }
    }

    /** Slime.checkSlimeSpawnRules' surface half (1.12.2's swamp test, the same numbers). */
    static boolean surfaceRule(World world, EntityLiving slime) {
        if (world.getWorldInfo().getTerrainType().handleSlimeSpawnReduction(slime.getRNG(), world)) {
            return false;
        }
        return slime.posY > 50.0D && slime.posY < 70.0D && slime.getRNG().nextFloat() < 0.5F
                && slime.getRNG().nextFloat() < world.getCurrentMoonPhaseFactor()
                && world.getLightFromNeighbors(new BlockPos(slime)) <= slime.getRNG().nextInt(8);
    }

    // --- tropical fish --------------------------------------------------------------------------

    private static final String UA = "updateaquaticbackport";
    private static final String WATER_AMBIENT = "UPDATEAQUATICBACKPORT_WATER_AMBIENT";
    private static Biome.SpawnListEntry fish;
    private static boolean fishLooked;
    private static Method fishSpawns;

    /**
     * 1.19's tropical fish (25, 8-8), when Update Aquatic has them: its water ambient list is filled per position,
     * so the entry is added there, the same object every time (the spawner checks a picked entry by identity), and
     * only while its fish spawn.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void potentialSpawns(WorldEvent.PotentialSpawns event) {
        if (biome == null || !WATER_AMBIENT.equals(event.getType().name())) {
            return;
        }
        if (event.getWorld().getBiome(event.getPos()) != biome) {
            return;
        }
        Biome.SpawnListEntry e = tropicalFish();
        if (e == null || !uaFishSpawn()) {
            return;
        }
        List<Biome.SpawnListEntry> list = event.getList();
        if (!list.contains(e)) {
            list.add(e);
        }
    }

    @Nullable
    @SuppressWarnings("unchecked")
    static Biome.SpawnListEntry tropicalFish() {
        if (!fishLooked) {
            fishLooked = true;
            EntityEntry ee = ForgeRegistries.ENTITIES.getValue(new ResourceLocation(UA, "tropical_fish"));
            if (ee != null && EntityLiving.class.isAssignableFrom(ee.getEntityClass())) {
                fish = new Biome.SpawnListEntry((Class<? extends EntityLiving>) ee.getEntityClass(), FISH_WEIGHT, FISH_GROUP, FISH_GROUP);
            }
            try {
                fishSpawns = Class.forName("com.exiledradio.updateaquaticbackport.fish.FishSpawning").getMethod("spawns");
            } catch (ReflectiveOperationException | LinkageError ignored) {
                fishSpawns = null;
            }
        }
        return fish;
    }

    private static boolean uaFishSpawn() {
        if (fishSpawns == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(fishSpawns.invoke(null));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    // --- other generators ------------------------------------------------------------------------

    private static final String DECORATION_SOURCE = "com.exiledradio.backportcore.terrain.DecorationSource";
    private static Class<?> sourceClass;
    private static Method sourcePlaces;
    private static boolean sourceLooked;

    /**
     * Backport Core's {@code DecorationSource} (1.2.0 onward), asked by name so this works with the Core it compiles
     * against: whether the world's generator places this 1.19 feature itself.
     */
    public static boolean generatorPlaces(World world, String feature) {
        if (!(world instanceof WorldServer)) {
            return false;
        }
        if (!sourceLooked) {
            sourceLooked = true;
            try {
                sourceClass = Class.forName(DECORATION_SOURCE);
                sourcePlaces = sourceClass.getMethod("placesFeature", World.class, String.class);
            } catch (ReflectiveOperationException | LinkageError ignored) {
                sourceClass = null;
            }
        }
        if (sourceClass == null) {
            return false;
        }
        IChunkGenerator generator = ((WorldServer) world).getChunkProvider().chunkGenerator;
        if (!sourceClass.isInstance(generator)) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(sourcePlaces.invoke(generator, world, feature));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }
}
