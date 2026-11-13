package com.exiledradio.wildupdatebackport.warden.client.anim;

/** A mutable three-float vector, 1.19's {@code Vector3f} as the animations use it. */
public final class Vec3f {

    public float x;
    public float y;
    public float z;

    public Vec3f(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public void set(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }
}
