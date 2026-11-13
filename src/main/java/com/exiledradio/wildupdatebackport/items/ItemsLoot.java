package com.exiledradio.wildupdatebackport.items;

import com.exiledradio.wildupdatebackport.common.Owners;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.storage.loot.LootEntry;
import net.minecraft.world.storage.loot.LootEntryEmpty;
import net.minecraft.world.storage.loot.LootEntryItem;
import net.minecraft.world.storage.loot.LootPool;
import net.minecraft.world.storage.loot.LootTable;
import net.minecraft.world.storage.loot.LootTableList;
import net.minecraft.world.storage.loot.RandomValueRange;
import net.minecraft.world.storage.loot.conditions.LootCondition;
import net.minecraft.world.storage.loot.functions.EnchantRandomly;
import net.minecraft.world.storage.loot.functions.LootFunction;
import net.minecraft.world.storage.loot.functions.SetCount;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Every loot this stream adds, in one place.
 *
 * <ul>
 *   <li><b>Pillager outposts</b> (Village &amp; Pillage's {@code villagerbackport:chests/pillager_outpost}, by
 *       name): 1.19's goat horn pool, one roll of 0-1 of a regular goat horn.</li>
 *   <li><b>Ancient-city loot</b> (echo shards, disc fragments, Swift Sneak books) is in the ancient cities' own chests,
 *       stream DD's {@code deepdark.AncientCityLoot}; the temporary stronghold and dungeon fallback is gone with them.</li>
 *   <li>Every table: list-less random enchanting skips Swift Sneak ({@link Discovery}).</li>
 * </ul>
 */
public final class ItemsLoot {

    public static final ResourceLocation OUTPOST = new ResourceLocation(Owners.VILLAGE_AND_PILLAGE, "chests/pillager_outpost");

    private static final LootCondition[] NONE = new LootCondition[0];

    @SubscribeEvent
    public void load(LootTableLoadEvent event) {
        LootTable table = event.getTable();
        Discovery.swapRandomEnchants(table);
        ResourceLocation name = event.getName();
        if (OUTPOST.equals(name)) {
            addOutpostHorns(table);
        }
    }

    static void addOutpostHorns(LootTable table) {
        Item horn = ItemsContent.goatHorn();
        if (horn == null) {
            return;
        }
        LootEntry entry = new LootEntryItem(horn, 1, 0, new LootFunction[] {new SetInstrument(NONE, SetInstrument.REGULAR)}, NONE,
                "wildupdatebackport:goat_horn");
        table.addPool(new LootPool(new LootEntry[] {entry}, NONE, new RandomValueRange(0, 1), new RandomValueRange(0),
                "wildupdatebackport:goat_horn"));
    }
}
