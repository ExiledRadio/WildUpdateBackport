package com.exiledradio.wildupdatebackport.frog;

import net.minecraft.block.BlockRotatedPillar;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;

/**
 * Ochre, verdant and pearlescent froglights: 1.19's {@code RotatedPillarBlock} with hardness and resistance
 * 0.3, light 15, the froglight sounds, any tool. Placed along an axis as logs are.
 *
 * <p>Map colors: 1.19's {@code SAND}, {@code GLOW_LICHEN} and {@code COLOR_PINK}; 1.12.2 has no glow lichen
 * color, so verdant takes the nearest one it has ({@code SILVER}).
 */
public class BlockFroglight extends BlockRotatedPillar {

    /** 1.19's {@code Material.FROGLIGHT}: solid, any tool, pushable. */
    public static final Material MATERIAL = new Material(MapColor.SAND);

    public BlockFroglight(MapColor color) {
        super(MATERIAL, color);
        this.setHardness(0.3F);
        this.setLightLevel(1.0F);
        this.setSoundType(FrogSounds.FROGLIGHT);
    }
}
