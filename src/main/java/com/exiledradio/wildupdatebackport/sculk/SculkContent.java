package com.exiledradio.wildupdatebackport.sculk;

import com.exiledradio.wildupdatebackport.ModConfig;
import com.exiledradio.wildupdatebackport.WildUpdateBackport;
import com.exiledradio.wildupdatebackport.common.Content;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemHoe;
import net.minecraft.item.ItemStack;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.registries.IForgeRegistry;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Sculk, the sculk vein, the sculk catalyst, the sculk sensor and the sculk shrieker (1.19's numbers throughout;
 * see each block's class), their sounds, and the rules around them that live outside the blocks:
 *
 * <ul>
 *   <li>the {@code doWardenSpawning} game rule (default true), added to every world that lacks it;</li>
 *   <li>each player's warden warning level ({@link WardenSpawnTracker}), ticked on the server;</li>
 *   <li>hoes mine all five faster, as 1.19's {@code #mineable/hoe} (1.12.2's hoes are not tools, so the hoe's
 *       material efficiency, plus Efficiency, is applied through {@code BreakSpeed});</li>
 *   <li>sneaking players still step on sensors and shriekers: 1.19's {@code stepOn} runs for them, 1.12.2's
 *       {@code onEntityWalk} does not.</li>
 * </ul>
 * The warden's four reply sounds are registered here, because the shrieker plays them with or without stream W.
 */
@Mod.EventBusSubscriber(modid = WildUpdateBackport.MOD_ID)
public final class SculkContent {

    /** 1.19's {@code Material.SCULK}: solid, no tool needed, black on maps. */
    public static final Material SCULK_MATERIAL = new Material(MapColor.BLACK);

    public static final String DO_WARDEN_SPAWNING = "doWardenSpawning";

    public static final SoundEvent SCULK_BREAK = sound("block.sculk.break");
    public static final SoundEvent SCULK_STEP = sound("block.sculk.step");
    public static final SoundEvent SCULK_PLACE = sound("block.sculk.place");
    public static final SoundEvent SCULK_HIT = sound("block.sculk.hit");
    public static final SoundEvent SCULK_FALL = sound("block.sculk.fall");
    public static final SoundEvent SCULK_CHARGE = sound("block.sculk.charge");
    public static final SoundEvent SCULK_SPREAD = sound("block.sculk.spread");
    public static final SoundEvent VEIN_BREAK = sound("block.sculk_vein.break");
    public static final SoundEvent VEIN_STEP = sound("block.sculk_vein.step");
    public static final SoundEvent VEIN_PLACE = sound("block.sculk_vein.place");
    public static final SoundEvent VEIN_HIT = sound("block.sculk_vein.hit");
    public static final SoundEvent VEIN_FALL = sound("block.sculk_vein.fall");
    public static final SoundEvent CATALYST_BREAK = sound("block.sculk_catalyst.break");
    public static final SoundEvent CATALYST_STEP = sound("block.sculk_catalyst.step");
    public static final SoundEvent CATALYST_PLACE = sound("block.sculk_catalyst.place");
    public static final SoundEvent CATALYST_HIT = sound("block.sculk_catalyst.hit");
    public static final SoundEvent CATALYST_FALL = sound("block.sculk_catalyst.fall");
    public static final SoundEvent CATALYST_BLOOM = sound("block.sculk_catalyst.bloom");
    public static final SoundEvent SENSOR_BREAK = sound("block.sculk_sensor.break");
    public static final SoundEvent SENSOR_STEP = sound("block.sculk_sensor.step");
    public static final SoundEvent SENSOR_PLACE = sound("block.sculk_sensor.place");
    public static final SoundEvent SENSOR_HIT = sound("block.sculk_sensor.hit");
    public static final SoundEvent SENSOR_FALL = sound("block.sculk_sensor.fall");
    public static final SoundEvent SENSOR_CLICKING = sound("block.sculk_sensor.clicking");
    public static final SoundEvent SENSOR_CLICKING_STOP = sound("block.sculk_sensor.clicking_stop");
    public static final SoundEvent SHRIEKER_BREAK = sound("block.sculk_shrieker.break");
    public static final SoundEvent SHRIEKER_STEP = sound("block.sculk_shrieker.step");
    public static final SoundEvent SHRIEKER_PLACE = sound("block.sculk_shrieker.place");
    public static final SoundEvent SHRIEKER_HIT = sound("block.sculk_shrieker.hit");
    public static final SoundEvent SHRIEKER_FALL = sound("block.sculk_shrieker.fall");
    public static final SoundEvent SHRIEKER_SHRIEK = sound("block.sculk_shrieker.shriek");
    public static final SoundEvent WARDEN_NEARBY_CLOSE = sound("entity.warden.nearby_close");
    public static final SoundEvent WARDEN_NEARBY_CLOSER = sound("entity.warden.nearby_closer");
    public static final SoundEvent WARDEN_NEARBY_CLOSEST = sound("entity.warden.nearby_closest");
    public static final SoundEvent WARDEN_LISTENING_ANGRY = sound("entity.warden.listening_angry");

