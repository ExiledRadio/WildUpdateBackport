package com.exiledradio.wildupdatebackport.gameevent;

import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A game event: something that happened in the world that listeners near it may hear.
 *
 * <p>1.19.2's 46 events with their notification radius (16 blocks; {@code shriek} 32, the jukebox's two 10),
 * and each one's vibration frequency (1-15) as current Java gives it, for comparators and calibrated
 * sensors; 0 for events that are not vibrations. Events 1.19.2 had that current Java folded into others
 * keep their 1.19.2 names with the frequency of the event they became (piston extend = block activate,
 * piston contract = block deactivate, entity roar and shake = entity action).
 *
 * <p>This is the API asked of Backport Core ({@code docs/CORE_REQUESTS.md} request 1); once Core has it,
 * this package is replaced by Core's.
 */
public final class GameEvent {

    private static final Map<ResourceLocation, GameEvent> BY_NAME = new LinkedHashMap<ResourceLocation, GameEvent>();

    public static final GameEvent BLOCK_ACTIVATE = vanilla("block_activate", 10);
    public static final GameEvent BLOCK_ATTACH = vanilla("block_attach", 10);
    public static final GameEvent BLOCK_CHANGE = vanilla("block_change", 11);
    public static final GameEvent BLOCK_CLOSE = vanilla("block_close", 9);
    public static final GameEvent BLOCK_DEACTIVATE = vanilla("block_deactivate", 9);
    public static final GameEvent BLOCK_DESTROY = vanilla("block_destroy", 12);
    public static final GameEvent BLOCK_DETACH = vanilla("block_detach", 9);
    public static final GameEvent BLOCK_OPEN = vanilla("block_open", 10);
    public static final GameEvent BLOCK_PLACE = vanilla("block_place", 13);
    public static final GameEvent CONTAINER_CLOSE = vanilla("container_close", 9);
    public static final GameEvent CONTAINER_OPEN = vanilla("container_open", 10);
    public static final GameEvent DISPENSE_FAIL = vanilla("dispense_fail", 10);
    public static final GameEvent DRINK = vanilla("drink", 8);
    public static final GameEvent EAT = vanilla("eat", 8);
    public static final GameEvent ELYTRA_GLIDE = vanilla("elytra_glide", 4);
    public static final GameEvent ENTITY_DAMAGE = vanilla("entity_damage", 7);
    public static final GameEvent ENTITY_DIE = vanilla("entity_die", 15);
    public static final GameEvent ENTITY_INTERACT = vanilla("entity_interact", 6);
    public static final GameEvent ENTITY_PLACE = vanilla("entity_place", 14);
    public static final GameEvent ENTITY_ROAR = vanilla("entity_roar", 4);
    public static final GameEvent ENTITY_SHAKE = vanilla("entity_shake", 4);
    public static final GameEvent EQUIP = vanilla("equip", 5);
    public static final GameEvent EXPLODE = vanilla("explode", 15);
    public static final GameEvent FLAP = vanilla("flap", 1);
    public static final GameEvent FLUID_PICKUP = vanilla("fluid_pickup", 12);
    public static final GameEvent FLUID_PLACE = vanilla("fluid_place", 13);
    public static final GameEvent HIT_GROUND = vanilla("hit_ground", 2);
    public static final GameEvent INSTRUMENT_PLAY = vanilla("instrument_play", 3);
    public static final GameEvent ITEM_INTERACT_FINISH = vanilla("item_interact_finish", 3);
    public static final GameEvent ITEM_INTERACT_START = vanilla("item_interact_start", 0);
    public static final GameEvent JUKEBOX_PLAY = register(new ResourceLocation("jukebox_play"), 10, 0);
    public static final GameEvent JUKEBOX_STOP_PLAY = register(new ResourceLocation("jukebox_stop_play"), 10, 0);
    public static final GameEvent LIGHTNING_STRIKE = vanilla("lightning_strike", 14);
    public static final GameEvent NOTE_BLOCK_PLAY = vanilla("note_block_play", 10);
    public static final GameEvent PISTON_CONTRACT = vanilla("piston_contract", 9);
    public static final GameEvent PISTON_EXTEND = vanilla("piston_extend", 10);
    public static final GameEvent PRIME_FUSE = vanilla("prime_fuse", 10);
    public static final GameEvent PROJECTILE_LAND = vanilla("projectile_land", 2);
    public static final GameEvent PROJECTILE_SHOOT = vanilla("projectile_shoot", 3);
    public static final GameEvent SCULK_SENSOR_TENDRILS_CLICKING = vanilla("sculk_sensor_tendrils_clicking", 0);
    public static final GameEvent SHEAR = vanilla("shear", 6);
    public static final GameEvent SHRIEK = register(new ResourceLocation("shriek"), 32, 0);
    public static final GameEvent SPLASH = vanilla("splash", 2);
    public static final GameEvent STEP = vanilla("step", 1);
    public static final GameEvent SWIM = vanilla("swim", 1);
    public static final GameEvent TELEPORT = vanilla("teleport", 14);

    private final ResourceLocation name;
    private final int radius;
    private final int frequency;

    private GameEvent(ResourceLocation name, int radius, int frequency) {
        this.name = name;
        this.radius = radius;
        this.frequency = frequency;
    }

    private static GameEvent vanilla(String name, int frequency) {
        return register(new ResourceLocation(name), 16, frequency);
    }

    /** Registers an event; another mod's events go under its own namespace. */
    public static synchronized GameEvent register(ResourceLocation name, int radius, int frequency) {
        GameEvent event = new GameEvent(name, radius, frequency);
        BY_NAME.put(name, event);
        return event;
    }

    @Nullable
    public static GameEvent get(ResourceLocation name) {
        return BY_NAME.get(name);
    }

    public static Collection<GameEvent> all() {
        return Collections.unmodifiableCollection(BY_NAME.values());
    }

    public ResourceLocation name() {
        return this.name;
    }

    public int radius() {
        return this.radius;
    }

    public int frequency() {
        return this.frequency;
    }

    @Override
    public String toString() {
        return "GameEvent{" + this.name + "}";
    }
}
