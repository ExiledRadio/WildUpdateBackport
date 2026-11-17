package com.exiledradio.wildupdatebackport;

import com.exiledradio.backportcore.settings.Setting;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Configuration: one category per stream, each with an {@code enabled} switch.
 *
 * <h2>Rules for streams</h2>
 * <ul>
 *   <li>A stream edits only inside its own category class.</li>
 *   <li>Category classes extend {@code Object} directly. Forge 1.12 crashes at startup ("Unknown type")
 *       on a category class that extends another one.</li>
 *   <li>{@code enabled} is read once, during registration, and needs a restart: switching a stream off
 *       means its blocks and items are never registered, so a world that has them loses them.</li>
 *   <li>A changed default never reaches an installed game (Forge keeps the value in the file), so a
 *       default that must change gets a new key.</li>
 *   <li>Every field has an explicit {@code @Config.LangKey}; its English name and {@code .tooltip} line are
 *       written into {@code en_us.lang} by the stream's generator.</li>
 * </ul>
 */
@Config(modid = WildUpdateBackport.MOD_ID, name = WildUpdateBackport.MOD_ID)
public final class ModConfig {
    @Setting.Feature(icon = "wildupdatebackport:sculk_sensor")
    @Config.LangKey("config.wildupdatebackport.vibrations")
    @Config.Comment("Game events and vibrations: what sculk sensors, shriekers, wardens and allays hear.")
    public static final Vibrations vibrations = new Vibrations();
    @Setting.Feature(icon = "wildupdatebackport:mangrove_log")
    @Config.LangKey("config.wildupdatebackport.mangrove")
    @Config.Comment("The mangrove wood set, mangrove roots, muddy mangrove roots, leaves and propagules.")
    public static final Mangrove mangrove = new Mangrove();
    @Setting.Feature(icon = "wildupdatebackport:mud")
    @Config.LangKey("config.wildupdatebackport.mud")
    @Config.Comment("Mud, packed mud, mud bricks and their stairs, slabs and walls.")
    public static final Mud mud = new Mud();
    @Setting.Feature(icon = "wildupdatebackport:oak_chest_boat")
    @Config.LangKey("config.wildupdatebackport.boats")
    @Config.Comment("The mangrove boat and boats with chests.")
    public static final Boats boats = new Boats();
    @Setting.Feature(icon = "wildupdatebackport:sculk")
    @Config.LangKey("config.wildupdatebackport.sculk")
    @Config.Comment("Sculk, sculk veins, sculk catalysts, sculk sensors and sculk shriekers.")
    public static final Sculk sculk = new Sculk();
    @Setting.Feature(icon = "wildupdatebackport:ochre_froglight")
    @Config.LangKey("config.wildupdatebackport.frogs")
    @Config.Comment("Frogs, tadpoles, frogspawn and froglights.")
    public static final Frogs frogs = new Frogs();
    @Setting.Feature(icon = "minecraft:noteblock")
    @Config.LangKey("config.wildupdatebackport.allay")
    @Config.Comment("The allay.")
    public static final Allay allay = new Allay();
    @Setting.Feature(icon = "wildupdatebackport:sculk_shrieker")
    @Config.LangKey("config.wildupdatebackport.warden")
    @Config.Comment("The warden and the Darkness effect.")
    public static final Warden warden = new Warden();
    @Setting.Feature(icon = "wildupdatebackport:recovery_compass")
    @Config.LangKey("config.wildupdatebackport.items")
    @Config.Comment("The recovery compass, echo shards, music disc 5, disc fragments, goat horns and Swift Sneak.")
    public static final Items items = new Items();
    @Setting.Feature(icon = "minecraft:book")
    @Config.LangKey("config.wildupdatebackport.changes")
    @Config.Comment("Smaller changes from 1.19 to 1.19.4 to existing blocks, items and mobs.")
    public static final Changes changes = new Changes();
    @Setting.Feature(icon = "wildupdatebackport:mangrove_propagule")
    @Config.LangKey("config.wildupdatebackport.swamp")
    @Config.Comment("Mangrove swamps.")
    public static final Swamp swamp = new Swamp();
    @Setting.Feature(icon = "wildupdatebackport:reinforced_deepslate")
    @Config.LangKey("config.wildupdatebackport.deepDark")
    @Config.Comment("The deep dark and ancient cities.")
    public static final DeepDark deepDark = new DeepDark();
    @Setting.Feature(icon = "wildupdatebackport:goat_horn")
    @Config.LangKey("config.wildupdatebackport.goats")
    @Config.Comment("Goats and screaming goats, when Caves & Cliffs (which owns them) is not installed.")
    public static final Goats goats = new Goats();
    // ------------------------------------------------------------------------------------------
    // Categories. Each stream owns exactly one.
    // ------------------------------------------------------------------------------------------

