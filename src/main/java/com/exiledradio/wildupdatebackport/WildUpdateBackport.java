package com.exiledradio.wildupdatebackport;

import com.exiledradio.wildupdatebackport.network.NetworkHandler;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Wild Update Backport: Minecraft 1.19's sculk, warden, mangroves, mud, frogs and allays for 1.12.2.
 *
 * <h2>Layout</h2>
 * One package per stream of content (see {@code docs/PLAN.md}): each registers its own blocks, items,
 * entities and sounds from its own {@code @Mod.EventBusSubscriber} class, behind its own config
 * category's {@code enabled} switch. {@code common} holds what several streams share. This class only
 * calls each stream's lifecycle hooks, one line per stream.
 *
 * <h2>Behavior source</h2>
 * Current Java Edition as minecraft.wiki describes it, with 1.14.4's decompiled code for the numbers the
 * wiki leaves out. Where today's game differs from 1.13, today's wins.
 */
@Mod(
        modid = WildUpdateBackport.MOD_ID,
        name = WildUpdateBackport.MOD_NAME,
        version = WildUpdateBackport.VERSION,
        acceptedMinecraftVersions = "[1.12.2]",
        // Loaded before quark, so recipes are listed first: its trapdoor recipe takes any planks, and listed
        // ahead of this mod's it turned this mod's planks into oak trapdoors instead of their own.
        dependencies = "required-after:backportcore@[1.1.0,);before:quark"
)
public final class WildUpdateBackport {

    public static final String MOD_ID = "wildupdatebackport";
    public static final String MOD_NAME = "Wild Update Backport";
    public static final String VERSION = "@VERSION@";

    public static final Logger LOGGER = LogManager.getLogger(MOD_NAME);

    @Mod.Instance(MOD_ID)
    public static WildUpdateBackport instance;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        // This mod's spawn eggs in Miscellaneous where vanilla lists each mob's (Backport Core's creative
        // placement).
        com.exiledradio.backportcore.creative.CreativePlacement.placeSpawnEggs(MOD_ID);
        NetworkHandler.init();
        com.exiledradio.wildupdatebackport.gameevent.AvoidVibrationTrigger.register();
        com.exiledradio.wildupdatebackport.sculk.KillMobNearCatalystTrigger.register();
        com.exiledradio.wildupdatebackport.frog.FrogContent.registerTriggers();
        com.exiledradio.wildupdatebackport.allay.AllayContent.preInit();

        // Client-only setup lives in a class a dedicated server never loads. The call names no client
        // type, so this class verifies on a server - a guarded method here would not.
        if (event.getSide().isClient()) {
            com.exiledradio.wildupdatebackport.client.ClientSetup.init();
        }
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        com.exiledradio.wildupdatebackport.common.Compat.init();
        com.exiledradio.wildupdatebackport.mangrove.MangroveContent.init();
        com.exiledradio.wildupdatebackport.mud.MudContent.init();
        com.exiledradio.wildupdatebackport.frog.FrogContent.init();
        com.exiledradio.wildupdatebackport.allay.AllayContent.init();
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
    }

    /**
     * Starts the headless self-test when a test build asks for one ({@code -Pselftest=<stream>}).
     *
     * <p>Reached by name, so a release jar - which has no test classes - never links against them.
     */
    @Mod.EventHandler
    public void serverStarted(FMLServerStartedEvent event) {
        String selftest = System.getProperty(MOD_ID + ".selftest");
        if (selftest == null || selftest.isEmpty()) {
            return;
        }
        try {
            Class.forName("com.exiledradio.wildupdatebackport.selftest.SelfTestRunner")
                    .getMethod("start", String.class).invoke(null, selftest);
        } catch (ReflectiveOperationException e) {
            LOGGER.error("SELFTEST DONE pass=0 fail=1: '{}' requested but the test classes are not in this build.", selftest, e);
            // A test run must always end, or it holds its port and its log files.

            net.minecraftforge.fml.common.FMLCommonHandler.instance().getMinecraftServerInstance().initiateShutdown();
        }
    }
}