    /** 1.19's sound types: volume 1, pitch 1. */
    public static final SoundType SCULK_SOUND = new SoundType(1.0F, 1.0F, SCULK_BREAK, SCULK_STEP, SCULK_PLACE, SCULK_HIT, SCULK_FALL);
    public static final SoundType VEIN_SOUND = new SoundType(1.0F, 1.0F, VEIN_BREAK, VEIN_STEP, VEIN_PLACE, VEIN_HIT, VEIN_FALL);
    public static final SoundType CATALYST_SOUND = new SoundType(1.0F, 1.0F, CATALYST_BREAK, CATALYST_STEP, CATALYST_PLACE, CATALYST_HIT,
            CATALYST_FALL);
    public static final SoundType SENSOR_SOUND = new SoundType(1.0F, 1.0F, SENSOR_BREAK, SENSOR_STEP, SENSOR_PLACE, SENSOR_HIT, SENSOR_FALL);
    public static final SoundType SHRIEKER_SOUND = new SoundType(1.0F, 1.0F, SHRIEKER_BREAK, SHRIEKER_STEP, SHRIEKER_PLACE, SHRIEKER_HIT,
            SHRIEKER_FALL);

    private static BlockSculk sculk;
    private static BlockSculkVein vein;
    private static BlockSculkCatalyst catalyst;
    private static BlockSculkSensor sensor;
    private static BlockSculkShrieker shrieker;

    private SculkContent() {
    }

    private static SoundEvent sound(String name) {
        SoundEvent event = new SoundEvent(Content.id(name));
        event.setRegistryName(Content.id(name));
        return event;
    }

    public static boolean enabled() {
        return ModConfig.sculk.enabled;
    }

    public static BlockSculk sculkBlock() {
        return sculk;
    }

    public static BlockSculkVein veinBlock() {
        return vein;
    }

    public static BlockSculkCatalyst catalystBlock() {
        return catalyst;
    }

    public static BlockSculkSensor sensorBlock() {
        return sensor;
    }

    public static BlockSculkShrieker shriekerBlock() {
        return shrieker;
    }

    /** @return the five blocks, for the tests and the hoe (empty when sculk is off) */
    public static List<Block> blocks() {
        return sculk == null ? Collections.<Block>emptyList() : Arrays.<Block>asList(sculk, vein, catalyst, sensor, shrieker);
    }

    /** 1.19's {@code SOUND_BY_LEVEL}: the warden answering a shriek, by warning level. */
    @Nullable
    public static SoundEvent wardenReply(int level) {
        switch (level) {
            case 1:
                return WARDEN_NEARBY_CLOSE;
            case 2:
                return WARDEN_NEARBY_CLOSER;
            case 3:
                return WARDEN_NEARBY_CLOSEST;
            case 4:
                return WARDEN_LISTENING_ANGRY;
            default:
                return null;
        }
    }

