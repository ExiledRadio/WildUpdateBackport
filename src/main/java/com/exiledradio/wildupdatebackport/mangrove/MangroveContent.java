package com.exiledradio.wildupdatebackport.mangrove;

import com.exiledradio.backportcore.trade.TradeRegistry;
import com.exiledradio.wildupdatebackport.ModConfig;
import com.exiledradio.wildupdatebackport.WildUpdateBackport;
import com.exiledradio.wildupdatebackport.common.Compat;
import com.exiledradio.wildupdatebackport.common.Content;
import com.exiledradio.wildupdatebackport.common.Owners;
import com.exiledradio.wildupdatebackport.common.VanillaRecipes;

import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.BlockFenceGate;
import net.minecraft.block.BlockLeaves;
import net.minecraft.block.BlockLog;
import net.minecraft.block.SoundType;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemDoor;
import net.minecraft.item.ItemSlab;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.village.MerchantRecipe;
import net.minecraft.world.World;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.furnace.FurnaceFuelBurnTimeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.registries.IForgeRegistry;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The mangrove wood set: logs, wood, stripped forms, planks, stairs, slab, fence, fence gate, door,
 * trapdoor, button, pressure plate, sign, leaves, propagule (and its potted form), roots and muddy roots.
 *
 * <h2>Numbers</h2>
 * 1.19.2's {@code Blocks}, {@code FireBlock}, furnace fuel table, composter table and loot tables: see
 * each block's class and {@code docs/streams/mangrove.md}.
 *
 * <h2>Switched off</h2>
 * With {@code mangrove.enabled} off nothing is registered and every lookup here returns null.
 */
@Mod.EventBusSubscriber(modid = WildUpdateBackport.MOD_ID)
public final class MangroveContent {

    public static final SoundEvent ROOTS_BREAK = sound("block.mangrove_roots.break");
    public static final SoundEvent ROOTS_STEP = sound("block.mangrove_roots.step");
    public static final SoundEvent ROOTS_PLACE = sound("block.mangrove_roots.place");
    public static final SoundEvent ROOTS_HIT = sound("block.mangrove_roots.hit");
    public static final SoundEvent ROOTS_FALL = sound("block.mangrove_roots.fall");
    public static final SoundEvent MUDDY_BREAK = sound("block.muddy_mangrove_roots.break");
    public static final SoundEvent MUDDY_STEP = sound("block.muddy_mangrove_roots.step");
    public static final SoundEvent MUDDY_PLACE = sound("block.muddy_mangrove_roots.place");
    public static final SoundEvent MUDDY_HIT = sound("block.muddy_mangrove_roots.hit");
    public static final SoundEvent MUDDY_FALL = sound("block.muddy_mangrove_roots.fall");
    public static final SoundEvent AXE_STRIP = sound("item.axe.strip");

    /** 1.19's {@code SoundType.MANGROVE_ROOTS} and {@code MUDDY_MANGROVE_ROOTS}: volume 1, pitch 1. */
    public static final SoundType ROOTS_SOUND = new SoundType(1.0F, 1.0F, ROOTS_BREAK, ROOTS_STEP, ROOTS_PLACE, ROOTS_HIT, ROOTS_FALL);
    public static final SoundType MUDDY_SOUND = new SoundType(1.0F, 1.0F, MUDDY_BREAK, MUDDY_STEP, MUDDY_PLACE, MUDDY_HIT, MUDDY_FALL);

    private static BlockMangroveLog log;
    private static BlockMangroveLog wood;
    private static BlockMangroveLog strippedLog;
    private static BlockMangroveLog strippedWood;
    private static MangroveBuilding.Planks planks;
    private static MangroveBuilding.Stairs stairs;
    private static MangroveBuilding.Slab.Half slab;
    private static MangroveBuilding.Slab.Double doubleSlab;
    private static MangroveBuilding.Fence fence;
    private static MangroveBuilding.FenceGate fenceGate;
    private static MangroveBuilding.Door door;
    private static MangroveBuilding.Trapdoor trapdoor;
    private static MangroveBuilding.Button button;
    private static MangroveBuilding.PressurePlate pressurePlate;
    private static MangroveSign.Standing standingSign;
    private static MangroveSign.Wall wallSign;
    private static BlockMangroveLeaves leaves;
    private static BlockPropagule propagule;
    private static BlockPottedPropagule pottedPropagule;
    private static BlockMangroveRoots.Roots roots;
    private static BlockMangroveRoots.Muddy muddyRoots;

