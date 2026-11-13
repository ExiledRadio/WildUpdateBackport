package com.exiledradio.wildupdatebackport.deepdark;

import com.exiledradio.backportcore.waterlog.Waterloggable;
import com.exiledradio.wildupdatebackport.ModConfig;
import com.exiledradio.wildupdatebackport.WildUpdateBackport;
import com.exiledradio.wildupdatebackport.common.Content;
import com.exiledradio.wildupdatebackport.common.ModBuilding;
import com.exiledradio.wildupdatebackport.common.Owners;

import net.minecraft.block.Block;
import net.minecraft.block.BlockFenceGate;
import net.minecraft.block.BlockPane;
import net.minecraft.block.BlockRotatedPillar;
import net.minecraft.block.BlockWall;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemSlab;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.registries.IForgeRegistry;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Deepslate and its four sets, for ancient cities when Caves &amp; Cliffs is not installed.
 *
 * <p>Caves &amp; Cliffs owns deepslate (family rule 1). Without it, this registers copies under this mod's id
 * with the owner's names, states, metadata, numbers, sounds, recipes, smelting and ore dictionary entries,
 * so the owner can take them over by name later. With it, nothing here is registered and {@link #get}
 * answers with the owner's blocks.
 *
 * <p>Numbers (current Java): deepslate hardness 3, resistance 6; everything else 3.5 and 6; pickaxe.
 * Metadata: deepslate is a pillar (axis y=0, x=4, z=8); a half slab is 0 bottom / 8 top; stairs are vanilla's;
 * the full blocks, double slabs and walls are always 0 (a wall's sides and post are worked out from its
 * neighbors, with current Java's none/low/tall sides).
 *
 * <p>The {@code block.deepslate} sound events are registered whenever the deep dark is on, even with
 * Caves &amp; Cliffs installed: reinforced deepslate uses {@link #deepslateSound()}.
 */
@Mod.EventBusSubscriber(modid = WildUpdateBackport.MOD_ID)
public final class DeepslateCopies {

    /** The four deepslate kinds that have stairs, slabs and walls. */
    public static final String[] SETS = {"cobbled_deepslate", "polished_deepslate", "deepslate_brick", "deepslate_tile"};

    private static final List<SoundEvent> DEEPSLATE_EVENTS = new ArrayList<SoundEvent>();
    private static final List<SoundEvent> SET_EVENTS = new ArrayList<SoundEvent>();

    /** 1.19's {@code SoundType.DEEPSLATE}, {@code POLISHED_DEEPSLATE}, {@code DEEPSLATE_BRICKS}, {@code DEEPSLATE_TILES}. */
    private static final SoundType DEEPSLATE_SOUND = type("deepslate", DEEPSLATE_EVENTS);
    private static final SoundType POLISHED_SOUND = type("polished_deepslate", SET_EVENTS);
    private static final SoundType BRICKS_SOUND = type("deepslate_bricks", SET_EVENTS);
    private static final SoundType TILES_SOUND = type("deepslate_tiles", SET_EVENTS);

    private static final Map<String, Block> BLOCKS = new LinkedHashMap<String, Block>();
    private static final Map<String, Slab> SLABS = new LinkedHashMap<String, Slab>();
    private static final Map<String, DoubleSlab> DOUBLE_SLABS = new LinkedHashMap<String, DoubleSlab>();

    private DeepslateCopies() {
    }

    private static SoundType type(String group, List<SoundEvent> into) {
        String b = "block." + group + ".";
        SoundEvent[] e = new SoundEvent[5];
        String[] kinds = {"break", "step", "place", "hit", "fall"};
        for (int i = 0; i < 5; i++) {
            e[i] = new SoundEvent(Content.id(b + kinds[i]));
            e[i].setRegistryName(Content.id(b + kinds[i]));
            into.add(e[i]);
        }
        return new SoundType(1.0F, 1.0F, e[0], e[1], e[2], e[3], e[4]);
    }

    /** @return 1.19's deepslate sound type, under this mod's namespace; registered whenever the deep dark is on */
    public static SoundType deepslateSound() {
        return DEEPSLATE_SOUND;
    }

    /** @return true when this mod registers its own deepslate (deep dark on, Caves &amp; Cliffs absent) */
    public static boolean registered() {
        return ModConfig.deepDark.enabled && !Owners.present(Owners.CAVES_AND_CLIFFS);
    }

    /**
     * @param name a block name such as {@code deepslate_tiles} or {@code cobbled_deepslate_wall}
     * @return Caves &amp; Cliffs' block of that name when it is installed (after block registration), else ours;
     * null when neither exists
     */
    @Nullable
    public static Block get(String name) {
        if (Owners.present(Owners.CAVES_AND_CLIFFS)) {
            return Owners.block(Owners.CAVES_AND_CLIFFS, name);
        }
        return BLOCKS.get(name);
    }

    /** Full block name for a set ("deepslate_brick" -> "deepslate_bricks"). */
    public static String full(String set) {
        return set.endsWith("_brick") || set.endsWith("_tile") ? set + "s" : set;
    }

    // --- registration ---------------------------------------------------------------------------

    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        if (!registered()) {
            return;
        }
        IForgeRegistry<Block> r = event.getRegistry();
        MapColor dark = MapColor.STONE;
        Pillar deepslate = add(r, "deepslate", new Pillar(Material.ROCK, dark, DEEPSLATE_SOUND, 3.0F, 6.0F));
        Map<String, SoundType> sounds = new LinkedHashMap<String, SoundType>();
        sounds.put("cobbled_deepslate", DEEPSLATE_SOUND);
        sounds.put("polished_deepslate", POLISHED_SOUND);
        sounds.put("deepslate_brick", BRICKS_SOUND);
        sounds.put("deepslate_tile", TILES_SOUND);
        for (String set : SETS) {
            SoundType sound = sounds.get(set);
            Block fullBlock = add(r, full(set), plain(dark, sound));
            IBlockState model = fullBlock.getDefaultState();
            add(r, set + "_stairs", new Stairs(model));
            Slab half = add(r, set + "_slab", new Slab(dark, sound));
            DoubleSlab dbl = add(r, set + "_double_slab", new DoubleSlab(half, dark, sound));
            dbl.setCreativeTab(null);
            Content.ignore(half, ModBuilding.Slab.VARIANT);
            Content.ignore(dbl, ModBuilding.Slab.VARIANT);
            SLABS.put(set, half);
            DOUBLE_SLABS.put(set, dbl);
            // 162 actual states drawn from one multipart file: one model name for all of them.
            Content.multipart(add(r, set + "_wall", new Wall(model)));
        }
        deepslate.dropsWithoutSilkTouch(BLOCKS.get("cobbled_deepslate"));
        add(r, "chiseled_deepslate", plain(dark, DEEPSLATE_SOUND));
        add(r, "cracked_deepslate_bricks", plain(dark, BRICKS_SOUND));
        add(r, "cracked_deepslate_tiles", plain(dark, TILES_SOUND));
        WildUpdateBackport.LOGGER.info("Caves & Cliffs is not installed: registered {} deepslate blocks of our own.", BLOCKS.size());
    }

    private static Block plain(MapColor color, SoundType sound) {
        Block block = new Block(Material.ROCK, color) {
            {
                this.setSoundType(sound);
            }
        };
        block.setHardness(3.5F);
        block.setResistance(6.0F * 5.0F / 3.0F);
        return block;
    }

    private static <B extends Block> B add(IForgeRegistry<Block> r, String name, B block) {
        Content.block(r, name, block, "pickaxe");
        BLOCKS.put(name, block);
        return block;
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        if (!registered()) {
            return;
        }
        IForgeRegistry<Item> r = event.getRegistry();
        for (Block b : BLOCKS.values()) {
            if (b instanceof DoubleSlab) {
                continue;
            }
            if (b instanceof Slab) {
                Slab half = (Slab) b;
                Content.blockItem(r, half, new ItemSlab(half, half, DOUBLE_SLABS.get(half.set())));
            } else {
                Content.registerBlockItem(r, b);
            }
        }
        // Current Java makes stone tools, furnaces and the other cobblestone things from cobbled deepslate.
        OreDictionary.registerOre("cobblestone", BLOCKS.get("cobbled_deepslate"));
        OreDictionary.registerOre("stone", BLOCKS.get("deepslate"));
    }

    @SubscribeEvent
    public static void registerSounds(RegistryEvent.Register<SoundEvent> event) {
        if (!ModConfig.deepDark.enabled) {
            return;
        }
        IForgeRegistry<SoundEvent> r = event.getRegistry();
        for (SoundEvent e : DEEPSLATE_EVENTS) {
            r.register(e);
        }
        if (registered()) {
            for (SoundEvent e : SET_EVENTS) {
                r.register(e);
            }
        }
    }

    @SubscribeEvent
    public static void registerRecipes(RegistryEvent.Register<IRecipe> event) {
        if (!registered()) {
            return;
        }
        IForgeRegistry<IRecipe> r = event.getRegistry();
        ItemStack cobbled = stack("cobbled_deepslate", 1);
        shaped(r, "polished_deepslate", stack("polished_deepslate", 4), "XX", "XX", 'X', cobbled);
        shaped(r, "deepslate_bricks", stack("deepslate_bricks", 4), "XX", "XX", 'X', stack("polished_deepslate", 1));
        shaped(r, "deepslate_tiles", stack("deepslate_tiles", 4), "XX", "XX", 'X', stack("deepslate_bricks", 1));
        shaped(r, "chiseled_deepslate", stack("chiseled_deepslate", 1), "X", "X", 'X', stack("cobbled_deepslate_slab", 1));
        for (String set : SETS) {
            ItemStack base = stack(full(set), 1);
            shaped(r, set + "_stairs", stack(set + "_stairs", 4), "X  ", "XX ", "XXX", 'X', base);
            shaped(r, set + "_slab", stack(set + "_slab", 6), "XXX", 'X', base);
            shaped(r, set + "_wall", stack(set + "_wall", 6), "XXX", "XXX", 'X', base);
        }
        // Smelting here, once every item exists (the furnace list is not a registry).
        FurnaceRecipes f = FurnaceRecipes.instance();
        f.addSmeltingRecipe(cobbled, stack("deepslate", 1), 0.1F);
        f.addSmeltingRecipe(stack("deepslate_bricks", 1), stack("cracked_deepslate_bricks", 1), 0.1F);
        f.addSmeltingRecipe(stack("deepslate_tiles", 1), stack("cracked_deepslate_tiles", 1), 0.1F);
    }

    private static ItemStack stack(String name, int count) {
        return new ItemStack(BLOCKS.get(name), count);
    }

    private static void shaped(IForgeRegistry<IRecipe> r, String name, ItemStack out, Object... recipe) {
        ShapedOreRecipe sr = new ShapedOreRecipe(Content.id(name), out, recipe);
        sr.setRegistryName(Content.id(name));
        r.register(sr);
    }

    // --- blocks -----------------------------------------------------------------------------------

    /** Deepslate: an axis, as logs have; without Silk Touch it drops another block (cobbled deepslate). */
    public static class Pillar extends BlockRotatedPillar {
        @Nullable
        private Block drop;

        public Pillar(Material material, MapColor color, SoundType sound, float hardness, float resistance) {
            super(material, color);
            setSoundType(sound);
            setHardness(hardness);
            setResistance(resistance * 5.0F / 3.0F);
        }

        void dropsWithoutSilkTouch(Block other) {
            this.drop = other;
        }

        @Override
        public Item getItemDropped(IBlockState state, Random rand, int fortune) {
            return Item.getItemFromBlock(this.drop != null ? this.drop : this);
        }

        @Override
        public int damageDropped(IBlockState state) {
            return 0;
        }

        @Override
        protected ItemStack getSilkTouchDrop(IBlockState state) {
            return new ItemStack(this);
        }
    }

    /** Stairs that hold water, as the owner's do. */
    public static class Stairs extends ModBuilding.Stairs implements Waterloggable {
        public Stairs(IBlockState base) {
            super(base);
        }
    }

    /** A half slab that holds water, as the owner's does. */
    public static class Slab extends ModBuilding.Slab.Half implements Waterloggable {
        public Slab(MapColor color, SoundType sound) {
            super(Material.ROCK, color, 3.5F, 6.0F, sound);
        }

        /** The set this slab belongs to, from its registry name ("deepslate_tile_slab" -> "deepslate_tile"). */
        String set() {
            String n = getRegistryName().getPath();
            return n.substring(0, n.length() - "_slab".length());
        }

        @Override
        public boolean canHoldWater(IBlockState state, IBlockAccess world, BlockPos pos) {
            return true;
        }
    }

    /** A double slab: a full block, holding no water. */
    public static class DoubleSlab extends ModBuilding.Slab.Double implements Waterloggable {
        public DoubleSlab(Slab half, MapColor color, SoundType sound) {
            super(half, Material.ROCK, color, 3.5F, 6.0F, sound);
        }

        @Override
        public boolean canHoldWater(IBlockState state, IBlockAccess world, BlockPos pos) {
            return false;
        }
    }

    /**
     * A wall with current Java's sides, the owner's states exactly: {@code up} and each side none, low or tall,
     * all worked out in {@link #getActualState}; metadata is always 0.
     *
     * <ul>
     *   <li>A side connects to walls (these and 1.12's), fence gates, panes and iron bars, and solid faces.</li>
     *   <li>A side is tall when the block above has a solid bottom or is a wall connected the same way.</li>
     *   <li>The post is dropped only on a straight run with matching sides and nothing above that needs it.</li>
     * </ul>
     */
    public static class Wall extends Block implements Waterloggable {

        public enum Side implements IStringSerializable {
            NONE("none"), LOW("low"), TALL("tall");

            private final String name;

            Side(String name) {
                this.name = name;
            }

            @Override
            public String getName() {
                return this.name;
            }
        }

        public static final PropertyBool UP = PropertyBool.create("up");
        public static final PropertyEnum<Side> NORTH = PropertyEnum.create("north", Side.class);
        public static final PropertyEnum<Side> EAST = PropertyEnum.create("east", Side.class);
        public static final PropertyEnum<Side> SOUTH = PropertyEnum.create("south", Side.class);
        public static final PropertyEnum<Side> WEST = PropertyEnum.create("west", Side.class);

        private static final double POST_LO = 4 / 16.0, POST_HI = 12 / 16.0;
        private static final double SIDE_LO = 5 / 16.0, SIDE_HI = 11 / 16.0;
        private static final double LOW_TOP = 14 / 16.0;
        /** Entities cannot step or jump over a wall: its collision is a block and a half tall. */
        private static final double COLLISION_TOP = 1.5;

        @SuppressWarnings("deprecation")
        public Wall(IBlockState full) {
            super(full.getMaterial(), full.getBlock().getMapColor(full, null, null));
            Block b = full.getBlock();
            setSoundType(b.getSoundType());
            setHardness(b.getBlockHardness(full, null, null));
            setResistance(b.getExplosionResistance(null) * 5.0F / 3.0F);
            setDefaultState(this.blockState.getBaseState().withProperty(UP, true).withProperty(NORTH, Side.NONE)
                    .withProperty(EAST, Side.NONE).withProperty(SOUTH, Side.NONE).withProperty(WEST, Side.NONE));
        }

        @Override
        protected BlockStateContainer createBlockState() {
            return new BlockStateContainer(this, UP, NORTH, EAST, SOUTH, WEST);
        }

        @Override
        @SuppressWarnings("deprecation")
        public IBlockState getStateFromMeta(int meta) {
            return getDefaultState();
        }

        @Override
        public int getMetaFromState(IBlockState state) {
            return 0;
        }

        public static PropertyEnum<Side> side(EnumFacing f) {
            switch (f) {
                case NORTH:
                    return NORTH;
                case EAST:
                    return EAST;
                case SOUTH:
                    return SOUTH;
                default:
                    return WEST;
            }
        }

        /** @return whether this wall at {@code pos} joins what is on its {@code facing} side */
        protected boolean connects(IBlockAccess world, BlockPos pos, EnumFacing facing) {
            BlockPos other = pos.offset(facing);
            IBlockState state = world.getBlockState(other);
            Block block = state.getBlock();
            if (block instanceof Wall || block instanceof BlockWall || block instanceof BlockPane) {
                return true;
            }
            if (block.canBeConnectedTo(world, other, facing.getOpposite())) {
                return true;
            }
            BlockFaceShape shape = state.getBlockFaceShape(world, other, facing.getOpposite());
            if (shape == BlockFaceShape.MIDDLE_POLE_THICK || shape == BlockFaceShape.MIDDLE_POLE && block instanceof BlockFenceGate) {
                return true;
            }
            return shape == BlockFaceShape.SOLID && !isExceptBlockForAttachWithPiston(block)
                    && block != Blocks.BARRIER && block != Blocks.MELON_BLOCK && block != Blocks.PUMPKIN && block != Blocks.LIT_PUMPKIN;
        }

        @Override
        public boolean canBeConnectedTo(IBlockAccess world, BlockPos pos, EnumFacing facing) {
            return true;
        }

        @Override
        @SuppressWarnings("deprecation")
        public IBlockState getActualState(IBlockState state, IBlockAccess world, BlockPos pos) {
            IBlockState above = world.getBlockState(pos.up());
            boolean aboveSolid = above.getBlockFaceShape(world, pos.up(), EnumFacing.DOWN) == BlockFaceShape.SOLID;
            boolean aboveWall = above.getBlock() instanceof Wall;
            IBlockState aboveActual = aboveWall ? above.getActualState(world, pos.up()) : above;
            Side[] sides = new Side[4];
            for (EnumFacing f : EnumFacing.HORIZONTALS) {
                Side s = Side.NONE;
                if (connects(world, pos, f)) {
                    boolean tall = aboveSolid || aboveWall && aboveActual.getValue(side(f)) != Side.NONE
                            || above.getBlock() instanceof BlockWall;
                    s = tall ? Side.TALL : Side.LOW;
                }
                sides[f.getHorizontalIndex()] = s;
                state = state.withProperty(side(f), s);
            }
            Side south = sides[0], west = sides[1], north = sides[2], east = sides[3];
            boolean straightNS = north != Side.NONE && north == south && east == Side.NONE && west == Side.NONE;
            boolean straightEW = east != Side.NONE && east == west && north == Side.NONE && south == Side.NONE;
            boolean post = !(straightNS || straightEW) || needsPost(above, aboveActual);
            return state.withProperty(UP, post);
        }

        /** @return true for what stands on a wall's post: another post, torches, lanterns, signs, banners, end rods */
        protected boolean needsPost(IBlockState above, IBlockState aboveActual) {
            Block b = above.getBlock();
            if (b instanceof Wall) {
                return aboveActual.getValue(UP);
            }
            if (b instanceof BlockWall) {
                return true;
            }
            if (b == Blocks.TORCH || b == Blocks.REDSTONE_TORCH || b == Blocks.UNLIT_REDSTONE_TORCH
                    || b == Blocks.STANDING_SIGN || b == Blocks.STANDING_BANNER || b == Blocks.END_ROD) {
                return true;
            }
            String path = b.getRegistryName() == null ? "" : b.getRegistryName().getPath();
            return path.endsWith("lantern") || path.endsWith("_torch") || path.equals("torch");
        }

        private static AxisAlignedBB box(IBlockState state, double sideTop, double tallTop, double postTop) {
            double minX = SIDE_LO, maxX = SIDE_HI, minZ = SIDE_LO, maxZ = SIDE_HI, top = 0;
            if (state.getValue(UP)) {
                minX = minZ = POST_LO;
                maxX = maxZ = POST_HI;
                top = postTop;
            }
            Side n = state.getValue(NORTH), s = state.getValue(SOUTH), e = state.getValue(EAST), w = state.getValue(WEST);
            if (n != Side.NONE) {
                minZ = 0;
            }
            if (s != Side.NONE) {
                maxZ = 1;
            }
            if (w != Side.NONE) {
                minX = 0;
            }
            if (e != Side.NONE) {
                maxX = 1;
            }
            for (Side side : new Side[] {n, s, e, w}) {
                if (side == Side.LOW) {
                    top = Math.max(top, sideTop);
                } else if (side == Side.TALL) {
                    top = Math.max(top, tallTop);
                }
            }
            if (top == 0) {
                top = postTop;
            }
            return new AxisAlignedBB(minX, 0, minZ, maxX, top, maxZ);
        }

        @Override
        @SuppressWarnings("deprecation")
        public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
            return box(getActualState(state, world, pos), LOW_TOP, 1, 1);
        }

        @Override
        @SuppressWarnings("deprecation")
        public void addCollisionBoxToList(IBlockState state, World world, BlockPos pos, AxisAlignedBB entityBox,
                                          List<AxisAlignedBB> boxes, @Nullable Entity entity, boolean isActualState) {
            IBlockState actual = isActualState ? state : getActualState(state, world, pos);
            // The post and each side as their own boxes, so a corner does not fill the whole block.
            if (actual.getValue(UP) || noSides(actual)) {
                addCollisionBoxToList(pos, entityBox, boxes, new AxisAlignedBB(POST_LO, 0, POST_LO, POST_HI, COLLISION_TOP, POST_HI));
            }
            if (actual.getValue(NORTH) != Side.NONE) {
                addCollisionBoxToList(pos, entityBox, boxes, new AxisAlignedBB(SIDE_LO, 0, 0, SIDE_HI, COLLISION_TOP, SIDE_HI));
            }
            if (actual.getValue(SOUTH) != Side.NONE) {
                addCollisionBoxToList(pos, entityBox, boxes, new AxisAlignedBB(SIDE_LO, 0, SIDE_LO, SIDE_HI, COLLISION_TOP, 1));
            }
            if (actual.getValue(WEST) != Side.NONE) {
                addCollisionBoxToList(pos, entityBox, boxes, new AxisAlignedBB(0, 0, SIDE_LO, SIDE_HI, COLLISION_TOP, SIDE_HI));
            }
            if (actual.getValue(EAST) != Side.NONE) {
                addCollisionBoxToList(pos, entityBox, boxes, new AxisAlignedBB(SIDE_LO, 0, SIDE_LO, 1, COLLISION_TOP, SIDE_HI));
            }
        }

        private static boolean noSides(IBlockState s) {
            return s.getValue(NORTH) == Side.NONE && s.getValue(SOUTH) == Side.NONE && s.getValue(EAST) == Side.NONE
                    && s.getValue(WEST) == Side.NONE;
        }

        @Override
        @SuppressWarnings("deprecation")
        public boolean isFullCube(IBlockState state) {
            return false;
        }

        @Override
        @SuppressWarnings("deprecation")
        public boolean isOpaqueCube(IBlockState state) {
            return false;
        }

        @Override
        public boolean isPassable(IBlockAccess world, BlockPos pos) {
            return false;
        }

        @Override
        public boolean canPlaceTorchOnTop(IBlockState state, IBlockAccess world, BlockPos pos) {
            return true;
        }

        @Override
        @SuppressWarnings("deprecation")
        public BlockFaceShape getBlockFaceShape(IBlockAccess world, IBlockState state, BlockPos pos, EnumFacing face) {
            return face != EnumFacing.UP && face != EnumFacing.DOWN ? BlockFaceShape.MIDDLE_POLE_THICK : BlockFaceShape.CENTER_BIG;
        }

        @Override
        @SuppressWarnings("deprecation")
        public boolean shouldSideBeRendered(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
            return side != EnumFacing.DOWN || super.shouldSideBeRendered(state, world, pos, side);
        }
    }
}
