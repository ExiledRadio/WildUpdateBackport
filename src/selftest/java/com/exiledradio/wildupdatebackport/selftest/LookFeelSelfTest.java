package com.exiledradio.wildupdatebackport.selftest;

import com.exiledradio.backportcore.presentation.Contribution;
import com.exiledradio.backportcore.presentation.DebugKeys;
import com.exiledradio.backportcore.presentation.GameVersion;
import com.exiledradio.backportcore.presentation.MenuMusic;
import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.backportcore.presentation.Splashes;
import com.exiledradio.backportcore.presentation.VersionedDefaults;
import com.exiledradio.backportcore.presentation.option.OptionScreen;
import com.exiledradio.backportcore.presentation.option.PresentationOption;
import com.exiledradio.backportcore.presentation.option.SliderOption;
import com.exiledradio.wildupdatebackport.WildUpdateBackport;
import com.exiledradio.wildupdatebackport.lookfeel.LookAndFeel;
import com.exiledradio.backportcore.presentation.versions.Look119;
import net.minecraft.util.ResourceLocation;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Stream Lf: 1.19.x's look and feel registered with Backport Core: this mod's panorama, music and Darkness Pulsing, and
 * Core's own 1.19 pieces (Core 1.4.7 took them over) showing with this mod installed.
 */
public final class LookFeelSelfTest implements SelfTest {

    private static final String ID = WildUpdateBackport.MOD_ID;

    @Override
    public String name() {
        return "lookfeel";
    }

    @Override
    public void run(Check c) throws Exception {
        registration(c);
        panorama(c);
        splashes(c);
        music(c);
        debugKeys(c);
        options(c);
        defaults(c);
        lang(c);
    }

    // ------------------------------------------------------------------------------------------

    private static void registration(Check c) {
        for (String v : new String[] {"1.19", "1.19.1", "1.19.3", "1.19.4"}) {
            Contribution con = Presentation.contribution(v);
            c.ok(con != null && con.owners().contains(ID), v + " is registered by " + ID + ": " + con);
        }
        // On its own (this dev server) 1.19.4 is the newest version: 1.19 wins every single-slot piece.
        c.eq(GameVersion.parse("1.19.4"), Presentation.newest(), "the newest installed version");
        c.ok(Presentation.isAtLeast("1.19.3"), "isAtLeast(1.19.3)");
        // Core's own 1.19 pieces (Core 1.4.7): served from Core's jar now that this mod declares 1.19.
        String[][] assets = {
                {"textures/gui/title/minecraft.png", "look/1.19/", "256x256"},
                {"textures/gui/title/mojangstudios.png", "look/1.19/", null},
                {"textures/gui/widgets.png", "look/1.19/", "256x256"},
                {"textures/gui/checkbox.png", "look/1.19/", "64x64"},
                {"textures/gui/container/creative_inventory/tabs.png", "look/1.19.3/", "256x256"},
        };
        for (String[] a : assets) {
            ResourceLocation src = Presentation.asset(a[0]);
            c.eq(new ResourceLocation("backportcore", a[1] + a[0]), src, "Core serves vanilla's " + a[0] + " from its own 1.19 pieces");
            int[] size;
            try {
                size = src == null ? null : pngSize(src);
            } catch (IOException e) {
                size = null;
            }
            c.ok(size != null && (a[2] == null || a[2].equals(size[0] + "x" + size[1])), a[0] + " is in Core's jar: " + Arrays.toString(size));
        }
        c.eq(new ResourceLocation("backportcore", "look/1.19/textures/gui/accessibility.png"), Presentation.titleAccessibilityIcon(),
                "the title screen's Accessibility icon");
        String[] warn = Presentation.multiplayerWarning();
        c.ok(warn != null && "multiplayerWarning.header".equals(warn[0]) && "multiplayerWarning.check".equals(warn[2]),
                "the multiplayer warning uses vanilla's keys: " + Arrays.toString(warn));
        c.ok(Presentation.vanillaTexts().contains(new ResourceLocation("backportcore", "look/1.19/vanilla_text")), "1.19's vanilla text (Core's) is registered");
        c.ok(!Presentation.vanillaTexts().contains(new ResourceLocation(ID, "vanilla_text")), "this mod has no vanilla text of its own any more");
        for (String gone : new String[] {"textures/gui/widgets.png", "textures/gui/title/minecraft.png", "texts/splashes_added_1_19.txt", "vanilla_text/en_us.lang"}) {
            c.ok(LookFeelSelfTest.class.getResourceAsStream("/assets/" + ID + "/" + gone) == null, "moved to Core: " + gone);
        }
        c.eq(com.exiledradio.backportcore.presentation.VanillaRevisions.Startup.STUDIOS_1_16,
                com.exiledradio.backportcore.presentation.VanillaRevisions.startup(), "the startup screen is 1.16's (1.19 kept it)");
    }