    private static Item doorItem;
    private static Item signItem;

    /** Blocks with an ordinary item, in creative-tab order. */
    private static final List<Block> WITH_ITEMS = new ArrayList<Block>();

    private MangroveContent() {
    }

    private static SoundEvent sound(String name) {
        SoundEvent event = new SoundEvent(Content.id(name));
        event.setRegistryName(Content.id(name));
        return event;
    }

    public static boolean enabled() {
        return ModConfig.mangrove.enabled;
    }

    // --- lookups ------------------------------------------------------------------------------

    @Nullable
    public static IBlockState log() {
        return log == null ? null : log.getDefaultState();
    }

    /** Leaves as a tree places them: decaying, not yet checked. */
    @Nullable
    public static IBlockState leavesForTree() {
        return leaves == null ? null : leaves.getDefaultState().withProperty(BlockLeaves.DECAYABLE, true)
                .withProperty(BlockLeaves.CHECK_DECAY, false);
    }

    @Nullable
    public static IBlockState roots() {
        return roots == null ? null : roots.getDefaultState();
    }

    @Nullable
    public static IBlockState muddyRoots() {
        return muddyRoots == null ? null : muddyRoots.getDefaultState();
    }

    @Nullable
    public static BlockPropagule propagule() {
        return propagule;
    }

    @Nullable
    public static BlockMangroveLeaves leaves() {
        return leaves;
    }

    public static Item doorItem() {
        return doorItem;
    }

    public static Item signItem() {
        return signItem;
    }

    public static MangroveSign.Standing standingSign() {
        return standingSign;
    }

    public static MangroveSign.Wall wallSign() {
        return wallSign;
    }

    @Nullable
    public static MangroveBuilding.Slab.Half slab() {
        return slab;
    }

    @Nullable
    public static MangroveBuilding.Slab.Double doubleSlab() {
        return doubleSlab;
    }

    // --- registration -------------------------------------------------------------------------

    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        if (!enabled()) {
            WildUpdateBackport.LOGGER.info("Mangrove wood is off (mangrove.enabled).");
            return;
        }
        IForgeRegistry<Block> r = event.getRegistry();
        log = withItem(r, "mangrove_log", new BlockMangroveLog(false, false), "axe");
        wood = withItem(r, "mangrove_wood", new BlockMangroveLog(true, false), "axe");
        strippedLog = withItem(r, "stripped_mangrove_log", new BlockMangroveLog(false, true), "axe");
        strippedWood = withItem(r, "stripped_mangrove_wood", new BlockMangroveLog(true, true), "axe");
        planks = withItem(r, "mangrove_planks", new MangroveBuilding.Planks(), "axe");
        stairs = withItem(r, "mangrove_stairs", new MangroveBuilding.Stairs(planks.getDefaultState()), "axe");
        slab = Content.block(r, "mangrove_slab", new MangroveBuilding.Slab.Half(), "axe");
        doubleSlab = Content.block(r, "mangrove_double_slab", new MangroveBuilding.Slab.Double(slab), "axe");
        doubleSlab.setCreativeTab(null);
        fence = withItem(r, "mangrove_fence", new MangroveBuilding.Fence(), "axe");
        fenceGate = withItem(r, "mangrove_fence_gate", new MangroveBuilding.FenceGate(), "axe");
        door = Content.block(r, "mangrove_door", new MangroveBuilding.Door(), "axe");
        trapdoor = withItem(r, "mangrove_trapdoor", new MangroveBuilding.Trapdoor(), "axe");
        button = withItem(r, "mangrove_button", new MangroveBuilding.Button(), "axe");
        pressurePlate = withItem(r, "mangrove_pressure_plate", new MangroveBuilding.PressurePlate(), "axe");
        standingSign = Content.block(r, "mangrove_sign", new MangroveSign.Standing(), "axe");
        wallSign = Content.block(r, "mangrove_wall_sign", new MangroveSign.Wall(), "axe");
        standingSign.setCreativeTab(null);
        wallSign.setCreativeTab(null);
        leaves = withItem(r, "mangrove_leaves", new BlockMangroveLeaves(), null);
        propagule = withItem(r, "mangrove_propagule", new BlockPropagule(), null);
        pottedPropagule = Content.block(r, "potted_mangrove_propagule", new BlockPottedPropagule(), null);
        pottedPropagule.setCreativeTab(null);
        roots = withItem(r, "mangrove_roots", new BlockMangroveRoots.Roots(ROOTS_SOUND), "axe");
        muddyRoots = withItem(r, "muddy_mangrove_roots", new BlockMangroveRoots.Muddy(MUDDY_SOUND), "shovel");

