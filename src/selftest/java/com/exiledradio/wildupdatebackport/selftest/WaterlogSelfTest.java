package com.exiledradio.wildupdatebackport.selftest;

import com.exiledradio.backportcore.waterlog.Waterloggable;
import com.exiledradio.backportcore.waterlog.Waterlogging;
import com.exiledradio.wildupdatebackport.WildUpdateBackport;
import com.exiledradio.wildupdatebackport.common.Owners;
import com.exiledradio.wildupdatebackport.mangrove.BlockPropagule;
import com.exiledradio.wildupdatebackport.mangrove.WorldGenMangroveTree;
import com.exiledradio.wildupdatebackport.sculk.BlockSculkSensor;
import com.exiledradio.wildupdatebackport.sculk.BlockSculkVein;
import com.exiledradio.wildupdatebackport.sculk.SculkBehaviour;
import com.exiledradio.wildupdatebackport.sculk.SculkContent;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;
import net.minecraft.world.IWorldEventListener;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;

/**
 * Stream Wl: 1.19.2's waterlogging over Backport Core's {@code waterlog} API, with Fluidlogged API and without it.
 *
 * <p>With it: exactly the blocks of ours 1.19.2 waterlogs hold a water source (never flowing water or lava), and
 * vanilla leaves too; placing into a source keeps it, into flowing water replaces it; flowing water does not run
 * into a dry block; buckets; breaking keeps the water; the sensor clicks silently in water; hanging and standing
 * propagules in water; a mangrove grown in water keeps it in its roots and leaves; sculk veins and growths go into
 * water sources and leave them behind. Without it ({@code -PnoFluidlogged}): nothing holds water, and veins and
 * growths stay out of water, as before 1.19.
 */
public final class WaterlogSelfTest implements SelfTest {

    @Override
    public String name() {
        return "waterlog";
    }

    /** @return whether 1.19.2 makes this block of ours waterloggable (a double slab never) */
    static boolean waterloggable(String path) {
        if (path.endsWith("_double_slab")) {
            return false;
        }
        return path.endsWith("_slab") || path.endsWith("_stairs") || path.endsWith("_wall")
                || path.equals("mangrove_fence") || path.equals("mangrove_trapdoor") || path.equals("mangrove_sign")
                || path.equals("mangrove_wall_sign") || path.equals("mangrove_leaves") || path.equals("mangrove_roots")
                || path.equals("mangrove_propagule") || path.equals("sculk_sensor") || path.equals("sculk_shrieker")
                || path.equals("sculk_vein");
    }

    @Override
    public void run(Check c) throws Exception {
        boolean fl = Waterlogging.isAvailable();
        WildUpdateBackport.LOGGER.info("SELFTEST waterlog: Fluidlogged API {}", fl ? "installed" : "absent");
        classes(c);
        if (fl) {
            states(c);
            placing(c);
            flowing(c);
            buckets(c);
            sensor(c);
            propagules(c);
            tree(c);
            sculk(c);
        } else {
            dry(c);
        }
    }

    private static Block ours(String name) {
        return ForgeRegistries.BLOCKS.getValue(new ResourceLocation(WildUpdateBackport.MOD_ID, name));
    }

    // --- 1. Which blocks are Waterloggable (both ways) ---------------------------------------------------------

    private static void classes(Check c) {
        List<String> wrong = new ArrayList<String>();
        int count = 0;
        for (Block block : ForgeRegistries.BLOCKS) {
            ResourceLocation name = block.getRegistryName();
            if (name == null || !WildUpdateBackport.MOD_ID.equals(name.getNamespace())) {
                continue;
            }
            boolean expected = waterloggable(name.getPath()) || name.getPath().endsWith("_double_slab") && block instanceof Waterloggable;
            if ((block instanceof Waterloggable) != expected) {
                wrong.add(name.getPath());
            }
            if (block instanceof Waterloggable && waterloggable(name.getPath())) {
                count++;
            }
        }
        c.ok(wrong.isEmpty(), "exactly the blocks 1.19.2 waterlogs are Waterloggable (" + count + "), wrong: " + wrong);
        for (String n : new String[] {"mangrove_door", "mangrove_fence_gate", "mangrove_button", "mangrove_pressure_plate",
                "frogspawn", "potted_mangrove_propagule", "muddy_mangrove_roots", "sculk_catalyst", "mud"}) {
            Block b = ours(n);
            c.ok(b == null || !(b instanceof Waterloggable), n + " is not waterloggable, as in 1.19.2");
        }
    }