    private static void panorama(Check c) throws IOException {
        List<Presentation.Panorama> all = Presentation.panoramas();
        Presentation.Panorama last = all.isEmpty() ? null : all.get(all.size() - 1);
        if (!c.ok(last != null && last.version.equals(GameVersion.parse("1.19")), "the newest panorama is 1.19's: " + all.size() + " panoramas")) {
            return;
        }
        for (int i = 0; i < 6; i++) {
            ResourceLocation face = last.faces[i];
            c.eq(new ResourceLocation(ID, "textures/gui/title/background/panorama_" + i + ".png"), face, "panorama face " + i);
            int[] size = pngSize(face);
            c.ok(size != null && size[0] == 1024 && size[1] == 1024, "face " + i + " is 1.19's 1024x1024 image: " + Arrays.toString(size));
        }
        ResourceLocation overlay = Presentation.panoramaOverlay();
        c.eq(new ResourceLocation(ID, "textures/gui/title/background/panorama_overlay.png"), overlay, "the panorama overlay");
        int[] size = overlay == null ? null : pngSize(overlay);
        c.ok(size != null && size[0] == 1 && size[1] == 1, "1.19.4's overlay is one clear pixel: " + Arrays.toString(size));
    }

    private static final String[] ADDED = {"Shriek like a Sculk Shrieker!", "Who let the frogs out?", "RIBBIT!", "Croak team!",
            "Nooooooooooooo!", "Flower forest TM perfume", "Hat Fridays!", "Technoblade never dies!", "You are valid!",
            "I'm glad you're here!", "You are welcome here!", "Your gender is valid!", "Contains infinite genders!", "Made with lave!"};

    private static void splashes(Check c) throws IOException {
        c.eq(7, lines(new ResourceLocation("backportcore", "look/1.19/texts/splashes_added.txt")).size(), "1.19 adds 7 splashes to 1.18.2's");
        c.eq(1, lines(new ResourceLocation("backportcore", "look/1.19/texts/splashes_removed.txt")).size(), "1.19 removes one");
        c.eq(1, lines(new ResourceLocation("backportcore", "look/1.19.1/texts/splashes_added.txt")).size(), "1.19.1 adds one");
        c.eq(6, lines(new ResourceLocation("backportcore", "look/1.19.3/texts/splashes_added.txt")).size(), "1.19.3 adds 6");
        List<String> base = lines("/assets/minecraft/texts/splashes.txt");
        c.ok(base.size() > 300, "1.12.2's splashes.txt is readable: " + base.size());
        List<String> pool = Splashes.pool(base, Presentation.installed(), f -> lines(f));
        for (String s : ADDED) {
            c.ok(pool.contains(s), "the splash pool has 1.19.x's \"" + s + "\"");
        }
        Set<String> expected = new LinkedHashSet<String>();
        for (String s : base) {
            expected.add(s.trim()); // as Core's pool reads them
        }
        List<String> removed = lines(new ResourceLocation("backportcore", "look/1.19/texts/splashes_removed.txt"));
        c.ok(base.contains("Привет Россия!") && !pool.contains("Привет Россия!") && removed.contains("Привет Россия!"),
                "1.19 took 1.12.2's \"Привет Россия!\" out of the pool");
        expected.removeAll(removed);
        expected.addAll(Arrays.asList(ADDED));
        Set<String> missing = new LinkedHashSet<String>(expected);
        missing.removeAll(pool);
        Set<String> extra = new LinkedHashSet<String>(pool);
        extra.removeAll(expected);
        c.ok(missing.isEmpty() && extra.isEmpty(), "pool: 1.12.2's set less 1.19's removal plus 1.19.x's 14; missing "
                + missing + ", extra " + extra + ", installed " + Presentation.installed());
    }

