package com.exiledradio.wildupdatebackport.changes;

import com.exiledradio.wildupdatebackport.ModConfig;
import com.exiledradio.wildupdatebackport.WildUpdateBackport;
import com.exiledradio.wildupdatebackport.common.Owners;
import net.minecraft.block.state.IBlockState;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityTNTPrimed;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemElytra;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.stats.StatList;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Explosion;
import net.minecraft.world.World;
import net.minecraft.world.storage.loot.LootEntry;
import net.minecraft.world.storage.loot.LootEntryItem;
import net.minecraft.world.storage.loot.LootPool;
import net.minecraft.world.storage.loot.LootTableList;
import net.minecraft.world.storage.loot.RandomValueRange;
import net.minecraft.world.storage.loot.conditions.EntityHasProperty;
import net.minecraft.world.storage.loot.conditions.LootCondition;
import net.minecraft.world.storage.loot.functions.LootFunction;
import net.minecraft.world.storage.loot.functions.LootingEnchantBonus;
import net.minecraft.world.storage.loot.functions.SetCount;
import net.minecraft.world.storage.loot.functions.Smelt;
import net.minecraft.world.storage.loot.properties.EntityOnFire;
import net.minecraft.world.storage.loot.properties.EntityProperty;
import net.minecraft.world.storage.loot.LootContext;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.furnace.FurnaceFuelBurnTimeEvent;
import net.minecraftforge.event.world.ExplosionEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * 1.19's to 1.19.4's changes to items and blocks that events reach:
 * <ul>
 *   <li>the four block minecarts are crafted shapeless (1.19; their drops are patch P4);</li>
 *   <li>ores and spawners blown up by an explosion a player caused drop experience (1.19's {@code Explosion}: the block's
 *       {@code spawnAfterBreak} with {@code dropExperience} when {@code getSourceMob()} is a player);</li>
 *   <li>rabbits always drop one raw rabbit (1.19.3's loot table);</li>
 *   <li>armor and elytra swap with what is worn (1.19.4's {@code Equipable.swapWithEquipmentSlot});</li>
 *   <li>scaffolding burns for 50 ticks, as bamboo (1.19's furnace fuel table).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = WildUpdateBackport.MOD_ID)
public final class ItemChanges {

    private ItemChanges() {
    }

    // ------------------------------------------------------------------------------------------
    // Minecart recipes.

    /** The four 1.19 shapeless recipes, keeping 1.12.2's names so the recipe book's unlocks still find them. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void recipes(RegistryEvent.Register<IRecipe> event) {
        if (!ChangesHooks.on(ModConfig.changes.minecarts)) {
            return;
        }
        Object[][] carts = {
                {"chest_minecart", Blocks.CHEST, Items.CHEST_MINECART},
                {"furnace_minecart", Blocks.FURNACE, Items.FURNACE_MINECART},
                {"tnt_minecart", Blocks.TNT, Items.TNT_MINECART},
                {"hopper_minecart", Blocks.HOPPER, Items.HOPPER_MINECART},
        };
        for (Object[] c : carts) {
            NonNullList<Ingredient> in = NonNullList.create();
            in.add(Ingredient.fromStacks(new ItemStack((net.minecraft.block.Block) c[1])));
            in.add(Ingredient.fromItem(Items.MINECART));
            ShapelessRecipes recipe = new ShapelessRecipes("", new ItemStack((Item) c[2]), in);
            recipe.setRegistryName(new ResourceLocation("minecraft", (String) c[0]));
            event.getRegistry().register(recipe);
        }
    }

    // ------------------------------------------------------------------------------------------
    // Experience from blown-up ores and spawners.

    /**
     * Before the blocks go: for each block the explosion destroys, the experience it would drop when mined without a
     * tool ({@code getExpDrop}, 1.12.2's ores and spawner give 1.19's amounts), as orbs at its center.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void explosion(ExplosionEvent.Detonate event) {
        World world = event.getWorld();
        if (world.isRemote || !ChangesHooks.on(ModConfig.changes.explosionExperience)
                || !world.getGameRules().getBoolean("doTileDrops") || !destroysBlocks(event.getExplosion())
                || !(sourceMob(event.getExplosion()) instanceof EntityPlayer)) {
            return;
        }
        for (BlockPos pos : event.getAffectedBlocks()) {
            IBlockState state = world.getBlockState(pos);
            if (state.getMaterial() == net.minecraft.block.material.Material.AIR
                    || !state.getBlock().canDropFromExplosion(event.getExplosion())) {
                continue;
            }
            int xp = state.getBlock().getExpDrop(state, world, pos, 0);
            while (xp > 0) {
                int orb = EntityXPOrb.getXPSplit(xp);
                xp -= orb;
                world.spawnEntity(new EntityXPOrb(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, orb));
            }
        }
    }

    static boolean destroysBlocks(Explosion explosion) {
        try {
            return ObfuscationReflectionHelper.getPrivateValue(Explosion.class, explosion, "field_82755_b");
        } catch (RuntimeException e) {
            return true;
        }
    }

    /** 1.19's {@code Explosion.getSourceMob}: a primed TNT's igniter, a living source, or a projectile's living owner. */
    static EntityLivingBase sourceMob(Explosion explosion) {
        Entity source;
        try {
            source = ObfuscationReflectionHelper.getPrivateValue(Explosion.class, explosion, "field_77283_e");
        } catch (RuntimeException e) {
            return explosion.getExplosivePlacedBy();
        }
        if (source instanceof EntityTNTPrimed) {
            return ((EntityTNTPrimed) source).getTntPlacedBy();
        }
        if (source instanceof EntityLivingBase) {
            return (EntityLivingBase) source;
        }
        if (source instanceof EntityFireball) {
            return ((EntityFireball) source).shootingEntity;
        }
        return null;
    }

    // ------------------------------------------------------------------------------------------
    // Rabbits.

    /** 1.19.3's rabbit table: the meat pool's count is 1 instead of 0 to 1; everything else as 1.12.2's. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void loot(LootTableLoadEvent event) {
        if (!LootTableList.ENTITIES_RABBIT.equals(event.getName()) || !ChangesHooks.on(ModConfig.changes.rabbitDrops)) {
            return;
        }
        LootPool pool = event.getTable().getPool("pool1");
        if (pool == null || pool.removeEntry("minecraft:rabbit") == null) {
            WildUpdateBackport.LOGGER.warn("The rabbit loot table is not vanilla's; its meat drop was left as it is.");
            return;
        }
        pool.addEntry(rabbitMeat());
    }

    static LootEntry rabbitMeat() {
        LootCondition[] none = new LootCondition[0];
        LootCondition onFire = new EntityHasProperty(new EntityProperty[] {new EntityOnFire(true)}, LootContext.EntityTarget.THIS);
        LootFunction[] functions = {
                new SetCount(none, new RandomValueRange(1.0F)),
                new Smelt(new LootCondition[] {onFire}),
                new LootingEnchantBonus(none, new RandomValueRange(0.0F, 1.0F), 0),
        };
        return new LootEntryItem(Items.RABBIT, 1, 0, functions, none, "minecraft:rabbit");
    }

    // ------------------------------------------------------------------------------------------
    // Armor swapping.

    private static final Map<Class<?>, Boolean> PLAIN_USE = new HashMap<Class<?>, Boolean>();

    /**
     * 1.19.4: using armor or an elytra while that slot is taken swaps the two (unless the worn piece has Curse of Binding
     * or is the same stack). With the slot empty, 1.12.2's own equip runs, which 1.19.4's matches. Items whose class has
     * its own right-click are left to it.
     */
    @SubscribeEvent
    public static void swap(PlayerInteractEvent.RightClickItem event) {
        ItemStack held = event.getItemStack();
        Item item = held.getItem();
        if (!(item instanceof ItemArmor || item instanceof ItemElytra) || !ChangesHooks.on(ModConfig.changes.armorSwap)
                || !plainUse(item)) {
            return;
        }
        EntityPlayer player = event.getEntityPlayer();
        EntityEquipmentSlot slot = EntityLiving.getSlotForItemStack(held);
        ItemStack worn = player.getItemStackFromSlot(slot);
        if (worn.isEmpty() || EnchantmentHelper.hasBindingCurse(worn) || ItemStack.areItemStacksEqual(held, worn)) {
            return;
        }
        if (!player.world.isRemote) {
            player.addStat(StatList.getObjectUseStats(item));
        }
        ItemStack toHand = worn.copy();
        ItemStack toSlot = held.copy();
        player.setItemStackToSlot(slot, toSlot);
        player.setHeldItem(event.getHand(), toHand);
        event.setCanceled(true);
        event.setCancellationResult(EnumActionResult.SUCCESS);
    }

    /** @return true when the item's right-click is {@code ItemArmor}'s or {@code ItemElytra}'s own */
    static synchronized boolean plainUse(Item item) {
        Boolean known = PLAIN_USE.get(item.getClass());
        if (known == null) {
            Object dev = Launch.blackboard == null ? null : Launch.blackboard.get("fml.deobfuscatedEnvironment");
            String name = Boolean.TRUE.equals(dev) ? "onItemRightClick" : "func_77659_a";
            try {
                Method m = item.getClass().getMethod(name, World.class, EntityPlayer.class, EnumHand.class);
                known = m.getDeclaringClass() == ItemArmor.class || m.getDeclaringClass() == ItemElytra.class;
            } catch (NoSuchMethodException e) {
                known = false;
            }
            PLAIN_USE.put(item.getClass(), known);
        }
        return known;
    }

    // ------------------------------------------------------------------------------------------
    // Scaffolding.

    /** 1.19's furnace fuel table: scaffolding 50 ticks, as bamboo (1.14 to 1.18: 400). Village &amp; Pillage's block. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void fuel(FurnaceFuelBurnTimeEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || !ChangesHooks.on(ModConfig.changes.scaffoldingFuel)) {
            return;
        }
        if (isScaffolding(stack.getItem().getRegistryName())) {
            event.setBurnTime(SCAFFOLDING_BURN_TIME);
        }
    }

    /** 1.19's {@code AbstractFurnaceBlockEntity}: {@code add(map, Blocks.SCAFFOLDING, 50)}, as bamboo. */
    public static final int SCAFFOLDING_BURN_TIME = 50;

    public static boolean isScaffolding(ResourceLocation id) {
        return id != null && Owners.VILLAGE_AND_PILLAGE.equals(id.getNamespace()) && "scaffolding".equals(id.getPath());
    }
}
