package com.exiledradio.wildupdatebackport.warden;

/**
 * 1.19's {@code AnimationState}: when an animation started, and how long it has played, in milliseconds of game time.
 * Held by the entity on both sides; only the client starts and reads them.
 */
public final class AnimationState {

    private static final long STOPPED = Long.MAX_VALUE;

    private long lastTime = STOPPED;
    private long accumulatedTime;

    public void start(int tickCount) {
        this.lastTime = (long) tickCount * 1000L / 20L;
        this.accumulatedTime = 0L;
    }

    public void stop() {
        this.lastTime = STOPPED;
    }

    public boolean isStarted() {
        return this.lastTime != STOPPED;
    }

    /** @param ageInTicks the entity's age plus the partial tick; @param speed 1 for real time */
    public void updateTime(float ageInTicks, float speed) {
        if (isStarted()) {
            long now = (long) Math.floor(ageInTicks * 1000.0F / 20.0F);
            this.accumulatedTime += (long) ((now - this.lastTime) * speed);
            this.lastTime = now;
        }
    }

    public long getAccumulatedTime() {
        return this.accumulatedTime;
    }
}
