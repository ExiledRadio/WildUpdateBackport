package com.exiledradio.wildupdatebackport.items;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;

/**
 * 1.19's Swift Sneak: leggings, levels I-III, very rare, enchantability 25 per level to 50 more.
 *
 * <p>1.19 marks it treasure, not tradeable and not discoverable: enchanting tables, {@code enchant_with_levels}
 * loot (fishing among it) and random enchanting never pick it, librarians never sell it, and it is only found
 * where a loot table names it (ancient cities). 1.12.2 has the treasure flag only:
 * <ul>
 *   <li>not discoverable at the enchanting table or by {@code enchant_with_levels}: both go through
 *       {@code EnchantmentHelper.getEnchantmentDatas}, which asks {@link #canApplyAtEnchantingTable} and, for
 *       books, {@link #isAllowedOnBooks}; both are false. An anvil asks {@link #canApply}, which Forge routes
 *       through {@code canApplyAtEnchantingTable}; here it asks the item instead, so a book still goes onto
 *       leggings;</li>
 *   <li>not by {@code enchant_randomly} without a list, nor in trades: {@link Discovery}.</li>
 * </ul>
 */
public class EnchantmentSwiftSneak extends Enchantment {

    /** 1.19's {@code EnchantmentHelper.SWIFT_SNEAK_EXTRA_FACTOR}. */
    public static final float PER_LEVEL = 0.15F;
    /** 1.12.2's and 1.19's base sneaking speed. */
    public static final float BASE = 0.3F;

    public EnchantmentSwiftSneak() {
        super(Rarity.VERY_RARE, EnumEnchantmentType.ARMOR_LEGS, new EntityEquipmentSlot[] {EntityEquipmentSlot.LEGS});
    }

    @Override
    public int getMinEnchantability(int level) {
        return level * 25;
    }

    @Override
    public int getMaxEnchantability(int level) {
        return this.getMinEnchantability(level) + 50;
    }

    @Override
    public int getMaxLevel() {
        return 3;
    }

    @Override
    public boolean isTreasureEnchantment() {
        return true;
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack) {
        return false;
    }

    /** Forge's {@code canApply} asks {@link #canApplyAtEnchantingTable}; the anvil's answer is the item's own. */
    @Override
    public boolean canApply(ItemStack stack) {
        return stack.getItem().canApplyAtEnchantingTable(stack, this);
    }

    @Override
    public boolean isAllowedOnBooks() {
        return false;
    }

    /**
     * @return 1.19's sneaking speed for this entity: {@code clamp(0.3 + 0.15 * level, 0, 1)}, the level being the
     * highest on its worn leggings
     */
    public static float sneakSpeed(Enchantment swiftSneak, EntityLivingBase entity) {
        int level = EnchantmentHelper.getMaxEnchantmentLevel(swiftSneak, entity);
        return Math.max(0.0F, Math.min(1.0F, BASE + PER_LEVEL * level));
    }
}
