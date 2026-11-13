package com.exiledradio.wildupdatebackport.deepdark;

import com.exiledradio.wildupdatebackport.common.Owners;
import com.exiledradio.wildupdatebackport.items.ItemsContent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.potion.Potion;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.storage.loot.LootContext;
import net.minecraft.world.storage.loot.LootEntry;
import net.minecraft.world.storage.loot.LootEntryEmpty;
import net.minecraft.world.storage.loot.LootEntryItem;
import net.minecraft.world.storage.loot.LootPool;
import net.minecraft.world.storage.loot.LootTable;
import net.minecraft.world.storage.loot.RandomValueRange;
import net.minecraft.world.storage.loot.conditions.LootCondition;
import net.minecraft.world.storage.loot.functions.EnchantRandomly;
import net.minecraft.world.storage.loot.functions.EnchantWithLevels;
import net.minecraft.world.storage.loot.functions.LootFunction;
import net.minecraft.world.storage.loot.functions.SetCount;
import net.minecraft.world.storage.loot.functions.SetDamage;
import net.minecraft.world.storage.loot.functions.SetNBT;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 1.19.2's {@code chests/ancient_city} and {@code chests/ancient_city_ice_box}, entry for entry, weight for weight.
 * The files ({@code loot_tables/chests/ancient_city*.json}) are empty; the pools are built here at load, because some
 * entries are other backports' items: Caves &amp; Cliffs' Otherside disc, candles, amethyst shards and glow berries,
 * Nether Update's soul torches, Village &amp; Pillage's suspicious stew. An entry whose item nobody installed provides
 * stays as an empty entry of its weight, so every other entry keeps 1.19's chance.
 */
public final class AncientCityLoot {

    public static final ResourceLocation CITY = DeepDarkContent.id("chests/ancient_city");
    public static final ResourceLocation ICE_BOX = DeepDarkContent.id("chests/ancient_city_ice_box");

    private static final LootCondition[] NONE = new LootCondition[0];

    @SubscribeEvent
    public void load(LootTableLoadEvent event) {
        if (CITY.equals(event.getName())) {
            event.getTable().addPool(city());
        } else if (ICE_BOX.equals(event.getName())) {
            event.getTable().addPool(iceBox());
        }
    }

    static LootPool city() {
        List<LootEntry> e = new ArrayList<LootEntry>();
        // 1.12.2's enchanted golden apple is the golden apple's metadata 1.
        add(e, Items.GOLDEN_APPLE, 1, 0, count(1, 2), meta(1));
        add(e, Owners.item(Owners.CAVES_AND_CLIFFS, "music_disc_otherside"), "music_disc_otherside", 1);
        add(e, Items.COMPASS, 2, 0);
        add(e, item("sculk_catalyst"), "sculk_catalyst", 2, count(1, 2));
        add(e, Items.NAME_TAG, 2, 0);
        add(e, Items.DIAMOND_HOE, 2, 0, new SetDamage(NONE, new RandomValueRange(0.8F, 1.0F)), levels(30, 50));
        add(e, Items.LEAD, 2, 0);
        add(e, Items.DIAMOND_HORSE_ARMOR, 2, 0);
        add(e, Items.SADDLE, 2, 0);
        add(e, Items.RECORD_13, 2, 0);
        add(e, Items.RECORD_CAT, 2, 0);
        add(e, Items.DIAMOND_LEGGINGS, 2, 0, levels(30, 50));
        Enchantment swiftSneak = ItemsContent.swiftSneak();
        if (swiftSneak != null) {
            // The enchantment is held as an object and the book made when the chest fills, so its id is the world's.
            add(e, Items.BOOK, 3, 0, new EnchantRandomly(NONE, Collections.singletonList(swiftSneak)));
        } else {
            e.add(new LootEntryEmpty(3, 0, NONE, "swift_sneak_book"));
        }
        add(e, item("sculk"), "sculk", 3, count(4, 10));
        add(e, item("sculk_sensor"), "sculk_sensor", 3, count(1, 3));
        add(e, Owners.item(Owners.CAVES_AND_CLIFFS, "candle"), "candle", 3, count(1, 4));
        add(e, Owners.item(Owners.CAVES_AND_CLIFFS, "amethyst_shard"), "amethyst_shard", 3, count(1, 15));
        add(e, Items.EXPERIENCE_BOTTLE, 3, 0, count(1, 3));
        add(e, Owners.item(Owners.CAVES_AND_CLIFFS, "glow_berries"), "glow_berries", 3, count(1, 15));
        add(e, Items.IRON_LEGGINGS, 3, 0, levels(20, 39));
        add(e, ItemsContent.echoShard(), "echo_shard", 4, count(1, 3));
        add(e, ItemsContent.discFragment(), "disc_fragment_5", 4, count(1, 3));
        add(e, Items.POTIONITEM, 5, 0, count(1, 3), potion("minecraft:strong_regeneration"));
        add(e, Items.BOOK, 5, 0, new EnchantRandomly(NONE, null));
        add(e, Items.BOOK, 5, 0, count(3, 10));
        add(e, Items.BONE, 5, 0, count(1, 15));
        add(e, foreign("netherupdatebackport:soul_torch"), "soul_torch", 5, count(1, 15));
        add(e, Items.COAL, 7, 0, count(6, 15));
        return new LootPool(e.toArray(new LootEntry[0]), NONE, new RandomValueRange(5, 10), new RandomValueRange(0),
                "wildupdatebackport:ancient_city");
    }