        Content.ignore(slab, MangroveBuilding.Slab.VARIANT);
        Content.ignore(doubleSlab, MangroveBuilding.Slab.VARIANT);
        Content.ignore(door, BlockDoor.POWERED);
        Content.ignore(fenceGate, BlockFenceGate.POWERED);
        Content.ignore(leaves, BlockLeaves.CHECK_DECAY, BlockLeaves.DECAYABLE);
        Content.ignore(propagule, BlockPropagule.STAGE);
        Content.ignore(standingSign, net.minecraft.block.BlockStandingSign.ROTATION);
        Content.ignore(wallSign, net.minecraft.block.BlockWallSign.FACING);
        Content.multipart(fence);

        for (Block b : new Block[] {planks, slab, doubleSlab, fenceGate, fence, stairs}) {
            Blocks.FIRE.setFireInfo(b, 5, 20);
        }
        for (Block b : new Block[] {log, strippedLog, strippedWood, wood}) {
            Blocks.FIRE.setFireInfo(b, 5, 5);
        }
        Blocks.FIRE.setFireInfo(roots, 5, 20);
        Blocks.FIRE.setFireInfo(leaves, 30, 60);

        GameRegistry.registerTileEntity(MangroveSign.Tile.class, Content.id("mangrove_sign"));
    }

    private static <B extends Block> B withItem(IForgeRegistry<Block> registry, String name, B block, @Nullable String tool) {
        Content.block(registry, name, block, tool);
        WITH_ITEMS.add(block);
        return block;
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        if (!enabled()) {
            return;
        }
        IForgeRegistry<Item> r = event.getRegistry();
        for (Block block : WITH_ITEMS) {
            Content.registerBlockItem(r, block);
        }
        Content.blockItem(r, slab, new ItemSlab(slab, slab, doubleSlab));
        doorItem = Content.item(r, "mangrove_door", new ItemDoor(door));
        signItem = Content.item(r, "mangrove_sign", new MangroveSign.SignItem());
        signItem.setMaxStackSize(16);

        for (Block b : new Block[] {log, wood, strippedLog, strippedWood}) {
            OreDictionary.registerOre("logWood", b);
        }
        OreDictionary.registerOre("plankWood", planks);
        OreDictionary.registerOre("slabWood", slab);
        OreDictionary.registerOre("stairWood", stairs);
        OreDictionary.registerOre("fenceWood", fence);
        OreDictionary.registerOre("fenceGateWood", fenceGate);
        OreDictionary.registerOre("doorWood", doorItem);
        OreDictionary.registerOre("trapdoorWood", trapdoor);
        OreDictionary.registerOre("treeLeaves", leaves);
        OreDictionary.registerOre("treeSapling", propagule);

        Compat.compost(0.3F, Item.getItemFromBlock(leaves));
        Compat.compost(0.3F, Item.getItemFromBlock(propagule));
        Compat.compost(0.3F, Item.getItemFromBlock(roots));
    }

    @SubscribeEvent
    public static void registerSounds(RegistryEvent.Register<SoundEvent> event) {
        if (!enabled()) {
            return;
        }
        event.getRegistry().registerAll(ROOTS_BREAK, ROOTS_STEP, ROOTS_PLACE, ROOTS_HIT, ROOTS_FALL,
                MUDDY_BREAK, MUDDY_STEP, MUDDY_PLACE, MUDDY_HIT, MUDDY_FALL, AXE_STRIP);
    }

    /** Called from the mod's init: the wandering trader's propagule (5 emeralds, 8 uses), through Core. */
    public static void init() {
        if (!enabled()) {
            return;
        }
        TradeRegistry.addTraderTrade(TradeRegistry.TraderPool.ORDINARY, random ->
                new MerchantRecipe(new ItemStack(Items.EMERALD, 5), ItemStack.EMPTY, new ItemStack(propagule), 0, 8));
    }

    // --- recipes ------------------------------------------------------------------------------

    @SubscribeEvent
    public static void registerRecipes(RegistryEvent.Register<IRecipe> event) {
        if (!enabled()) {
            return;
        }
        IForgeRegistry<IRecipe> r = event.getRegistry();
        Ingredient anyLog = Ingredient.fromStacks(new ItemStack(log), new ItemStack(wood), new ItemStack(strippedLog),
                new ItemStack(strippedWood));
        Ingredient p = Ingredient.fromStacks(new ItemStack(planks));
        Ingredient stick = Ingredient.fromItem(Items.STICK);
        Ingredient e = Ingredient.EMPTY;
        Ingredient l = Ingredient.fromStacks(new ItemStack(log));
        Ingredient sl = Ingredient.fromStacks(new ItemStack(strippedLog));

        shapeless(r, "mangrove_planks", "planks", new ItemStack(planks, 4), anyLog);
        shaped(r, "mangrove_wood", "bark", new ItemStack(wood, 3), 2, 2, l, l, l, l);
        shaped(r, "stripped_mangrove_wood", "bark", new ItemStack(strippedWood, 3), 2, 2, sl, sl, sl, sl);
        shaped(r, "mangrove_stairs", "wooden_stairs", new ItemStack(stairs, 4), 3, 3, p, e, e, p, p, e, p, p, p);
        shaped(r, "mangrove_slab", "wooden_slab", new ItemStack(slab, 6), 3, 1, p, p, p);
        shaped(r, "mangrove_fence", "wooden_fence", new ItemStack(fence, 3), 3, 2, p, stick, p, p, stick, p);
        shaped(r, "mangrove_fence_gate", "wooden_fence_gate", new ItemStack(fenceGate), 3, 2, stick, p, stick, stick, p, stick);
        shaped(r, "mangrove_door", "wooden_door", new ItemStack(doorItem, 3), 2, 3, p, p, p, p, p, p);
        shaped(r, "mangrove_trapdoor", "wooden_trapdoor", new ItemStack(trapdoor, 2), 3, 2, p, p, p, p, p, p);
        shapeless(r, "mangrove_button", "wooden_button", new ItemStack(button), p);
        shaped(r, "mangrove_pressure_plate", "wooden_pressure_plate", new ItemStack(pressurePlate), 2, 1, p, p);
        shaped(r, "mangrove_sign", "wooden_sign", new ItemStack(signItem, 3), 3, 3, p, p, p, p, p, p, e, stick, e);
        // Forge turned vanilla's sign, trapdoor, button and plate recipes into "any planks" ones, registered
        // before ours: without this, mangrove planks would make oak ones.
        final Item planksItem = Item.getItemFromBlock(planks);
        for (String vanilla : new String[] {"minecraft:sign", "minecraft:trapdoor", "minecraft:wooden_button",
                "minecraft:wooden_pressure_plate"}) {
            VanillaRecipes.exclude(r, vanilla, stack -> stack.getItem() == planksItem);
        }
        Block mud = Owners.ourBlock("mud");
        if (mud != null) {
            shapeless(r, "muddy_mangrove_roots", "", new ItemStack(muddyRoots), Ingredient.fromStacks(new ItemStack(mud)),
                    Ingredient.fromStacks(new ItemStack(roots)));
        }
        net.minecraft.item.crafting.FurnaceRecipes.instance().addSmeltingRecipeForBlock(log, new ItemStack(Items.COAL, 1, 1), 0.15F);
        net.minecraft.item.crafting.FurnaceRecipes.instance().addSmeltingRecipeForBlock(wood, new ItemStack(Items.COAL, 1, 1), 0.15F);
        net.minecraft.item.crafting.FurnaceRecipes.instance().addSmeltingRecipeForBlock(strippedLog, new ItemStack(Items.COAL, 1, 1), 0.15F);
        net.minecraft.item.crafting.FurnaceRecipes.instance().addSmeltingRecipeForBlock(strippedWood, new ItemStack(Items.COAL, 1, 1), 0.15F);
    }

    private static void shaped(IForgeRegistry<IRecipe> r, String name, String group, ItemStack result, int w, int h,
                               Ingredient... slots) {
        NonNullList<Ingredient> list = NonNullList.create();
        Collections.addAll(list, slots);
        r.register(new ShapedRecipes(group, w, h, list, result).setRegistryName(Content.id(name)));
    }

    private static void shapeless(IForgeRegistry<IRecipe> r, String name, String group, ItemStack result, Ingredient... in) {
        NonNullList<Ingredient> list = NonNullList.create();
        Collections.addAll(list, in);
        r.register(new ShapelessRecipes(group, result, list).setRegistryName(Content.id(name)));
    }

    // --- fuel ---------------------------------------------------------------------------------

    /**
     * 1.19's burn times where 1.12.2's material rule (300 for any wooden block) differs: the slab 150, door
     * and sign 200, button and propagule 100.
     */
    @SubscribeEvent
    public static void fuel(FurnaceFuelBurnTimeEvent event) {
        if (!enabled() || event.getItemStack().isEmpty()) {
            return;
        }
        Item item = event.getItemStack().getItem();
        if (item == Item.getItemFromBlock(slab)) {
            event.setBurnTime(150);
        } else if (item == doorItem || item == signItem) {
            event.setBurnTime(200);
        } else if (item == Item.getItemFromBlock(button) || item == Item.getItemFromBlock(propagule)) {
            event.setBurnTime(100);
        }
    }

    // --- right clicks: stripping, potting ----------------------------------------------------

    @SubscribeEvent
    public static void rightClick(PlayerInteractEvent.RightClickBlock event) {
        if (!enabled()) {
            return;
        }
        World world = event.getWorld();
        BlockPos pos = event.getPos();
        ItemStack held = event.getItemStack();
        if (BlockPottedPropagule.tryPot(event, pottedPropagule, propagule)) {
            event.setCanceled(true);
            event.setCancellationResult(EnumActionResult.SUCCESS);
            return;
        }
        if (held.isEmpty() || !held.getItem().getToolClasses(held).contains("axe")) {
            return;
        }
        IBlockState state = world.getBlockState(pos);
        Block stripped = state.getBlock() == log ? strippedLog : state.getBlock() == wood ? strippedWood : null;
        if (stripped == null) {
            return;
        }
        EntityPlayer player = event.getEntityPlayer();
        world.playSound(player, pos, AXE_STRIP, SoundCategory.BLOCKS, 1.0F, 1.0F);
        if (!world.isRemote) {
            world.setBlockState(pos, stripped.getDefaultState().withProperty(BlockLog.LOG_AXIS, state.getValue(BlockLog.LOG_AXIS)), 11);
            held.damageItem(1, player);
        }
        event.setCanceled(true);
        event.setCancellationResult(EnumActionResult.SUCCESS);
    }

    /** @return every block of this stream that has a plain item, for the tests */
    public static List<Block> blocksWithItems() {
        return Collections.unmodifiableList(WITH_ITEMS);
    }

    /** Names for the generator and tests. */
    public static ResourceLocation name(Block block) {
        return block.getRegistryName();
    }
}
