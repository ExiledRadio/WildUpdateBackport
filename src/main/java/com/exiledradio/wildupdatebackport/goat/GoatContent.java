package com.exiledradio.wildupdatebackport.goat;

import com.exiledradio.wildupdatebackport.ModConfig;
import com.exiledradio.wildupdatebackport.WildUpdateBackport;
import com.exiledradio.wildupdatebackport.common.Content;
import com.exiledradio.wildupdatebackport.common.Owners;

import net.minecraft.advancements.Advancement;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.EntityEntryBuilder;
import net.minecraftforge.fml.common.registry.EntityRegistry;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * Stream Gt: Caves &amp; Cliffs' goat, copied for games without Caves &amp; Cliffs.
 *
 * <p>Caves &amp; Cliffs owns goats (family rule 1). Wild Update needs them for goat horns, and every family mod
 * works on its own, so without Caves &amp; Cliffs this registers an identical goat: the owner's code, names
 * ({@code goat}, {@code entity.goat.*}), saved tags ({@code IsScreamingGoat}, {@code HasLeftHorn},
 * {@code HasRightHorn}, the two {@code Brain} cooldowns), size, sounds, texture, spawning and settings, under this
 * mod's id. Caves &amp; Cliffs added to such a world later remaps {@code wildupdatebackport:goat} onto its own goat
 * by name (its {@code GoatRemap}), and the saved tags read the same, so every goat carries over as it is.
 *
 * <p>The copy adds only what 1.19 added to the goat: a ram that hits a {@code #snaps_goat_horn} block breaks off a
 * horn ({@link EntityAIGoatRam}, through {@code items.GoatHorns}). With Caves &amp; Cliffs installed nothing here is
 * registered, and {@code items.GoatHorns} gives the owner's goats the same rule from outside.
 */
@Mod.EventBusSubscriber(modid = WildUpdateBackport.MOD_ID)
public final class GoatContent {

    /** Entity id 25 of this mod (plan: goats 25-29). */
    public static final int GOAT_ENTITY_ID = 25;

    /** Granted in code: a player and a goat in the same boat (1.17's, kept with the copy). */
    public static final String BOAT_ADVANCEMENT = "husbandry/ride_a_boat_with_a_goat";

    /** Sounds that have a screaming version: index 0 the normal goat's, 1 the screaming goat's. */
    static final SoundEvent[] AMBIENT = new SoundEvent[2], DEATH = new SoundEvent[2], EAT = new SoundEvent[2],
            HURT = new SoundEvent[2], LONG_JUMP = new SoundEvent[2], MILK = new SoundEvent[2],
            PREPARE_RAM = new SoundEvent[2], RAM_IMPACT = new SoundEvent[2];
    static final SoundEvent STEP = sound("entity.goat.step");

    private static final List<SoundEvent> SOUNDS = new ArrayList<SoundEvent>();

    static {
        pair(AMBIENT, "ambient");
        pair(DEATH, "death");
        pair(EAT, "eat");
        pair(HURT, "hurt");
        pair(LONG_JUMP, "long_jump");
        pair(MILK, "milk");
        pair(PREPARE_RAM, "prepare_ram");
        pair(RAM_IMPACT, "ram_impact");
        SOUNDS.add(STEP);
    }

    private static boolean registered;

    private GoatContent() {
    }

    private static SoundEvent sound(String name) {
        SoundEvent event = new SoundEvent(Content.id(name));
        event.setRegistryName(Content.id(name));
        return event;
    }

    private static void pair(SoundEvent[] into, String name) {
        into[0] = sound("entity.goat." + name);
        into[1] = sound("entity.goat.screaming." + name);
        SOUNDS.add(into[0]);
        SOUNDS.add(into[1]);
    }

    /**
     * @return true when this mod registers its goat: goats switched on and Caves &amp; Cliffs, the owner, not
     * installed. Read from the registry events on.
     */
    public static boolean enabled() {
        return ModConfig.goats.enabled && !Owners.present(Owners.CAVES_AND_CLIFFS);
    }

    /** @return true once the goat is registered in this game */
    public static boolean on() {
        return registered;
    }

    /** @return the ram impact sound of our goat (screaming or not), or null when it is not registered */
    public static SoundEvent ramImpact(boolean screaming) {
        return registered ? RAM_IMPACT[screaming ? 1 : 0] : null;
    }

    @SubscribeEvent
    public static void registerSounds(RegistryEvent.Register<SoundEvent> event) {
        if (!enabled()) {
            return;
        }
        event.getRegistry().registerAll(SOUNDS.toArray(new SoundEvent[0]));
    }

    @SubscribeEvent
    public static void registerEntities(RegistryEvent.Register<EntityEntry> event) {
        if (!enabled()) {
            return;
        }
        event.getRegistry().register(EntityEntryBuilder.create()
                .entity(EntityGoat.class)
                .id(Content.id("goat"), GOAT_ENTITY_ID)
                .name(WildUpdateBackport.MOD_ID + ".goat")
                .tracker(80, 3, true)
                .egg(0xA59B8E, 0x55493E)
                .build());
        registered = true;
        WildUpdateBackport.LOGGER.info("Caves & Cliffs is not installed: registered a goat of our own.");
    }

    /** From the mod's init, after every registry: natural spawning. */
    public static void init() {
        if (!on() || ModConfig.goats.spawnWeight <= 0) {
            return;
        }
        List<Biome> biomes = new ArrayList<Biome>();
        for (String name : ModConfig.goats.spawnBiomes) {
            ResourceLocation id = new ResourceLocation(name.trim());
            Biome biome = ForgeRegistries.BIOMES.getValue(id);
            if (biome != null && id.equals(biome.getRegistryName())) {
                biomes.add(biome);
            } else {
                WildUpdateBackport.LOGGER.warn("Goats: no biome named {}; skipped.", name);
            }
        }
        if (!biomes.isEmpty()) {
            // 1.18's group size (1-3). Its weight of 5 is out of a list of 9; here goats join the hills' sheep,
            // pigs, chickens, cows and llamas, so the weight is a setting.
            EntityRegistry.addSpawn(EntityGoat.class, ModConfig.goats.spawnWeight, 1, 3, EnumCreatureType.CREATURE,
                    biomes.toArray(new Biome[0]));
        }
        WildUpdateBackport.LOGGER.info("Goats spawn in {} biomes.", biomes.size());
    }

    /** Grants a code-triggered advancement of this mod ({@code code} criterion). */
    static void grant(EntityPlayer player, String path) {
        if (!(player instanceof EntityPlayerMP) || player instanceof FakePlayer) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        Advancement adv = server.getAdvancementManager().getAdvancement(Content.id(path));
        if (adv != null) {
            ((EntityPlayerMP) player).getAdvancements().grantCriterion(adv, "code");
        }
    }
}
