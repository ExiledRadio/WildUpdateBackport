package com.exiledradio.wildupdatebackport.warden.client;

import com.exiledradio.wildupdatebackport.WildUpdateBackport;
import com.exiledradio.wildupdatebackport.warden.DarknessFactor;
import com.exiledradio.wildupdatebackport.warden.WardenContent;

import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;

import java.lang.reflect.Field;

/**
 * Darkness on the client, as 1.19.2 draws it, without patches.
 *
 * <h2>The factor</h2>
 * 1.19's {@code FactorData} ({@link DarknessFactor}), kept here for the player since 1.12's effects have no such data.
 *
 * <h2>The light</h2>
 * 1.19's {@code LightTexture}: everything darker by a pulse, {@code max(0, cos((age - partial) * pi * 0.025) * 0.45
 * * factor)}, and the brightness setting lowered by the factor. 1.12 recomputes the lightmap in {@code renderWorld}
 * when its update flag is set: before each frame this class computes it instead (1.12.2's own formula plus those two
 * steps), uploads it and clears the flag, so vanilla's pass is skipped for that tick. Night Vision still brightens first,
 * and the pulse is taken off after it, as in 1.19. The {@code darknessEffectScale} client setting scales both, as 1.19's
 * <i>Darkness Pulsing</i> option.
 *
 * <h2>The fog</h2>
 * 1.19's {@code DarknessFogFunction}: the fog's end moves from the view distance to 15 blocks with the factor (the start
 * at three quarters of it, 0 for the sky), and the fog color darkens by {@code (1 - factor)^2}. Blindness takes
 * priority, lava's fog is left alone; under water Darkness replaces the water fog, as in 1.19.
 */
@Mod.EventBusSubscriber(modid = WildUpdateBackport.MOD_ID, value = Side.CLIENT)
public final class DarknessClient {

    private static final DarknessFactor FACTOR = new DarknessFactor();

    private static Field updateNeeded;
    private static Field torchFlickerX;
    private static Field bossColor;
    private static Field bossColorPrev;
    private static Field colors;
    private static Field texture;
    private static boolean reflectionFailed;

    private DarknessClient() {
    }