    private static void music(Check c) throws IOException {
        List<MenuMusic.Track> base = new ArrayList<MenuMusic.Track>();
        for (int i = 1; i <= 4; i++) {
            base.add(new MenuMusic.Track("minecraft:music/menu/menu" + i, 1));
        }
        // As Caves & Cliffs would have it installed: 1.18's five menu tracks, then 1.19 takes them away.
        Contribution v118 = Presentation.detached("1.18");
        for (String t : new String[] {"infinite_amethyst", "left_to_bloom", "one_more_day", "stand_tall", "wending"}) {
            v118.addMenuMusic("cavesandcliffsbackport:music/game/" + t, 1);
        }
        List<Contribution> withCc = new ArrayList<Contribution>();
        withCc.add(v118);
        withCc.addAll(Presentation.installed());
        for (List<Contribution> installed : java.util.Arrays.<List<Contribution>>asList(Presentation.installed(), withCc)) {
            List<String> names = new ArrayList<String>();
            for (MenuMusic.Track t : MenuMusic.pool(base, installed)) {
                names.add(t.soundName);
            }
            String what = installed == withCc ? " (with 1.18's tracks)" : "";
            c.eq(Arrays.asList("minecraft:music/menu/menu1", "minecraft:music/menu/menu2", "minecraft:music/menu/menu3",
                    "minecraft:music/menu/menu4", ID + ":music/game/swamp/aerie", ID + ":music/game/swamp/firebugs",
                    ID + ":music/game/swamp/labyrinthine"), sorted(names), "1.19's menu pool" + what);
        }
        c.ok(MenuMusic.changed(Presentation.installed()), "Core sees a changed menu pool");
        for (String t : new String[] {"aerie", "firebugs", "labyrinthine"}) {
            InputStream in = LookFeelSelfTest.class.getResourceAsStream("/assets/" + ID + "/sounds/music/game/swamp/" + t + ".ogg");
            byte[] b = in == null ? new byte[0] : read(in);
            c.ok(b.length > 1000000 && b[0] == 'O' && b[1] == 'g' && b[2] == 'g' && b[3] == 'S', t + ".ogg is an Ogg file of " + b.length + " bytes");
        }
    }

    private static List<String> sorted(List<String> in) {
        List<String> out = new ArrayList<String>(in);
        java.util.Collections.sort(out);
        return out;
    }

    private static void debugKeys(Check c) {
        Map<Integer, DebugKeys.Key> keys = DebugKeys.map(Presentation.installed());
        c.ok(!keys.containsKey(33), "F3 + F (cycle render distance) is gone from 1.19 on: " + keys.keySet());
        c.ok(keys.containsKey(DebugKeys.KEY_Q), "F3 + Q stays");
        c.ok(DebugKeys.changed(Presentation.installed()), "Core sees a changed key map");
    }

