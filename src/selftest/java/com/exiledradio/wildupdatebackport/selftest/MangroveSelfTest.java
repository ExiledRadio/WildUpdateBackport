package com.exiledradio.wildupdatebackport.selftest;

import com.exiledradio.backportcore.trade.TradeRegistry;
import com.exiledradio.wildupdatebackport.common.Content;
import com.exiledradio.wildupdatebackport.common.Owners;
import com.exiledradio.wildupdatebackport.mangrove.BlockMangroveLeaves;
import com.exiledradio.wildupdatebackport.mangrove.BlockPropagule;
import com.exiledradio.wildupdatebackport.mangrove.MangroveContent;
import com.exiledradio.wildupdatebackport.mangrove.MangroveSign;
import com.exiledradio.wildupdatebackport.mangrove.WorldGenMangroveTree;

import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.BlockLeaves;
import net.minecraft.block.BlockLog;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemDye;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.village.MerchantRecipe;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;
import net.minecraftforge.oredict.OreDictionary;

import java.util.Random;

/** Stream M: the mangrove wood set. */
public final class MangroveSelfTest implements SelfTest {

    private static final String[] BLOCKS = {"mangrove_log", "mangrove_wood", "stripped_mangrove_log", "stripped_mangrove_wood",
            "mangrove_planks", "mangrove_stairs", "mangrove_slab", "mangrove_double_slab", "mangrove_fence",
            "mangrove_fence_gate", "mangrove_door", "mangrove_trapdoor", "mangrove_button", "mangrove_pressure_plate",
            "mangrove_sign", "mangrove_wall_sign", "mangrove_leaves", "mangrove_propagule", "potted_mangrove_propagule",
            "mangrove_roots", "muddy_mangrove_roots"};

    @Override
    public String name() {
        return "mangrove";
    }

    private static Block block(String name) {
        return Owners.ourBlock(name);
    }

    private static Item item(String name) {
        return Owners.ours(name);
    }