    public static final class Vibrations {
        @Config.Comment("Game events are sent to sculk sensors, shriekers, wardens and allays. Off, none of them hears anything.")
        @Config.LangKey("config.wildupdatebackport.vibrations.enabled")
        public boolean enabled = true;
    }

    public static final class Mangrove {
        @Config.RequiresMcRestart
        @Config.Comment("Mangrove logs, wood, planks, their building blocks, roots, leaves and propagules. Off, nothing is registered.")
        @Config.LangKey("config.wildupdatebackport.mangrove.enabled")
        public boolean enabled = true;
    }

    public static final class Mud {
        @Config.RequiresMcRestart
        @Config.Comment("Mud, packed mud, mud bricks and their stairs, slab and wall. Off, nothing is registered.")
        @Config.LangKey("config.wildupdatebackport.mud.enabled")
        public boolean enabled = true;
    }

    public static final class Boats {
        @Config.RequiresMcRestart
        @Config.Comment("The mangrove boat and boats with chests for every wood. Off, nothing is registered.")
        @Config.LangKey("config.wildupdatebackport.boats.enabled")
        public boolean enabled = true;
    }

    public static final class Sculk {
        @Config.RequiresMcRestart
        @Config.Comment("Sculk, sculk veins, sculk catalysts, sculk sensors and sculk shriekers. Off, nothing is registered.")
        @Config.LangKey("config.wildupdatebackport.sculk.enabled")
        public boolean enabled = true;
    }

    public static final class Frogs {
        @Config.RequiresMcRestart
        @Config.Comment("Frogs, tadpoles, frogspawn, froglights and the bucket of tadpole. Off, nothing is registered.")
        @Config.LangKey("config.wildupdatebackport.frogs.enabled")
        public boolean enabled = true;
    }

    public static final class Allay {
        @Config.RequiresMcRestart
        @Config.Comment("The allay and its spawn egg. Off, nothing is registered.")
        @Config.LangKey("config.wildupdatebackport.allay.enabled")
        public boolean enabled = true;
    }

    public static final class Warden {
        @Config.RequiresMcRestart
        @Config.Comment("The warden, Darkness and the warden's spawn egg. Off, nothing is registered and shriekers summon nothing.")
        @Config.LangKey("config.wildupdatebackport.warden.enabled")
        public boolean enabled = true;
    }

    public static final class Items {
        @Config.RequiresMcRestart
        @Config.Comment("The recovery compass, echo shards, music disc 5, disc fragments, goat horns and Swift Sneak. Off, nothing is registered.")
        @Config.LangKey("config.wildupdatebackport.items.enabled")
        public boolean enabled = true;
    }

    public static final class Changes {
        @Config.RequiresMcRestart
        @Config.Comment("The smaller 1.19.x changes to existing blocks, items and mobs. Off, all of them are left as 1.12.2 has them.")
        @Config.LangKey("config.wildupdatebackport.changes.enabled")
        public boolean enabled = true;

