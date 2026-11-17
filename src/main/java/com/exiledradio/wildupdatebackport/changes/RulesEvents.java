package com.exiledradio.wildupdatebackport.changes;

import com.exiledradio.wildupdatebackport.ModConfig;
import com.exiledradio.wildupdatebackport.WildUpdateBackport;
import com.exiledradio.wildupdatebackport.common.Owners;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraft.world.storage.loot.LootEntry;
import net.minecraft.world.storage.loot.LootEntryItem;
import net.minecraft.world.storage.loot.LootPool;
import net.minecraft.world.storage.loot.LootTableList;
import net.minecraft.world.storage.loot.RandomValueRange;
import net.minecraft.world.storage.loot.conditions.LootCondition;
import net.minecraft.world.storage.loot.functions.LootFunction;
import net.minecraft.world.storage.loot.functions.SetCount;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * The event-driven low-impact 1.19.x changes:
 * <ul>
 *   <li>the 1.19.3 and 1.19.4 game rules ({@link RulesHooks#RULES}) added to every world that lacks them; {@code
 *       waterSourceConversion} and {@code lavaSourceConversion} through Forge's {@code CreateFluidSourceEvent} (1.19.3's
 *       {@code WaterFluid}/{@code LavaFluid.canConvertToSource}), the others in the patches' hooks;</li>
 *   <li>no spawns on chorus flowers or scaffolding (1.19.3, 22w42a, MC-160610 and MC-150488: their {@code isValidSpawn}
 *       is never), outside spawners as in 1.19.3's {@code Mob.checkMobSpawnRules};</li>
 *   <li>mangrove logs in bonus chests (1.19, 22w15a: {@code chests/spawn_bonus_chest}'s log pool gains {@code
 *       mangrove_log}, weight 3, 1 to 3, as each other log);</li>
 *   <li>all leaves waterloggable (1.19, 22w12a), with Fluidlogged API ({@code LeavesWater}).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = WildUpdateBackport.MOD_ID)
public final class RulesEvents {

    private RulesEvents() {
    }

    public static void init() {
        if (Loader.isModLoaded("fluidlogged_api")) {
            try {
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(
                        Class.forName("com.exiledradio.wildupdatebackport.changes.LeavesWater"));
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    // ------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void worldLoad(WorldEvent.Load event) {
        if (event.getWorld().isRemote || !RulesHooks.rulesOn()) {
            return;
        }
        addRules(event.getWorld().getGameRules());
    }

    public static void addRules(GameRules rules) {
        for (String[] rule : RulesHooks.RULES) {
            if (!rules.hasRule(rule[0])) {
                rules.addGameRule(rule[0], rule[1], "true".equals(rule[1]) || "false".equals(rule[1])
                        ? GameRules.ValueType.BOOLEAN_VALUE : GameRules.ValueType.NUMERICAL_VALUE);
            }
        }
    }

    @SubscribeEvent
    public static void fluidSource(BlockEvent.CreateFluidSourceEvent event) {
        if (!RulesHooks.rulesOn()) {
            return;
        }
        World world = event.getWorld();
        Material m = event.getState().getMaterial();
        if (m == Material.WATER && !RulesHooks.bool(world, RulesHooks.WATER_SOURCES, true)) {
            event.setResult(Event.Result.DENY);
        } else if (m == Material.LAVA && RulesHooks.bool(world, RulesHooks.LAVA_SOURCES, false)) {
            event.setResult(Event.Result.ALLOW);
        }
    }

    // ------------------------------------------------------------------------------------------

    /** @return true for a block 1.19.3 never lets a mob spawn on */
    public static boolean noSpawnOn(Block block) {
        return block == Blocks.CHORUS_FLOWER || ItemChanges.isScaffolding(block.getRegistryName());
    }

    @SubscribeEvent
    public static void checkSpawn(LivingSpawnEvent.CheckSpawn event) {
        if (event.isSpawner() || !ChangesHooks.on(ModConfig.changes.spawnBlocks)) {
            return;
        }
        BlockPos below = new BlockPos(event.getX(), event.getY(), event.getZ()).down();
        if (noSpawnOn(event.getWorld().getBlockState(below).getBlock())) {
            event.setResult(Event.Result.DENY);
        }
    }

    // ------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void loot(LootTableLoadEvent event) {
        if (!LootTableList.CHESTS_SPAWN_BONUS_CHEST.equals(event.getName()) || !ChangesHooks.on(ModConfig.changes.mangroveBonusChest)) {
            return;
        }
        Block log = Owners.ourBlock("mangrove_log");
        LootPool pool = null;
        for (String name : new String[] {"pool3", "main", "pool1", "pool2"}) {
            LootPool p = event.getTable().getPool(name);
            if (p != null && p.getEntry("minecraft:log2") != null) {
                pool = p;
            }
        }
        if (log == null || pool == null) {
            WildUpdateBackport.LOGGER.warn("The bonus chest loot table is not vanilla's; no mangrove logs were added to it.");
            return;
        }
        pool.addEntry(mangroveLogs(Item.getItemFromBlock(log)));
    }

    static LootEntry mangroveLogs(Item log) {
        LootCondition[] none = new LootCondition[0];
        LootFunction[] count = {new SetCount(none, new RandomValueRange(1.0F, 3.0F))};
        return new LootEntryItem(log, 3, 0, count, none, "wildupdatebackport:mangrove_log");
    }
}
