package com.exiledradio.wildupdatebackport.mud;

import com.exiledradio.wildupdatebackport.ModConfig;
import com.exiledradio.wildupdatebackport.WildUpdateBackport;
import com.exiledradio.wildupdatebackport.common.Content;
import com.exiledradio.wildupdatebackport.common.ModBuilding;
import com.exiledradio.wildupdatebackport.common.Owners;

import net.minecraft.block.Block;
import net.minecraft.block.BlockDirt;
import net.minecraft.block.BlockDispenser;
import net.minecraft.block.BlockWall;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.dispenser.BehaviorDefaultDispenseItem;
import net.minecraft.dispenser.IBehaviorDispenseItem;
import net.minecraft.dispenser.IBlockSource;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.init.PotionTypes;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemSlab;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraft.potion.PotionUtils;
import net.minecraft.stats.StatList;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.NonNullList;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.registries.IForgeRegistry;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Mud, packed mud, mud bricks, and mud brick stairs, slab and wall.
 *
 * <p>1.19.2's numbers: mud is dirt's (hardness 0.5, shovel); packed mud hardness 1, resistance 3 (shovel,
 * as dirt's); mud bricks, stairs, slab and wall hardness 1.5, resistance 3, pickaxe. Mud comes from a water
 * bottle used on dirt, coarse dirt or Caves &amp; Cliffs' rooted dirt, by a player or a dispenser.
 */
@Mod.EventBusSubscriber(modid = WildUpdateBackport.MOD_ID)
public final class MudContent {

    public static final SoundEvent MUD_BREAK = sound("block.mud.break");
    public static final SoundEvent MUD_STEP = sound("block.mud.step");
    public static final SoundEvent MUD_PLACE = sound("block.mud.place");
    public static final SoundEvent MUD_HIT = sound("block.mud.hit");
    public static final SoundEvent MUD_FALL = sound("block.mud.fall");
    public static final SoundEvent PACKED_BREAK = sound("block.packed_mud.break");
    public static final SoundEvent PACKED_STEP = sound("block.packed_mud.step");
    public static final SoundEvent PACKED_PLACE = sound("block.packed_mud.place");
    public static final SoundEvent PACKED_HIT = sound("block.packed_mud.hit");
    public static final SoundEvent PACKED_FALL = sound("block.packed_mud.fall");
    public static final SoundEvent BRICKS_BREAK = sound("block.mud_bricks.break");
    public static final SoundEvent BRICKS_STEP = sound("block.mud_bricks.step");
    public static final SoundEvent BRICKS_PLACE = sound("block.mud_bricks.place");
    public static final SoundEvent BRICKS_HIT = sound("block.mud_bricks.hit");
    public static final SoundEvent BRICKS_FALL = sound("block.mud_bricks.fall");

    /** 1.19's {@code SoundType.MUD}, {@code PACKED_MUD}, {@code MUD_BRICKS}: volume 1, pitch 1. */
    public static final SoundType MUD_SOUND = new SoundType(1.0F, 1.0F, MUD_BREAK, MUD_STEP, MUD_PLACE, MUD_HIT, MUD_FALL);
    public static final SoundType PACKED_SOUND = new SoundType(1.0F, 1.0F, PACKED_BREAK, PACKED_STEP, PACKED_PLACE, PACKED_HIT, PACKED_FALL);
    public static final SoundType BRICKS_SOUND = new SoundType(1.0F, 1.0F, BRICKS_BREAK, BRICKS_STEP, BRICKS_PLACE, BRICKS_HIT, BRICKS_FALL);

    private static BlockMud mud;
    private static Block packedMud;
    private static Block mudBricks;
    private static Block stairs;
    private static ModBuilding.Slab.Half slab;
    private static ModBuilding.Slab.Double doubleSlab;
    private static ModBuilding.Wall wall;

    private static final List<Block> WITH_ITEMS = new ArrayList<Block>();

    private MudContent() {
    }

    private static SoundEvent sound(String name) {
        SoundEvent event = new SoundEvent(Content.id(name));
        event.setRegistryName(Content.id(name));
        return event;
    }

    public static boolean enabled() {
        return ModConfig.mud.enabled;
    }

    @Nullable
    public static Block mud() {
        return mud;
    }

    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        if (!enabled()) {
            WildUpdateBackport.LOGGER.info("Mud is off (mud.enabled).");
            return;
        }
        IForgeRegistry<Block> r = event.getRegistry();
        mud = withItem(r, "mud", new BlockMud(MUD_SOUND), "shovel");
        packedMud = withItem(r, "packed_mud", plain(Material.GROUND, MapColor.DIRT, 1.0F, 3.0F, PACKED_SOUND), "shovel");
        mudBricks = withItem(r, "mud_bricks", plain(Material.ROCK, MapColor.SILVER_STAINED_HARDENED_CLAY, 1.5F, 3.0F, BRICKS_SOUND), "pickaxe");
        stairs = withItem(r, "mud_brick_stairs", new ModBuilding.Stairs(mudBricks.getDefaultState()), "pickaxe");
        slab = Content.block(r, "mud_brick_slab", new ModBuilding.Slab.Half(Material.ROCK, MapColor.BROWN, 1.5F, 3.0F, BRICKS_SOUND), "pickaxe");
        doubleSlab = Content.block(r, "mud_brick_double_slab",
                new ModBuilding.Slab.Double(slab, Material.ROCK, MapColor.BROWN, 1.5F, 3.0F, BRICKS_SOUND), "pickaxe");
        doubleSlab.setCreativeTab(null);
        wall = withItem(r, "mud_brick_wall", new ModBuilding.Wall(mudBricks, MapColor.SILVER_STAINED_HARDENED_CLAY), "pickaxe");

        Content.ignore(slab, ModBuilding.Slab.VARIANT);
        Content.ignore(doubleSlab, ModBuilding.Slab.VARIANT);
        Content.multipart(wall);
    }

    private static Block plain(Material material, MapColor color, float hardness, float resistance, SoundType sound) {
        Block block = new Block(material, color) {
            {
                this.setSoundType(sound);
            }
        };
        block.setHardness(hardness);
        block.setResistance(resistance * 5.0F / 3.0F);
        return block;
    }

    private static <B extends Block> B withItem(IForgeRegistry<Block> registry, String name, B block, String tool) {
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
    }

    @SubscribeEvent
    public static void registerSounds(RegistryEvent.Register<SoundEvent> event) {
        if (!enabled()) {
            return;
        }
        event.getRegistry().registerAll(MUD_BREAK, MUD_STEP, MUD_PLACE, MUD_HIT, MUD_FALL, PACKED_BREAK, PACKED_STEP,
                PACKED_PLACE, PACKED_HIT, PACKED_FALL, BRICKS_BREAK, BRICKS_STEP, BRICKS_PLACE, BRICKS_HIT, BRICKS_FALL);
    }

    @SubscribeEvent
    public static void registerRecipes(RegistryEvent.Register<IRecipe> event) {
        if (!enabled()) {
            return;
        }
        IForgeRegistry<IRecipe> r = event.getRegistry();
        Ingredient pm = Ingredient.fromStacks(new ItemStack(packedMud));
        Ingredient b = Ingredient.fromStacks(new ItemStack(mudBricks));
        Ingredient e = Ingredient.EMPTY;
        shapeless(r, "packed_mud", new ItemStack(packedMud), Ingredient.fromStacks(new ItemStack(mud)), Ingredient.fromItem(Items.WHEAT));
        shaped(r, "mud_bricks", new ItemStack(mudBricks, 4), 2, 2, pm, pm, pm, pm);
        shaped(r, "mud_brick_stairs", new ItemStack(stairs, 4), 3, 3, b, e, e, b, b, e, b, b, b);
        shaped(r, "mud_brick_slab", new ItemStack(slab, 6), 3, 1, b, b, b);
        shaped(r, "mud_brick_wall", new ItemStack(wall, 6), 3, 2, b, b, b, b, b, b);
    }

    private static void shaped(IForgeRegistry<IRecipe> r, String name, ItemStack result, int w, int h, Ingredient... slots) {
        NonNullList<Ingredient> list = NonNullList.create();
        Collections.addAll(list, slots);
        r.register(new ShapedRecipes("", w, h, list, result).setRegistryName(Content.id(name)));
    }

    private static void shapeless(IForgeRegistry<IRecipe> r, String name, ItemStack result, Ingredient... in) {
        NonNullList<Ingredient> list = NonNullList.create();
        Collections.addAll(list, in);
        r.register(new ShapelessRecipes("", result, list).setRegistryName(Content.id(name)));
    }

    // --- making mud ---------------------------------------------------------------------------

    /** 1.19's {@code #convertable_to_mud}: dirt, coarse dirt, rooted dirt. */
    static boolean convertible(IBlockState state) {
        if (state.getBlock() == Blocks.DIRT) {
            BlockDirt.DirtType type = state.getValue(BlockDirt.VARIANT);
            return type == BlockDirt.DirtType.DIRT || type == BlockDirt.DirtType.COARSE_DIRT;
        }
        Block rooted = Owners.present(Owners.CAVES_AND_CLIFFS) ? Owners.block(Owners.CAVES_AND_CLIFFS, "rooted_dirt") : null;
        return rooted != null && state.getBlock() == rooted;
    }

    static boolean isWaterBottle(ItemStack stack) {
        return stack.getItem() == Items.POTIONITEM && PotionUtils.getPotionFromItem(stack) == PotionTypes.WATER;
    }

    /** A player using a water bottle on dirt: mud, a splash, and the glass bottle back. */
    @SubscribeEvent
    public static void rightClick(PlayerInteractEvent.RightClickBlock event) {
        if (!enabled() || event.getFace() == EnumFacing.DOWN || !isWaterBottle(event.getItemStack())) {
            return;
        }
        World world = event.getWorld();
        BlockPos pos = event.getPos();
        if (!convertible(world.getBlockState(pos))) {
            return;
        }
        EntityPlayer player = event.getEntityPlayer();
        ItemStack held = event.getItemStack();
        world.playSound(null, pos, SoundEvents.ENTITY_GENERIC_SPLASH, SoundCategory.PLAYERS, 1.0F, 1.0F);
        player.addStat(StatList.getObjectUseStats(Items.POTIONITEM));
        if (!world.isRemote) {
            splash(world, pos);
            world.playSound(null, pos, SoundEvents.ITEM_BOTTLE_EMPTY, SoundCategory.BLOCKS, 1.0F, 1.0F);
            world.setBlockState(pos, mud.getDefaultState());
            if (!player.capabilities.isCreativeMode) {
                held.shrink(1);
                ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
                if (held.isEmpty()) {
                    player.setHeldItem(event.getHand(), bottle);
                } else if (!player.inventory.addItemStackToInventory(bottle)) {
                    player.dropItem(bottle, false);
                }
            }
        }
        event.setCanceled(true);
        event.setCancellationResult(EnumActionResult.SUCCESS);
    }

    private static void splash(World world, BlockPos pos) {
        if (world instanceof WorldServer) {
            for (int i = 0; i < 5; i++) {
                ((WorldServer) world).spawnParticle(EnumParticleTypes.WATER_SPLASH, pos.getX() + world.rand.nextDouble(),
                        pos.getY() + 1, pos.getZ() + world.rand.nextDouble(), 1, 0.0D, 0.0D, 0.0D, 1.0D);
            }
        }
    }

    /**
     * Called from the mod's init: dispensers with a water bottle facing dirt make mud and keep the glass
     * bottle, as 1.19.2's; anything else does what the dispenser did before.
     */
    public static void init() {
        if (!enabled()) {
            return;
        }
        final IBehaviorDispenseItem before = BlockDispenser.DISPENSE_BEHAVIOR_REGISTRY.getObject(Items.POTIONITEM);
        final IBehaviorDispenseItem fallback = before != null ? before : new BehaviorDefaultDispenseItem();
        BlockDispenser.DISPENSE_BEHAVIOR_REGISTRY.putObject(Items.POTIONITEM, (source, stack) -> dispense(source, stack, fallback));
    }

    static ItemStack dispense(IBlockSource source, ItemStack stack, IBehaviorDispenseItem fallback) {
        if (!isWaterBottle(stack)) {
            return fallback.dispense(source, stack);
        }
        World world = source.getWorld();
        BlockPos front = source.getBlockPos().offset(source.getBlockState().getValue(BlockDispenser.FACING));
        if (!convertible(world.getBlockState(front))) {
            return fallback.dispense(source, stack);
        }
        splash(world, source.getBlockPos());
        world.playSound(null, source.getBlockPos(), SoundEvents.ITEM_BOTTLE_EMPTY, SoundCategory.BLOCKS, 1.0F, 1.0F);
        world.setBlockState(front, mud.getDefaultState());
        return new ItemStack(Items.GLASS_BOTTLE);
    }

    /** For the tests. */
    public static List<Block> blocksWithItems() {
        return Collections.unmodifiableList(WITH_ITEMS);
    }

    /** For the tests: vanilla's wall variant property, left out of the blockstate. */
    static Object wallVariant() {
        return BlockWall.VARIANT;
    }
}