    @Override
    public void run(Check c) {
        WorldServer world = c.world;

        // 0. the roots' break particles: 1.12.2 cannot resolve a '#' naming the model's own texture (missingno)
        for (String model : new String[]{"mangrove_roots", "muddy_mangrove_roots"}) {
            c.ok(!selfReferencedParticle(model), model + ": the particle texture is named, not '#' one of the model's own");
        }

        // 0. the roots' destroy crack (RootsCrack): the model's layers all take it, as in 1.19 ------------
        try {
            RootsCrackCheck.run(c);
        } catch (Exception e) {
            c.fail("roots crack check threw " + e);
        }
        IBlockState roots = MangroveContent.roots();
        if (c.ok(roots != null, "mangrove roots registered for the crack check")) {
            c.ok(roots instanceof net.minecraftforge.common.property.IExtendedBlockState, "mangrove roots' states carry the crack view property");
            c.ok(roots.getBlock().getExtendedState(roots, world, BlockPos.ORIGIN) == roots,
                    "mangrove roots' extended state is the plain state outside the client's crack pass");
            c.eq(0, roots.getBlock().getMetaFromState(roots), "mangrove roots keep meta 0");
        }

        // 1. registration and metadata ----------------------------------------------------------
        for (String name : BLOCKS) {
            Block b = block(name);
            if (!c.ok(b != null, name + " is registered")) {
                continue;
            }
            for (int meta = 0; meta < 16; meta++) {
                @SuppressWarnings("deprecation")
                IBlockState s = b.getStateFromMeta(meta);
                @SuppressWarnings("deprecation")
                IBlockState back = b.getStateFromMeta(b.getMetaFromState(s));
                if (back != s) {
                    c.fail(name + " metadata " + meta + " does not round-trip: " + s + " -> " + back);
                }
            }
        }
        for (String name : new String[] {"mangrove_log", "mangrove_planks", "mangrove_slab", "mangrove_door", "mangrove_sign",
                "mangrove_propagule", "mangrove_leaves", "mangrove_roots", "muddy_mangrove_roots", "mangrove_fence"}) {
            c.ok(item(name) != null, name + " has an item");
        }
        c.eq(null, item("mangrove_double_slab"), "the double slab has no item");
        c.eq(null, item("mangrove_wall_sign"), "the wall sign has no item");
        c.eq(16, item("mangrove_sign").getItemStackLimit(new ItemStack(item("mangrove_sign"))), "signs stack to 16");

        // hanging propagule metadata is 8 + age, standing 0 + stage
        BlockPropagule propagule = (BlockPropagule) block("mangrove_propagule");
        c.eq(8 + 3, propagule.getMetaFromState(propagule.hanging(3)), "hanging age 3 is meta 11");
        c.eq(4, propagule.getDefaultState().getValue(BlockPropagule.AGE).intValue(), "a standing propagule is age 4");

        // 2. numbers ----------------------------------------------------------------------------
        c.eq(5, Blocks.FIRE.getFlammability(block("mangrove_log")), "log burns as a log");
        c.eq(20, Blocks.FIRE.getFlammability(block("mangrove_planks")), "planks burn as planks");
        c.eq(60, Blocks.FIRE.getFlammability(block("mangrove_leaves")), "leaves burn as leaves");
        c.eq(20, Blocks.FIRE.getFlammability(block("mangrove_roots")), "roots burn as 1.19's");
        c.eq(0, Blocks.FIRE.getFlammability(block("muddy_mangrove_roots")), "muddy roots do not burn");
        c.eq(300, TileEntityFurnace.getItemBurnTime(new ItemStack(item("mangrove_planks"))), "planks fuel 300");
        c.eq(150, TileEntityFurnace.getItemBurnTime(new ItemStack(item("mangrove_slab"))), "slab fuel 150");
        c.eq(200, TileEntityFurnace.getItemBurnTime(new ItemStack(item("mangrove_door"))), "door fuel 200");
        c.eq(200, TileEntityFurnace.getItemBurnTime(new ItemStack(item("mangrove_sign"))), "sign fuel 200");
        c.eq(100, TileEntityFurnace.getItemBurnTime(new ItemStack(item("mangrove_button"))), "button fuel 100");
        c.eq(100, TileEntityFurnace.getItemBurnTime(new ItemStack(item("mangrove_propagule"))), "propagule fuel 100");
        c.eq(300, TileEntityFurnace.getItemBurnTime(new ItemStack(item("mangrove_roots"))), "roots fuel 300");
        c.ok(Math.abs(block("mangrove_planks").getExplosionResistance(null) - 3.0F) < 0.01F, "planks resistance 3");
        c.ok(Math.abs(block("mangrove_roots").getExplosionResistance(null) - 0.7F) < 0.01F, "roots resistance 0.7");
        c.eq(Items.COAL, FurnaceRecipes.instance().getSmeltingResult(new ItemStack(item("mangrove_log"))).getItem(), "logs smelt to charcoal");
        c.ok(has("logWood", item("stripped_mangrove_wood")) && has("plankWood", item("mangrove_planks"))
                && has("treeSapling", item("mangrove_propagule")) && has("treeLeaves", item("mangrove_leaves")), "ore dictionary names");

        // 3. recipes ----------------------------------------------------------------------------
        ItemStack log = new ItemStack(item("mangrove_log"));
        ItemStack planks = new ItemStack(item("mangrove_planks"));
        ItemStack stick = new ItemStack(Items.STICK);
        Crafting.expect(c, Crafting.grid(1, 1, log), new ItemStack(item("mangrove_planks"), 4), "planks from a log");
        Crafting.expect(c, Crafting.grid(1, 1, new ItemStack(item("stripped_mangrove_wood"))), new ItemStack(item("mangrove_planks"), 4), "planks from stripped wood");
        Crafting.expect(c, Crafting.grid(2, 2, log, log, log, log), new ItemStack(item("mangrove_wood"), 3), "wood from logs");
        Crafting.expect(c, Crafting.grid(1, 2, planks, planks), new ItemStack(Items.STICK, 4), "vanilla sticks from mangrove planks");
        Crafting.expect(c, Crafting.grid(2, 2, planks, planks, planks, planks), new ItemStack(Blocks.CRAFTING_TABLE), "a crafting table from mangrove planks");
        Crafting.expect(c, Crafting.grid(3, 1, planks, planks, planks), new ItemStack(item("mangrove_slab"), 6), "slabs");
        Crafting.expect(c, Crafting.grid(2, 3, planks, planks, planks, planks, planks, planks), new ItemStack(item("mangrove_door"), 3), "doors");
        Crafting.expect(c, Crafting.grid(3, 2, planks, planks, planks, planks, planks, planks), new ItemStack(item("mangrove_trapdoor"), 2), "trapdoors");
        Crafting.expect(c, Crafting.grid(3, 2, planks, stick, planks, planks, stick, planks), new ItemStack(item("mangrove_fence"), 3), "fences");
        Crafting.expect(c, Crafting.grid(3, 2, stick, planks, stick, stick, planks, stick), new ItemStack(item("mangrove_fence_gate")), "a fence gate");
        Crafting.expect(c, Crafting.grid(3, 3, planks, planks, planks, planks, planks, planks, ItemStack.EMPTY, stick, ItemStack.EMPTY),
                new ItemStack(item("mangrove_sign"), 3), "signs");
        Crafting.expect(c, Crafting.grid(1, 1, planks), new ItemStack(item("mangrove_button")), "a button");
        Crafting.expect(c, Crafting.grid(2, 1, planks, planks), new ItemStack(item("mangrove_pressure_plate")), "a pressure plate");
        Crafting.expect(c, Crafting.grid(3, 3, planks, ItemStack.EMPTY, ItemStack.EMPTY, planks, planks, ItemStack.EMPTY, planks, planks, planks),
                new ItemStack(item("mangrove_stairs"), 4), "stairs");

        // 4. a real player: stripping, doors, slabs, signs, pots --------------------------------
        BlockPos area = c.area();
        EntityPlayerMP player = Players.real(world, "mangrove");
        player.interactionManager.setGameType(GameType.SURVIVAL);
        player.setPosition(area.getX() + 0.5, area.getY(), area.getZ() - 3);

        BlockPos logPos = area.add(2, 0, 2);
        world.setBlockState(logPos, block("mangrove_log").getDefaultState().withProperty(BlockLog.LOG_AXIS, BlockLog.EnumAxis.X));
        ItemStack axe = new ItemStack(Items.IRON_AXE);
        player.setHeldItem(EnumHand.MAIN_HAND, axe);
        player.interactionManager.processRightClickBlock(player, world, axe, EnumHand.MAIN_HAND, logPos, EnumFacing.UP, 0.5F, 1.0F, 0.5F);
        IBlockState stripped = world.getBlockState(logPos);
        c.eq(block("stripped_mangrove_log"), stripped.getBlock(), "an axe strips a mangrove log");
        c.eq(BlockLog.EnumAxis.X, stripped.getValue(BlockLog.LOG_AXIS), "stripping keeps the axis");
        c.eq(1, axe.getItemDamage(), "stripping wears the axe");
        world.setBlockState(logPos.east(), block("mangrove_wood").getDefaultState());
        player.interactionManager.processRightClickBlock(player, world, axe, EnumHand.MAIN_HAND, logPos.east(), EnumFacing.UP, 0.5F, 1.0F, 0.5F);
        c.eq(block("stripped_mangrove_wood"), world.getBlockState(logPos.east()).getBlock(), "an axe strips mangrove wood");
        player.interactionManager.processRightClickBlock(player, world, axe, EnumHand.MAIN_HAND, logPos, EnumFacing.UP, 0.5F, 1.0F, 0.5F);
        c.eq(block("stripped_mangrove_log"), world.getBlockState(logPos).getBlock(), "a stripped log stays stripped");

        BlockPos doorAt = area.add(5, -1, 2);
        place(player, world, new ItemStack(item("mangrove_door")), doorAt);
        c.eq(block("mangrove_door"), world.getBlockState(doorAt.up()).getBlock(), "the door's lower half is placed");
        c.eq(block("mangrove_door"), world.getBlockState(doorAt.up(2)).getBlock(), "the door's upper half is placed");
        player.setHeldItem(EnumHand.MAIN_HAND, ItemStack.EMPTY);
        player.interactionManager.tryHarvestBlock(doorAt.up());
        c.ok(world.isAirBlock(doorAt.up()) && world.isAirBlock(doorAt.up(2)), "breaking a door removes both halves");
        c.eq(1, Drops.count(world, doorAt.up(), item("mangrove_door")), "a door drops one door");

        BlockPos slabAt = area.add(8, -1, 2);
        place(player, world, new ItemStack(item("mangrove_slab"), 2), slabAt);
        c.eq(BlockSlab.EnumBlockHalf.BOTTOM, world.getBlockState(slabAt.up()).getValue(BlockSlab.HALF), "a slab placed on the floor is a bottom slab");
        place(player, world, new ItemStack(item("mangrove_slab"), 2), slabAt.up());
        c.eq(block("mangrove_double_slab"), world.getBlockState(slabAt.up()).getBlock(), "a second slab makes a double slab");
        player.setHeldItem(EnumHand.MAIN_HAND, ItemStack.EMPTY);
        player.interactionManager.tryHarvestBlock(slabAt.up());
        c.eq(2, Drops.count(world, slabAt.up(), item("mangrove_slab")), "a double slab drops two slabs");

        BlockPos signAt = area.add(11, -1, 2);
        place(player, world, new ItemStack(item("mangrove_sign")), signAt);
        TileEntity tile = world.getTileEntity(signAt.up());
        c.eq(block("mangrove_sign"), world.getBlockState(signAt.up()).getBlock(), "a sign placed on top stands");
        c.ok(tile instanceof MangroveSign.Tile && tile instanceof TileEntitySign, "the sign's tile is a vanilla sign tile");
        world.setBlockState(signAt.add(0, 1, 2), Blocks.STONE.getDefaultState());
        place(player, world, new ItemStack(item("mangrove_sign")), signAt.add(0, 1, 2), EnumFacing.NORTH);
        c.eq(block("mangrove_wall_sign"), world.getBlockState(signAt.add(0, 1, 1)).getBlock(), "a sign placed on a side hangs");
        player.setHeldItem(EnumHand.MAIN_HAND, ItemStack.EMPTY);
        player.interactionManager.tryHarvestBlock(signAt.up());
        c.eq(1, Drops.count(world, signAt.up(), item("mangrove_sign")), "a standing sign drops a sign");

        BlockPos potAt = area.add(14, 0, 2);
        world.setBlockState(potAt, Blocks.FLOWER_POT.getDefaultState());
        ItemStack prop = new ItemStack(item("mangrove_propagule"), 2);
        player.setHeldItem(EnumHand.MAIN_HAND, prop);
        player.interactionManager.processRightClickBlock(player, world, prop, EnumHand.MAIN_HAND, potAt, EnumFacing.UP, 0.5F, 0.5F, 0.5F);
        c.eq(block("potted_mangrove_propagule"), world.getBlockState(potAt).getBlock(), "a propagule goes into a flower pot");
        c.eq(1, prop.getCount(), "potting uses one propagule");
        player.setHeldItem(EnumHand.MAIN_HAND, ItemStack.EMPTY);
        player.inventory.clear();
        player.interactionManager.processRightClickBlock(player, world, ItemStack.EMPTY, EnumHand.MAIN_HAND, potAt, EnumFacing.UP, 0.5F, 0.5F, 0.5F);
        c.eq(Blocks.FLOWER_POT, world.getBlockState(potAt).getBlock(), "an empty hand takes it back out");
        c.ok(player.inventory.hasItemStack(new ItemStack(item("mangrove_propagule"))), "the propagule goes back to the player");

        // 5. leaves -----------------------------------------------------------------------------
        BlockMangroveLeaves leaves = (BlockMangroveLeaves) block("mangrove_leaves");
        Random random = new Random(7);
        int sticks = 0;
        for (int i = 0; i < 20000; i++) {
            NonNullList<ItemStack> drops = NonNullList.create();
            leaves.getDrops(drops, world, BlockPos.ORIGIN, leaves.getDefaultState(), 0);
            for (ItemStack s : drops) {
                c.ok(s.getItem() == Items.STICK, "leaves drop only sticks");
                sticks++;
            }
        }
        c.ok(sticks > 200 && sticks < 1100, "about 2% of leaves drop sticks: " + sticks + " sticks from 20000");
        c.eq(1, leaves.onSheared(new ItemStack(Items.SHEARS), world, BlockPos.ORIGIN, 0).size(), "shears take the leaves");
        c.eq(false, leaves.getStateForPlacement(world, BlockPos.ORIGIN, EnumFacing.UP, 0, 0, 0, 0, player).getValue(BlockLeaves.DECAYABLE),
                "placed leaves never decay");

        BlockPos leafAt = area.add(2, 6, 8);
        world.setBlockState(leafAt, leaves.getDefaultState());
        ItemStack meal = new ItemStack(Items.DYE, 4, 15);
        player.setHeldItem(EnumHand.MAIN_HAND, meal);
        c.ok(ItemDye.applyBonemeal(meal, world, leafAt, player, EnumHand.MAIN_HAND), "bone meal works on mangrove leaves");
        IBlockState hanging = world.getBlockState(leafAt.down());
        c.ok(hanging.getBlock() == propagule && hanging.getValue(BlockPropagule.HANGING) && hanging.getValue(BlockPropagule.AGE) == 0,
                "bone meal grows a hanging propagule, age 0: " + hanging);
        c.ok(!ItemDye.applyBonemeal(meal, world, leafAt, player, EnumHand.MAIN_HAND), "no second one while something hangs below");
        for (int i = 0; i < 4; i++) {
            propagule.updateTick(world, leafAt.down(), world.getBlockState(leafAt.down()), random);
        }
        c.eq(4, world.getBlockState(leafAt.down()).getValue(BlockPropagule.AGE).intValue(), "a hanging propagule grows one age per random tick");
        NonNullList<ItemStack> young = NonNullList.create();
        propagule.getDrops(young, world, leafAt.down(), propagule.hanging(2), 0);
        c.eq(0, young.size(), "a young hanging propagule drops nothing");
        NonNullList<ItemStack> grown = NonNullList.create();
        propagule.getDrops(grown, world, leafAt.down(), propagule.hanging(4), 0);
        c.eq(1, grown.size(), "a grown one drops itself");
        world.setBlockToAir(leafAt);
        Pools.settle(world);
        c.ok(world.getBlockState(leafAt.down()).getBlock() != propagule, "a hanging propagule falls without its leaves");
        Drops.count(world, leafAt.down(), item("mangrove_propagule"));

        // 6. standing propagule: soil -----------------------------------------------------------
        BlockPos soil = area.add(2, -1, 12);
        for (Block b : new Block[] {Blocks.GRASS, Blocks.DIRT, Blocks.CLAY, Blocks.FARMLAND, Blocks.STONE, Blocks.SAND}) {
            world.setBlockState(soil, b.getDefaultState());
            boolean can = propagule.canPlaceBlockAt(world, soil.up());
            c.eq(b != Blocks.STONE && b != Blocks.SAND, can, "a propagule on " + b.getRegistryName());
        }

        // 7. growing into a tree ----------------------------------------------------------------
        int trees = 0;
        int tries = 0;
        for (int t = 0; t < 6; t++) {
            BlockPos floor = Pools.pool(c, 2);
            for (int x = 0; x < 12; x++) {
                for (int z = 0; z < 12; z++) {
                    world.setBlockState(floor.add(x, 0, z), Blocks.DIRT.getDefaultState(), 2);
                }
            }
            BlockPos at = floor.add(6, 1, 6);
            world.setBlockState(at, propagule.getDefaultState());
            for (int i = 0; i < 40 && world.getBlockState(at).getBlock() == propagule; i++) {
                propagule.grow(world, random, at, world.getBlockState(at));
                tries++;
            }
            if (world.getBlockState(at).getBlock() != propagule) {
                trees++;
                checkTree(c, world, floor, t);
            }
        }
        c.ok(trees >= 5, "propagules in water grow into mangroves: " + trees + " of 6 (" + tries + " bone meals)");

        // 8. the generator straight: on land, in deep water, and refusing with no ground -------
        int onLand = 0;
        for (int t = 0; t < 10; t++) {
            BlockPos a = c.area().add(16, 0, 16);
            world.setBlockState(a.down(), Blocks.GRASS.getDefaultState());
            if (new WorldGenMangroveTree(false, t % 2 == 0).generate(world, new Random(100 + t), a)) {
                onLand++;
            }
        }
        c.ok(onLand >= 8, "mangroves generate on dry grass: " + onLand + " of 10");
        BlockPos sky = c.area().add(16, 40, 16);
        c.ok(!new WorldGenMangroveTree(false, false).generate(world, new Random(3), sky), "no tree without ground for its roots");

        // 9. the wandering trader ----------------------------------------------------------------
        boolean trade = false;
        for (TradeRegistry.TraderTrade t : TradeRegistry.traderTrades(TradeRegistry.TraderPool.ORDINARY)) {
            MerchantRecipe r = t.offer(random);
            if (r != null && r.getItemToSell().getItem() == item("mangrove_propagule")) {
                trade = r.getItemToBuy().getItem() == Items.EMERALD && r.getItemToBuy().getCount() == 5 && r.getMaxTradeUses() == 8;
            }
        }
        c.ok(trade, "the wandering trader sells a propagule for 5 emeralds, 8 times");
        c.ok(Content.ignored(block("mangrove_door")).contains(BlockDoor.POWERED), "the door's power is left out of its blockstate");
    }