    private static void options(Check c) {
        Map<String, PresentationOption> byId = new HashMap<String, PresentationOption>();
        for (PresentationOption o : Presentation.options()) {
            byId.put(o.id(), o);
        }
        Object[][] want = {
                {LookAndFeel.DARKNESS, "1.19", "options.darknessEffectScale", 1.0},
                {Look119.DAMAGE_TILT, "1.19.4", "options.damageTiltStrength", 1.0},
                {Look119.GLINT_SPEED, "1.19.4", "options.glintSpeed", 0.5},
                {Look119.GLINT_STRENGTH, "1.19.4", "options.glintStrength", 0.75},
        };
        for (Object[] w : want) {
            SliderOption o = (SliderOption) w[0];
            c.ok(byId.get(o.id()) == o, o.id() + " is registered");
            c.eq(OptionScreen.ACCESSIBILITY, o.screen(), o.id() + "'s screen");
            c.eq(GameVersion.parse((String) w[1]), o.since(), o.id() + "'s since");
            c.eq(w[2], o.labelKey(), o.id() + "'s label");
            c.eq(w[2] + ".tooltip", o.tooltipKey(), o.id() + "'s tooltip");
            c.eq("options.off", o.zeroKey(), o.id() + " reads OFF at zero");
            c.eq(w[3], o.defaultValue(), o.id() + "'s default");
            c.eq(0.0, o.min(), o.id() + "'s min");
            c.eq(1.0, o.max(), o.id() + "'s max");
            c.ok(o.isShown(), o.id() + " shows at the newest version");
        }
        c.eq("netherupdatebackport:fov_effect_scale", LookAndFeel.DARKNESS.after(), "Darkness Pulsing follows FOV Effects");
        c.eq(LookAndFeel.DARKNESS.id(), Look119.DAMAGE_TILT.after(), "Damage Tilt follows Darkness Pulsing");
        c.eq(Look119.DAMAGE_TILT.id(), Look119.GLINT_SPEED.after(), "Glint Speed follows Damage Tilt");
        c.eq(Look119.GLINT_SPEED.id(), Look119.GLINT_STRENGTH.after(), "Glint Strength follows Glint Speed");
        List<String> access = ids(Presentation.optionsOn(OptionScreen.ACCESSIBILITY));
        int d = access.indexOf(LookAndFeel.DARKNESS.id());
        int t = access.indexOf(Look119.DAMAGE_TILT.id());
        int s = access.indexOf(Look119.GLINT_SPEED.id());
        int g = access.indexOf(Look119.GLINT_STRENGTH.id());
        c.ok(d >= 0 && t > d && s > t && g > s, "Accessibility Settings: Darkness Pulsing, Damage Tilt, Glint Speed, Glint Strength: " + access);
        c.ok(access.contains("backportcore:panorama_speed"), "Core's Panorama Scroll Speed shows from 1.19.3 on: " + access);
        List<String> video = ids(Presentation.optionsOn(OptionScreen.VIDEO));
        c.ok(video.contains(Look119.GLINT_SPEED.id()) && video.contains(Look119.GLINT_STRENGTH.id())
                && !video.contains(Look119.DAMAGE_TILT.id()), "Video Settings has the two glint sliders, and only them: " + video);
        c.eq(1.0F, LookAndFeel.darknessScale(), "Darkness Pulsing reads 100% by default");
    }

    private static List<String> ids(List<PresentationOption> options) {
        List<String> out = new ArrayList<String>();
        for (PresentationOption o : options) {
            out.add(o.id());
        }
        return out;
    }

    private static void defaults(Check c) {
        boolean autoJump = false;
        for (VersionedDefaults.Entry e : VersionedDefaults.pending(Presentation.installed())) {
            if ("autoJump".equals(e.key)) {
                autoJump = "false".equals(e.value) && e.version.equals(GameVersion.parse("1.19.4"));
            }
        }
        c.ok(autoJump, "1.19.4's Auto-Jump OFF waits for the client's first title screen (a server never applies it)");
    }

    private static final String[] OPTION_KEYS = {"options.darknessEffectScale"};
    /** The option labels Core has now (Core 1.4.7), no longer in this mod's lang. */
    private static final String[] CORE_KEYS = {"options.damageTiltStrength", "options.glintSpeed", "options.glintStrength"};

