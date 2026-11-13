package com.exiledradio.wildupdatebackport.lookfeel;

/**
 * What patches P1 to P3 ({@code docs/PATCHES.md}) call. All three patch client render code; the descriptors are
 * primitives only, so this class loads anywhere. Each hook takes vanilla's value and returns it scaled by a 1.19.4
 * option; at the option's 100% the value is vanilla 1.12.2's.
 */
public final class LookHooks {

    private LookHooks() {
    }

    /**
     * P1, {@code EntityRenderer.hurtCameraEffect}: the tilt's 14 degrees. 1.19.4's {@code GameRenderer.bobHurt}:
     * {@code -sin(...) * 14.0 * damageTiltStrength}.
     */
    public static float damageTilt(float degrees) {
        return degrees * (float) LookAndFeel.DAMAGE_TILT.get();
    }

    /**
     * P2, {@code RenderItem.renderEffect}: the clock the item glint scrolls by, {@code Minecraft.getSystemTime()}.
     * 1.19.4's {@code RenderStateShard.setupGlintTexturing} scrolls by {@code millis * glintSpeed * 8} where 1.19.3's
     * scrolled by {@code millis * 8}, so 1.12.2's clock is scaled by Glint Speed.
     */
    public static long glintTime(long millis) {
        return (long) (millis * LookAndFeel.GLINT_SPEED.get());
    }

    /**
     * P2, {@code RenderItem.renderEffect}: the glint's color, {@code 0xFF8040CC}. 1.19.4's glint shader multiplies
     * the added color by {@code GlintAlpha} (Glint Strength); the alpha byte is kept.
     */
    public static int glintColor(int argb) {
        float s = (float) LookAndFeel.GLINT_STRENGTH.get();
        int r = Math.round(((argb >> 16) & 0xFF) * s);
        int g = Math.round(((argb >> 8) & 0xFF) * s);
        int b = Math.round((argb & 0xFF) * s);
        return (argb & 0xFF000000) | r << 16 | g << 8 | b;
    }

    /**
     * P3, {@code LayerArmorBase.renderEnchantedGlint} (armor and elytra): each of the glint color's three components,
     * scaled by Glint Strength as {@link #glintColor}.
     */
    public static float glintComponent(float c) {
        return c * (float) LookAndFeel.GLINT_STRENGTH.get();
    }

    /**
     * P3, {@code LayerArmorBase.renderEnchantedGlint}: its clock, {@code ticksExisted + partialTicks}, scaled by Glint
     * Speed as {@link #glintTime}.
     */
    public static float glintTicks(float ticks) {
        return ticks * (float) LookAndFeel.GLINT_SPEED.get();
    }
}