    // --- 2. Every state of ours, with Fluidlogged API ----------------------------------------------------------

    private static void states(Check c) throws Exception {
        WorldServer world = c.world;
        BlockPos probe = c.area().add(16, 1, 16);
        int states = 0, holding = 0;
        List<String> wrong = new ArrayList<String>();
        for (Block block : ForgeRegistries.BLOCKS) {
            ResourceLocation name = block.getRegistryName();
            if (name == null || !WildUpdateBackport.MOD_ID.equals(name.getNamespace())) {
                continue;
            }
            boolean expected = waterloggable(name.getPath());
            for (IBlockState state : block.getBlockState().getValidStates()) {
                states++;
                boolean can = Waterlogging.canHoldWater(state, world, probe);
                holding += can ? 1 : 0;
                if (can != expected) {
                    wrong.add(state + " holds water: " + can);
                }
                String other = WaterlogFluidChecks.otherFluids(state, world, probe);
                if (other != null) {
                    wrong.add(state + " takes " + other);
                }
            }
        }
        c.ok(wrong.isEmpty(), states + " states of ours checked against 1.19.2 (" + holding + " hold a water source), wrong: "
                + (wrong.size() > 20 ? wrong.subList(0, 20) + " ..." : wrong));
        WildUpdateBackport.LOGGER.info("SELFTEST waterlog: {} states of ours, {} hold a water source, none flowing water or lava", states, holding);

        // Vanilla leaves are Fluidlogged API's own (its built-in list waterlogs every BlockLeaves), so they hold
        // a source as 1.19's do; what else its list lets in is its config's business, not ours.
        int leaves = 0;
        for (Block leaf : new Block[] {Blocks.LEAVES, Blocks.LEAVES2}) {
            for (IBlockState state : leaf.getBlockState().getValidStates()) {
                leaves += Waterlogging.canHoldWater(state, world, probe) ? 1 : 0;
            }
        }
        c.eq(24, leaves, "every vanilla leaf state holds a water source, as 1.19's (Fluidlogged API's own list)");
        c.ok(!Waterlogging.canHoldWater(Blocks.LOG.getDefaultState(), world, probe), "vanilla logs still hold none");
    }

    // --- 3. Placing into water ----------------------------------------------------------------------------------

    /** A 14x14 stone-walled pool of still water one deep on a fresh area's floor. @return its corner, one below the water */
    static BlockPos pool(Check c, int depth) {
        BlockPos corner = c.area();
        for (int x = 0; x < 14; x++) {
            for (int z = 0; z < 14; z++) {
                boolean edge = x == 0 || z == 0 || x == 13 || z == 13;
                for (int y = 0; y < depth; y++) {
                    c.world.setBlockState(corner.add(x, y, z), (edge ? Blocks.STONE : Blocks.WATER).getDefaultState(), 2 | 16);
                }
            }
        }
        return corner.add(0, -1, 0);
    }

