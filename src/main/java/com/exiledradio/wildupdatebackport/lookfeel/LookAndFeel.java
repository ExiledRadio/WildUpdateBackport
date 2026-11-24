package com.exiledradio.wildupdatebackport.lookfeel;

import com.exiledradio.backportcore.presentation.Contribution;
import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.backportcore.presentation.option.OptionScreen;
import com.exiledradio.backportcore.presentation.option.SliderOption;
import com.exiledradio.wildupdatebackport.WildUpdateBackport;
import net.minecraft.util.ResourceLocation;

/**
 * Stream Lf: 1.19.x's look and feel, the changes 1.19 to 1.19.4 made beyond their content. Backport Core owns every
 * piece only one version can have at a time (the panorama, the splash list, the menu music, the options screens, the
 * F3 keys, changed defaults) and shows the newest installed version's; this class declares 1.19 to 1.19.4 installed and
 * hands Core what is this mod's own.
 *
 * <ul>
 *   <li>1.19 (22w15a): the mangrove swamp panorama, with 1.19.4's overlay (unchanged since 1.16: one clear pixel);</li>
 *   <li>menu music (22w16a, 22w17a): <i>Aerie</i>, <i>Firebugs</i> and <i>Labyrinthine</i> join the title screen's
 *       pool, and the five tracks 1.18 put there leave it (Caves &amp; Cliffs' names: a removal of a track that is not
 *       installed is nothing);</li>
 *   <li>Darkness Pulsing (22w12a, Accessibility Settings): the scale the warden stream's Darkness reads.</li>
 * </ul>
 * The rest of 1.19.x's look and feel is Backport Core's own since Core 1.4.7 ({@code presentation.versions.Look119}:
 * the title logo, the Mojang Studios logo, the button, check box and creative tab textures, the Accessibility icon, the
 * multiplayer warning, the splashes, vanilla's text, F3 + F's removal and the F3 "Allocation rate" line, Auto-Jump off,
 * Damage Tilt, Glint Speed and Glint Strength with their patches), so that any 1.19 or later backport has it.
 */
public final class LookAndFeel {

    private static final String ID = WildUpdateBackport.MOD_ID;

    /**
     * 1.19's Darkness Pulsing (Accessibility Settings), 0 to 100%: how far the Darkness effect's pulse darkens the
     * screen. 1.19.4's {@code darknessEffectScale}; the warden stream reads it by its id
     * ({@code Presentation.option("wildupdatebackport:darkness_effect_scale")}) or through {@link #darknessScale()}.
     */
    public static final SliderOption DARKNESS = new SliderOption(ID + ":darkness_effect_scale",
            "options.darknessEffectScale", OptionScreen.ACCESSIBILITY, 0.0, 1.0, 0.0, 1.0);

    /** Caves &amp; Cliffs' 1.18 menu tracks, which 1.19 took off the title screen. */
    static final String[] MENU_REMOVED_1_19 = {
            "cavesandcliffsbackport:music/game/infinite_amethyst",
            "cavesandcliffsbackport:music/game/left_to_bloom",
            "cavesandcliffsbackport:music/game/one_more_day",
            "cavesandcliffsbackport:music/game/stand_tall",
            "cavesandcliffsbackport:music/game/wending",
    };

    /** The tracks 1.19 put on the title screen (1.19's {@code music.menu}, weight 1 each). */
    static final String[] MENU_ADDED_1_19 = {
            ID + ":music/game/swamp/aerie",
            ID + ":music/game/swamp/firebugs",
            ID + ":music/game/swamp/labyrinthine",
    };

    private static boolean registered;

    private LookAndFeel() {
    }

    /** Pre-init, both sides: hands Core 1.19's pieces. */
    public static void preInit() {
        if (registered) {
            return;
        }
        registered = true;
        DARKNESS.zeroKey("options.off");
        DARKNESS.tooltip("options.darknessEffectScale.tooltip");

        Contribution v119 = Presentation.version("1.19");
        v119.panorama(ID + ":textures/gui/title/background/panorama_")
                .panoramaOverlay(new ResourceLocation(ID, "textures/gui/title/background/panorama_overlay.png"));
        for (String track : MENU_REMOVED_1_19) {
            v119.removeMenuMusic(track);
        }
        for (String track : MENU_ADDED_1_19) {
            v119.addMenuMusic(track, 1);
        }
        // 1.19.4's Accessibility Settings: ... Distortion Effects, FOV Effects, Darkness Pulsing, Damage Tilt, Glint
        // Speed, Glint Strength ... FOV Effects is Core's (from Nether Update); Damage Tilt and the glint sliders are
        // Core's and follow Darkness Pulsing.
        v119.option(DARKNESS.after("netherupdatebackport:fov_effect_scale"));

        // 1.19.1, 1.19.3 and 1.19.4: their pieces are Core's (splashes, creative tabs, options, Auto-Jump off).
        Presentation.version("1.19.1");
        Presentation.version("1.19.3");
        Presentation.version("1.19.4");
    }

    /** @return Darkness Pulsing, 0 to 1 (1.19.4's {@code darknessEffectScale().get()}) */
    public static float darknessScale() {
        return (float) DARKNESS.get();
    }
}
