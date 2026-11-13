package com.exiledradio.wildupdatebackport.items;

import com.exiledradio.wildupdatebackport.ModConfig;
import com.exiledradio.wildupdatebackport.WildUpdateBackport;
import com.exiledradio.wildupdatebackport.common.Content;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.IMerchant;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.util.NonNullList;
import net.minecraft.util.SoundEvent;
import net.minecraft.world.storage.loot.functions.LootFunctionManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.registries.IForgeRegistry;

import javax.annotation.Nullable;
import java.util.Collections;

/**
 * Stream I: the recovery compass, echo shards, music disc 5 and its fragments, goat horns and Swift Sneak.
 *
 * <p>1.19.2's numbers: echo shards, fragments and compasses stack to 64; the disc and horns to 1; the disc is rare
 * (1.12.2's records are). Recipes: the recovery compass is a compass ringed by eight echo shards, disc 5 nine
 * fragments (both shaped, as 1.19's).
 */
@Mod.EventBusSubscriber(modid = WildUpdateBackport.MOD_ID)
public final class ItemsContent {

    public static final SoundEvent[] HORN_SOUNDS = new SoundEvent[Instrument.values().length];
    public static final SoundEvent HORN_BREAK = sound("entity.goat.horn_break");
    public static final SoundEvent MUSIC_DISC_5 = sound("music_disc.5");

    static {
        for (Instrument i : Instrument.values()) {
            HORN_SOUNDS[i.ordinal()] = sound(i.soundName());
        }
    }

    private static Item echoShard;
    private static Item recoveryCompass;
    private static ItemMusicDisc musicDisc5;
    private static Item discFragment;
    private static ItemGoatHorn goatHorn;
    private static EnchantmentSwiftSneak swiftSneak;

    private ItemsContent() {
    }

    private static SoundEvent sound(String name) {
        SoundEvent event = new SoundEvent(Content.id(name));
        event.setRegistryName(Content.id(name));
        return event;
    }

    public static boolean enabled() {
        return ModConfig.items.enabled;
    }

    @Nullable
    public static Item echoShard() {
        return echoShard;
    }

    @Nullable
    public static Item recoveryCompass() {
        return recoveryCompass;
    }

    @Nullable
    public static ItemMusicDisc musicDisc5() {
        return musicDisc5;
    }

    @Nullable
    public static Item discFragment() {
        return discFragment;
    }

    @Nullable
    public static ItemGoatHorn goatHorn() {
        return goatHorn;
    }

    @Nullable
    public static EnchantmentSwiftSneak swiftSneak() {
        return swiftSneak;
    }

    public static SoundEvent hornSound(Instrument instrument) {
        return HORN_SOUNDS[instrument.ordinal()];
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        if (!enabled()) {
            WildUpdateBackport.LOGGER.info("Items are off (items.enabled).");
            return;
        }
        IForgeRegistry<Item> r = event.getRegistry();
        echoShard = Content.item(r, "echo_shard", new Item());
        recoveryCompass = Content.item(r, "recovery_compass", new Item());
        musicDisc5 = Content.item(r, "music_disc_5", new ItemMusicDisc("5", MUSIC_DISC_5, 15));
        discFragment = Content.item(r, "disc_fragment_5", new ItemDescribed());
        goatHorn = Content.item(r, "goat_horn", new ItemGoatHorn());
    }

    @SubscribeEvent
    public static void registerEnchantments(RegistryEvent.Register<Enchantment> event) {
        if (!enabled()) {
            return;
        }
        swiftSneak = new EnchantmentSwiftSneak();
        swiftSneak.setRegistryName(Content.id("swift_sneak"));
        swiftSneak.setName(WildUpdateBackport.MOD_ID + ".swift_sneak");
        event.getRegistry().register(swiftSneak);
        Discovery.hide(swiftSneak);
    }

    @SubscribeEvent
    public static void registerSounds(RegistryEvent.Register<SoundEvent> event) {
        if (!enabled()) {
            return;
        }
        event.getRegistry().registerAll(HORN_SOUNDS);
        event.getRegistry().registerAll(HORN_BREAK, MUSIC_DISC_5);
    }

    @SubscribeEvent
    public static void registerRecipes(RegistryEvent.Register<IRecipe> event) {
        if (!enabled()) {
            return;
        }
        Ingredient s = Ingredient.fromItem(echoShard);
        Ingredient c = Ingredient.fromItem(Items.COMPASS);
        Ingredient f = Ingredient.fromItem(discFragment);
        shaped(event.getRegistry(), "recovery_compass", new ItemStack(recoveryCompass), s, s, s, s, c, s, s, s, s);
        shaped(event.getRegistry(), "music_disc_5", new ItemStack(musicDisc5), f, f, f, f, f, f, f, f, f);
    }

    private static void shaped(IForgeRegistry<IRecipe> r, String name, ItemStack result, Ingredient... slots) {
        NonNullList<Ingredient> list = NonNullList.create();
        Collections.addAll(list, slots);
        r.register(new ShapedRecipes("", 3, 3, list, result).setRegistryName(Content.id(name)));
    }

    /** Called from the mod's init. */
    public static void init() {
        if (!enabled()) {
            return;
        }
        LootFunctionManager.registerFunction(new SetInstrument.Serializer());
        MinecraftForge.EVENT_BUS.register(new LastDeathLocation.Events());
        MinecraftForge.EVENT_BUS.register(new GoatHorns.Events());
        MinecraftForge.EVENT_BUS.register(new ItemsLoot());
        MinecraftForge.EVENT_BUS.register(new Trades());
    }

    /** Before a merchant's offers are shown, books naming Swift Sneak are re-rolled ({@link Discovery}). */
    public static final class Trades {
        @SubscribeEvent
        public void interact(PlayerInteractEvent.EntityInteract event) {
            if (!event.getWorld().isRemote && event.getTarget() instanceof IMerchant) {
                IMerchant merchant = (IMerchant) event.getTarget();
                Discovery.rerollTrades(merchant, merchant.getRecipes(event.getEntityPlayer()), event.getWorld().rand);
            }
        }
    }
}
