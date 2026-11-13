package com.exiledradio.wildupdatebackport.items;

import com.exiledradio.wildupdatebackport.WildUpdateBackport;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentData;
import net.minecraft.entity.IMerchant;
import net.minecraft.init.Items;
import net.minecraft.item.ItemEnchantedBook;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.MathHelper;
import net.minecraft.village.MerchantRecipe;
import net.minecraft.village.MerchantRecipeList;
import net.minecraft.world.storage.loot.LootContext;
import net.minecraft.world.storage.loot.LootEntry;
import net.minecraft.world.storage.loot.LootEntryItem;
import net.minecraft.world.storage.loot.LootPool;
import net.minecraft.world.storage.loot.LootTable;
import net.minecraft.world.storage.loot.conditions.LootCondition;
import net.minecraft.world.storage.loot.functions.EnchantRandomly;
import net.minecraft.world.storage.loot.functions.LootFunction;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * 1.19's {@code isDiscoverable} and {@code isTradeable} for enchantments 1.12.2 has no flag for: Swift Sneak.
 *
 * <ul>
 *   <li><b>{@code enchant_randomly} without a list</b> (mineshaft, temple, mansion books...) picks any
 *       enchantment in 1.12.2. Every such function in every loot table is swapped, as the table loads, for one
 *       that does the same but skips hidden enchantments, as 1.19's skips undiscoverable ones. A function that
 *       names its enchantments (the ancient city's Swift Sneak book) is left alone.</li>
 *   <li><b>Trades</b>: 1.12.2's librarian (and Village &amp; Pillage's, which uses the same trade) picks any
 *       enchantment. Before a merchant's trades are shown, a book offering a hidden enchantment is re-rolled in
 *       place with 1.12.2's own book formula over the tradeable ones, so the offer the player sees never names
 *       it. The trade objects are left as they are.</li>
 * </ul>
 */
public final class Discovery {

    private static final Set<Enchantment> HIDDEN = new LinkedHashSet<Enchantment>();
    private static boolean warned;

    private Discovery() {
    }

    /** Marks an enchantment as 1.19's undiscoverable and untradeable. */
    public static void hide(Enchantment enchantment) {
        HIDDEN.add(enchantment);
    }

    public static boolean hidden(Enchantment enchantment) {
        return HIDDEN.contains(enchantment);
    }

    // --- loot ---------------------------------------------------------------------------------

    /** Swaps every list-less {@code enchant_randomly} in a table; called from the table's load event. */
    public static int swapRandomEnchants(LootTable table) {
        int swapped = 0;
        try {
            List<LootPool> pools = ObfuscationReflectionHelper.getPrivateValue(LootTable.class, table, "field_186466_c");
            for (LootPool pool : pools) {
                List<LootEntry> entries = ObfuscationReflectionHelper.getPrivateValue(LootPool.class, pool, "field_186453_a");
                for (LootEntry entry : entries) {
                    if (!(entry instanceof LootEntryItem)) {
                        continue;
                    }
                    LootFunction[] functions = ObfuscationReflectionHelper.getPrivateValue(LootEntryItem.class,
                            (LootEntryItem) entry, "field_186369_b");
                    for (int i = 0; i < functions.length; i++) {
                        if (functions[i] instanceof EnchantRandomly && unlisted((EnchantRandomly) functions[i])) {
                            functions[i] = new DiscoverableEnchantRandomly(functions[i].getConditions());
                            swapped++;
                        }
                    }
                }
            }
        } catch (RuntimeException e) {
            if (!warned) {
                warned = true;
                WildUpdateBackport.LOGGER.warn("Could not read a loot table's functions; random enchanted books there may name Swift Sneak.", e);
            }
        }
        return swapped;
    }

    private static boolean unlisted(EnchantRandomly function) {
        List<?> list = ObfuscationReflectionHelper.getPrivateValue(EnchantRandomly.class, function, "field_186558_b");
        return list == null || list.isEmpty();
    }

    /** 1.12.2's {@code EnchantRandomly} with an empty list, minus hidden enchantments. */
    static final class DiscoverableEnchantRandomly extends LootFunction {

        DiscoverableEnchantRandomly(LootCondition[] conditions) {
            super(conditions);
        }

        @Override
        public ItemStack apply(ItemStack stack, Random rand, LootContext context) {
            List<Enchantment> list = new ArrayList<Enchantment>();
            for (Enchantment e : Enchantment.REGISTRY) {
                if (!hidden(e) && (stack.getItem() == Items.BOOK || e.canApply(stack))) {
                    list.add(e);
                }
            }
            if (list.isEmpty()) {
                return stack;
            }
            Enchantment enchantment = list.get(rand.nextInt(list.size()));
            int level = MathHelper.getInt(rand, enchantment.getMinLevel(), enchantment.getMaxLevel());
            if (stack.getItem() == Items.BOOK) {
                ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
                ItemEnchantedBook.addEnchantment(book, new EnchantmentData(enchantment, level));
                return book;
            }
            stack.addEnchantment(enchantment, level);
            return stack;
        }
    }

    // --- trades -------------------------------------------------------------------------------

    /** @return how many offers were re-rolled */
    public static int rerollTrades(IMerchant merchant, MerchantRecipeList recipes, Random random) {
        int changed = 0;
        if (recipes == null) {
            return 0;
        }
        for (MerchantRecipe recipe : recipes) {
            ItemStack sell = recipe.getItemToSell();
            if (sell.getItem() == Items.ENCHANTED_BOOK && namesHidden(sell)) {
                reroll(sell, recipe.getItemToBuy(), random);
                changed++;
            }
        }
        return changed;
    }

    static boolean namesHidden(ItemStack book) {
        NBTTagList list = ItemEnchantedBook.getEnchantments(book);
        for (int i = 0; i < list.tagCount(); i++) {
            Enchantment e = Enchantment.getEnchantmentByID(list.getCompoundTagAt(i).getShort("id"));
            if (e != null && hidden(e)) {
                return true;
            }
        }
        return false;
    }

    /** 1.12.2's {@code ListEnchantedBookForEmeralds}, over the tradeable enchantments, written into the offer. */
    private static void reroll(ItemStack book, ItemStack price, Random random) {
        List<Enchantment> tradeable = new ArrayList<Enchantment>();
        for (Enchantment e : Enchantment.REGISTRY) {
            if (!hidden(e)) {
                tradeable.add(e);
            }
        }
        if (tradeable.isEmpty()) {
            return;
        }
        Collections.shuffle(tradeable, random);
        Enchantment enchantment = tradeable.get(0);
        int level = MathHelper.getInt(random, enchantment.getMinLevel(), enchantment.getMaxLevel());
        int emeralds = 2 + random.nextInt(5 + level * 10) + 3 * level;
        if (enchantment.isTreasureEnchantment()) {
            emeralds *= 2;
        }
        NBTTagCompound tag = book.getTagCompound();
        if (tag != null) {
            tag.removeTag("StoredEnchantments");
        }
        ItemEnchantedBook.addEnchantment(book, new EnchantmentData(enchantment, level));
        if (price.getItem() == Items.EMERALD) {
            price.setCount(Math.min(emeralds, 64));
        }
    }
}
