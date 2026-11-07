package com.exiledradio.wildupdatebackport;

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
    @Config.LangKey("config.wildupdatebackport.vibrations")
    @Config.Comment("Game events and vibrations: what sculk sensors, shriekers, wardens and allays hear.")
    public static final Vibrations vibrations = new Vibrations();
    @Config.LangKey("config.wildupdatebackport.mangrove")
    @Config.Comment("The mangrove wood set, mangrove roots, muddy mangrove roots, leaves and propagules.")
    public static final Mangrove mangrove = new Mangrove();
    @Config.LangKey("config.wildupdatebackport.mud")
    @Config.Comment("Mud, packed mud, mud bricks and their stairs, slabs and walls.")
    public static final Mud mud = new Mud();
    @Config.LangKey("config.wildupdatebackport.boats")
    @Config.Comment("The mangrove boat and boats with chests.")
    public static final Boats boats = new Boats();
    @Config.LangKey("config.wildupdatebackport.sculk")
    @Config.Comment("Sculk, sculk veins, sculk catalysts, sculk sensors and sculk shriekers.")
    public static final Sculk sculk = new Sculk();
    @Config.LangKey("config.wildupdatebackport.frogs")
    @Config.Comment("Frogs, tadpoles, frogspawn and froglights.")
    public static final Frogs frogs = new Frogs();
    @Config.LangKey("config.wildupdatebackport.allay")
    @Config.Comment("The allay.")
    public static final Allay allay = new Allay();
    @Config.LangKey("config.wildupdatebackport.warden")
    @Config.Comment("The warden and the Darkness effect.")
    public static final Warden warden = new Warden();
    @Config.LangKey("config.wildupdatebackport.items")
    @Config.Comment("The recovery compass, echo shards, music disc 5, disc fragments, goat horns and Swift Sneak.")
    public static final Items items = new Items();
    @Config.LangKey("config.wildupdatebackport.changes")
    @Config.Comment("Smaller changes from 1.19 to 1.19.4 to existing blocks, items and mobs.")
    public static final Changes changes = new Changes();
    @Config.LangKey("config.wildupdatebackport.swamp")
    @Config.Comment("Mangrove swamps.")
    public static final Swamp swamp = new Swamp();
    @Config.LangKey("config.wildupdatebackport.deepDark")
    @Config.Comment("The deep dark and ancient cities.")
    public static final DeepDark deepDark = new DeepDark();
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
    }

    public static final class Swamp {
        @Config.RequiresWorldRestart
        @Config.Comment("Mangrove swamps generate in newly generated chunks.")
        @Config.LangKey("config.wildupdatebackport.swamp.enabled")
        public boolean enabled = true;
    }

    public static final class DeepDark {
        @Config.RequiresWorldRestart
        @Config.Comment("The deep dark and ancient cities generate in newly generated chunks.")
        @Config.LangKey("config.wildupdatebackport.deepDark.enabled")
        public boolean enabled = true;
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