    /** @return whether the block model's particle is '#x' for a texture x of its own textures (missingno in 1.12.2) */
    private static boolean selfReferencedParticle(String model) {
        java.io.InputStream in = MangroveSelfTest.class.getResourceAsStream("/assets/wildupdatebackport/models/block/" + model + ".json");
        if (in == null) {
            return true;
        }
        try {
            com.google.gson.JsonObject json = new com.google.gson.JsonParser().parse(
                    new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            if (!json.has("textures")) {
                return false;
            }
            com.google.gson.JsonObject t = json.getAsJsonObject("textures");
            String p = t.has("particle") ? t.get("particle").getAsString() : "";
            return p.startsWith("#") && t.has(p.substring(1));
        } finally {
            try {
                in.close();
            } catch (java.io.IOException ignored) {
            }
        }
    }

    /** Checks one grown tree: logs, leaves near logs, roots reaching the dirt floor. */
    private static void checkTree(Check c, WorldServer world, BlockPos floor, int n) {
        Block log = Owners.ourBlock("mangrove_log");
        Block leaves = Owners.ourBlock("mangrove_leaves");
        Block roots = Owners.ourBlock("mangrove_roots");
        int logs = 0;
        int leafCount = 0;
        int rootCount = 0;
        int rootsOnFloor = 0;
        int farLeaves = 0;
        for (BlockPos p : BlockPos.getAllInBox(floor.add(-6, 1, -6), floor.add(18, 30, 18))) {
            Block b = world.getBlockState(p).getBlock();
            if (b == log) {
                logs++;
            } else if (b == leaves) {
                leafCount++;
                IBlockState s = world.getBlockState(p).withProperty(BlockLeaves.CHECK_DECAY, true);
                world.setBlockState(p, s, 4);
                leaves.updateTick(world, p, s, new Random(1));
                if (world.getBlockState(p).getBlock() != leaves) {
                    farLeaves++;
                }
            } else if (b == roots) {
                rootCount++;
                if (p.getY() == floor.getY() + 1) {
                    rootsOnFloor++;
                }
            }
        }
        if (n == 0) {
            // cut every log; ticking the leaves repeatedly must clear the whole canopy
            for (BlockPos p : BlockPos.getAllInBox(floor.add(-6, 1, -6), floor.add(18, 30, 18))) {
                if (world.getBlockState(p).getBlock() == log) {
                    world.destroyBlock(p, false);
                }
            }
            for (int round = 0; round < 12; round++) {
                for (BlockPos p : BlockPos.getAllInBox(floor.add(-6, 1, -6), floor.add(18, 30, 18))) {
                    IBlockState s = world.getBlockState(p);
                    if (s.getBlock() == leaves) {
                        leaves.updateTick(world, p, s, new Random(round));
                    }
                }
            }
            int left = 0;
            for (BlockPos p : BlockPos.getAllInBox(floor.add(-6, 1, -6), floor.add(18, 30, 18))) {
                if (world.getBlockState(p).getBlock() == leaves) {
                    left++;
                }
            }
            c.eq(0, left, "a felled mangrove's canopy decays completely");
            Drops.count(world, floor.add(6, 5, 6), Items.STICK);
        }
        c.ok(logs >= 3, "tree " + n + " has logs: " + logs);
        c.ok(leafCount >= 20, "tree " + n + " has leaves: " + leafCount);
        c.ok(rootCount >= 5, "tree " + n + " has roots: " + rootCount);
        c.ok(rootsOnFloor >= 1, "tree " + n + "'s roots reach the floor: " + rootsOnFloor);
        c.eq(0, farLeaves, "tree " + n + ": no leaf of a fresh tree decays");
    }

    private static boolean has(String ore, Item item) {
        for (ItemStack s : OreDictionary.getOres(ore)) {
            if (s.getItem() == item) {
                return true;
            }
        }
        return false;
    }

    private static void place(EntityPlayerMP player, WorldServer world, ItemStack stack, BlockPos on) {
        place(player, world, stack, on, EnumFacing.UP);
    }

    private static void place(EntityPlayerMP player, WorldServer world, ItemStack stack, BlockPos on, EnumFacing face) {
        player.setHeldItem(EnumHand.MAIN_HAND, stack);
        float hy = face == EnumFacing.UP ? 1.0F : 0.5F;
        stack.getItem().onItemUse(player, world, on, EnumHand.MAIN_HAND, face, 0.5F, hy, 0.5F);
    }
}
