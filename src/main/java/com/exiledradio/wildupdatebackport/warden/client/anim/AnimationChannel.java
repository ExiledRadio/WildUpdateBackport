package com.exiledradio.wildupdatebackport.warden.client.anim;

/** 1.19's {@code AnimationChannel}: keyframes for one property (position or rotation) of one part. */
public final class AnimationChannel {

    public interface Interpolation {
        Vec3f apply(Vec3f out, float t, Keyframe[] keyframes, int from, int to, float scale);
    }

    public interface Target {
        void apply(AnimatedPart part, Vec3f value);
    }

    public static final class Interpolations {
        public static final Interpolation LINEAR = (out, t, k, a, b, s) -> {
            Vec3f p = k[a].target;
            Vec3f q = k[b].target;
            out.set(lerp(t, p.x, q.x) * s, lerp(t, p.y, q.y) * s, lerp(t, p.z, q.z) * s);
            return out;
        };
        public static final Interpolation CATMULLROM = (out, t, k, a, b, s) -> {
            Vec3f p0 = k[Math.max(0, a - 1)].target;
            Vec3f p1 = k[a].target;
            Vec3f p2 = k[b].target;
            Vec3f p3 = k[Math.min(k.length - 1, b + 1)].target;
            out.set(catmullrom(t, p0.x, p1.x, p2.x, p3.x) * s, catmullrom(t, p0.y, p1.y, p2.y, p3.y) * s,
                    catmullrom(t, p0.z, p1.z, p2.z, p3.z) * s);
            return out;
        };

        private Interpolations() {
        }
    }

    public static final class Targets {
        public static final Target POSITION = AnimatedPart::offsetPos;
        public static final Target ROTATION = AnimatedPart::offsetRotation;

        private Targets() {
        }
    }

    public final Target target;
    public final Keyframe[] keyframes;

    public AnimationChannel(Target target, Keyframe... keyframes) {
        this.target = target;
        this.keyframes = keyframes;
    }

    static float lerp(float t, float a, float b) {
        return a + t * (b - a);
    }

    /** 1.19's {@code Mth.catmullrom}. */
    static float catmullrom(float t, float p0, float p1, float p2, float p3) {
        return 0.5F * (2.0F * p1 + (p2 - p0) * t + (2.0F * p0 - 5.0F * p1 + 4.0F * p2 - p3) * t * t
                + (3.0F * p1 - p0 - 3.0F * p2 + p3) * t * t * t);
    }
}
