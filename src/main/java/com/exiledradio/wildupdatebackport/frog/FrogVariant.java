package com.exiledradio.wildupdatebackport.frog;

import com.exiledradio.wildupdatebackport.common.Content;

import net.minecraft.util.ResourceLocation;

/**
 * 1.19's three frog variants, saved as 1.19 saves them ({@code variant: "minecraft:warm"}).
 *
 * <p>Each one has the froglight it drops when it eats a small magma cube: temperate frogs ochre, warm frogs
 * pearlescent, cold frogs verdant.
 */
public enum FrogVariant {
    TEMPERATE("temperate", "ochre_froglight"),
    WARM("warm", "pearlescent_froglight"),
    COLD("cold", "verdant_froglight");

    private static final FrogVariant[] BY_ID = values();

    /** 1.19's name, {@code minecraft:temperate} and so on. */
    public final String id;
    /** The froglight block this variant makes from a small magma cube. */
    public final String froglight;
    /** {@code textures/entity/frog/<name>_frog.png} */
    public final ResourceLocation texture;

    FrogVariant(String name, String froglight) {
        this.id = "minecraft:" + name;
        this.froglight = froglight;
        this.texture = Content.id("textures/entity/frog/" + name + "_frog.png");
    }

    public static FrogVariant byIndex(int index) {
        return index >= 0 && index < BY_ID.length ? BY_ID[index] : TEMPERATE;
    }

    /** @return the variant saved under this name (with or without {@code minecraft:}), or temperate */
    public static FrogVariant byId(String id) {
        for (FrogVariant v : BY_ID) {
            if (v.id.equals(id) || v.id.equals("minecraft:" + id)) {
                return v;
            }
        }
        return TEMPERATE;
    }
}
