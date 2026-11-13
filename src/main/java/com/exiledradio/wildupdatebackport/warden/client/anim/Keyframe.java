package com.exiledradio.wildupdatebackport.warden.client.anim;

/** 1.19's {@code Keyframe}: a time in seconds, a target and how to get there from the previous one. */
public final class Keyframe {

    public final float timestamp;
    public final Vec3f target;
    public final AnimationChannel.Interpolation interpolation;

    public Keyframe(float timestamp, Vec3f target, AnimationChannel.Interpolation interpolation) {
        this.timestamp = timestamp;
        this.target = target;
        this.interpolation = interpolation;
    }
}
