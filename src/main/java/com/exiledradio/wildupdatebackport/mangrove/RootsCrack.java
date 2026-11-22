package com.exiledradio.wildupdatebackport.mangrove;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.property.IUnlistedProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * The destroy crack on mangrove roots, drawn as 1.19 draws it.
 *
 * <h2>Why roots need this</h2>
 * Mangrove roots are see-through, with layers inside the block: the two crossed planes and the inner sides of the
 * six walls, all seen through the holes of the near walls. 1.19 draws the crack on every layer: its crumbling pass
 * writes color only, so no crack quad hides another. 1.12.2's pass ({@code RenderGlobal.drawBlockDamageTexture})
 * also writes depth, and it draws the model's quads in a fixed order: the down, up, north, south, west and east
 * buckets, then the unculled quads. A crack quad behind one drawn before it fails the depth test. The crossed planes
 * come last, so they never get the crack, and the far inner walls get it from some sides but not others.
 *
 * <h2>What this does</h2>
 * During the crack pass only, the block gives its model the camera position and the sides it would draw
 * ({@link #VIEW}, from {@code getExtendedState}). The model then returns every quad it would draw as unculled quads,
 * each split into quarters (so the crossed planes no longer cross each other), each drawn before the quads in front
 * of it ({@link #order}). Each layer then passes the depth test where 1.19's would. The quarters also take 1.19's crack coordinates
 * ({@code SheetedDecalTextureGenerator}: projected from the block position along the face's direction), not the
 * model's own UVs. The chunk render (and everything else) gets the model's quads as they are.
 *
 * The geometry here is plain math, so the headless self-test runs it on a dedicated server.
 */
public final class RootsCrack {

    private RootsCrack() {
    }

    /** The camera, relative to the block's corner, and the sides the block draws (bit {@code 1 << face index}). */
    public static final class View {
        public final double x;
        public final double y;
        public final double z;
        public final int sides;

        public View(double x, double y, double z, int sides) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.sides = sides;
        }

        public boolean draws(EnumFacing side) {
            return (this.sides & (1 << side.getIndex())) != 0;
        }

        @Override
        public String toString() {
            return String.format("View[%.2f %.2f %.2f sides %s]", this.x, this.y, this.z, Integer.toBinaryString(this.sides));
        }
    }

    /** Set by the client: the view of a block in the crack pass, or null. */
    public interface Viewer {
        View view(IBlockState state, IBlockAccess world, BlockPos pos);
    }

    /** Set by the client (null on a server). */
    public static volatile Viewer viewer;

    public static final IUnlistedProperty<View> VIEW = new IUnlistedProperty<View>() {
        @Override
        public String getName() {
            return "crack_view";
        }

        @Override
        public boolean isValid(View value) {
            return true;
        }

        @Override
        public Class<View> getType() {
            return View.class;
        }

        @Override
        public String valueToString(View value) {
            return String.valueOf(value);
        }
    };

    // --- geometry ----------------------------------------------------------------------------------------------------

    /**
     * The four quarters of a quad: corners {@code c} (x, y, z of four corners in winding order, in blocks), split at
     * the middle of both pairs of sides. Each quarter keeps the winding.
     */
    public static float[][] quarters(float[] c) {
        float[][] out = new float[4][];
        float[][] st = {{0.0F, 0.5F, 0.0F, 0.5F}, {0.0F, 0.5F, 0.5F, 1.0F}, {0.5F, 1.0F, 0.5F, 1.0F}, {0.5F, 1.0F, 0.0F, 0.5F}};
        for (int q = 0; q < 4; q++) {
            float s0 = st[q][0];
            float s1 = st[q][1];
            float t0 = st[q][2];
            float t1 = st[q][3];
            float[] r = new float[12];
            point(c, s0, t0, r, 0);
            point(c, s0, t1, r, 3);
            point(c, s1, t1, r, 6);
            point(c, s1, t0, r, 9);
            out[q] = r;
        }
        return out;
    }

    /** The point (s, t) of the quad: corner 0 at (0, 0), 1 at (0, 1), 2 at (1, 1), 3 at (1, 0). */
    private static void point(float[] c, float s, float t, float[] out, int at) {
        for (int k = 0; k < 3; k++) {
            out[at + k] = c[k] * (1 - s) * (1 - t) + c[3 + k] * (1 - s) * t + c[6 + k] * s * t + c[9 + k] * s * (1 - t);
        }
    }

    /**
     * 1.19's crack coordinates for a point (block-local, in blocks) of a quad facing {@code face}: the position turned
     * by 180 degrees about Y, -90 about X and the direction's rotation, negated. The crack repeats every block.
     */
    public static float[] decal(EnumFacing face, float x, float y, float z) {
        switch (face) {
            case DOWN:
                return new float[]{x, -z};
            case UP:
                return new float[]{x, z};
            case NORTH:
                return new float[]{-x, -y};
            case SOUTH:
                return new float[]{x, -y};
            case WEST:
                return new float[]{-z, -y};
            case EAST:
            default:
                return new float[]{z, -y};
        }
    }

    /**
     * The crack coordinates (u, v per corner, 0-1 of the crack texture) of a quad no larger than a block face: 1.19's
     * repeating coordinates, moved by whole blocks into 0-1, since 1.12.2's crack is a sprite in the atlas.
     */
    public static float[] decalUVs(EnumFacing face, float[] c) {
        float[] uv = new float[8];
        float minU = Float.MAX_VALUE;
        float minV = Float.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            float[] d = decal(face, c[i * 3], c[i * 3 + 1], c[i * 3 + 2]);
            uv[i * 2] = d[0];
            uv[i * 2 + 1] = d[1];
            minU = Math.min(minU, d[0]);
            minV = Math.min(minV, d[1]);
        }
        float du = (float) Math.floor(minU + 1.0E-4F);
        float dv = (float) Math.floor(minV + 1.0E-4F);
        for (int i = 0; i < 4; i++) {
            uv[i * 2] = clamp01(uv[i * 2] - du);
            uv[i * 2 + 1] = clamp01(uv[i * 2 + 1] - dv);
        }
        return uv;
    }

    private static float clamp01(float f) {
        return f < 0.0F ? 0.0F : f > 1.0F ? 1.0F : f;
    }

    /** A quad of the crack: its corners and what it was made from. */
    public static final class Piece<T> {
        public final float[] corners;
        public final T source;

        Piece(float[] corners, T source) {
            this.corners = corners;
            this.source = source;
        }
    }

    /**
     * The crack's quads for a view: the quarters of every quad given, in an order that draws each one before every
     * quad in front of it. The quarters are ordered as a BSP tree of their planes: at each plane, the side away from
     * the camera, then the plane's own quads, then the camera's side. No quarter of the roots crosses another's plane
     * (the crossed planes meet at their middles, the walls lie on the block's sides), so nothing is split. A quad that
     * did cross a plane would go to the side of its center.
     *
     * @param corners the corners of each quad (see {@link #quarters})
     * @param sources what each quad was made from, handed back with its quarters
     */
    public static <T> List<Piece<T>> order(List<float[]> corners, List<T> sources, View view) {
        List<Piece<T>> all = new ArrayList<Piece<T>>(corners.size() * 4);
        for (int i = 0; i < corners.size(); i++) {
            for (float[] q : quarters(corners.get(i))) {
                all.add(new Piece<T>(q, sources.get(i)));
            }
        }
        List<Piece<T>> out = new ArrayList<Piece<T>>(all.size());
        farFirst(all, view, out);
        return out;
    }

    private static final double ON_PLANE = 1.0E-5D;

    private static <T> void farFirst(List<Piece<T>> pieces, View view, List<Piece<T>> out) {
        if (pieces.isEmpty()) {
            return;
        }
        double[] plane = plane(pieces.get(0).corners);
        if (plane == null) {
            // A degenerate quad: no plane to split by.
            out.add(pieces.get(0));
            farFirst(pieces.subList(1, pieces.size()), view, out);
            return;
        }
        List<Piece<T>> back = new ArrayList<Piece<T>>();
        List<Piece<T>> front = new ArrayList<Piece<T>>();
        List<Piece<T>> on = new ArrayList<Piece<T>>();
        for (Piece<T> p : pieces) {
            double lo = Double.MAX_VALUE;
            double hi = -Double.MAX_VALUE;
            double sum = 0.0D;
            for (int i = 0; i < 4; i++) {
                double d = side(plane, p.corners[i * 3], p.corners[i * 3 + 1], p.corners[i * 3 + 2]);
                lo = Math.min(lo, d);
                hi = Math.max(hi, d);
                sum += d;
            }
            if (lo >= -ON_PLANE && hi <= ON_PLANE) {
                on.add(p);
            } else if (hi <= ON_PLANE) {
                back.add(p);
            } else if (lo >= -ON_PLANE) {
                front.add(p);
            } else {
                (sum > 0.0D ? front : back).add(p);
            }
        }
        boolean cameraInFront = side(plane, view.x, view.y, view.z) > 0.0D;
        farFirst(cameraInFront ? back : front, view, out);
        out.addAll(on);
        farFirst(cameraInFront ? front : back, view, out);
    }

    /** The plane of a quad: unit normal (from its first three corners) and offset; null for a degenerate quad. */
    private static double[] plane(float[] c) {
        double ax = c[3] - c[0];
        double ay = c[4] - c[1];
        double az = c[5] - c[2];
        double bx = c[9] - c[0];
        double by = c[10] - c[1];
        double bz = c[11] - c[2];
        double nx = ay * bz - az * by;
        double ny = az * bx - ax * bz;
        double nz = ax * by - ay * bx;
        double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1.0E-9D) {
            return null;
        }
        nx /= len;
        ny /= len;
        nz /= len;
        return new double[]{nx, ny, nz, nx * c[0] + ny * c[1] + nz * c[2]};
    }

    private static double side(double[] plane, double x, double y, double z) {
        return plane[0] * x + plane[1] * y + plane[2] * z - plane[3];
    }
}
