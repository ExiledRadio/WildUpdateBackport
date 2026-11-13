package com.exiledradio.wildupdatebackport.frog.client;

import com.exiledradio.wildupdatebackport.WildUpdateBackport;
import com.exiledradio.wildupdatebackport.common.Content;
import com.exiledradio.wildupdatebackport.frog.AnimationClock;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResource;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 1.19's keyframe animations ({@code AnimationDefinition}, {@code KeyframeAnimations}) for our models.
 *
 * <p>The keyframes themselves are data: {@code tools/frogs/generate.py} reads 1.19.2's animation definitions
 * into {@code assets/wildupdatebackport/animations/<entity>.json}, already in model units (positions in pixels
 * with y flipped, rotations in radians, scales as the amount added to 1), as 1.19's {@code posVec},
 * {@code degreeVec} and {@code scaleVec} make them. Interpolation is linear or Catmull-Rom, as 1.19's.
 */
@SideOnly(Side.CLIENT)
public final class Keyframes {

    /** One bone's channel: position, rotation or scale keyframes. */
    static final class Channel {
        final String bone;
        final int target; // 0 position, 1 rotation, 2 scale
        final float[] times;
        final float[][] values;
        final boolean[] catmull;

        Channel(String bone, int target, float[] times, float[][] values, boolean[] catmull) {
            this.bone = bone;
            this.target = target;
            this.times = times;
            this.values = values;
            this.catmull = catmull;
        }
    }

    /** One animation. */
    public static final class Definition {
        final float length;
        final boolean looping;
        final List<Channel> channels;

        Definition(float length, boolean looping, List<Channel> channels) {
            this.length = length;
            this.looping = looping;
            this.channels = channels;
        }
    }

    private static final Map<String, Map<String, Definition>> CACHE = new HashMap<String, Map<String, Definition>>();

    private Keyframes() {
    }

    /** @return the animations in {@code animations/<file>.json}, read once (empty if missing) */
    public static Map<String, Definition> load(String file) {
        Map<String, Definition> cached = CACHE.get(file);
        if (cached != null) {
            return cached;
        }
        Map<String, Definition> out = new HashMap<String, Definition>();
        try {
            IResource res = Minecraft.getMinecraft().getResourceManager().getResource(Content.id("animations/" + file + ".json"));
            JsonObject root = new JsonParser().parse(new InputStreamReader(res.getInputStream(), StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                JsonObject a = e.getValue().getAsJsonObject();
                List<Channel> channels = new ArrayList<Channel>();
                for (JsonElement ce : a.getAsJsonArray("channels")) {
                    JsonObject c = ce.getAsJsonObject();
                    JsonArray keys = c.getAsJsonArray("keyframes");
                    int n = keys.size();
                    float[] times = new float[n];
                    float[][] values = new float[n][3];
                    boolean[] catmull = new boolean[n];
                    for (int i = 0; i < n; i++) {
                        JsonArray k = keys.get(i).getAsJsonArray();
                        times[i] = k.get(0).getAsFloat();
                        values[i][0] = k.get(1).getAsFloat();
                        values[i][1] = k.get(2).getAsFloat();
                        values[i][2] = k.get(3).getAsFloat();
                        catmull[i] = "catmullrom".equals(k.get(4).getAsString());
                    }
                    String t = c.get("target").getAsString();
                    channels.add(new Channel(c.get("bone").getAsString(), "position".equals(t) ? 0 : "rotation".equals(t) ? 1 : 2,
                            times, values, catmull));
                }
                out.put(e.getKey(), new Definition(a.get("length").getAsFloat(), a.get("looping").getAsBoolean(), channels));
            }
        } catch (Exception ex) {
            WildUpdateBackport.LOGGER.error("Could not read animations/{}.json; that entity will not animate.", file, ex);
        }
        Map<String, Definition> done = Collections.unmodifiableMap(out);
        CACHE.put(file, done);
        return done;
    }

    /** 1.19's {@code HierarchicalModel.animate}: advances the clock and, if it runs, applies the animation. */
    public static void animate(Map<String, Part> parts, AnimationClock clock, Definition def, float ageInTicks, float speed) {
        if (def == null) {
            return;
        }
        clock.updateTime(ageInTicks, speed);
        if (clock.isStarted()) {
            apply(parts, def, clock.accumulatedTime());
        }
    }

    /** 1.19's {@code KeyframeAnimations.animate}, at full strength. */
    static void apply(Map<String, Part> parts, Definition def, long millis) {
        float seconds = millis / 1000.0F;
        float elapsed = def.looping ? seconds % def.length : seconds;
        float[] out = new float[3];
        for (Channel c : def.channels) {
            Part part = parts.get(c.bone);
            if (part == null) {
                continue;
            }
            int n = c.times.length;
            // binarySearch(0, n, i -> elapsed <= time[i]) - 1, clamped
            int lo = 0;
            int hi = n;
            while (lo < hi) {
                int mid = (lo + hi) >>> 1;
                if (elapsed <= c.times[mid]) {
                    hi = mid;
                } else {
                    lo = mid + 1;
                }
            }
            int a = Math.max(0, lo - 1);
            int b = Math.min(n - 1, a + 1);
            float span = c.times[b] - c.times[a];
            float t = span == 0.0F ? 0.0F : clamp((elapsed - c.times[a]) / span);
            if (c.catmull[b]) {
                float[] p0 = c.values[Math.max(0, a - 1)];
                float[] p1 = c.values[a];
                float[] p2 = c.values[b];
                float[] p3 = c.values[Math.min(n - 1, b + 1)];
                for (int i = 0; i < 3; i++) {
                    out[i] = catmullrom(t, p0[i], p1[i], p2[i], p3[i]);
                }
            } else {
                for (int i = 0; i < 3; i++) {
                    out[i] = c.values[a][i] + t * (c.values[b][i] - c.values[a][i]);
                }
            }
            switch (c.target) {
                case 0:
                    part.rotationPointX += out[0];
                    part.rotationPointY += out[1];
                    part.rotationPointZ += out[2];
                    break;
                case 1:
                    part.rotateAngleX += out[0];
                    part.rotateAngleY += out[1];
                    part.rotateAngleZ += out[2];
                    break;
                default:
                    part.xScale += out[0];
                    part.yScale += out[1];
                    part.zScale += out[2];
                    break;
            }
        }
    }

    private static float clamp(float f) {
        return f < 0.0F ? 0.0F : f > 1.0F ? 1.0F : f;
    }

    /** 1.19's {@code Mth.catmullrom}. */
    static float catmullrom(float t, float p0, float p1, float p2, float p3) {
        return 0.5F * (2.0F * p1 + (p2 - p0) * t + (2.0F * p0 - 5.0F * p1 + 4.0F * p2 - p3) * t * t
                + (3.0F * p1 - p0 - 3.0F * p2 + p3) * t * t * t);
    }
}