    /** Vanilla's own keys as 1.19.4 words them (vanilla_text/), newest version wins. */
    private static final String[][] VANILLA = {
            {"selectWorld.mapFeatures.info", "Villages, Shipwrecks, etc."},
            {"options.realmsNotifications", "Realms News & Invites"},
            {"options.vsync", "VSync"},
            {"key.swapHands", "Swap Item With Offhand"},
            {"deathScreen.title", "You Died!"},
            {"advancements.story.mine_stone.description", "Mine Stone with your new Pickaxe"},
            {"multiplayerWarning.header", "Caution: Third-Party Online Play"},
    };

    private static void lang(Check c) throws IOException {
        Map<String, String> en = langFile(ID, "lang", "en_us");
        Map<String, String> core = langFile("backportcore", "lang", "en_us");
        Map<String, String> ven = langFile("backportcore", "look/1.19/vanilla_text", "en_us");
        c.eq("Darkness Pulsing", en.get("options.darknessEffectScale"), "en_us options.darknessEffectScale");
        c.eq("Damage Tilt", core.get("options.damageTiltStrength"), "Core's en_us options.damageTiltStrength");
        for (String k : OPTION_KEYS) {
            c.ok(en.get(k) != null && en.get(k + ".tooltip") != null, "en_us has " + k + " and its tooltip");
            c.ok(!ven.containsKey(k), k + " is the mod's own lang, not vanilla text");
        }
        for (String k : CORE_KEYS) {
            c.ok(core.get(k) != null && core.get(k + ".tooltip") != null, "Core's en_us has " + k + " and its tooltip");
            c.ok(!en.containsKey(k), k + " is no longer in this mod's lang");
        }
        for (String[] kv : VANILLA) {
            c.eq(kv[1], ven.get(kv[0]), "Core's 1.19 vanilla_text en_us " + kv[0]);
            c.ok(!en.containsKey(kv[0]), kv[0] + " is not in the mod's own lang (load order would decide it)");
        }
        c.ok(!ven.containsKey("debug.cycle_renderdistance.help"), "F3 + F's help line is not reworded: 1.19 removed the key");
    }

    // ------------------------------------------------------------------------------------------

    private static Map<String, String> langFile(String domain, String folder, String code) throws IOException {
        Map<String, String> out = new HashMap<String, String>();
        for (String line : lines(new ResourceLocation(domain, folder + "/" + code + ".lang"))) {
            int eq = line.indexOf('=');
            if (eq > 0 && !line.startsWith("#")) {
                out.put(line.substring(0, eq), line.substring(eq + 1));
            }
        }
        return out;
    }

    private static List<String> lines(ResourceLocation rl) throws IOException {
        return lines("/assets/" + rl.getNamespace() + "/" + rl.getPath());
    }

    private static List<String> lines(String path) throws IOException {
        InputStream in = LookFeelSelfTest.class.getResourceAsStream(path);
        if (in == null) {
            throw new IOException("not on the classpath: " + path);
        }
        List<String> out = new ArrayList<String>();
        BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        try {
            String line;
            while ((line = r.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    out.add(line);
                }
            }
        } finally {
            r.close();
        }
        return out;
    }

    private static int[] pngSize(ResourceLocation rl) throws IOException {
        InputStream in = LookFeelSelfTest.class.getResourceAsStream("/assets/" + rl.getNamespace() + "/" + rl.getPath());
        if (in == null) {
            return null;
        }
        byte[] b = read(in);
        if (b.length < 24 || b[1] != 'P' || b[2] != 'N' || b[3] != 'G') {
            return null;
        }
        return new int[] {readInt(b, 16), readInt(b, 20)};
    }

    private static int readInt(byte[] b, int at) {
        return (b[at] & 0xFF) << 24 | (b[at + 1] & 0xFF) << 16 | (b[at + 2] & 0xFF) << 8 | (b[at + 3] & 0xFF);
    }

    private static byte[] read(InputStream in) throws IOException {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        } finally {
            in.close();
        }
    }
}
