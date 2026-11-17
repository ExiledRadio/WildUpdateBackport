package com.exiledradio.wildupdatebackport.changes;

import com.exiledradio.wildupdatebackport.ModConfig;

import net.minecraft.block.BlockSnow;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.item.EntityEnderCrystal;
import net.minecraft.entity.item.EntityMinecartTNT;
import net.minecraft.entity.item.EntityTNTPrimed;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemMonsterPlacer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.MobSpawnerBaseLogic;
import net.minecraft.util.WeightedSpawnerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.Explosion;
import net.minecraft.world.GameRules;
import net.minecraft.world.IWorldEventListener;
import net.minecraft.world.World;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

import java.lang.reflect.Field;

/**
 * The static methods patches P12 to P18 put into vanilla code ({@code docs/PATCHES.md}), and the game rules they read.
 * Each takes vanilla's value and gives it back unchanged while its switch is off, so a disabled change behaves exactly
 * as 1.12.2.
 */
public final class RulesHooks {

    // 1.19.3 (22w44a) and 1.19.4 (23w03a, 23w06a) game rules, with their defaults.
    public static final String BLOCK_DECAY = "blockExplosionDropDecay";
    public static final String MOB_DECAY = "mobExplosionDropDecay";
    public static final String TNT_DECAY = "tntExplosionDropDecay";
    public static final String SNOW_HEIGHT = "snowAccumulationHeight";
    public static final String WATER_SOURCES = "waterSourceConversion";
    public static final String LAVA_SOURCES = "lavaSourceConversion";
    public static final String GLOBAL_SOUNDS = "globalSoundEvents";
    public static final String BLOCK_LIMIT = "commandModificationBlockLimit";
    public static final String VINES = "doVinesSpread";

    /** Every rule with its default, in the order 1.19.4's GameRules lists them. */
    public static final String[][] RULES = {
            {BLOCK_DECAY, "true"}, {MOB_DECAY, "true"}, {TNT_DECAY, "false"}, {SNOW_HEIGHT, "1"}, {WATER_SOURCES, "true"},
            {LAVA_SOURCES, "false"}, {GLOBAL_SOUNDS, "true"}, {BLOCK_LIMIT, "32768"}, {VINES, "true"},
    };

    private static final Field EXPLODER = ObfuscationReflectionHelper.findField(Explosion.class, "field_77283_e");
    private static final Field SPAWN_DATA = ObfuscationReflectionHelper.findField(MobSpawnerBaseLogic.class, "field_98282_f");

    private RulesHooks() {
    }

    static boolean rulesOn() {
        return ChangesHooks.on(ModConfig.changes.gameRules);
    }

    /** A boolean rule, or its default where the world does not have it (yet). */
    public static boolean bool(World world, String rule, boolean def) {
        GameRules rules = world.getGameRules();
        return rules.hasRule(rule) ? rules.getBoolean(rule) : def;
    }

    public static int integer(World world, String rule, int def) {
        GameRules rules = world.getGameRules();
        return rules.hasRule(rule) ? rules.getInt(rule) : def;
    }

    // ------------------------------------------------------------------------------------------
    // P12: explosion drop decay. 1.19.3's Level.explode picks the decay rule by ExplosionInteraction: TNT for primed
    // TNT and TNT minecarts, BLOCK for beds, respawn anchors and end crystals (no entity, or a crystal), MOB for
    // everything else (creepers, ghast fireballs, withers, wither skulls). Without decay every block destroyed drops.

