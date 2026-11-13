package com.exiledradio.wildupdatebackport.frog;

/**
 * 1.19's {@code AnimationState}: when an entity animation started and how far it has run, in milliseconds
 * at 20 ticks a second. Plain data, so the entity can hold it on both sides; only the client advances it.
 */
public final class AnimationClock {

    private static final long STOPPED = Long.MAX_VALUE;

    private long lastTime = STOPPED;
    private long accumulatedTime;

    public void start(int tick) {
        this.lastTime = tick * 1000L / 20L;
        this.accumulatedTime = 0L;
    }

    public void startIfStopped(int tick) {
        if (!isStarted()) {
            start(tick);
        }
    }

    public void stop() {
        this.lastTime = STOPPED;
    }

    /** Advances to {@code ageInTicks} (partial ticks included), {@code speed} times as fast. */
    public void updateTime(float ageInTicks, float speed) {
        if (isStarted()) {
            long now = (long) Math.floor(ageInTicks * 1000.0F / 20.0F);
            this.accumulatedTime += (long) ((now - this.lastTime) * speed);
            this.lastTime = now;
        }
    }

    public long accumulatedTime() {
        return this.accumulatedTime;
    }

    public boolean isStarted() {
        return this.lastTime != STOPPED;
    }
}