    // --- registration ------------------------------------------------------------------------

    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        if (!enabled()) {
            WildUpdateBackport.LOGGER.info("Sculk is off (sculk.enabled).");
            return;
        }
        IForgeRegistry<Block> r = event.getRegistry();
        sculk = Content.block(r, "sculk", new BlockSculk(), null);
        vein = Content.block(r, "sculk_vein", new BlockSculkVein(), null);
        catalyst = Content.block(r, "sculk_catalyst", new BlockSculkCatalyst(), null);
        sensor = Content.block(r, "sculk_sensor", new BlockSculkSensor(), null);
        shrieker = Content.block(r, "sculk_shrieker", new BlockSculkShrieker(), null);
        Content.multipart(vein);
        Content.ignore(shrieker, BlockSculkShrieker.SHRIEKING);
        GameRegistry.registerTileEntity(TileSculkVein.class, Content.id("sculk_vein"));
        GameRegistry.registerTileEntity(TileSculkCatalyst.class, Content.id("sculk_catalyst"));
        GameRegistry.registerTileEntity(TileSculkSensor.class, Content.id("sculk_sensor"));
        GameRegistry.registerTileEntity(TileSculkShrieker.class, Content.id("sculk_shrieker"));
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        if (!enabled()) {
            return;
        }
        IForgeRegistry<Item> r = event.getRegistry();
        Content.registerBlockItem(r, sculk);
        Content.blockItem(r, vein, new ItemSculkVein(vein));
        Content.registerBlockItem(r, catalyst);
        Content.registerBlockItem(r, sensor);
        Content.registerBlockItem(r, shrieker);
    }

    @SubscribeEvent
    public static void registerSounds(RegistryEvent.Register<SoundEvent> event) {
        if (!enabled()) {
            return;
        }
        event.getRegistry().registerAll(SCULK_BREAK, SCULK_STEP, SCULK_PLACE, SCULK_HIT, SCULK_FALL, SCULK_CHARGE, SCULK_SPREAD,
                VEIN_BREAK, VEIN_STEP, VEIN_PLACE, VEIN_HIT, VEIN_FALL, CATALYST_BREAK, CATALYST_STEP, CATALYST_PLACE, CATALYST_HIT,
                CATALYST_FALL, CATALYST_BLOOM, SENSOR_BREAK, SENSOR_STEP, SENSOR_PLACE, SENSOR_HIT, SENSOR_FALL, SENSOR_CLICKING,
                SENSOR_CLICKING_STOP, SHRIEKER_BREAK, SHRIEKER_STEP, SHRIEKER_PLACE, SHRIEKER_HIT, SHRIEKER_FALL, SHRIEKER_SHRIEK,
                WARDEN_NEARBY_CLOSE, WARDEN_NEARBY_CLOSER, WARDEN_NEARBY_CLOSEST, WARDEN_LISTENING_ANGRY);
    }

    // --- rules outside the blocks ----------------------------------------------------------------

    /** 1.19's {@code doWardenSpawning}, for every world that does not have it yet. */
    @SubscribeEvent
    public static void worldLoad(WorldEvent.Load event) {
        if (!enabled() || event.getWorld().isRemote) {
            return;
        }
        GameRules rules = event.getWorld().getGameRules();
        if (!rules.hasRule(DO_WARDEN_SPAWNING)) {
            rules.addGameRule(DO_WARDEN_SPAWNING, "true", GameRules.ValueType.BOOLEAN_VALUE);
        }
    }

    @SubscribeEvent
    public static void playerTick(TickEvent.PlayerTickEvent event) {
        if (!enabled() || event.phase != TickEvent.Phase.END || event.player.world.isRemote) {
            return;
        }
        EntityPlayer player = event.player;
        WardenSpawnTracker.tick(player);
        if (player.onGround && player.isSneaking() && !player.isRiding()) {
            World world = player.world;
            BlockPos below = new BlockPos(MathHelper.floor(player.posX), MathHelper.floor(player.posY - 0.2D), MathHelper.floor(player.posZ));
            Block block = world.getBlockState(below).getBlock();
            if (block == sensor) {
                BlockSculkSensor.stepOn(world, below, player);
            } else if (block == shrieker) {
                BlockSculkShrieker.stepOn(world, below, player);
            }
        }
    }

    /** Hoes mine sculk blocks at their material's speed (1.19's {@code #mineable/hoe}), Efficiency included. */
    @SubscribeEvent
    public static void breakSpeed(PlayerEvent.BreakSpeed event) {
        if (!enabled() || !blocks().contains(event.getState().getBlock())) {
            return;
        }
        ItemStack held = event.getEntityPlayer().getHeldItemMainhand();
        if (!(held.getItem() instanceof ItemHoe)) {
            return;
        }
        float speed;
        try {
            speed = Item.ToolMaterial.valueOf(((ItemHoe) held.getItem()).getMaterialName()).getEfficiency();
        } catch (IllegalArgumentException e) {
            return;
        }
        int efficiency = EnchantmentHelper.getEfficiencyModifier(event.getEntityPlayer());
        if (efficiency > 0) {
            speed += efficiency * efficiency + 1;
        }
        // Everything else 1.12.2 applied (haste, water, air) multiplied the hand's speed of 1.
        event.setNewSpeed(event.getNewSpeed() * speed);
    }
}