    private static void placing(Check c) {
        WorldServer world = c.world;
        BlockPos pool = pool(c, 1);
        List<IBlockState> wet = new ArrayList<IBlockState>();
        for (String n : new String[] {"mangrove_stairs", "mangrove_fence", "mangrove_trapdoor", "mangrove_sign", "mangrove_wall_sign",
                "mangrove_leaves", "mangrove_roots", "mud_brick_stairs", "mud_brick_wall", "sculk_sensor", "sculk_shrieker"}) {
            Block b = ours(n);
            if (c.ok(b != null, n + " exists")) {
                wet.add(b.getDefaultState());
            }
        }
        wet.add(ours("mangrove_slab").getDefaultState().withProperty(BlockSlab.HALF, BlockSlab.EnumBlockHalf.TOP));
        wet.add(ours("mud_brick_slab").getDefaultState());
        wet.add(Blocks.LEAVES.getDefaultState());
        for (int i = 0; i < wet.size(); i++) {
            BlockPos p = pool.add(1 + i % 12, 1, 1 + i / 12);
            IBlockState s = wet.get(i);
            world.setBlockState(p, s, 2);
            c.ok(world.getBlockState(p).getBlock() == s.getBlock() && Waterlogging.hasWaterSource(world, p),
                    s.getBlock().getRegistryName() + " placed into a water source keeps it");
        }
        BlockPos v = pool.add(1, 1, 4);
        c.ok(BlockSculkVein.set(world, v, BlockSculkVein.bit(EnumFacing.DOWN), 2), "a sculk vein is set on the pool floor");
        c.ok(BlockSculkVein.isVein(world.getBlockState(v)) && Waterlogging.hasWaterSource(world, v), "a sculk vein in a source keeps it");
        for (String n : new String[] {"mangrove_double_slab", "mud_brick_double_slab", "mangrove_fence_gate", "mangrove_door", "sculk_catalyst"}) {
            BlockPos p = pool.add(2 + 2 * java.util.Arrays.asList("mangrove_double_slab", "mud_brick_double_slab", "mangrove_fence_gate",
                    "mangrove_door", "sculk_catalyst").indexOf(n), 1, 6);
            world.setBlockState(p, ours(n).getDefaultState(), 2);
            c.ok(!Waterlogging.hasWater(world, p), n + " placed into water replaces it");
        }
        // Breaking a waterlogged block leaves its water.
        BlockPos stairs = pool.add(1, 1, 1);
        world.destroyBlock(stairs, false);
        c.ok(world.getBlockState(stairs).getMaterial() == net.minecraft.block.material.Material.WATER && Waterlogging.hasWaterSource(world, stairs),
                "breaking waterlogged mangrove stairs leaves the water source");
    }

    // --- 4. Flowing water -------------------------------------------------------------------------------------

    private static void flowing(Check c) {
        WorldServer world = c.world;
        BlockPos runs = c.area();
        String[] names = {"mangrove_stairs", "mangrove_roots", "mangrove_leaves", "sculk_sensor", "mud_brick_wall"};
        for (int i = 0; i < names.length; i++) {
            BlockPos p = runs.add(2 + 2 * i, 0, 2);
            world.setBlockState(p, Blocks.FLOWING_WATER.getDefaultState().withProperty(BlockLiquid.LEVEL, 3), 2);
            Block b = ours(names[i]);
            world.setBlockState(p, b.getDefaultState(), 2);
            c.ok(world.getBlockState(p).getBlock() == b && !Waterlogging.hasWater(world, p), names[i] + " placed into flowing water replaces it");
        }
        BlockPos spring = c.area().add(10, 0, 10);
        world.setBlockState(spring.east(), ours("mangrove_fence").getDefaultState(), 3);
        world.setBlockState(spring.west(), ours("mangrove_leaves").getDefaultState(), 3);
        world.setBlockState(spring.north(), ours("sculk_shrieker").getDefaultState(), 3);
        world.setBlockState(spring, Blocks.FLOWING_WATER.getDefaultState(), 3);
        settle(world);
        c.ok(Waterlogging.hasWater(world, spring.south()), "the spring's water spreads");
        c.ok(!Waterlogging.hasWater(world, spring.east()), "not into a dry mangrove fence");
        c.ok(!Waterlogging.hasWater(world, spring.west()), "nor dry mangrove leaves");
        c.ok(!Waterlogging.hasWater(world, spring.north()), "nor a dry sculk shrieker");
        // Each in an area of its own: water kept in full-block roots stays in them, as 1.19's full shape keeps it;
        // a bottom slab lets it flow out. Leaves: Fluidlogged API's list lets fluid out of any BlockLeaves.
        BlockPos roots = c.area().add(16, 0, 16);
        Waterlogging.placeWithWater(world, roots, ours("mangrove_roots").getDefaultState(), 3);
        settle(world);
        c.ok(Waterlogging.hasWaterSource(world, roots), "waterlogged mangrove roots keep their source");
        boolean dry = true;
        for (EnumFacing side : EnumFacing.HORIZONTALS) {
            dry &= !Waterlogging.hasWater(world, roots.offset(side));
        }
        c.ok(dry && !Waterlogging.hasWater(world, roots.up()), "and no water flows out of them");
        BlockPos slab = c.area().add(16, 0, 16);
        Waterlogging.placeWithWater(world, slab, ours("mangrove_slab").getDefaultState(), 3);
        settle(world);
        c.ok(Waterlogging.hasWaterSource(world, slab) && Waterlogging.hasWater(world, slab.east()), "a waterlogged bottom slab lets its water flow out");
        BlockPos leaf = c.area().add(16, 0, 16);
        Waterlogging.placeWithWater(world, leaf, ours("mangrove_leaves").getDefaultState(), 3);
        settle(world);
        WildUpdateBackport.LOGGER.info("SELFTEST waterlog: water flows out of waterlogged mangrove leaves (Fluidlogged API's BlockLeaves rule): {}",
                Waterlogging.hasWater(world, leaf.east()));
    }

