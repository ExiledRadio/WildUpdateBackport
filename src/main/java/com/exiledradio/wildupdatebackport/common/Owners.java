package com.exiledradio.wildupdatebackport.common;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import javax.annotation.Nullable;

/**
 * Who owns a block that more than one mod of this family needs.
 *
 * <h2>The rule</h2>
 * The mod that shipped a block first owns it. When the owner is installed this mod registers nothing
 * for that block and uses the owner's; when it is not, this mod registers its own copy under its own
 * id, with the same states and metadata, so the owner can later rename it in place.
 *
 * <h2>Lookups by name</h2>
 * The family's ownership table is Backport Core's {@code BlockOwnership}; this class keeps the owners'
 * mod ids and the by-name lookups every stream uses, which never link against another mod's classes.
 */
public final class Owners {

    /** 1.14: the wandering trader, pillager outposts, scaffolding, the composter, sign dyeing, stripped logs. */
    public static final String VILLAGE_AND_PILLAGE = "villagerbackport";

    /** 1.15: bee nests (in mangrove trees). */
    public static final String BUZZY_BEES = "buzzybeesbackport";

    /** 1.17 and 1.18: goats, deepslate, pointed dripstone, rooted dirt, moss, amethyst, glow lichen. */
    public static final String CAVES_AND_CLIFFS = "cavesandcliffsbackport";

    /** 1.13: kelp, seagrass and the water plants' technique. */
    public static final String UPDATE_AQUATIC = "updateaquaticbackport";
    /** Game Drops Backport (26.x): 26.1 took name tags out of the ancient city's chests. */
    public static final String GAME_DROPS = "gamedropsbackport";

    private Owners() {
    }

    /** @return true if the owning mod is installed, so its block is used and ours is never registered */
    public static boolean present(String owner) {
        return Loader.isModLoaded(owner);
    }

    /**
     * @return the block registered under {@code owner:name} if the owner is installed, else ours under
     * this mod's id; null if neither exists (the stream that would register ours is switched off)
     */
    @Nullable
    public static Block block(String owner, String name) {
        ResourceLocation id = present(owner) ? new ResourceLocation(owner, name) : Content.id(name);
        Block block = ForgeRegistries.BLOCKS.getValue(id);
        return block == null || !id.equals(block.getRegistryName()) ? null : block;
    }

    /** @return the item for {@code name} the same way as {@link #block}, or null */
    @Nullable
    public static Item item(String owner, String name) {
        ResourceLocation id = present(owner) ? new ResourceLocation(owner, name) : Content.id(name);
        Item item = ForgeRegistries.ITEMS.getValue(id);
        return item == null || !id.equals(item.getRegistryName()) ? null : item;
    }

    /**
     * Looks up another stream's item by name, so streams never link against each other's classes.
     *
     * @return this mod's item {@code name}, or null when its stream is switched off
     */
    @Nullable
    public static Item ours(String name) {
        Item item = ForgeRegistries.ITEMS.getValue(Content.id(name));
        return item == null || !Content.id(name).equals(item.getRegistryName()) ? null : item;
    }

    /** As {@link #ours(String)}, for blocks. */
    @Nullable
    public static Block ourBlock(String name) {
        Block block = ForgeRegistries.BLOCKS.getValue(Content.id(name));
        return block == null || !Content.id(name).equals(block.getRegistryName()) ? null : block;
    }
}