    /** The rule this explosion's drops follow. */
    public static String decayRule(Explosion explosion) {
        Entity exploder;
        try {
            exploder = (Entity) EXPLODER.get(explosion);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
        if (exploder instanceof EntityTNTPrimed || exploder instanceof EntityMinecartTNT) {
            return TNT_DECAY;
        }
        if (exploder == null || exploder instanceof EntityEnderCrystal) {
            return BLOCK_DECAY;
        }
        return MOB_DECAY;
    }

    public static float dropChance(float chance, Explosion explosion) {
        if (!rulesOn()) {
            return chance;
        }
        String rule = decayRule(explosion);
        World world = worldOf(explosion);
        return world == null || bool(world, rule, !TNT_DECAY.equals(rule)) ? chance : 1.0F;
    }

    private static final Field WORLD = ObfuscationReflectionHelper.findField(Explosion.class, "field_77287_j");

    private static World worldOf(Explosion explosion) {
        try {
            return (World) WORLD.get(explosion);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------------------------------------
    // P13: snowAccumulationHeight. 1.19.3's ServerLevel.tickChunk: while it snows, an empty spot gets a layer (rule > 0),
    // and a snow layer under the rule's height (at most 8) one more.

    /** In place of {@code world.canSnowAt(pos, checkLight)}: true only where 1.12.2 is to place its one layer. */
    public static boolean snowAt(World world, BlockPos pos, boolean checkLight) {
        if (!rulesOn()) {
            return world.canSnowAt(pos, checkLight);
        }
        int height = integer(world, SNOW_HEIGHT, 1);
        if (height <= 0) {
            return false;
        }
        IBlockState state = world.getBlockState(pos);
        if (height > 1 && state.getBlock() == Blocks.SNOW_LAYER && snowCould(world, pos)) {
            int layers = state.getValue(BlockSnow.LAYERS);
            if (layers < Math.min(height, 8)) {
                world.setBlockState(pos, state.withProperty(BlockSnow.LAYERS, layers + 1));
            }
            return false;
        }
        return world.canSnowAt(pos, checkLight);
    }

    /** 1.12.2's canSnowAtBody for a position that already holds snow: cold enough, in the world, dark enough. */
    private static boolean snowCould(World world, BlockPos pos) {
        return world.getBiome(pos).getTemperature(pos) < 0.15F && pos.getY() >= 0 && pos.getY() < 256
                && world.getLightFor(EnumSkyBlock.BLOCK, pos) < 10;
    }

    // ------------------------------------------------------------------------------------------
    // P14: globalSoundEvents. 1.19.3's ServerLevel.globalLevelEvent: with the rule off, the wither's spawn, the dragon's
    // death and the end portal opening are heard as any other level event, near the place only.

    public static void broadcastSound(IWorldEventListener listener, int id, BlockPos pos, int data, World world) {
        if (!rulesOn() || bool(world, GLOBAL_SOUNDS, true)) {
            listener.broadcastSound(id, pos, data);
        } else {
            listener.playEvent(null, id, pos, data);
        }
    }

    // ------------------------------------------------------------------------------------------

    /** P15: doVinesSpread. A roll that is never 0 is a vine that never spreads (1.19.4's VineBlock.randomTick). */
    public static int vineRoll(int roll, World world) {
        return !rulesOn() || bool(world, VINES, true) ? roll : 1;
    }

    /** P16: commandModificationBlockLimit (1.19.4), for /fill and /clone, on the running server. */
    public static int blockLimit() {
        return blockLimit(FMLCommonHandler.instance().getMinecraftServerInstance());
    }

    /** commandModificationBlockLimit on {@code server}, or 1.12.2's 32768. */
    public static int blockLimit(MinecraftServer server) {
        if (!rulesOn() || server == null || server.worlds.length == 0) {
            return 32768;
        }
        return integer(server.worlds[0], BLOCK_LIMIT, 32768);
    }

    // ------------------------------------------------------------------------------------------
    // P17: empty spawners (1.19.3, 22w44a: "Monster spawners no longer have a pig in them and flame particles by
    // default"). 1.19.3's BaseSpawner starts with an empty SpawnData, and a spawner with no entity neither spins, nor
    // flames, nor spawns (isNearPlayer is asked only with an entity).

    /** The first spawn data of every spawner: 1.12.2's pig, or nothing. */
    public static WeightedSpawnerEntity newSpawnData(WeightedSpawnerEntity pig) {
        return ChangesHooks.on(ModConfig.changes.emptySpawners) ? new WeightedSpawnerEntity(1, new NBTTagCompound()) : pig;
    }

    public static boolean isEmpty(MobSpawnerBaseLogic logic) {
        try {
            WeightedSpawnerEntity data = (WeightedSpawnerEntity) SPAWN_DATA.get(logic);
            return data == null || !data.getNbt().hasKey("id", 8) || data.getNbt().getString("id").isEmpty();
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    public static boolean spawnerActive(boolean active, MobSpawnerBaseLogic logic) {
        return active && !isEmpty(logic);
    }

    /** Client: the mob drawn inside, none for an empty spawner (1.12.2 would log a warning every frame). */
    public static Entity spawnerEntity(NBTTagCompound tag, World world, boolean spawn) {
        return tag.hasKey("id", 8) && !tag.getString("id").isEmpty() ? AnvilChunkLoader.readWorldEntity(tag, world, spawn) : null;
    }

    // ------------------------------------------------------------------------------------------
    // P18: armor stand names (1.19.4, 23w03a: "Armor stands now preserve custom names when placed and broken").
    // 1.19.4's ArmorStandItem.useOn names the stand from the item (EntityType.createDefaultStackConfig), hidden as any
    // custom name until CustomNameVisible; ArmorStand.brokenByPlayer names the item from the stand.

    public static void armorStandData(World world, EntityPlayer player, ItemStack stack, Entity entity) {
        ItemMonsterPlacer.applyItemEntityDataToEntity(world, player, stack, entity);
        if (ChangesHooks.on(ModConfig.changes.armorStandNames) && stack.hasDisplayName()) {
            entity.setCustomNameTag(stack.getDisplayName());
        }
    }

    public static ItemStack armorStandItem(ItemStack stack, EntityArmorStand stand) {
        if (ChangesHooks.on(ModConfig.changes.armorStandNames) && stand.hasCustomName()) {
            stack.setStackDisplayName(stand.getCustomNameTag());
        }
        return stack;
    }
}