        @Config.RequiresMcRestart
        @Config.Comment("Minecarts with chest, furnace, TNT and hopper drop themselves when broken and are crafted shapeless (1.19).")
        @Config.LangKey("config.wildupdatebackport.changes.minecarts")
        public boolean minecarts = true;

        @Config.Comment("Ores and spawners blown up by TNT a player lit drop experience (1.19).")
        @Config.LangKey("config.wildupdatebackport.changes.explosionExperience")
        public boolean explosionExperience = true;

        @Config.RequiresMcRestart
        @Config.Comment("Iron golem and snow golem spawn eggs (1.19.3).")
        @Config.LangKey("config.wildupdatebackport.changes.golemSpawnEggs")
        public boolean golemSpawnEggs = true;

        @Config.Comment("Creepers can be ignited with a fire charge (1.19.3).")
        @Config.LangKey("config.wildupdatebackport.changes.creeperFireCharge")
        public boolean creeperFireCharge = true;

        @Config.Comment("An enderman's carried block drops as if mined with a Silk Touch tool (1.19.3).")
        @Config.LangKey("config.wildupdatebackport.changes.endermanBlockDrops")
        public boolean endermanBlockDrops = true;

        @Config.RequiresWorldRestart
        @Config.Comment("Rabbits always drop one raw rabbit, cooked when on fire (1.19.3).")
        @Config.LangKey("config.wildupdatebackport.changes.rabbitDrops")
        public boolean rabbitDrops = true;

        @Config.Comment("Playing jukeboxes show notes, give a redstone signal of 15, and take discs from hoppers and droppers (1.19.4).")
        @Config.LangKey("config.wildupdatebackport.changes.jukeboxes")
        public boolean jukeboxes = true;

        @Config.Comment("Using a chestplate, elytra or other armor swaps it with what is worn (1.19.4).")
        @Config.LangKey("config.wildupdatebackport.changes.armorSwap")
        public boolean armorSwap = true;

        @Config.Comment("Foals' health, speed and jump vary around their parents' average (1.19.4).")
        @Config.LangKey("config.wildupdatebackport.changes.foalStats")
        public boolean foalStats = true;

        @Config.RequiresMcRestart
        @Config.Comment("The effect colors of potions and tipped arrows of 1.19.4.")
        @Config.LangKey("config.wildupdatebackport.changes.potionColors")
        public boolean potionColors = true;

        @Setting.Client
        @Config.Comment("Potions have no enchantment glint unless enchanted (1.19.4).")
        @Config.LangKey("config.wildupdatebackport.changes.potionGlint")
        public boolean potionGlint = true;

        @Setting.Client
        @Config.RequiresMcRestart
        @Config.Comment("The tops of doors no longer flip when they open (1.19's door models).")
        @Config.LangKey("config.wildupdatebackport.changes.doorModels")
        public boolean doorModels = true;

        @Config.Comment("Opening shulker boxes pop the blocks attached to their open faces (1.19.3).")
        @Config.LangKey("config.wildupdatebackport.changes.shulkerBoxes")
        public boolean shulkerBoxes = true;

        @Config.Comment("Endermen and skeletons spawn in the Nether at light level 7 and below (1.19.3).")
        @Config.LangKey("config.wildupdatebackport.changes.netherSpawnLight")
        public boolean netherSpawnLight = true;

        @Config.Comment("Scaffolding burns in a furnace as long as bamboo (1.19).")
        @Config.LangKey("config.wildupdatebackport.changes.scaffoldingFuel")
        public boolean scaffoldingFuel = true;

        @Config.Comment("All leaves can be waterlogged, not only mangrove leaves (1.19). Needs Fluidlogged API.")
        @Config.LangKey("config.wildupdatebackport.changes.leavesWaterlog")
        public boolean leavesWaterlog = true;

