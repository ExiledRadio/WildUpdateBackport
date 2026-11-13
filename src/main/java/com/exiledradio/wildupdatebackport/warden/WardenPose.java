package com.exiledradio.wildupdatebackport.warden;

/** The poses 1.19's warden uses (1.19's {@code Pose}), synced to the client to start each animation. */
public enum WardenPose {
    STANDING, EMERGING, DIGGING, ROARING, SNIFFING;

    static WardenPose byId(int id) {
        WardenPose[] all = values();
        return id >= 0 && id < all.length ? all[id] : STANDING;
    }
}
