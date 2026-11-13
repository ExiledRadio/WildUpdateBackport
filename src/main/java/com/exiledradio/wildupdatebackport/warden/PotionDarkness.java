package com.exiledradio.wildupdatebackport.warden;

import com.exiledradio.wildupdatebackport.WildUpdateBackport;

import net.minecraft.client.gui.Gui;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Darkness: 1.19's harmful effect, color 2696993. What it does is all on the client
 * ({@code warden.client.DarknessClient}): the world's light pulses down and the fog closes in to 15 blocks.
 * Drawn from its own 18x18 icon, as 1.19 draws every effect.
 */
public class PotionDarkness extends Potion {

    public static final int COLOR = 2696993;
    private static final ResourceLocation ICON = new ResourceLocation(WildUpdateBackport.MOD_ID, "textures/mob_effect/darkness.png");

    PotionDarkness() {
        super(true, COLOR);
        setPotionName("effect." + WildUpdateBackport.MOD_ID + ".darkness");
        setRegistryName(WardenContent.DARKNESS_ID);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean hasStatusIcon() {
        return false;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderInventoryEffect(PotionEffect effect, Gui gui, int x, int y, float z) {
        com.exiledradio.wildupdatebackport.warden.client.DarknessClient.drawIcon(ICON, x + 6, y + 7, 1.0F);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderHUDEffect(PotionEffect effect, Gui gui, int x, int y, float z, float alpha) {
        com.exiledradio.wildupdatebackport.warden.client.DarknessClient.drawIcon(ICON, x + 3, y + 3, alpha);
    }
}
