package com.exiledradio.wildupdatebackport.sculk;

import com.exiledradio.wildupdatebackport.common.Owners;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRedSandstone;
import net.minecraft.block.BlockSandStone;
import net.minecraft.block.BlockStone;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;

import java.util.HashSet;
import java.util.Set;

/**
 * 1.19's {@code #sculk_replaceable} and {@code #sculk_replaceable_world_gen} block tags, as fixed sets (1.12.2 has
 * no tags).
 *
 * <p>{@code #sculk_replaceable}: overworld base stone (stone, granite, diorite, andesite; Caves &amp; Cliffs'
 * deepslate and tuff), {@code #dirt} (dirt, coarse dirt, podzol, grass, mycelium; Caves &amp; Cliffs' rooted dirt
 * and moss; our mud and muddy mangrove roots), terracotta, netherrack, sand and red sand, gravel, soul sand, clay,
 * end stone, plain sandstone and red sandstone, and Caves &amp; Cliffs' calcite, smooth basalt and dripstone block.
 * 1.16's nylium, basalt, blackstone and soul soil have no 1.12.2 blocks to name here.
 *
 * <p>{@code #sculk_replaceable_world_gen} adds the deepslate building blocks ancient cities are made of.
 */
public final class SculkTags {

    private static Set<Block> extra;
    private static Set<Block> worldGenExtra;

    private SculkTags() {
    }

    public static boolean replaceable(IBlockState state, boolean worldGen) {
        Block b = state.getBlock();
        if (b == Blocks.STONE) {
            BlockStone.EnumType t = state.getValue(BlockStone.VARIANT);
            return t == BlockStone.EnumType.STONE || t == BlockStone.EnumType.GRANITE || t == BlockStone.EnumType.DIORITE
                    || t == BlockStone.EnumType.ANDESITE;
        }
        if (b == Blocks.SANDSTONE) {
            return state.getValue(BlockSandStone.TYPE) == BlockSandStone.EnumType.DEFAULT;
        }
        if (b == Blocks.RED_SANDSTONE) {
            return state.getValue(BlockRedSandstone.TYPE) == BlockRedSandstone.EnumType.DEFAULT;
        }
        if (b == Blocks.DIRT || b == Blocks.GRASS || b == Blocks.MYCELIUM || b == Blocks.HARDENED_CLAY
                || b == Blocks.STAINED_HARDENED_CLAY || b == Blocks.NETHERRACK || b == Blocks.SAND || b == Blocks.GRAVEL
                || b == Blocks.SOUL_SAND || b == Blocks.CLAY || b == Blocks.END_STONE) {
            return true;
        }
        if (extra == null) {
            build();
        }
        return extra.contains(b) || worldGen && worldGenExtra.contains(b);
    }

    private static synchronized void build() {
        Set<Block> set = new HashSet<Block>();
        String cc = Owners.CAVES_AND_CLIFFS;
        if (Owners.present(cc)) {
            for (String name : new String[]{"tuff", "rooted_dirt", "moss_block", "calcite", "smooth_basalt", "dripstone_block"}) {
                add(set, Owners.block(cc, name));
            }
        }
        // Deepslate: Caves & Cliffs' own, or this mod's copy when it registers one (ancient cities, stream DD).
        add(set, Owners.block(cc, "deepslate"));
        add(set, Owners.ourBlock("mud"));
        add(set, Owners.ourBlock("muddy_mangrove_roots"));
        Set<Block> gen = new HashSet<Block>();
        for (String name : new String[]{"deepslate_bricks", "deepslate_tiles", "cobbled_deepslate", "cracked_deepslate_bricks",
                "cracked_deepslate_tiles", "polished_deepslate"}) {
            add(gen, Owners.block(cc, name));
        }
        worldGenExtra = gen;
        extra = set;
    }

    private static void add(Set<Block> set, Block block) {
        if (block != null && block != Blocks.AIR) {
            set.add(block);
        }
    }
}
