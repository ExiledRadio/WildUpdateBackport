package com.exiledradio.wildupdatebackport.boat;

import com.exiledradio.backportcore.boat.BoatWood;
import com.exiledradio.backportcore.boat.Boats;
import com.exiledradio.backportcore.boat.ItemWoodBoat;
import com.exiledradio.wildupdatebackport.ModConfig;
import com.exiledradio.wildupdatebackport.WildUpdateBackport;
import com.exiledradio.wildupdatebackport.common.Content;
import com.exiledradio.wildupdatebackport.common.Owners;

import net.minecraft.block.Block;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.registries.IForgeRegistry;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The mangrove boat and boats with chests for all seven woods.
 *
 * <p>The boats are Backport Core's ({@code backportcore:boat} and {@code chest_boat}, Core's {@code boat/}, our
 * request 4): this registers the mangrove wood with Core and the items, under our names, and the recipes. Worlds
 * saved before (our own {@code wildupdatebackport:boat} and {@code chest_boat}, ids 1 and 2) load as Core's, wood and
 * chest kept ({@link #preInit}). The mangrove boat exists only when the mangrove wood stream is on. Boats and boats
 * with chests burn for 1200 ticks, as 1.19's (Core's item).
 */
@Mod.EventBusSubscriber(modid = WildUpdateBackport.MOD_ID)
public final class BoatContent {

    public static final int BURN_TIME = ItemWoodBoat.BURN_TIME;

    @Nullable
    private static BoatWood mangrove;
    private static final List<Item> ITEMS = new ArrayList<Item>();

    private BoatContent() {
    }

    public static boolean enabled() {
        return ModConfig.boats.enabled;
    }

    /** Our boats saved before Core 1.4.4 load as Core's, with or without this stream on. */
    public static void preInit() {
        Boats.renamed(Content.id("boat"), false);
        Boats.renamed(Content.id("chest_boat"), true);
    }

    /** @return every boat and chest boat item of ours, for the tests */
    public static List<Item> items() {
        return Collections.unmodifiableList(ITEMS);
    }

    /** @return the woods we make chest boats of: vanilla's six, then mangrove when it is on */
    public static List<BoatWood> woods() {
        List<BoatWood> woods = new ArrayList<BoatWood>();
        for (EntityBoat.Type type : EntityBoat.Type.values()) {
            woods.add(BoatWood.of(type));
        }
        if (mangrove != null) {
            woods.add(mangrove);
        }
        return woods;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void registerItems(RegistryEvent.Register<Item> event) {
        if (!enabled()) {
            WildUpdateBackport.LOGGER.info("Boats are off (boats.enabled).");
            return;
        }
        final Block planks = Owners.ourBlock("mangrove_planks");
        if (planks != null && mangrove == null) {
            mangrove = BoatWood.register(Content.id("mangrove"), () -> new ItemStack(planks), false)
                    .setTextures(Content.id("textures/entity/boat/mangrove.png"), Content.id("textures/entity/chest_boat/mangrove.png"));
        }
        IForgeRegistry<Item> r = event.getRegistry();
        if (mangrove != null) {
            mangrove.setBoatItem(add(Content.item(r, "mangrove_boat", new ItemWoodBoat(mangrove, false))));
        }
        for (BoatWood wood : woods()) {
            if (wood.vanilla != null) {
                wood.setTextures(null, Content.id("textures/entity/chest_boat/" + wood.name + ".png"));
            }
            wood.setChestBoatItem(add(Content.item(r, wood.name + "_chest_boat", new ItemWoodBoat(wood, true))));
        }
    }

    private static Item add(Item item) {
        ITEMS.add(item);
        return item;
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
            r.register(new ShapedRecipes("boat", 3, 2, slots, new ItemStack(mangrove.boatItem())).setRegistryName(Content.id("mangrove_boat")));
        }
        for (BoatWood wood : woods()) {
            NonNullList<Ingredient> in = NonNullList.create();
            Collections.addAll(in, Ingredient.fromItem(Item.getItemFromBlock(Blocks.CHEST)), Ingredient.fromItem(wood.boatItem()));
            r.register(new ShapelessRecipes("chest_boat", new ItemStack(wood.chestBoatItem()), in)
                    .setRegistryName(new ResourceLocation(WildUpdateBackport.MOD_ID, wood.name + "_chest_boat")));
        }
    }
}