    static LootPool iceBox() {
        List<LootEntry> e = new ArrayList<LootEntry>();
        Item stew = foreign("villagerbackport:suspicious_stew");
        if (stew != null) {
            e.add(new LootEntryItem(stew, 1, 0, new LootFunction[] {new StewEffect(NONE), count(2, 6)}, NONE, "suspicious_stew"));
        } else {
            e.add(new LootEntryEmpty(1, 0, NONE, "suspicious_stew"));
        }
        add(e, Items.GOLDEN_CARROT, 1, 0, count(1, 10));
        add(e, Items.BAKED_POTATO, 1, 0, count(1, 10));
        add(e, Item.getItemFromBlock(net.minecraft.init.Blocks.PACKED_ICE), 2, 0, count(2, 6));
        add(e, Items.SNOWBALL, 4, 0, count(2, 6));
        return new LootPool(e.toArray(new LootEntry[0]), NONE, new RandomValueRange(4, 10), new RandomValueRange(0),
                "wildupdatebackport:ancient_city_ice_box");
    }

    private static void add(List<LootEntry> entries, Item item, int weight, int quality, LootFunction... functions) {
        entries.add(new LootEntryItem(item, weight, quality, functions, NONE, item.getRegistryName().toString() + "#" + entries.size()));
    }

    /** An entry for an item that may be absent; absent, an empty entry of the same weight. */
    private static void add(List<LootEntry> entries, @Nullable Item item, String name, int weight, LootFunction... functions) {
        if (item == null) {
            entries.add(new LootEntryEmpty(weight, 0, NONE, name));
        } else {
            entries.add(new LootEntryItem(item, weight, 0, functions, NONE, name));
        }
    }

    @Nullable
    private static Item item(String name) {
        return Owners.ours(name);
    }

    @Nullable
    private static Item foreign(String id) {
        ResourceLocation r = new ResourceLocation(id);
        return ForgeRegistries.ITEMS.containsKey(r) ? ForgeRegistries.ITEMS.getValue(r) : null;
    }

    private static SetCount count(int min, int max) {
        return new SetCount(NONE, new RandomValueRange(min, max));
    }

    private static LootFunction meta(int meta) {
        return new LootFunction(NONE) {
            @Override
            public ItemStack apply(ItemStack stack, Random rand, LootContext context) {
                stack.setItemDamage(meta);
                return stack;
            }
        };
    }

    private static EnchantWithLevels levels(int min, int max) {
        return new EnchantWithLevels(NONE, new RandomValueRange(min, max), true);
    }

    private static SetNBT potion(String id) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("Potion", id);
        return new SetNBT(NONE, tag);
    }

    /**
     * 1.19's {@code set_stew_effect} for the ice box: one of night vision (7 to 10 seconds) or blindness (5 to 7), in
     * 1.14's stew layout ({@code Effects: [{EffectId: byte, EffectDuration: int}]}), which Village &amp; Pillage reads.
     */
    static final class StewEffect extends LootFunction {
        StewEffect(LootCondition[] conditions) {
            super(conditions);
        }

        @Override
        public ItemStack apply(ItemStack stack, Random rand, LootContext context) {
            boolean night = rand.nextInt(2) == 0;
            Potion effect = night ? MobEffects.NIGHT_VISION : MobEffects.BLINDNESS;
            int seconds = night ? 7 + rand.nextInt(4) : 5 + rand.nextInt(3);
            NBTTagCompound e = new NBTTagCompound();
            e.setByte("EffectId", (byte) Potion.getIdFromPotion(effect));
            e.setInteger("EffectDuration", seconds * 20);
            NBTTagList list = new NBTTagList();
            list.appendTag(e);
            NBTTagCompound tag = stack.hasTagCompound() ? stack.getTagCompound() : new NBTTagCompound();
            tag.setTag("Effects", list);
            stack.setTagCompound(tag);
            return stack;
        }
    }
}
