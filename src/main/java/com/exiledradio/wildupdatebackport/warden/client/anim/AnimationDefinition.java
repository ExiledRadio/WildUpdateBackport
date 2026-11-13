package com.exiledradio.wildupdatebackport.warden.client.anim;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 1.19's {@code AnimationDefinition}: a length in seconds and channels by part name. Built with its own builder. */
public final class AnimationDefinition {

    public final float lengthInSeconds;
    public final boolean looping;
    public final Map<String, List<AnimationChannel>> boneAnimations;

    private AnimationDefinition(float length, boolean looping, Map<String, List<AnimationChannel>> bones) {
        this.lengthInSeconds = length;
        this.looping = looping;
        this.boneAnimations = bones;
    }

    public static Builder withLength(float seconds) {
        return new Builder(seconds);
    }

    public static final class Builder {
        private final float length;
        private final Map<String, List<AnimationChannel>> bones = new LinkedHashMap<String, List<AnimationChannel>>();
        private boolean looping;

        private Builder(float length) {
            this.length = length;
        }

        public Builder looping() {
            this.looping = true;
            return this;
        }

        public Builder addAnimation(String bone, AnimationChannel channel) {
            this.bones.computeIfAbsent(bone, k -> new ArrayList<AnimationChannel>()).add(channel);
            return this;
        }

        public AnimationDefinition build() {
            return new AnimationDefinition(this.length, this.looping, this.bones);
        }
    }
}
