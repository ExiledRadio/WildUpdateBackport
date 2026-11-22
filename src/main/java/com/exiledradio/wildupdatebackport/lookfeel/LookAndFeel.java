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
 * F3 keys, changed defaults) and shows the newest installed version's; this class hands Core 1.19's pieces.
 *
 * <ul>
 *   <li>1.19 (22w15a): the mangrove swamp panorama, with 1.19.4's overlay (unchanged since 1.16: one clear pixel);
 *       the title logo, the Mojang Studios logo, the button and check box textures and the title screen's
 *       Accessibility icon as 1.19.4's jar has them (unchanged since 1.16), so the mod alone shows 1.19's title
 *       screen;</li>
 *   <li>splashes: 1.19's seven and its removal, 1.19.1's one, 1.19.3's six (from the jars' {@code splashes.txt});</li>
 *   <li>menu music (22w16a, 22w17a): <i>Aerie</i>, <i>Firebugs</i> and <i>Labyrinthine</i> join the title screen's
 *       pool, and the five tracks 1.18 put there leave it (Caves &amp; Cliffs' names: a removal of a track that is not
 *       installed is nothing);</li>
 *   <li>F3 + F (cycle render distance) removed in 1.19 (22w11a); the F3 screen's "Allocation rate" line (client,
 *       {@code lookfeel.client.LookAndFeelClient});</li>
 *   <li>Darkness Pulsing (22w12a, Accessibility Settings): the scale the warden stream's Darkness reads;</li>
 *   <li>1.19.3: Panorama Scroll Speed is Core's own option and shows once 1.19.3 is installed; 1.19.3's creative tabs
 *       (Building Blocks, Colored Blocks, Natural Blocks ...) are Core's too, drawn with 1.19.3's {@code tabs.png}
 *       from this mod;</li>
 *   <li>1.19.4: Auto-Jump off by default (23w03a), applied once per instance; Damage Tilt (23w06a) and Glint Speed and
 *       Glint Strength (23w05a, also on Video Settings), through patches P1 to P3 ({@code docs/PATCHES.md});</li>
 *   <li>vanilla's own text as 1.19.4 words it ({@code vanilla_text/}, Mojang's own translations).</li>
 * </ul>
 * The rest of the 1.19.x list that only Core can do is {@code docs/CORE_REQUESTS.md} request 6.
 */
public final class LookAndFeel {

    private static final String ID = WildUpdateBackport.MOD_ID;

    /** LWJGL 2's {@code Keyboard.KEY_F}, written as a number so this class loads on a dedicated server. */
    static final int KEY_F = 33;

    /**
     * 1.19's Darkness Pulsing (Accessibility Settings), 0 to 100%: how far the Darkness effect's pulse darkens the
     * screen. 1.19.4's {@code darknessEffectScale}; the warden stream reads it by its id
     * ({@code Presentation.option("wildupdatebackport:darkness_effect_scale")}) or through {@link #darknessScale()}.
     */
    public static final SliderOption DARKNESS = new SliderOption(ID + ":darkness_effect_scale",
            "options.darknessEffectScale", OptionScreen.ACCESSIBILITY, 0.0, 1.0, 0.0, 1.0);

    /** 1.19.4's Damage Tilt (Accessibility Settings), 0 to 100%: scales the camera's tilt when the player is hurt (P1). */
    public static final SliderOption DAMAGE_TILT = new SliderOption(ID + ":damage_tilt_strength",
            "options.damageTiltStrength", OptionScreen.ACCESSIBILITY, 0.0, 1.0, 0.0, 1.0);

    /**
     * 1.19.4's Glint Speed (Accessibility and Video Settings), 0 to 100%, default 50%. 1.19.4's glint moves with
     * {@code millis * glintSpeed * 8} where 1.19.3's moved with {@code millis * 8}: at the default the glint runs at
     * half the earlier speed, as in 1.19.4 (P2, P3).
     */
    public static final SliderOption GLINT_SPEED = new SliderOption(ID + ":glint_speed",
            "options.glintSpeed", OptionScreen.ACCESSIBILITY, 0.0, 1.0, 0.0, 0.5);

    /**
     * 1.19.4's Glint Strength (Accessibility and Video Settings), 0 to 100%, default 75%: the glint shader's
     * {@code GlintAlpha}, which scales the added glint color (P2, P3).
     */
    public static final SliderOption GLINT_STRENGTH = new SliderOption(ID + ":glint_strength",
            "options.glintStrength", OptionScreen.ACCESSIBILITY, 0.0, 1.0, 0.0, 0.75);

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

    /** 1.19.3's creative tab sprites (vanilla's path; the copy in this mod's assets is the same path). */
    static final String CREATIVE_TABS = "textures/gui/container/creative_inventory/tabs.png";

    private static boolean registered;

