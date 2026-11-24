package com.exiledradio.wildupdatebackport.selftest;

import com.exiledradio.wildupdatebackport.WildUpdateBackport;
import com.exiledradio.wildupdatebackport.deepdark.city.CityBeard;
import com.exiledradio.wildupdatebackport.deepdark.city.CityPiece;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.gen.structure.StructureBoundingBox;

import java.io.File;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A census of an ancient city's space once it is built: liquid in the open space (a piece's box or the cavern its
 * beard carves), and glow lichen (Caves &amp; Cliffs') left with no face on anything, in the city's box grown by the
 * beard's 12 blocks. 26.3's cities have neither: the beard is in the noise before any liquid or lichen is placed,
 * and the deep dark's aquifer holds no fluid. Every position goes to {@code city-strays.txt} in the run directory.
 */
final class CityStrays {

    int sources, exposedSources, flowing, lichen, floatingLichen;
    final Map<String, Integer> byKind = new TreeMap<String, Integer>();

    static CityStrays count(WorldServer world, List<CityPiece> layout, StructureBoundingBox all, int lo, int hi, String tag) throws Exception {
        CityStrays out = new CityStrays();
        double rock = 0.5 - 1.0 / 24.0;
        // The pieces, for a census of another world (vanilla's) by the same rules: boxes (B, with the sauna marked),
        // the rigid pieces' beard boxes from their ground (R) and the junctions (J).
        PrintWriter pieces = new PrintWriter(new File("city-pieces-" + tag + ".txt"), "UTF-8");
        try {
            for (CityPiece piece : layout) {
                StructureBoundingBox b = piece.getBoundingBox();
                if (!piece.isFeature()) {
                    pieces.println("B " + b.minX + " " + b.minY + " " + b.minZ + " " + b.maxX + " " + b.maxY + " " + b.maxZ + " "
                            + (DeepDarkSelfTest.describe(piece.element()).contains("sauna") ? "sauna" : "-"));
                }
                if (piece.rigid()) {
                    pieces.println("R " + b.minX + " " + (b.minY + piece.groundLevelDelta()) + " " + b.minZ + " " + b.maxX + " " + b.maxY + " " + b.maxZ);
                }
                int[] j = piece.junctions();
                for (int n = 0; n < j.length; n += 3) {
                    pieces.println("J " + j[n] + " " + j[n + 1] + " " + j[n + 2]);
                }
            }
        } finally {
            pieces.close();
        }
        PrintWriter file = new PrintWriter(new File("city-strays-" + tag + ".txt"), "UTF-8");
        try {
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (int x = all.minX - 12; x <= all.maxX + 12; x++) {
                for (int z = all.minZ - 12; z <= all.maxZ + 12; z++) {
                    for (int y = Math.max(lo, all.minY - 12); y <= Math.min(hi, all.maxY + 12); y++) {
                        p.setPos(x, y, z);
                        IBlockState s = world.getBlockState(p);
                        Material m = s.getMaterial();
                        boolean liquid = m.isLiquid() && s.getBlock() instanceof BlockLiquid;
                        String name = String.valueOf(s.getBlock().getRegistryName());
                        boolean isLichen = name.startsWith("cavesandcliffsbackport:glow_lichen");
                        if (!liquid && !isLichen) {
                            continue;
                        }
                        String where = where(layout, x, y, z, rock);
                        if (where == null) {
                            continue;
                        }
                        if (liquid && "sauna".equals(where)) {
                            out.byKind.merge("the sauna's own " + (m == Material.LAVA ? "lava" : "water"), 1, Integer::sum);
                            continue;
                        }
                        if (liquid) {
                            int level = s.getValue(BlockLiquid.LEVEL);
                            String kind = (m == Material.LAVA ? "lava" : "water") + (level == 0 ? " source" : " flowing") + " in " + where;
                            out.byKind.merge(kind, 1, Integer::sum);
                            if (level == 0) {
                                out.sources++;
                                if (exposed(world, p)) {
                                    out.exposedSources++;
                                }
                            } else {
                                out.flowing++;
                            }
                            file.println(x + " " + y + " " + z + " " + kind);
                        } else {
                            out.lichen++;
                            if (!held(world, p, s, name)) {
                                out.floatingLichen++;
                                out.byKind.merge("floating lichen in " + where, 1, Integer::sum);
                                file.println(x + " " + y + " " + z + " floating lichen in " + where);
                            }
                        }
                    }
                }
            }
        } finally {
            file.close();
        }
        WildUpdateBackport.LOGGER.info("SELFTEST city strays ({}): {} liquid sources ({} by air), {} flowing, lichen {} ({} float); {}",
                tag, out.sources, out.exposedSources, out.flowing, out.lichen, out.floatingLichen, out.byKind);
        return out;
    }

    /** @return "piece" or "beard" for the city's open space, or null outside it */
    static String where(List<CityPiece> layout, int x, int y, int z, double rock) {
        String in = null;
        for (CityPiece piece : layout) {
            if (!piece.isFeature() && piece.getBoundingBox().isVecInside(new BlockPos(x, y, z))) {
                // The sauna's pool is 1.19's own water (structures/sauna_1).
                if (DeepDarkSelfTest.describe(piece.element()).contains("sauna")) {
                    return "sauna";
                }
                in = "piece";
            }
        }
        if (in != null) {
            return in;
        }
        return CityBeard.density(layout, x, y, z) < -rock ? "beard" : null;
    }

    /** @return true if a side or the bottom of a liquid opens onto air: it would run as soon as anything updates it */
    static boolean exposed(WorldServer world, BlockPos p) {
        for (EnumFacing f : EnumFacing.values()) {
            if (f != EnumFacing.UP && world.getBlockState(p.offset(f)).getMaterial() == Material.AIR) {
                return true;
            }
        }
        return false;
    }

    /** Glow lichen: whether any of its faces lies on a solid face (Caves &amp; Cliffs' {@code BlockGlowLichen.supported}). */
    @SuppressWarnings("unchecked")
    static boolean held(WorldServer world, BlockPos p, IBlockState s, String name) {
        boolean down = name.endsWith("_down");
        boolean up = name.endsWith("_up") || name.endsWith("_up_down");
        for (EnumFacing f : EnumFacing.values()) {
            boolean has;
            if (f == EnumFacing.DOWN) {
                has = down;
            } else if (f == EnumFacing.UP) {
                has = up;
            } else {
                has = false;
                for (IProperty<?> prop : s.getPropertyKeys()) {
                    if (prop.getName().equals(f.getName())) {
                        has = Boolean.TRUE.equals(s.getValue(prop));
                    }
                }
            }
            if (has) {
                BlockPos b = p.offset(f);
                if (world.getBlockState(b).getBlockFaceShape(world, b, f.getOpposite()) == BlockFaceShape.SOLID) {
                    return true;
                }
            }
        }
        return false;
    }
}
