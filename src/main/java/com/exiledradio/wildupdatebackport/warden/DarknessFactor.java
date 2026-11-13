package com.exiledradio.wildupdatebackport.warden;

/**
 * 1.19.2's {@code MobEffectInstance.FactorData} for Darkness (padding 22 ticks) and its {@code LightTexture} pulse, as
 * plain math so the server-side tests can check it. The client keeps one for its player.
 *
 * <p>The factor rises from 0 to 1 over the effect's first 22 ticks and falls back to 0 over its last 22; an effect
 * lengthened while it runs moves the timestamp along (1.19's {@code update}), so the factor does not jump.
 */
public final class DarknessFactor {

    public static final int PADDING = 22;

    private float factorStart;
    private float factorTarget = 1.0F;
    private float factorCurrent;
    private float factorPrevious;
    private int effectChangedTimestamp;
    private boolean hadEffectLastTick;
    private int lastDuration = -1;

    /** No effect: back to a new effect's state. */
    public void reset() {
        this.factorStart = 0.0F;
        this.factorTarget = 1.0F;
        this.factorCurrent = 0.0F;
        this.factorPrevious = 0.0F;
        this.effectChangedTimestamp = 0;
        this.hadEffectLastTick = false;
        this.lastDuration = -1;
    }

    /** Once a tick while the effect runs, with its remaining duration. */
    public void tick(int duration) {
        if (this.lastDuration >= 0 && duration > this.lastDuration) {
            this.effectChangedTimestamp += duration - this.lastDuration;
        }
        this.lastDuration = duration;
        this.factorPrevious = this.factorCurrent;
        boolean on = duration > PADDING;
        if (this.hadEffectLastTick != on) {
            this.hadEffectLastTick = on;
            this.effectChangedTimestamp = duration;
            this.factorStart = this.factorCurrent;
            this.factorTarget = on ? 1.0F : 0.0F;
        }
        float t = (this.effectChangedTimestamp - (float) duration) / PADDING;
        t = t < 0.0F ? 0.0F : t > 1.0F ? 1.0F : t;
        this.factorCurrent = this.factorStart + t * (this.factorTarget - this.factorStart);
    }

    public float get(float partialTicks) {
        return this.factorPrevious + partialTicks * (this.factorCurrent - this.factorPrevious);
    }

    /**
     * 1.19's {@code calculateDarknessScale}: how much darker the lightmap is this frame.
     *
     * @param gamma the factor times the pulsing setting
     */
    public static float pulse(int age, float partialTicks, float gamma) {
        return Math.max(0.0F, (float) Math.cos((age - partialTicks) * (float) Math.PI * 0.025F) * 0.45F * gamma);
    }
}
