package com.exiledradio.wildupdatebackport.boat;

import net.minecraft.entity.item.EntityBoat;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The woods boats are made of, by id ({@code "minecraft:oak"}, {@code "wildupdatebackport:mangrove"}).
 *
 * <p>1.12.2's boat type enum indexes a texture array, so no mod can add a wood to vanilla's boat. This is the
 * small registry the family asks Backport Core for (request 4 in {@code docs/CORE_REQUESTS.md}), kept here
 * until Core has it: our boat and chest-boat entities save the wood's id as a string, so moving them onto
 * Core's entities later is a rename of the entity only.
 */
public final class BoatWood {

    private static final Map<String, BoatWood> BY_ID = new LinkedHashMap<String, BoatWood>();

    public final String id;
    /** Vanilla's boat type for the six old woods, null for new woods. */
    @Nullable
    public final EntityBoat.Type vanilla;
    /** Item name stem: oak, spruce, ..., mangrove. */
    public final String name;
    private final Supplier<ItemStack> planks;
    public final ResourceLocation boatTexture;
    public final ResourceLocation chestBoatTexture;

    /** Set when the items are registered. */
    @Nullable
    Item boatItem;
    @Nullable
    Item chestBoatItem;

    private BoatWood(String id, @Nullable EntityBoat.Type vanilla, String name, Supplier<ItemStack> planks,
                     ResourceLocation boatTexture, ResourceLocation chestBoatTexture) {
        this.id = id;
        this.vanilla = vanilla;
        this.name = name;
        this.planks = planks;
        this.boatTexture = boatTexture;
        this.chestBoatTexture = chestBoatTexture;
    }

    /** Registers a wood. Vanilla's six are registered first, then ours. */
    public static BoatWood register(String id, @Nullable EntityBoat.Type vanilla, String name, Supplier<ItemStack> planks,
                                    ResourceLocation boatTexture, ResourceLocation chestBoatTexture) {
        BoatWood wood = new BoatWood(id, vanilla, name, planks, boatTexture, chestBoatTexture);
        BY_ID.put(id, wood);
        return wood;
    }

    static void registerVanilla(String modid) {
        if (!BY_ID.isEmpty()) {
            return;
        }
        for (final EntityBoat.Type type : EntityBoat.Type.values()) {
            String n = type.getName();
            register("minecraft:" + n, type, n, () -> new ItemStack(Blocks.PLANKS, 1, type.getMetadata()),
                    new ResourceLocation("textures/entity/boat/boat_" + n + ".png"),
                    new ResourceLocation(modid, "textures/entity/chest_boat/" + n + ".png"));
        }
    }

    /** @return the wood with this id, or oak's when it is unknown (a wood whose mod was removed) */
    public static BoatWood get(String id) {
        BoatWood wood = BY_ID.get(id);
        return wood != null ? wood : BY_ID.get("minecraft:oak");
    }

    public static List<BoatWood> all() {
        return Collections.unmodifiableList(new ArrayList<BoatWood>(BY_ID.values()));
    }

    public ItemStack planks() {
        return this.planks.get();
    }

    @Nullable
    public Item boatItem() {
        return this.boatItem;
    }

    @Nullable
    public Item chestBoatItem() {
        return this.chestBoatItem;
    }
}