    private LookAndFeel() {
    }

    /** Pre-init, both sides: hands Core 1.19's pieces. */
    public static void preInit() {
        if (registered) {
            return;
        }
        registered = true;
        for (SliderOption o : new SliderOption[] {DARKNESS, DAMAGE_TILT, GLINT_SPEED, GLINT_STRENGTH}) {
            o.zeroKey("options.off");
        }
        DARKNESS.tooltip("options.darknessEffectScale.tooltip");
        DAMAGE_TILT.tooltip("options.damageTiltStrength.tooltip");
        GLINT_SPEED.tooltip("options.glintSpeed.tooltip");
        GLINT_STRENGTH.tooltip("options.glintStrength.tooltip");

        Contribution v119 = Presentation.version("1.19");
        v119.panorama(ID + ":textures/gui/title/background/panorama_")
                .panoramaOverlay(new ResourceLocation(ID, "textures/gui/title/background/panorama_overlay.png"))
                .addSplashes(new ResourceLocation(ID, "texts/splashes_added_1_19.txt"))
                .removeSplashes(new ResourceLocation(ID, "texts/splashes_removed_1_19.txt"))
                .titleLogo(new ResourceLocation(ID, "textures/gui/title/minecraft.png"))
                .startupLogo("textures/gui/title/mojangstudios.png", new ResourceLocation(ID, "textures/gui/title/mojangstudios.png"))
                .replaceAsset("textures/gui/widgets.png", new ResourceLocation(ID, "textures/gui/widgets.png"))
                .replaceAsset("textures/gui/checkbox.png", new ResourceLocation(ID, "textures/gui/checkbox.png"))
                .titleAccessibilityIcon(new ResourceLocation(ID, "textures/gui/accessibility.png"))
                .multiplayerWarning("multiplayerWarning.header", "multiplayerWarning.message", "multiplayerWarning.check")
                .vanillaText(new ResourceLocation(ID, "vanilla_text"))
                .removeDebugKey(KEY_F);
        for (String track : MENU_REMOVED_1_19) {
            v119.removeMenuMusic(track);
        }
        for (String track : MENU_ADDED_1_19) {
            v119.addMenuMusic(track, 1);
        }
        // 1.19.4's Accessibility Settings: ... Distortion Effects, FOV Effects, Darkness Pulsing, Damage Tilt, Glint
        // Speed, Glint Strength, Hide Lightning Flashes, Monochrome Logo, Panorama Scroll Speed. FOV Effects is Nether
        // Update's; without it the four go to the end of the list, in this order.
        v119.option(DARKNESS.after("netherupdatebackport:fov_effect_scale"));

        Presentation.version("1.19.1").addSplashes(new ResourceLocation(ID, "texts/splashes_added_1_19_1.txt"));
        // 1.19.3: Panorama Scroll Speed is Backport Core's (it shows from 1.19.3 on), and so are 1.19.3's creative tabs
        // (Core's creative.client.ModernTabs, patch P47); their sprites are 1.19.3's tabs.png, seven 26-pixel tabs a row
        // where 1.12.2's had six 28-pixel ones.
        Contribution v1193 = Presentation.version("1.19.3").addSplashes(new ResourceLocation(ID, "texts/splashes_added_1_19_3.txt"));
        if (coreDrawsModernTabs()) {
            // Only a Core that lays the tabs out 26 wide (1.4.5 and later): an older one would draw these sprites at
            // 1.12.2's 28.
            v1193.replaceAsset(CREATIVE_TABS, new ResourceLocation(ID, CREATIVE_TABS));
        }

        // 1.19.4: Video Settings ends ... Entity Shadows, Distortion Effects, Entity Distance, FOV Effects, Autosave
        // Indicator, Glint Speed, Glint Strength: 1.12.2 has no autosave indicator, so the two go to the end there.
        GLINT_SPEED.alsoOn(OptionScreen.VIDEO);
        GLINT_STRENGTH.alsoOn(OptionScreen.VIDEO);
        Presentation.version("1.19.4")
                .gameOptionDefault("autoJump", "false")
                .option(DAMAGE_TILT.after(DARKNESS.id()))
                .option(GLINT_SPEED.after(DAMAGE_TILT.id()))
                .option(GLINT_STRENGTH.after(GLINT_SPEED.id()));
    }

    /** @return true when the installed Backport Core has 1.19.3's creative tabs (1.4.5 and later) */
    public static boolean coreDrawsModernTabs() {
        return LookAndFeel.class.getResource("/com/exiledradio/backportcore/creative/client/ModernTabs.class") != null;
    }

    /** @return Darkness Pulsing, 0 to 1 (1.19.4's {@code darknessEffectScale().get()}) */
    public static float darknessScale() {
        return (float) DARKNESS.get();
    }
}
