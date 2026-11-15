package com.exiledradio.wildupdatebackport.selftest;

import com.exiledradio.wildupdatebackport.WildUpdateBackport;
import com.exiledradio.wildupdatebackport.lookfeel.LookAndFeel;
import com.exiledradio.wildupdatebackport.warden.DarknessFactor;
import com.exiledradio.wildupdatebackport.warden.EntityWarden;
import com.exiledradio.wildupdatebackport.warden.WardenContent;
import com.exiledradio.wildupdatebackport.warden.client.DarknessClient;
import com.exiledradio.wildupdatebackport.warden.client.DarknessShaders;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.potion.PotionEffect;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.opengl.GL11;

import java.io.File;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Darkness on the client: a survival player in a superflat world (stone pillars with torches every 4 blocks ahead)
 * gets Darkness from {@code WardenContent.applyDarknessAround}, and the test checks and photographs it at the pulse's
 * peak, middle and rest, then after it ends: the lightmap (sky 15 and block 15 entries), the fog's end (15 blocks at
 * full factor) and, with OptiFine and a shader pack loaded, the P10/P11 values ({@code Shaders.darknessFactor},
 * {@code darknessLightFactor}, the {@code ShaderParameterFloat} constants and a pack's custom uniform
 * {@code maxBlindnessDarkness}). With a shader pack the phases run once with it and once with shaders off, and the
 * pack is put back. Screenshots: {@code screenshots/wu_darkness_<setup>_<phase>.png}.
 *
 * <pre>    ./gradlew runClient -Pclienttest=darkness</pre>
 * or a test instance with a file {@code selftest-darkness} in its game directory.
 */
public final class DarknessClientSelfTest {

    private static final String WORLD = "wu_darkness_selftest";
    private static final int[] PHASES = {0, 10, 40};
    private static final String[] PHASE_NAMES = {"peak", "mid", "rest"};

    private final List<String> failures = new ArrayList<String>();
    private int passed;
    private int state;
    private int wait;
    private int phase;
    private String setup = "vanilla";
    private String pack;
    /** Set by a tick; the next frame records and photographs it. */
    private String shot;
    private float fogEnd = -1.0F;
    private float baselineSky = -1.0F;
    private float baselineBlock = -1.0F;
    private final float[] phaseSky = new float[3];
    private AtomicReference<PotionEffect> applied;

    public static void start() {
        MinecraftForge.EVENT_BUS.register(new DarknessClientSelfTest());
        WildUpdateBackport.LOGGER.info("CLIENT SELFTEST darkness requested");
    }

