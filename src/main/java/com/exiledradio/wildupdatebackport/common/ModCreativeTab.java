package com.exiledradio.wildupdatebackport.common;

import com.exiledradio.wildupdatebackport.WildUpdateBackport;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** The mod's creative tab: {@code itemGroup.wildupdatebackport}. */
public final class ModCreativeTab extends CreativeTabs {

    public static final ModCreativeTab TAB = new ModCreativeTab();

    /** Items that can stand for the tab, first one registered wins; switched-off streams have none. */
    private static final String[] ICONS = {"sculk_shrieker", "sculk_catalyst", "sculk", "echo_shard", "mangrove_propagule", "mud_bricks"};

    private ModCreativeTab() {
        super(WildUpdateBackport.MOD_ID);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public ItemStack createIcon() {
        for (String name : ICONS) {
            Item item = ForgeRegistries.ITEMS.getValue(Content.id(name));
            if (item != null) {
                return new ItemStack(item);
            }
        }
        return new ItemStack(Items.SLIME_BALL);
    }
}