    public static void drawIcon(ResourceLocation icon, int x, int y, float alpha) {
        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, alpha);
        Minecraft.getMinecraft().getTextureManager().bindTexture(icon);
        Gui.drawModalRectWithCustomSizedTexture(x, y, 0.0F, 0.0F, 18, 18, 18.0F, 18.0F);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
    }

    // --- the factor ------------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        EntityPlayer player = Minecraft.getMinecraft().player;
        PotionEffect effect = player == null || WardenContent.darkness == null ? null : player.getActivePotionEffect(WardenContent.darkness);
        if (effect == null) {
            FACTOR.reset();
        } else {
            FACTOR.tick(effect.getDuration());
        }
    }

    /** @return the Darkness factor for the frame, 0 without the effect */
    public static float factor(float partialTicks) {
        EntityPlayer player = Minecraft.getMinecraft().player;
        if (player == null || WardenContent.darkness == null || player.getActivePotionEffect(WardenContent.darkness) == null) {
            return 0.0F;
        }
        return FACTOR.get(partialTicks);
    }

    static float scale() {
        return MathHelper.clamp(com.exiledradio.wildupdatebackport.lookfeel.LookAndFeel.darknessScale(), 0.0F, 1.0F);
    }

    // --- the light -------------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void beforeFrame(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START || reflectionFailed) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.player == null || factor(event.renderTickTime) <= 0.0F) {
            return;
        }
        try {
            if (updateNeeded == null) {
                updateNeeded = ReflectionHelper.findField(EntityRenderer.class, "lightmapUpdateNeeded", "field_78536_aa");
                torchFlickerX = ReflectionHelper.findField(EntityRenderer.class, "torchFlickerX", "field_78514_e");
                bossColor = ReflectionHelper.findField(EntityRenderer.class, "bossColorModifier", "field_82831_U");
                bossColorPrev = ReflectionHelper.findField(EntityRenderer.class, "bossColorModifierPrev", "field_82832_V");
                colors = ReflectionHelper.findField(EntityRenderer.class, "lightmapColors", "field_78504_Q");
                texture = ReflectionHelper.findField(EntityRenderer.class, "lightmapTexture", "field_78513_d");
            }
            EntityRenderer renderer = mc.entityRenderer;
            if (!updateNeeded.getBoolean(renderer)) {
                return;
            }
            computeLightmap(mc, event.renderTickTime, torchFlickerX.getFloat(renderer), bossColor.getFloat(renderer),
                    bossColorPrev.getFloat(renderer), (int[]) colors.get(renderer));
            ((DynamicTexture) texture.get(renderer)).updateDynamicTexture();
            updateNeeded.setBoolean(renderer, false);
        } catch (Exception e) {
            reflectionFailed = true;
            WildUpdateBackport.LOGGER.warn("Darkness cannot dim the light: {}", e.toString());
        }
    }

    /** 1.12.2's {@code EntityRenderer.updateLightmap} with 1.19.2's two Darkness steps. */
    private static void computeLightmap(Minecraft mc, float partialTicks, float torchFlicker, float boss, float bossPrev, int[] out) {
        World world = mc.world;
        EntityPlayer player = mc.player;
        float scale = scale();
        float darknessGamma = factor(partialTicks) * scale;
        float darknessPulse = DarknessFactor.pulse(player.ticksExisted, partialTicks, darknessGamma) * scale;
        float sun = world.getSunBrightness(1.0F);
        float sky = sun * 0.95F + 0.05F;
        float[] table = world.provider.getLightBrightnessTable();
        for (int i = 0; i < 256; ++i) {
            float f2 = table[i / 16] * sky;
            float f3 = table[i % 16] * (torchFlicker * 0.1F + 1.5F);
            if (world.getLastLightningBolt() > 0) {
                f2 = table[i / 16];
            }
            float f4 = f2 * (sun * 0.65F + 0.35F);
            float f5 = f2 * (sun * 0.65F + 0.35F);
            float f6 = f3 * ((f3 * 0.6F + 0.4F) * 0.6F + 0.4F);
            float f7 = f3 * (f3 * f3 * 0.6F + 0.4F);
            float r = (f4 + f3) * 0.96F + 0.03F;
            float g = (f5 + f6) * 0.96F + 0.03F;
            float b = (f2 + f7) * 0.96F + 0.03F;
            if (boss > 0.0F) {
                float f11 = bossPrev + (boss - bossPrev) * partialTicks;
                r = r * (1.0F - f11) + r * 0.7F * f11;
                g = g * (1.0F - f11) + g * 0.6F * f11;
                b = b * (1.0F - f11) + b * 0.6F * f11;
            }
            if (world.provider.getDimensionType().getId() == 1) {
                r = 0.22F + f3 * 0.75F;
                g = 0.28F + f6 * 0.75F;
                b = 0.25F + f7 * 0.75F;
            }
            float[] c = {r, g, b};
            world.provider.getLightmapColors(partialTicks, sun, f2, f3, c);
            r = MathHelper.clamp(c[0], 0.0F, 1.0F);
            g = MathHelper.clamp(c[1], 0.0F, 1.0F);
            b = MathHelper.clamp(c[2], 0.0F, 1.0F);
            if (player.isPotionActive(MobEffects.NIGHT_VISION)) {
                float nv = nightVision(player, partialTicks);
                float k = Math.min(1.0F / r, Math.min(1.0F / g, 1.0F / b));
                r = r * (1.0F - nv) + r * k * nv;
                g = g * (1.0F - nv) + g * k * nv;
                b = b * (1.0F - nv) + b * k * nv;
            }
            r = Math.min(r, 1.0F);
            g = Math.min(g, 1.0F);
            b = Math.min(b, 1.0F);
            // 1.19: the pulse, after Night Vision.
            r = MathHelper.clamp(r - darknessPulse, 0.0F, 1.0F);
            g = MathHelper.clamp(g - darknessPulse, 0.0F, 1.0F);
            b = MathHelper.clamp(b - darknessPulse, 0.0F, 1.0F);
            // 1.19: the brightness setting, lowered by the factor.
            float gamma = Math.max(0.0F, mc.gameSettings.gammaSetting - darknessGamma);
            float ir = 1.0F - r;
            float ig = 1.0F - g;
            float ib = 1.0F - b;
            ir = 1.0F - ir * ir * ir * ir;
            ig = 1.0F - ig * ig * ig * ig;
            ib = 1.0F - ib * ib * ib * ib;
            r = r * (1.0F - gamma) + ir * gamma;
            g = g * (1.0F - gamma) + ig * gamma;
            b = b * (1.0F - gamma) + ib * gamma;
            r = Math.min(r * 0.96F + 0.03F, 1.0F);
            g = Math.min(g * 0.96F + 0.03F, 1.0F);
            b = Math.min(b * 0.96F + 0.03F, 1.0F);
            out[i] = 0xFF000000 | (int) (r * 255.0F) << 16 | (int) (g * 255.0F) << 8 | (int) (b * 255.0F);
        }
    }

    private static float nightVision(EntityLivingBase entity, float partialTicks) {
        int i = entity.getActivePotionEffect(MobEffects.NIGHT_VISION).getDuration();
        return i > 200 ? 1.0F : 0.7F + MathHelper.sin((i - partialTicks) * (float) Math.PI * 0.2F) * 0.3F;
    }

    // --- the fog -----------------------------------------------------------------------------------------------------

    private static boolean applies(Entity viewer) {
        return viewer instanceof EntityLivingBase && !((EntityLivingBase) viewer).isPotionActive(MobEffects.BLINDNESS)
                && viewer == Minecraft.getMinecraft().player;
    }

    /** Fog in open air: vanilla's linear fog, the end pulled in. */
    @SubscribeEvent
    public static void fog(EntityViewRenderEvent.RenderFogEvent event) {
        if (!applies(event.getEntity())) {
            return;
        }
        float factor = factor((float) event.getRenderPartialTicks());
        if (factor <= 0.0F) {
            return;
        }
        float end = event.getFarPlaneDistance() + (15.0F - event.getFarPlaneDistance()) * factor;
        GlStateManager.setFogStart(event.getFogMode() == -1 ? 0.0F : end * 0.75F);
        GlStateManager.setFogEnd(end);
    }

    /** Fog under water: 1.19 puts Darkness's fog before water's. */
    @SubscribeEvent
    public static void fogDensity(EntityViewRenderEvent.FogDensity event) {
        if (!applies(event.getEntity()) || event.getState().getMaterial() != Material.WATER) {
            return;
        }
        float factor = factor((float) event.getRenderPartialTicks());
        if (factor <= 0.0F) {
            return;
        }
        float far = Minecraft.getMinecraft().gameSettings.renderDistanceChunks * 16;
        float end = far + (15.0F - far) * factor;
        GlStateManager.setFog(GlStateManager.FogMode.LINEAR);
        GlStateManager.setFogStart(end * 0.75F);
        GlStateManager.setFogEnd(end);
        event.setDensity(0.1F);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void fogColor(EntityViewRenderEvent.FogColors event) {
        if (!applies(event.getEntity()) || event.getState().getMaterial() == Material.LAVA) {
            return;
        }
        float factor = factor((float) event.getRenderPartialTicks());
        if (factor <= 0.0F) {
            return;
        }
        float m = 1.0F - factor;
        m *= m;
        event.setRed(event.getRed() * m);
        event.setGreen(event.getGreen() * m);
        event.setBlue(event.getBlue() * m);
    }
}
