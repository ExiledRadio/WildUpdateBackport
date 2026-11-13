package com.exiledradio.wildupdatebackport.items;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.translation.I18n;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;

/**
 * An item with a gray description line under its name: 1.19's {@code DiscFragmentItem}
 * ({@code item.minecraft.disc_fragment_5.desc}, "Music Disc - 5").
 */
public class ItemDescribed extends Item {

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        tooltip.add(TextFormatting.GRAY + I18n.translateToLocal(this.getTranslationKey() + ".desc"));
    }
}
