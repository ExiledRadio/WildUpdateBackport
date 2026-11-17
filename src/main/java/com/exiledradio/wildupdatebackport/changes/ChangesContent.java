package com.exiledradio.wildupdatebackport.changes;

import com.exiledradio.backportcore.creative.CreativePlacement;
import com.exiledradio.wildupdatebackport.ModConfig;
import com.exiledradio.wildupdatebackport.WildUpdateBackport;
import net.minecraft.entity.EntityList;
import net.minecraft.potion.Potion;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

/**
 * Stream C, "Other 1.19.x changes": smaller changes 1.19 to 1.19.4 made to things 1.12.2 already has. Each sits behind
 * its own switch in {@code ModConfig.changes}. The rest of the stream is in {@link ItemChanges}, {@link MobChanges},
 * {@link Jukeboxes}, {@link ShulkerBoxes}, the hooks of patches P4 to P7 ({@link ChangesHooks}) and, on the client,
 * the door models ({@code client.DoorModels}).
 */
public final class ChangesContent {

    /** 1.19.3's two spawn eggs: entity, colors (from 1.19.4's {@code Items}), and the vanilla item they stand for. */
    static final Object[][] GOLEM_EGGS = {
            {"villager_golem", 14405058, 7643954, "minecraft:iron_golem_spawn_egg"},
            {"snowman", 14283506, 8496292, "minecraft:snow_golem_spawn_egg"},
    };

    /** 1.19.4's new effect colors (its {@code MobEffects}), by 1.12.2 registry name; Turtle Master follows its two. */
    static final Object[][] POTION_COLORS = {
            {"speed", 3402751}, {"slowness", 9154528}, {"strength", 16762624}, {"instant_damage", 11101546},
            {"jump_boost", 16646020}, {"resistance", 9520880}, {"fire_resistance", 16750848}, {"water_breathing", 10017472},
            {"invisibility", 16185078}, {"night_vision", 12779366}, {"poison", 8889187}, {"luck", 5882118},
    };

    private ChangesContent() {
    }

    public static void preInit() {
        if (ChangesHooks.on(ModConfig.changes.golemSpawnEggs)) {
            for (Object[] egg : GOLEM_EGGS) {
                CreativePlacement.placeSpawnEgg(new ResourceLocation("minecraft", (String) egg[0]), (String) egg[3]);
            }
        }
    }

    public static void init() {
        if (ChangesHooks.on(ModConfig.changes.golemSpawnEggs)) {
            golemEggs();
        }
        if (ChangesHooks.on(ModConfig.changes.potionColors)) {
            potionColors();
        }
        RulesEvents.init();
    }

    static void golemEggs() {
        for (Object[] egg : GOLEM_EGGS) {
            ResourceLocation id = new ResourceLocation("minecraft", (String) egg[0]);
            EntityEntry entry = ForgeRegistries.ENTITIES.getValue(id);
            // Another mod may have given the golems eggs already; one is enough (a second would register its stats twice).
            if (entry == null || entry.getEgg() != null || EntityList.ENTITY_EGGS.containsKey(id)) {
                continue;
            }
            entry.setEgg(new EntityList.EntityEggInfo(id, (Integer) egg[1], (Integer) egg[2]));
        }
    }

    static void potionColors() {
        for (Object[] c : POTION_COLORS) {
            Potion potion = Potion.REGISTRY.getObject(new ResourceLocation("minecraft", (String) c[0]));
            if (potion == null) {
                continue;
            }
            try {
                ObfuscationReflectionHelper.setPrivateValue(Potion.class, potion, c[1], "field_76414_N");
            } catch (RuntimeException e) {
                WildUpdateBackport.LOGGER.warn("Could not set {}'s color; it keeps 1.12.2's.", c[0], e);
            }
        }
    }
}
