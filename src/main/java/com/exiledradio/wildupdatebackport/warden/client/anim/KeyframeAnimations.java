package com.exiledradio.wildupdatebackport.warden.client.anim;

import java.util.List;
import java.util.Map;

/** 1.19's {@code KeyframeAnimations}: applies a definition at a time to a model's named parts. */
public final class KeyframeAnimations {

    private static final float DEG = (float) (Math.PI / 180.0D);

    private KeyframeAnimations() {
    }

    public static void animate(Map<String, AnimatedPart> parts, AnimationDefinition def, long millis, float scale, Vec3f cache) {
        float seconds = millis / 1000.0F;
        if (def.looping) {
            seconds %= def.lengthInSeconds;
        }
        for (Map.Entry<String, List<AnimationChannel>> e : def.boneAnimations.entrySet()) {
            AnimatedPart part = parts.get(e.getKey());
            if (part == null) {
                continue;
            }
            for (AnimationChannel channel : e.getValue()) {
                Keyframe[] k = channel.keyframes;
                int a = Math.max(0, firstAtOrAfter(k, seconds) - 1);
                int b = Math.min(k.length - 1, a + 1);
                float t = (seconds - k[a].timestamp) / (k[b].timestamp - k[a].timestamp);
                t = t < 0.0F ? 0.0F : t > 1.0F ? 1.0F : (Float.isNaN(t) ? 0.0F : t);
                k[b].interpolation.apply(cache, t, k, a, b, scale);
                channel.target.apply(part, cache);
            }
        }
    }

    /** 1.19's {@code Mth.binarySearch}: the first index whose timestamp is at or after the time, or the length. */
    private static int firstAtOrAfter(Keyframe[] k, float seconds) {
        int lo = 0;
        int len = k.length;
        while (len > 0) {
            int half = len / 2;
            int mid = lo + half;
            if (seconds <= k[mid].timestamp) {
                len = half;
            } else {
                lo = mid + 1;
                len -= half + 1;
            }
        }
        return lo;
    }

    public static Vec3f posVec(float x, float y, float z) {
        return new Vec3f(x, -y, z);
    }

    public static Vec3f degreeVec(float x, float y, float z) {
        return new Vec3f(x * DEG, y * DEG, z * DEG);
    }
}