    // --- per frame ---------------------------------------------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void fog(EntityViewRenderEvent.RenderFogEvent event) {
        if (event.getFogMode() != -1) {
            this.fogEnd = GL11.glGetFloat(GL11.GL_FOG_END);
        }
    }

    @SubscribeEvent
    public void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || this.shot == null) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        String name = this.shot;
        this.shot = null;
        try {
            record(mc, name, event.renderTickTime);
        } catch (Throwable t) {
            WildUpdateBackport.LOGGER.error("CLIENT SELFTEST threw", t);
            fail("threw " + t);
        }
        ScreenShotHelper.saveScreenshot(mc.gameDir, "wu_darkness_" + this.setup + "_" + name + ".png", mc.displayWidth, mc.displayHeight,
                mc.getFramebuffer());
        WildUpdateBackport.LOGGER.info("CLIENT SELFTEST screenshot wu_darkness_{}_{}.png", this.setup, name);
    }

    private void record(Minecraft mc, String name, float partial) throws ReflectiveOperationException {
        float scale = LookAndFeel.darknessScale();
        float factor = DarknessClient.factor(partial);
        float pulse = DarknessFactor.pulse(mc.player.ticksExisted, partial, factor * scale) * scale;
        int[] lightmap = (int[]) ReflectionHelper.findField(EntityRenderer.class, "lightmapColors", "field_78504_Q").get(mc.entityRenderer);
        float sky = luma(lightmap[15 * 16]);
        float block = luma(lightmap[15]);
        WildUpdateBackport.LOGGER.info("CLIENT SELFTEST {} {}: age {} factor {} pulse {} scale {} lightmap sky15 {} block15 {} fogEnd {}",
                this.setup, name, mc.player.ticksExisted, factor, pulse, scale, sky, block, this.fogEnd);
        boolean shaders = shaderPackLoaded();
        if ("none".equals(name)) {
            this.baselineSky = sky;
            this.baselineBlock = block;
            ok(factor == 0.0F, this.setup + ": no factor without the effect");
            ok(this.fogEnd > 30.0F, this.setup + ": fog at the view distance without the effect: " + this.fogEnd);
        } else if ("after".equals(name)) {
            ok(factor == 0.0F, this.setup + ": the factor is back to 0 after the effect");
            ok(Math.abs(sky - this.baselineSky) < 0.02F, this.setup + ": the lightmap is back: " + sky + " vs " + this.baselineSky);
        } else {
            ok(Math.abs(factor - 1.0F) < 1.0E-4F, this.setup + " " + name + ": full factor, " + factor);
            if (!shaders) {
                ok(Math.abs(this.fogEnd - 15.0F) < 0.5F, this.setup + " " + name + ": the fog ends at 15 blocks: " + this.fogEnd);
            }
            int i = indexOf(name);
            this.phaseSky[i] = sky;
            // At rest only the brightness setting is lowered (by the factor), so with Moody (0) nothing changes there.
            if ("rest".equals(name) && mc.gameSettings.gammaSetting <= 0.0F) {
                ok(sky <= this.baselineSky, this.setup + " rest: the lightmap no brighter (brightness 0): " + sky + " <= " + this.baselineSky);
            } else {
                ok(sky < this.baselineSky, this.setup + " " + name + ": the lightmap is darker than without the effect: " + sky + " < " + this.baselineSky);
            }
            ok(block <= this.baselineBlock, this.setup + " " + name + ": torch light no brighter: " + block + " <= " + this.baselineBlock);
            if ("peak".equals(name)) {
                ok(pulse > 0.40F * scale * scale, this.setup + " peak: the pulse is near 0.45: " + pulse);
            } else if ("rest".equals(name)) {
                ok(pulse == 0.0F, this.setup + " rest: no pulse: " + pulse);
                ok(this.phaseSky[0] < sky, this.setup + ": darker at the peak than at rest: " + this.phaseSky[0] + " < " + sky);
            }
        }
        if (optifine()) {
            Class<?> s = Class.forName("net.optifine.shaders.Shaders");
            float of = staticFloat(s, "darknessFactor");
            float ol = staticFloat(s, "darknessLightFactor");
            WildUpdateBackport.LOGGER.info("CLIENT SELFTEST {} {}: Shaders.darknessFactor {} darknessLightFactor {} (shaders {})", this.setup, name, of, ol,
                    shaders);
            if (shaders) {
                ok(Math.abs(of - DarknessShaders.factor(partial)) < 0.02F, this.setup + " " + name + ": Shaders.darknessFactor " + of);
                ok(Math.abs(ol - DarknessShaders.lightFactor(partial)) < 0.05F, this.setup + " " + name + ": Shaders.darknessLightFactor " + ol
                        + " vs " + DarknessShaders.lightFactor(partial));
                ok(sent(s.getField("uniform_darknessFactor").get(null), of), this.setup + " " + name + ": darknessFactor " + of + " sent to the pack's programs");
                ok(sent(s.getField("uniform_darknessLightFactor").get(null), ol), this.setup + " " + name + ": darknessLightFactor " + ol
                        + " sent to the pack's programs");
                Object custom = customUniform(s, "maxBlindnessDarkness");
                if (custom != null) {
                    WildUpdateBackport.LOGGER.info("CLIENT SELFTEST {} {}: custom uniform maxBlindnessDarkness per program {}", this.setup, name,
                            java.util.Arrays.toString(programValues(custom)).replace("-3.4028235E38", "-"));
                    ok(sent(custom, of), this.setup + " " + name + ": the pack's maxBlindnessDarkness = max(blindness, darknessFactor) sent as " + of);
                }
            }
        }
    }

    private static int indexOf(String name) {
        for (int i = 0; i < PHASE_NAMES.length; i++) {
            if (PHASE_NAMES[i].equals(name)) {
                return i;
            }
        }
        return 0;
    }

    private static float luma(int argb) {
        return (((argb >> 16) & 255) * 0.299F + ((argb >> 8) & 255) * 0.587F + (argb & 255) * 0.114F) / 255.0F;
    }

    // --- per tick ----------------------------------------------------------------------------------------------------

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || this.state == 99) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (this.wait > 0) {
            this.wait--;
            return;
        }
        try {
            step(mc);
        } catch (Throwable t) {
            WildUpdateBackport.LOGGER.error("CLIENT SELFTEST threw", t);
            fail("threw " + t);
            finish(mc);
        }
    }

    private void step(Minecraft mc) throws Exception {
        switch (this.state) {
            case 0:
                if (!(mc.currentScreen instanceof GuiMainMenu)) {
                    return;
                }
                mc.gameSettings.pauseOnLostFocus = false;
                this.wait = 40;
                this.state = 1;
                return;
            case 1:
                if (optifine()) {
                    Object name = Class.forName("net.optifine.shaders.Shaders").getField("currentShaderName").get(null);
                    this.pack = name == null ? null : name.toString();
                    WildUpdateBackport.LOGGER.info("CLIENT SELFTEST OptiFine, shader pack {}", this.pack);
                    this.setup = shaderPackLoaded() ? "shaders" : "optifine";
                    ok(Class.forName("net.optifine.shaders.uniform.ShaderParameterFloat").getField("DARKNESS_FACTOR").get(null) != null,
                            "P11: ShaderParameterFloat.DARKNESS_FACTOR");
                }
                mc.getSaveLoader().deleteWorldDirectory(WORLD);
                mc.launchIntegratedServer(WORLD, WORLD, new WorldSettings(1719L, GameType.SURVIVAL, false, false, WorldType.FLAT));
                this.state = 2;
                return;
            case 2: {
                if (mc.world == null || mc.player == null || mc.getIntegratedServer() == null) {
                    return;
                }
                IntegratedServer server = mc.getIntegratedServer();
                server.addScheduledTask(() -> {
                    WorldServer w = server.worlds[0];
                    w.getWorldInfo().setDifficulty(EnumDifficulty.PEACEFUL);
                    w.getGameRules().setOrCreateGameRule("doDaylightCycle", "false");
                    w.getGameRules().setOrCreateGameRule("doWeatherCycle", "false");
                    w.setWorldTime(6000L);
                    EntityPlayerMP p = server.getPlayerList().getPlayers().get(0);
                    w.getChunk(0, 0);
                    int y = w.getHeight(new BlockPos(0, 0, 0)).getY();
                    WildUpdateBackport.LOGGER.info("CLIENT SELFTEST ground at y {}", y);
                    for (int z = 4; z <= 48; z += 4) {
                        for (int dy = 0; dy < 3; dy++) {
                            w.setBlockState(new BlockPos(-2, y + dy, z), Blocks.STONEBRICK.getDefaultState());
                            w.setBlockState(new BlockPos(2, y + dy, z), Blocks.STONEBRICK.getDefaultState());
                        }
                        w.setBlockState(new BlockPos(-2, y + 3, z), Blocks.TORCH.getDefaultState());
                        w.setBlockState(new BlockPos(2, y + 3, z), Blocks.GLOWSTONE.getDefaultState());
                    }
                    p.connection.setPlayerLocation(0.5D, y, 0.5D, 0.0F, 5.0F);
                });
                this.wait = 200;
                this.state = 3;
                return;
            }
            case 3:
                ok(mc.playerController.getCurrentGameType() == GameType.SURVIVAL, "the player is in survival");
                this.shot = "none";
                this.wait = 5;
                this.state = 4;
                return;
            case 4: {
                IntegratedServer server = mc.getIntegratedServer();
                AtomicReference<PotionEffect> got = new AtomicReference<PotionEffect>();
                server.addScheduledTask(() -> {
                    EntityPlayerMP p = server.getPlayerList().getPlayers().get(0);
                    WardenContent.applyDarknessAround(server.worlds[0], p.getPositionVector().add(5.0D, 0.0D, 0.0D), null,
                            EntityWarden.DARKNESS_RADIUS);
                    PotionEffect e = p.getActivePotionEffect(WardenContent.darkness);
                    // A copy: the live effect counts down before the client reads it.
                    got.set(e == null ? null : new PotionEffect(e));
                });
                this.applied = got;
                this.wait = 30;
                this.state = 5;
                return;
            }
            case 5: {
                PotionEffect a = this.applied.get();
                ok(a != null && a.getDuration() == 260 && !a.doesShowParticles(), "the warden's Darkness reaches a survival player: " + a);
                PotionEffect e = mc.player.getActivePotionEffect(WardenContent.darkness);
                ok(e != null && e.getDuration() > 200, "the client has Darkness: " + e);
                // Long enough for every phase: the effect lengthened while it runs, as a second shriek does.
                IntegratedServer server = mc.getIntegratedServer();
                server.addScheduledTask(() -> server.getPlayerList().getPlayers().get(0)
                        .addPotionEffect(new PotionEffect(WardenContent.darkness, 400, 0, false, false)));
                this.phase = 0;
                this.state = 6;
                return;
            }
            case 6:
                // Wait for the pulse's phase, then photograph it.
                if (mc.player.ticksExisted % 80 != PHASES[this.phase]) {
                    return;
                }
                this.shot = PHASE_NAMES[this.phase];
                this.phase++;
                if (this.phase == PHASES.length) {
                    this.state = 7;
                }
                this.wait = 2;
                return;
            case 7: {
                IntegratedServer server = mc.getIntegratedServer();
                server.addScheduledTask(() -> server.getPlayerList().getPlayers().get(0).removePotionEffect(WardenContent.darkness));
                this.wait = 40;
                this.state = 8;
                return;
            }
            case 8:
                this.shot = "after";
                this.wait = 5;
                this.state = 9;
                return;
            case 9:
                if ("shaders".equals(this.setup)) {
                    // The same phases with shaders off (OptiFine's own rendering).
                    shaders(mc, "OFF");
                    this.setup = "optifine-after-shaders";
                    this.wait = 200;
                    this.state = 3;
                    return;
                }
                if ("optifine-after-shaders".equals(this.setup)) {
                    shaders(mc, this.pack);
                    this.wait = 40;
                }
                this.state = 10;
                return;
            default:
                finish(mc);
        }
    }

    // --- OptiFine, by reflection (the test runs with and without it) --------------------------------------------------

    private static boolean optifine() {
        try {
            Class.forName("net.optifine.shaders.Shaders", false, DarknessClientSelfTest.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static boolean shaderPackLoaded() {
        if (!optifine()) {
            return false;
        }
        try {
            return Class.forName("net.optifine.shaders.Shaders").getField("shaderPackLoaded").getBoolean(null);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    private static float staticFloat(Class<?> owner, String name) throws ReflectiveOperationException {
        Field f = owner.getDeclaredField(name);
        f.setAccessible(true);
        return f.getFloat(null);
    }

    /**
     * The values a {@code ShaderUniform1f} was last sent, per shader program (the uniform's {@code programValues};
     * OptiFine's "unknown", -Float.MAX, for programs that never set it). Read from the frame's programs, since outside
     * one a uniform or a custom uniform's variables have no value.
     */
    private static float[] programValues(Object uniform) throws ReflectiveOperationException {
        Field f = Class.forName("net.optifine.shaders.uniform.ShaderUniform1f").getDeclaredField("programValues");
        f.setAccessible(true);
        return (float[]) f.get(uniform);
    }

    /** @return whether some program was last sent value (within 0.02) */
    private static boolean sent(Object uniform, float value) throws ReflectiveOperationException {
        for (float v : programValues(uniform)) {
            if (Math.abs(v - value) < 0.02F) {
                return true;
            }
        }
        return false;
    }

    /** A custom uniform's {@code ShaderUniform1f}, or null. */
    private static Object customUniform(Class<?> shaders, String name) throws ReflectiveOperationException {
        Field cu = shaders.getDeclaredField("customUniforms");
        cu.setAccessible(true);
        Object all = cu.get(null);
        if (all == null) {
            return null;
        }
        Field list = all.getClass().getDeclaredField("uniforms");
        list.setAccessible(true);
        for (Object u : (Object[]) list.get(all)) {
            if (name.equals(u.getClass().getMethod("getName").invoke(u))) {
                return u.getClass().getMethod("getShaderUniform").invoke(u);
            }
        }
        return null;
    }

    private static void shaders(Minecraft mc, String pack) throws ReflectiveOperationException {
        Class<?> shaders = Class.forName("net.optifine.shaders.Shaders");
        shaders.getMethod("setShaderPack", String.class).invoke(null, pack);
        shaders.getMethod("uninit").invoke(null);
        mc.renderGlobal.loadRenderers();
        WildUpdateBackport.LOGGER.info("CLIENT SELFTEST shader pack set to {}", pack);
    }

    // --- results -----------------------------------------------------------------------------------------------------

    private void ok(boolean condition, String what) {
        if (condition) {
            this.passed++;
        } else {
            fail(what);
        }
    }

    private void fail(String what) {
        this.failures.add(what);
        WildUpdateBackport.LOGGER.error("CLIENT SELFTEST FAIL {}", what);
    }

    private void finish(Minecraft mc) {
        this.state = 99;
        WildUpdateBackport.LOGGER.info("CLIENT SELFTEST DONE pass={} fail={}", this.passed, this.failures.size());
        try {
            PrintWriter out = new PrintWriter(new File(mc.gameDir, "selftest-client-report.txt"), "UTF-8");
            out.println("pass=" + this.passed + " fail=" + this.failures.size());
            for (String f : this.failures) {
                out.println(f);
            }
            out.close();
        } catch (Exception ignored) {
        }
        mc.shutdown();
    }
}
