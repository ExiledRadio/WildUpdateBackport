package com.exiledradio.wildupdatebackport.warden.client;

import com.exiledradio.wildupdatebackport.warden.DarknessFactor;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

/**
 * What OptiFine's shader uniforms {@code darknessFactor} and {@code darknessLightFactor} read (patch P10,
 * {@code asm.ShadersTransformer}), once a frame from {@code Shaders.beginRender}. Modern OptiFine sets them from
 * 1.19's {@code LightTexture.updateLightTexture} and Iris from the same two values:
 *
 * <ul>
 *   <li>{@code darknessFactor}: {@code getDarknessGamma(partialTicks) * darknessEffectScale}, the effect's
 *   {@code FactorData} factor times the Darkness Pulsing setting;</li>
 *   <li>{@code darknessLightFactor}: {@code calculateDarknessScale(player, darknessFactor, partialTicks) *
 *   darknessEffectScale}, {@code max(0, cos((tickCount - partialTicks) * pi * 0.025) * 0.45 * darknessFactor)} times
 *   the setting again.</li>
 * </ul>
 * Both 0 without the effect, as in 1.19.
 */
public final class DarknessShaders {

    private DarknessShaders() {
    }

    public static float factor(float partialTicks) {
        return DarknessClient.factor(partialTicks) * DarknessClient.scale();
    }

    public static float lightFactor(float partialTicks) {
        EntityPlayer player = Minecraft.getMinecraft().player;
        if (player == null) {
            return 0.0F;
        }
        return DarknessFactor.pulse(player.ticksExisted, partialTicks, factor(partialTicks)) * DarknessClient.scale();
    }
}