    // --- 5. Buckets -------------------------------------------------------------------------------------------

    private static void buckets(Check c) {
        WorldServer world = c.world;
        BlockPos cups = c.area();
        EntityPlayerMP player = Players.real(world, "wlbucket");
        player.interactionManager.setGameType(GameType.SURVIVAL);
        String[] names = {"mangrove_stairs", "mangrove_roots", "sculk_sensor", "mangrove_leaves"};
        for (int i = 0; i < names.length; i++) {
            BlockPos s = cup(world, cups.add(2 + 4 * i, 0, 2));
            Block b = ours(names[i]);
            world.setBlockState(s, b.getDefaultState(), 3);
            c.eq(Items.BUCKET, bucket(player, s, Items.WATER_BUCKET).getItem(), "a water bucket on dry " + names[i] + " comes back empty");
            c.ok(world.getBlockState(s).getBlock() == b && Waterlogging.hasWaterSource(world, s), "and waterlogs it");
            c.eq(Items.WATER_BUCKET, bucket(player, s, Items.BUCKET).getItem(), "an empty bucket takes the water back");
            c.ok(world.getBlockState(s).getBlock() == b && !Waterlogging.hasWater(world, s), "leaving it dry");
        }
        BlockPos d = cup(world, cups.add(2, 0, 8));
        world.setBlockState(d, ours("mangrove_double_slab").getDefaultState(), 3);
        bucket(player, d, Items.WATER_BUCKET);
        c.ok(!Waterlogging.hasWater(world, d) && Waterlogging.hasWater(world, d.up()), "a water bucket on a double slab pours on top of it");
    }

