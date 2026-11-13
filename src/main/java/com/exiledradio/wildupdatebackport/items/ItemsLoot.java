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
 *   <li><b>Ancient-city loot, temporary fallback</b> (the plan's <i>Decided</i> 3): until stream DD builds
 *       ancient cities, stronghold and dungeon chests get the three ancient-city entries of this stream at the
 *       ancient city's rate: a pool of 5-10 rolls (the city chest's) over echo shards (weight 4, 1-3), disc
 *       fragments (4, 1-3), a Swift Sneak book (3) and nothing (77, the rest of the city chest's weight of 88).
 *       {@link #FALLBACK} is removed in the commit that adds ancient cities.</li>
 *   <li>Every table: list-less random enchanting skips Swift Sneak ({@link Discovery}).</li>
 * </ul>
 */
public final class ItemsLoot {

    public static final ResourceLocation OUTPOST = new ResourceLocation(Owners.VILLAGE_AND_PILLAGE, "chests/pillager_outpost");

    /** The chests that stand in for ancient cities until DD; removed with it. */
    public static final Set<ResourceLocation> FALLBACK = new HashSet<ResourceLocation>(Arrays.asList(
            LootTableList.CHESTS_STRONGHOLD_CORRIDOR, LootTableList.CHESTS_STRONGHOLD_CROSSING,
            LootTableList.CHESTS_STRONGHOLD_LIBRARY, LootTableList.CHESTS_SIMPLE_DUNGEON));

    /** 1.19.2's {@code chests/ancient_city}: its weights for this stream's three entries, and the whole. */
    static final int ECHO_WEIGHT = 4, FRAGMENT_WEIGHT = 4, SWIFT_SNEAK_WEIGHT = 3, CITY_TOTAL_WEIGHT = 88;

    private static final LootCondition[] NONE = new LootCondition[0];

    @SubscribeEvent
    public void load(LootTableLoadEvent event) {
        LootTable table = event.getTable();
        Discovery.swapRandomEnchants(table);
        ResourceLocation name = event.getName();
        if (OUTPOST.equals(name)) {
            addOutpostHorns(table);
        }
        if (FALLBACK.contains(name)) {
            addAncientCityFallback(table);
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

    static void addAncientCityFallback(LootTable table) {
        Item shard = ItemsContent.echoShard();
        Item fragment = ItemsContent.discFragment();
        Enchantment swiftSneak = ItemsContent.swiftSneak();
        if (shard == null || fragment == null || swiftSneak == null) {
            return;
        }
        int rest = CITY_TOTAL_WEIGHT - ECHO_WEIGHT - FRAGMENT_WEIGHT - SWIFT_SNEAK_WEIGHT;
        LootEntry[] entries = {
                new LootEntryItem(shard, ECHO_WEIGHT, 0, new LootFunction[] {new SetCount(NONE, new RandomValueRange(1, 3))}, NONE,
                        "wildupdatebackport:echo_shard"),
                new LootEntryItem(fragment, FRAGMENT_WEIGHT, 0, new LootFunction[] {new SetCount(NONE, new RandomValueRange(1, 3))}, NONE,
                        "wildupdatebackport:disc_fragment_5"),
                // The enchantment is held as an object and the book made when the chest fills, so the book's numeric
                // id is the world's own.
                new LootEntryItem(Items.BOOK, SWIFT_SNEAK_WEIGHT, 0,
                        new LootFunction[] {new EnchantRandomly(NONE, Collections.singletonList(swiftSneak))}, NONE,
                        "wildupdatebackport:swift_sneak_book"),
                new LootEntryEmpty(rest, 0, NONE, "wildupdatebackport:nothing"),
        };
        table.addPool(new LootPool(entries, NONE, new RandomValueRange(5, 10), new RandomValueRange(0),
                "wildupdatebackport:ancient_city_fallback"));
    }
}
