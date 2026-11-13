package com.exiledradio.wildupdatebackport.deepdark.city;

import com.exiledradio.backportcore.structure.TemplateProcessor;
import com.exiledradio.wildupdatebackport.deepdark.DeepDarkContent;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 1.19.2's three ancient city processor lists ({@code ProcessorLists}), each in its order:
 *
 * <ul>
 *   <li>{@code ancient_city_start_degradation}: the rule below, then {@code protected_blocks}.</li>
 *   <li>{@code ancient_city_generic_degradation}: {@code block_rot} of {@code #ancient_city_replaceable} at integrity
 *       0.95, the rule, {@code protected_blocks}.</li>
 *   <li>{@code ancient_city_walls_degradation}: as generic, with deepslate tile slabs 30% gone too.</li>
 * </ul>
 * The rule: 30% of deepslate bricks cracked, 30% of deepslate tiles cracked, 5% of soul lanterns gone (the first rule
 * a block matches wins). {@code protected_blocks} leaves {@code #features_cannot_replace} in the world alone.
 */
public final class CityProcessors {

    /** 1.19.2's {@code #ancient_city_replaceable}. */
    public static final Set<String> REPLACEABLE = new HashSet<String>(Arrays.asList("minecraft:deepslate", "minecraft:deepslate_bricks",
            "minecraft:deepslate_tiles", "minecraft:deepslate_brick_slab", "minecraft:deepslate_tile_slab", "minecraft:deepslate_brick_stairs",
            "minecraft:deepslate_tile_wall", "minecraft:deepslate_brick_wall", "minecraft:cobbled_deepslate", "minecraft:cracked_deepslate_bricks",
            "minecraft:cracked_deepslate_tiles", "minecraft:gray_wool"));

    public static final float INTEGRITY = 0.95F;

    private CityProcessors() {
    }

    public static List<TemplateProcessor> forList(String name) {
        switch (name) {
            case "minecraft:ancient_city_start_degradation":
                return Arrays.asList(rule(false), PROTECTED);
            case "minecraft:ancient_city_generic_degradation":
                return Arrays.asList(ROT, rule(false), PROTECTED);
            case "minecraft:ancient_city_walls_degradation":
                return Arrays.asList(ROT, rule(true), PROTECTED);
            default:
                return Collections.emptyList();
        }
    }

    /** {@code BlockRotProcessor(#ancient_city_replaceable, 0.95)}: a listed block is left out one time in twenty. */
    static final TemplateProcessor ROT = (world, pos, block, random) ->
            REPLACEABLE.contains(block.name()) && random.nextFloat() > INTEGRITY ? null : block;

    static TemplateProcessor rule(boolean walls) {
        return (world, pos, block, random) -> {
            String n = block.name();
            if ("minecraft:deepslate_bricks".equals(n) && random.nextFloat() < 0.3F) {
                return block.replacedBy("minecraft:cracked_deepslate_bricks");
            }
            if ("minecraft:deepslate_tiles".equals(n) && random.nextFloat() < 0.3F) {
                return block.replacedBy("minecraft:cracked_deepslate_tiles");
            }
            if (walls && "minecraft:deepslate_tile_slab".equals(n) && random.nextFloat() < 0.3F) {
                return block.replacedBy("minecraft:air");
            }
            if ("minecraft:soul_lantern".equals(n) && random.nextFloat() < 0.05F) {
                return block.replacedBy("minecraft:air");
            }
            return block;
        };
    }

    /** {@code ProtectedBlockProcessor(#features_cannot_replace)}: bedrock, spawners, chests, end portal frames, reinforced deepslate. */
    static final TemplateProcessor PROTECTED = (world, pos, block, random) -> {
        IBlockState there = world.getBlockState(pos);
        return cannotReplace(there.getBlock()) ? null : block;
    };

    public static boolean cannotReplace(Block b) {
        return b == Blocks.BEDROCK || b == Blocks.MOB_SPAWNER || b == Blocks.CHEST || b == Blocks.END_PORTAL_FRAME
                || (b != null && b == DeepDarkContent.reinforcedDeepslate());
    }

    static List<String> listNames() {
        return new ArrayList<String>(Arrays.asList("minecraft:ancient_city_start_degradation", "minecraft:ancient_city_generic_degradation",
                "minecraft:ancient_city_walls_degradation"));
    }
}
