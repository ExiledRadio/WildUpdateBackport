package com.exiledradio.wildupdatebackport.selftest;

import com.exiledradio.wildupdatebackport.mangrove.RootsCrack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.util.EnumFacing;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The roots' crack without a client ({@link RootsCrack}): the quads of {@code models/block/mangrove_roots.json}, and
 * 1.12.2's crack pass simulated along rays. That pass writes depth, so along a ray a crack quad is drawn only if no
 * quad drawn before it is nearer. With every layer seen (as through the roots' holes), 1.19 draws the crack on every
 * front-facing layer a ray meets. The check counts the layers 1.12.2 would miss, in the model's own bucket order (the
 * bug) and in {@link RootsCrack#order} (the fix, expected 0).
 */
final class RootsCrackCheck {

    private RootsCrackCheck() {
    }

    /** A face of the model: corners in blocks, its direction and its cullface (null: none). */
    private static final class Face {
        final float[] corners;
        final EnumFacing facing;
        final EnumFacing cull;

        Face(float[] corners, EnumFacing facing, EnumFacing cull) {
            this.corners = corners;
            this.facing = facing;
            this.cull = cull;
        }
    }

    static void run(Check c) throws Exception {
        List<Face> faces = load();
        c.eq(16, faces.size(), "roots crack: the model has 16 faces (two crossed planes, six two-sided walls)");

        // Sides culled: with every neighbor solid only the crossed planes remain, as in the chunk render.
        c.eq(4, kept(faces, 0).size(), "roots crack: all neighbors solid leaves the 4 plane faces");
        c.eq(16, kept(faces, 63).size(), "roots crack: no neighbors leaves all 16 faces");
        RootsCrack.View sealed = new RootsCrack.View(-2.5, 1.5, -2.5, 0);
        c.eq(16, RootsCrack.order(corners(kept(faces, 0)), kept(faces, 0), sealed).size(),
                "roots crack: all neighbors solid gives 16 crack quarters (the planes)");

        // 1.19's crack coordinates: a whole face spans the whole crack, the top as u = x, v = z.
        float[] top = {0, 1, 0, 0, 1, 1, 1, 1, 1, 1, 1, 0};
        float[] uv = RootsCrack.decalUVs(EnumFacing.UP, top);
        c.ok(near(uv, new float[]{0, 0, 0, 1, 1, 1, 1, 0}), "roots crack: the top face's crack is u = x, v = z: " + java.util.Arrays.toString(uv));
        boolean spans = true;
        for (Face f : faces) {
            float[] w = RootsCrack.decalUVs(f.facing, f.corners);
            spans &= span(w, 0) > 0.999F && span(w, 1) > 0.999F;
            for (float[] q : RootsCrack.quarters(f.corners)) {
                float[] v = RootsCrack.decalUVs(f.facing, q);
                spans &= Math.abs(span(v, 0) - 0.5F) < 1.0E-3F && Math.abs(span(v, 1) - 0.5F) < 1.0E-3F;
            }
        }
        c.ok(spans, "roots crack: every face takes the whole crack, every quarter a quarter of it");

        // Rays from cameras all around the block.
        int oldMissed = 0;
        int newMissed = 0;
        int layers = 0;
        int cameras = 0;
        List<Face> all = kept(faces, 63);
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                for (int k = -1; k <= 1; k++) {
                    if (i == 0 && j == 0 && k == 0) {
                        continue;
                    }
                    for (double d : new double[]{0.9, 1.7, 3.1, 4.6}) {
                        double ex = 0.5 + i * d + 0.13 * j;
                        double ey = 0.5 + j * d + 0.07 * k;
                        double ez = 0.5 + k * d + 0.11 * i;
                        cameras++;
                        RootsCrack.View view = new RootsCrack.View(ex, ey, ez, 63);
                        List<RootsCrack.Piece<Face>> pieces = RootsCrack.order(corners(all), all, view);
                        List<float[]> sorted = new ArrayList<float[]>();
                        List<EnumFacing> sortedFacing = new ArrayList<EnumFacing>();
                        for (RootsCrack.Piece<Face> p : pieces) {
                            sorted.add(p.corners);
                            sortedFacing.add(p.source.facing);
                        }
                        List<float[]> bucket = new ArrayList<float[]>();
                        List<EnumFacing> bucketFacing = new ArrayList<EnumFacing>();
                        for (Face f : bucketOrder(all)) {
                            bucket.add(f.corners);
                            bucketFacing.add(f.facing);
                        }
                        for (int a = 0; a < 7; a++) {
                            for (int b = 0; b < 7; b++) {
                                for (int h = 0; h < 7; h++) {
                                    double tx = (a + 0.5) / 7.0 + 0.013;
                                    double ty = (b + 0.5) / 7.0 + 0.017;
                                    double tz = (h + 0.5) / 7.0 + 0.011;
                                    double[] dir = {tx - ex, ty - ey, tz - ez};
                                    int[] r = missed(sorted, sortedFacing, ex, ey, ez, dir);
                                    newMissed += r[0];
                                    layers += r[1];
                                    oldMissed += missed(bucket, bucketFacing, ex, ey, ez, dir)[0];
                                }
                            }
                        }
                    }
                }
            }
        }
        c.ok(layers > 0, "roots crack: the rays meet layers (" + layers + " layers, " + cameras + " cameras)");
        c.ok(oldMissed > layers / 20, "roots crack: 1.12.2's own order misses layers (the bug): " + oldMissed + " of " + layers);
        c.eq(0, newMissed, "roots crack: layers the sorted crack misses, of " + layers);
    }

    /**
     * Two layers closer than this along a ray (in blocks) are one: where a wall's inner face meets another wall's
     * 0.002-thin edge, which no pixel shows.
     */
    private static final double SLIVER = 0.004D;

    /**
     * Along one ray: the front-facing quads it meets, drawn in list order with a depth test and depth writes.
     *
     * @return {missed layers, layers met}
     */
    private static int[] missed(List<float[]> quads, List<EnumFacing> facing, double ex, double ey, double ez, double[] dir) {
        double nearestDrawn = Double.MAX_VALUE;
        double length = Math.sqrt(dir[0] * dir[0] + dir[1] * dir[1] + dir[2] * dir[2]);
        int missed = 0;
        int met = 0;
        for (int i = 0; i < quads.size(); i++) {
            EnumFacing f = facing.get(i);
            if (f.getXOffset() * dir[0] + f.getYOffset() * dir[1] + f.getZOffset() * dir[2] >= 0) {
                // Back-facing: culled before the depth test.
                continue;
            }
            double t = hit(quads.get(i), f, ex, ey, ez, dir);
            if (t < 0) {
                continue;
            }
            met++;
            if (t <= nearestDrawn) {
                nearestDrawn = t;
            } else if ((t - nearestDrawn) * length > SLIVER) {
                missed++;
            }
        }
        return new int[]{missed, met};
    }

    /** The ray's distance (in direction lengths) to the axis-aligned quad, or -1 if it misses. */
    private static double hit(float[] c, EnumFacing f, double ex, double ey, double ez, double[] dir) {
        int axis = f.getAxis().ordinal();
        double[] e = {ex, ey, ez};
        double plane = c[axis];
        if (Math.abs(dir[axis]) < 1.0E-9) {
            return -1;
        }
        double t = (plane - e[axis]) / dir[axis];
        if (t <= 0) {
            return -1;
        }
        for (int k = 0; k < 3; k++) {
            if (k == axis) {
                continue;
            }
            double p = e[k] + dir[k] * t;
            double lo = Math.min(Math.min(c[k], c[3 + k]), Math.min(c[6 + k], c[9 + k]));
            double hi = Math.max(Math.max(c[k], c[3 + k]), Math.max(c[6 + k], c[9 + k]));
            if (p < lo || p > hi) {
                return -1;
            }
        }
        return t;
    }

    /** 1.12.2's draw order: the six side buckets, then the unculled quads, each in model order. */
    private static List<Face> bucketOrder(List<Face> faces) {
        List<Face> out = new ArrayList<Face>();
        for (EnumFacing side : EnumFacing.values()) {
            for (Face f : faces) {
                if (f.cull == side) {
                    out.add(f);
                }
            }
        }
        for (Face f : faces) {
            if (f.cull == null) {
                out.add(f);
            }
        }
        return out;
    }

    private static List<Face> kept(List<Face> faces, int sides) {
        List<Face> out = new ArrayList<Face>();
        for (Face f : faces) {
            if (f.cull == null || (sides & (1 << f.cull.getIndex())) != 0) {
                out.add(f);
            }
        }
        return out;
    }

    private static List<float[]> corners(List<Face> faces) {
        List<float[]> out = new ArrayList<float[]>();
        for (Face f : faces) {
            out.add(f.corners);
        }
        return out;
    }

    private static float span(float[] uv, int which) {
        float lo = Float.MAX_VALUE;
        float hi = -Float.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            lo = Math.min(lo, uv[i * 2 + which]);
            hi = Math.max(hi, uv[i * 2 + which]);
        }
        return hi - lo;
    }

    private static boolean near(float[] a, float[] b) {
        for (int i = 0; i < a.length; i++) {
            if (Math.abs(a[i] - b[i]) > 1.0E-4F) {
                return false;
            }
        }
        return true;
    }

    private static List<Face> load() throws Exception {
        InputStream in = RootsCrackCheck.class.getResourceAsStream("/assets/wildupdatebackport/models/block/mangrove_roots.json");
        JsonObject model;
        try {
            model = new JsonParser().parse(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } finally {
            in.close();
        }
        List<Face> out = new ArrayList<Face>();
        for (JsonElement el : model.getAsJsonArray("elements")) {
            JsonObject e = el.getAsJsonObject();
            float[] from = vec(e.getAsJsonArray("from"));
            float[] to = vec(e.getAsJsonArray("to"));
            for (Map.Entry<String, JsonElement> face : e.getAsJsonObject("faces").entrySet()) {
                EnumFacing facing = EnumFacing.byName(face.getKey());
                JsonObject fo = face.getValue().getAsJsonObject();
                EnumFacing cull = fo.has("cullface") ? EnumFacing.byName(fo.get("cullface").getAsString()) : null;
                out.add(new Face(corners(facing, from, to), facing, cull));
            }
        }
        return out;
    }

    private static float[] vec(JsonArray a) {
        return new float[]{a.get(0).getAsFloat() / 16.0F, a.get(1).getAsFloat() / 16.0F, a.get(2).getAsFloat() / 16.0F};
    }

    /** The face's four corners, in winding order. */
    private static float[] corners(EnumFacing facing, float[] from, float[] to) {
        int axis = facing.getAxis().ordinal();
        float plane = facing.getAxisDirection() == EnumFacing.AxisDirection.POSITIVE ? to[axis] : from[axis];
        int a = (axis + 1) % 3;
        int b = (axis + 2) % 3;
        float[][] ab = {{from[a], from[b]}, {from[a], to[b]}, {to[a], to[b]}, {to[a], from[b]}};
        float[] c = new float[12];
        for (int i = 0; i < 4; i++) {
            c[i * 3 + axis] = plane;
            c[i * 3 + a] = ab[i][0];
            c[i * 3 + b] = ab[i][1];
        }
        return c;
    }
}