        @Config.Comment({"The game rules of 1.19.3 and 1.19.4: blockExplosionDropDecay, mobExplosionDropDecay, tntExplosionDropDecay,",
                "snowAccumulationHeight, waterSourceConversion, lavaSourceConversion, globalSoundEvents, commandModificationBlockLimit",
                "and doVinesSpread. At their defaults the game plays as 1.12.2 does, except that every block TNT blows up drops."})
        @Config.LangKey("config.wildupdatebackport.changes.gameRules")
        public boolean gameRules = true;

        @Config.Comment("Mobs do not spawn on chorus flowers or scaffolding (1.19.3).")
        @Config.LangKey("config.wildupdatebackport.changes.spawnBlocks")
        public boolean spawnBlocks = true;

        @Config.Comment("A spawner placed or set without a mob is empty: no pig, no flames, no spawning until a spawn egg is used on it (1.19.3).")
        @Config.LangKey("config.wildupdatebackport.changes.emptySpawners")
        public boolean emptySpawners = true;

        @Config.Comment("A renamed armor stand keeps its name when placed, and the armor stand item keeps it when the stand is broken (1.19.4).")
        @Config.LangKey("config.wildupdatebackport.changes.armorStandNames")
        public boolean armorStandNames = true;

        @Config.RequiresWorldRestart
        @Config.Comment("Bonus chests can hold mangrove logs (1.19).")
        @Config.LangKey("config.wildupdatebackport.changes.mangroveBonusChest")
        public boolean mangroveBonusChest = true;
    }

    public static final class Swamp {
        @Config.RequiresWorldRestart
        @Config.Comment("Mangrove swamps generate in newly generated chunks.")
        @Setting.NewChunks
        @Config.LangKey("config.wildupdatebackport.swamp.enabled")
        public boolean enabled = true;
    }

    public static final class DeepDark {
        @Config.RequiresWorldRestart
        @Config.Comment("The deep dark and ancient cities generate in newly generated chunks.")
        @Setting.NewChunks
        @Config.LangKey("config.wildupdatebackport.deepDark.enabled")
        public boolean enabled = true;
    }

    /** Caves & Cliffs' goat settings, copied with its goat: used only when Caves & Cliffs is not installed. */
    public static final class Goats {
        @Config.RequiresMcRestart
        @Config.Comment("Goats, when Caves & Cliffs is not installed (with it, its goats are used). Off, nothing is registered.")
        @Config.LangKey("config.wildupdatebackport.goats.enabled")
        public boolean enabled = true;

        @Config.RequiresMcRestart
        @Config.Comment({"How often goats spawn in the biomes of spawnBiomes, next to those biomes' other animals",
                "(sheep are 12, cows 8, llamas 5). 0: goats never spawn on their own."})
        @Config.LangKey("config.wildupdatebackport.goats.spawnWeight")
        @Config.RangeInt(min = 0, max = 100)
        public int spawnWeight = 10;

        @Config.RequiresMcRestart
        @Config.Comment("The biomes goats spawn in, in groups of 1 to 3, on stone, gravel, snow and packed ice.")
        @Config.LangKey("config.wildupdatebackport.goats.spawnBiomes")
        public String[] spawnBiomes = {"minecraft:extreme_hills", "minecraft:smaller_extreme_hills", "minecraft:extreme_hills_with_trees",
                "minecraft:mutated_extreme_hills", "minecraft:mutated_extreme_hills_with_trees"};

        @Config.Comment("Goats ram players and mobs that stand still near them. Off, goats never ram.")
        @Config.LangKey("config.wildupdatebackport.goats.ramming")
        public boolean ramming = true;
    }

    // ------------------------------------------------------------------------------------------

    /** Saves edits made in the in-game config screen. */
    @Mod.EventBusSubscriber(modid = WildUpdateBackport.MOD_ID)
    public static final class EventHandler {
        private EventHandler() {
        }

        @SubscribeEvent
        public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
            if (WildUpdateBackport.MOD_ID.equals(event.getModID())) {
                ConfigManager.sync(WildUpdateBackport.MOD_ID, Config.Type.INSTANCE);
            }
        }
    }

    private ModConfig() {
    }
}
