package com.exiledradio.wildupdatebackport.allay;

import com.exiledradio.wildupdatebackport.ModConfig;
import com.exiledradio.wildupdatebackport.WildUpdateBackport;
import com.exiledradio.wildupdatebackport.common.Content;
import com.exiledradio.wildupdatebackport.common.Owners;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.EntityEntryBuilder;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.oredict.OreDictionary;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * The allay: entity {@code wildupdatebackport:allay} (id 10, tracked as 1.19's: 128 blocks, every 2 ticks),
 * its spawn egg (1.19's colors 0x00DAFF and 0x00ADFF), sounds, advancements and the structures it lives in
 * ({@link AllayStructures}).
 *
 * <h2>Duplication</h2>
 * 1.19.1: a dancing allay given an amethyst shard duplicates. The shard is Caves &amp; Cliffs' (by name);
 * without it, another mod's {@code gemAmethyst}; with neither, allays do not duplicate. The chime is
 * Caves &amp; Cliffs' {@code block.amethyst_block.chime}, or our identical copy without it.
 */
@Mod.EventBusSubscriber(modid = WildUpdateBackport.MOD_ID)
public final class AllayContent {

    /** Where a thrown item remembers the allay that threw it (1.19's {@code ItemEntity.thrower}). */
    static final String THROWER_TAG = WildUpdateBackport.MOD_ID + ":thrower";

    public static final SoundEvent AMBIENT_WITH_ITEM = sound("entity.allay.ambient_with_item");
    public static final SoundEvent AMBIENT_WITHOUT_ITEM = sound("entity.allay.ambient_without_item");
    public static final SoundEvent DEATH = sound("entity.allay.death");
    public static final SoundEvent HURT = sound("entity.allay.hurt");
    public static final SoundEvent ITEM_GIVEN = sound("entity.allay.item_given");
    public static final SoundEvent ITEM_TAKEN = sound("entity.allay.item_taken");
    public static final SoundEvent ITEM_THROWN = sound("entity.allay.item_thrown");
    /** Ours, registered only when Caves &amp; Cliffs (which owns it) is absent. */
    public static final SoundEvent OWN_CHIME = sound("block.amethyst_block.chime");

    private AllayContent() {
    }

    public static boolean enabled() {
        return ModConfig.allay.enabled;
    }

    private static SoundEvent sound(String name) {
        SoundEvent event = new SoundEvent(Content.id(name));
        event.setRegistryName(Content.id(name));
        return event;
    }

    /** From the mod's pre-init. */
    public static void preInit() {
        if (enabled()) {
            AllayTriggers.register();
        }
    }

    /** From the mod's init. */
    public static void init() {
        if (enabled()) {
            AllayStructures.init();
        }
    }

    @SubscribeEvent
    public static void registerEntities(RegistryEvent.Register<EntityEntry> event) {
        if (!enabled()) {
            WildUpdateBackport.LOGGER.info("Allays are off (allay.enabled).");
            return;
        }
        event.getRegistry().register(EntityEntryBuilder.create().entity(EntityAllay.class).id(Content.id("allay"), 10)
                .name(WildUpdateBackport.MOD_ID + ".allay").tracker(128, 2, true).egg(0x00DAFF, 0x00ADFF).build());
    }

    @SubscribeEvent
    public static void registerSounds(RegistryEvent.Register<SoundEvent> event) {
        if (!enabled()) {
            return;
        }
        event.getRegistry().registerAll(AMBIENT_WITH_ITEM, AMBIENT_WITHOUT_ITEM, DEATH, HURT, ITEM_GIVEN, ITEM_TAKEN, ITEM_THROWN);
        if (!Owners.present(Owners.CAVES_AND_CLIFFS)) {
            event.getRegistry().register(OWN_CHIME);
        }
    }

    /** @return the amethyst chime: Caves &amp; Cliffs', else ours */
    @Nullable
    static SoundEvent chime() {
        SoundEvent theirs = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation(Owners.CAVES_AND_CLIFFS, "block.amethyst_block.chime"));
        if (theirs != null) {
            return theirs;
        }
        return ForgeRegistries.SOUND_EVENTS.containsValue(OWN_CHIME) ? OWN_CHIME : null;
    }

    /** 1.19's {@code #duplicates_allays}: the amethyst shard. */
    public static boolean isDuplicationItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        Item shard = Owners.item(Owners.CAVES_AND_CLIFFS, "amethyst_shard");
        if (shard != null) {
            return stack.getItem() == shard;
        }
        for (int id : OreDictionary.getOreIDs(stack)) {
            if ("gemAmethyst".equals(OreDictionary.getOreName(id))) {
                return true;
            }
        }
        return false;
    }

    /** <i>You've Got a Friend in Me</i>: a player picks up an item an allay threw. */
    @SubscribeEvent
    public static void onPickup(PlayerEvent.ItemPickupEvent event) {
        EntityItem item = event.getOriginalEntity();
        if (!enabled() || item == null || !(event.player instanceof EntityPlayerMP)
                || !item.getEntityData().hasUniqueId(THROWER_TAG)) {
            return;
        }
        UUID id = item.getEntityData().getUniqueId(THROWER_TAG);
        Entity thrower = event.player.world instanceof WorldServer ? ((WorldServer) event.player.world).getEntityFromUuid(id) : null;
        AllayTriggers.THROWN_ITEM_PICKED_UP.trigger((EntityPlayerMP) event.player, event.getStack(), thrower);
    }
}