    private static ItemStack bucket(EntityPlayerMP player, BlockPos pos, Item bucket) {
        player.setLocationAndAngles(pos.getX() + 0.5D, pos.getY() + 1.4D, pos.getZ() + 0.5D, 0.0F, 90.0F);
        player.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(bucket));
        ActionResult<ItemStack> result = bucket.onItemRightClick(player.world, player, EnumHand.MAIN_HAND);
        return result.getResult();
    }

    private static BlockPos cup(WorldServer world, BlockPos pos) {
        world.setBlockState(pos.down(), Blocks.STONE.getDefaultState(), 2);
        for (EnumFacing side : EnumFacing.HORIZONTALS) {
            world.setBlockState(pos.offset(side), Blocks.STONE.getDefaultState(), 2);
        }
        return pos;
    }

    // --- 6. The sculk sensor clicks silently in water -----------------------------------------------------------

    private static void sensor(Check c) {
        WorldServer world = c.world;
        BlockPos a = c.area().add(4, 0, 4);
        Block sensor = SculkContent.sensorBlock();
        world.setBlockState(a, sensor.getDefaultState(), 3);
        Waterlogging.placeWithWater(world, a.add(6, 0, 0), sensor.getDefaultState(), 3);
        List<SoundEvent> heard = new ArrayList<SoundEvent>();
        IWorldEventListener ear = ear(heard);
        world.addEventListener(ear);
        try {
            BlockSculkSensor.activate(null, world, a, world.getBlockState(a), 15);
            c.ok(heard.contains(SculkContent.SENSOR_CLICKING), "a dry sensor clicks when it activates");
            heard.clear();
            BlockSculkSensor.deactivate(world, a, world.getBlockState(a));
            c.ok(heard.contains(SculkContent.SENSOR_CLICKING_STOP), "and when it stops");
            heard.clear();
            BlockPos w = a.add(6, 0, 0);
            BlockSculkSensor.activate(null, world, w, world.getBlockState(w), 15);
            c.ok(!heard.contains(SculkContent.SENSOR_CLICKING), "a waterlogged sensor activates without the clicking sound");
            c.eq(BlockSculkSensor.Phase.ACTIVE, world.getBlockState(w).getValue(BlockSculkSensor.PHASE), "but it does activate");
            c.ok(Waterlogging.hasWaterSource(world, w), "and keeps its water while active");
            heard.clear();
            BlockSculkSensor.deactivate(world, w, world.getBlockState(w));
            c.ok(!heard.contains(SculkContent.SENSOR_CLICKING_STOP), "nor the stop sound");
        } finally {
            world.removeEventListener(ear);
        }
    }

    private static IWorldEventListener ear(final List<SoundEvent> heard) {
        return new IWorldEventListener() {
            @Override
            public void notifyBlockUpdate(World w, BlockPos pos, IBlockState oldState, IBlockState newState, int flags) {
            }

            @Override
            public void notifyLightSet(BlockPos pos) {
            }

            @Override
            public void markBlockRangeForRenderUpdate(int x1, int y1, int z1, int x2, int y2, int z2) {
            }

            @Override
            public void playSoundToAllNearExcept(@Nullable EntityPlayer player, SoundEvent sound, SoundCategory category,
                                                 double x, double y, double z, float volume, float pitch) {
                heard.add(sound);
            }

            @Override
            public void playRecord(SoundEvent sound, BlockPos pos) {
            }

            @Override
            public void spawnParticle(int id, boolean ignoreRange, double x, double y, double z, double xs, double ys, double zs, int... params) {
            }

            @Override
            public void spawnParticle(int id, boolean ignoreRange, boolean minimize, double x, double y, double z, double xs, double ys,
                                      double zs, int... params) {
            }

            @Override
            public void onEntityAdded(Entity entity) {
            }

            @Override
            public void onEntityRemoved(Entity entity) {
            }

            @Override
            public void broadcastSound(int id, BlockPos pos, int data) {
            }

            @Override
            public void playEvent(EntityPlayer player, int type, BlockPos pos, int data) {
            }

            @Override
            public void sendBlockBreakProgress(int breakerId, BlockPos pos, int progress) {
            }
        };
    }

    // --- 7. Propagules --------------------------------------------------------------------------------------------

    private static void propagules(Check c) {
        WorldServer world = c.world;
        BlockPropagule propagule = (BlockPropagule) ours("mangrove_propagule");
        Block mud = ours("mud");
        BlockPos pool = pool(c, 3);
        // Standing in water on mud: kept, and it stays when its neighbors change.
        BlockPos stand = pool.add(3, 1, 3);
        world.setBlockState(stand.down(), mud.getDefaultState(), 2);
        c.ok(propagule.canPlaceBlockAt(world, stand), "a propagule can be placed into water on mud");
        world.setBlockState(stand, propagule.getDefaultState(), 3);
        world.neighborChanged(stand, Blocks.STONE, stand.up());
        c.ok(world.getBlockState(stand).getBlock() == propagule && Waterlogging.hasWaterSource(world, stand),
                "a standing propagule in water keeps the source");
        // Hanging under submerged leaves: kept; the leaves go, the propagule drops and the water stays.
        BlockPos leaf = pool.add(8, 3, 8);
        Waterlogging.placeWithWater(world, leaf, ours("mangrove_leaves").getDefaultState(), 3);
        BlockPos hang = leaf.down();
        world.setBlockState(hang, propagule.hanging(2), 3);
        c.ok(world.getBlockState(hang).getBlock() == propagule && world.getBlockState(hang).getValue(BlockPropagule.HANGING)
                && Waterlogging.hasWaterSource(world, hang), "a hanging propagule under leaves in water keeps the source");
        c.ok(propagule.canBlockStay(world, hang, world.getBlockState(hang)), "and hangs on, as 1.19's");
        world.setBlockState(leaf, Blocks.WATER.getDefaultState(), 3);
        world.neighborChanged(hang, Blocks.AIR, leaf);
        c.ok(world.getBlockState(hang).getBlock() != propagule, "without the leaves it falls");
        c.ok(Waterlogging.hasWaterSource(world, hang), "leaving its water");
    }

    // --- 8. A mangrove grown in water ------------------------------------------------------------------------------

    private static void tree(Check c) {
        WorldServer world = c.world;
        Block mud = ours("mud");
        Block roots = ours("mangrove_roots");
        Block leaves = ours("mangrove_leaves");
        Block log = ours("mangrove_log");
        boolean grown = false;
        for (int attempt = 0; attempt < 30 && !grown; attempt++) {
            BlockPos pool = pool(c, 3);
            for (BlockPos p : BlockPos.getAllInBox(pool.add(1, 0, 1), pool.add(12, 0, 12))) {
                world.setBlockState(p, mud.getDefaultState(), 2 | 16);
                world.setBlockState(p.down(), Blocks.STONE.getDefaultState(), 2 | 16);
            }
            BlockPos at = pool.add(7, 1, 7);
            if (!new WorldGenMangroveTree(false, false).generate(world, new Random(attempt), at)) {
                continue;
            }
            grown = true;
            int wetRoots = 0, dryRoots = 0, wetLeaves = 0, wetLogs = 0;
            for (BlockPos p : BlockPos.getAllInBox(pool.add(1, 1, 1), pool.add(12, 3, 12))) {
                Block b = world.getBlockState(p).getBlock();
                boolean water = Waterlogging.hasWaterSource(world, p);
                if (b == roots) {
                    if (water) {
                        wetRoots++;
                    } else {
                        dryRoots++;
                    }
                } else if (b == leaves && water) {
                    wetLeaves++;
                } else if (b == log && water) {
                    wetLogs++;
                }
            }
            c.ok(wetRoots > 0 && dryRoots == 0, "a mangrove grown in water keeps a source in every root below the surface ("
                    + wetRoots + " wet, " + dryRoots + " dry)");
            c.eq(0, wetLogs, "logs replace the water");
            WildUpdateBackport.LOGGER.info("SELFTEST waterlog: mangrove in a pool: {} waterlogged roots, {} waterlogged leaves", wetRoots, wetLeaves);
        }
        c.ok(grown, "a mangrove grows in a pool of water over mud");
    }

    // --- 9. Sculk -----------------------------------------------------------------------------------------------

    private static void sculk(Check c) throws Exception {
        WorldServer world = c.world;
        BlockPos pool = pool(c, 1);
        BlockPos p = pool.add(3, 1, 3);
        c.ok(BlockSculkVein.waterSource(world, p), "a water source is one a vein may take");
        c.ok(SculkBehaviour.DEFAULT.attemptSpreadVein(world, p, world.getBlockState(p), EnumSet.of(EnumFacing.DOWN), false),
                "a vein regrows into a water source");
        c.ok(BlockSculkVein.isVein(world.getBlockState(p)) && Waterlogging.hasWaterSource(world, p), "and keeps it");
        BlockSculkVein.set(world, p, 0, 3);
        c.ok(!BlockSculkVein.isVein(world.getBlockState(p)) && Waterlogging.hasWaterSource(world, p), "a vein losing its last face leaves its water");
        BlockPos f = c.area().add(3, 0, 3);
        world.setBlockState(f, Blocks.FLOWING_WATER.getDefaultState().withProperty(BlockLiquid.LEVEL, 2), 2);
        c.ok(!BlockSculkVein.waterSource(world, f), "flowing water is not");
        c.ok(!SculkBehaviour.DEFAULT.attemptSpreadVein(world, f, world.getBlockState(f), EnumSet.of(EnumFacing.DOWN), false),
                "and no vein grows into it");
        // A growth rises into water above sculk.
        BlockPos s = pool.add(6, 0, 6);
        world.setBlockState(s, SculkContent.sculkBlock().getDefaultState(), 2);
        Method can = SculkContent.sculkBlock().getClass().getDeclaredMethod("canPlaceGrowth", World.class, BlockPos.class);
        can.setAccessible(true);
        c.ok((Boolean) can.invoke(null, world, s), "a sculk growth may rise into a water source above sculk");
        BlockPos s2 = c.area().add(6, -1, 6);
        world.setBlockState(s2, SculkContent.sculkBlock().getDefaultState(), 2);
        world.setBlockState(s2.up(), Blocks.FLOWING_WATER.getDefaultState().withProperty(BlockLiquid.LEVEL, 2), 2);
        c.ok(!(Boolean) can.invoke(null, world, s2), "but not into flowing water");
    }

    // --- Without Fluidlogged API ---------------------------------------------------------------------------------

    private static void dry(Check c) throws Exception {
        WorldServer world = c.world;
        BlockPos probe = c.area().add(16, 1, 16);
        List<String> wrong = new ArrayList<String>();
        for (Block block : ForgeRegistries.BLOCKS) {
            ResourceLocation name = block.getRegistryName();
            if (name != null && WildUpdateBackport.MOD_ID.equals(name.getNamespace()) && block instanceof Waterloggable
                    && Waterlogging.canHoldWater(block.getDefaultState(), world, probe)) {
                wrong.add(name.getPath());
            }
        }
        c.ok(wrong.isEmpty(), "without Fluidlogged API no block of ours holds water: " + wrong);
        c.ok(!Waterlogging.canHoldWater(Blocks.LEAVES.getDefaultState(), world, probe), "nor vanilla leaves");
        BlockPos pool = pool(c, 1);
        BlockPos p = pool.add(2, 1, 2);
        world.setBlockState(p, ours("mangrove_stairs").getDefaultState(), 2);
        c.ok(!Waterlogging.hasWater(world, p), "stairs placed into water replace it, as before 1.13");
        BlockPos v = pool.add(4, 1, 4);
        c.ok(!BlockSculkVein.waterSource(world, v), "no water source may be taken by a vein");
        c.ok(!SculkBehaviour.DEFAULT.attemptSpreadVein(world, v, world.getBlockState(v), EnumSet.of(EnumFacing.DOWN), false),
                "so veins do not grow into water");
        c.ok(world.getBlockState(v).getBlock() == Blocks.WATER, "and the water stays");
        BlockPos s = pool.add(6, 0, 6);
        world.setBlockState(s, SculkContent.sculkBlock().getDefaultState(), 2);
        Method can = SculkContent.sculkBlock().getClass().getDeclaredMethod("canPlaceGrowth", World.class, BlockPos.class);
        can.setAccessible(true);
        c.ok(!(Boolean) can.invoke(null, world, s), "nor do growths rise into it");
        BlockPos t = pool.add(9, 1, 9);
        Waterlogging.placeKeepingWater(world, t, ours("mangrove_roots").getDefaultState(), 2);
        c.ok(world.getBlockState(t).getBlock() == ours("mangrove_roots") && !Waterlogging.hasWater(world, t),
                "generation's placeKeepingWater places roots dry");
        c.ok(Owners.ourBlock("mangrove_leaves") != null, "the mangrove blocks are there");
    }

    /** Runs every pending block tick now, a few rounds, so water finishes flowing. */
    static void settle(WorldServer world) {
        for (int i = 0; i < 40; i++) {
            world.tickUpdates(true);
        }
    }
}
