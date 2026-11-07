package com.exiledradio.wildupdatebackport.boat;

import com.exiledradio.wildupdatebackport.ModConfig;
import com.exiledradio.wildupdatebackport.WildUpdateBackport;
import com.exiledradio.wildupdatebackport.common.Content;
import com.exiledradio.wildupdatebackport.common.Owners;

import net.minecraft.block.Block;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.furnace.FurnaceFuelBurnTimeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.EntityEntryBuilder;
import net.minecraftforge.registries.IForgeRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The mangrove boat and boats with chests for all seven woods.
 *
 * <p>Entities {@code wildupdatebackport:boat} (id 1, a boat of a new wood) and {@code chest_boat} (id 2),
 * tracked as vanilla's boat is (80 blocks, every 3 ticks, with velocity). The mangrove ones exist only when
 * the mangrove wood stream is on. Boats and boats with chests burn for 1200 ticks, as 1.19's.
 */
@Mod.EventBusSubscriber(modid = WildUpdateBackport.MOD_ID)
public final class BoatContent {

    public static final int BURN_TIME = 1200;

    private static BoatWood mangrove;
    private static final List<Item> ITEMS = new ArrayList<Item>();

    private BoatContent() {
    }

    public static boolean enabled() {
        return ModConfig.boats.enabled;
    }

    /** @return every boat and chest boat item of ours, for the tests */
    public static List<Item> items() {
        return Collections.unmodifiableList(ITEMS);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void registerItems(RegistryEvent.Register<Item> event) {
        if (!enabled()) {
            WildUpdateBackport.LOGGER.info("Boats are off (boats.enabled).");
            return;
        }
        BoatWood.registerVanilla(WildUpdateBackport.MOD_ID);
        final Block planks = Owners.ourBlock("mangrove_planks");
        if (planks != null && mangrove == null) {
            mangrove = BoatWood.register(Content.id("mangrove").toString(), null, "mangrove", () -> new ItemStack(planks),
                    Content.id("textures/entity/boat/mangrove.png"), Content.id("textures/entity/chest_boat/mangrove.png"));
        }
        IForgeRegistry<Item> r = event.getRegistry();
        if (mangrove != null) {
            mangrove.boatItem = add(Content.item(r, "mangrove_boat", new ItemWoodBoat(mangrove, false)));
        }
        for (BoatWood wood : BoatWood.all()) {
            wood.chestBoatItem = add(Content.item(r, wood.name + "_chest_boat", new ItemWoodBoat(wood, true)));
        }
    }

    private static Item add(Item item) {
        ITEMS.add(item);
        return item;
    }

    @SubscribeEvent
    public static void registerEntities(RegistryEvent.Register<EntityEntry> event) {
        if (!enabled()) {
            return;
        }
        event.getRegistry().register(EntityEntryBuilder.create().entity(EntityWoodBoat.class).id(Content.id("boat"), 1)
                .name(WildUpdateBackport.MOD_ID + ".boat").tracker(80, 3, true).build());
        event.getRegistry().register(EntityEntryBuilder.create().entity(EntityWoodChestBoat.class).id(Content.id("chest_boat"), 2)
                .name(WildUpdateBackport.MOD_ID + ".chest_boat").tracker(80, 3, true).build());
    }

    @SubscribeEvent
    public static void registerRecipes(RegistryEvent.Register<IRecipe> event) {
        if (!enabled()) {
            return;
        }
        IForgeRegistry<IRecipe> r = event.getRegistry();
        if (mangrove != null) {
            Ingredient p = Ingredient.fromStacks(mangrove.planks());
            NonNullList<Ingredient> slots = NonNullList.create();
            Collections.addAll(slots, p, Ingredient.EMPTY, p, p, p, p);
            r.register(new ShapedRecipes("boat", 3, 2, slots, new ItemStack(mangrove.boatItem)).setRegistryName(Content.id("mangrove_boat")));
        }
        for (BoatWood wood : BoatWood.all()) {
            Item boat = wood.vanilla != null ? vanillaBoat(wood.vanilla) : wood.boatItem;
            NonNullList<Ingredient> in = NonNullList.create();
            Collections.addAll(in, Ingredient.fromItem(Item.getItemFromBlock(Blocks.CHEST)), Ingredient.fromItem(boat));
            r.register(new ShapelessRecipes("chest_boat", new ItemStack(wood.chestBoatItem), in)
                    .setRegistryName(new ResourceLocation(WildUpdateBackport.MOD_ID, wood.name + "_chest_boat")));
        }
    }

    static Item vanillaBoat(EntityBoat.Type type) {
        switch (type) {
            case SPRUCE:
                return Items.SPRUCE_BOAT;
            case BIRCH:
                return Items.BIRCH_BOAT;
            case JUNGLE:
                return Items.JUNGLE_BOAT;
            case ACACIA:
                return Items.ACACIA_BOAT;
            case DARK_OAK:
                return Items.DARK_OAK_BOAT;
            default:
                return Items.BOAT;
        }
    }

    @SubscribeEvent
    public static void fuel(FurnaceFuelBurnTimeEvent event) {
        if (enabled() && !event.getItemStack().isEmpty() && ITEMS.contains(event.getItemStack().getItem())) {
            event.setBurnTime(BURN_TIME);
        }
    }
}
