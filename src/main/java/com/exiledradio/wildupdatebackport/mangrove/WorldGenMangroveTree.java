package com.exiledradio.wildupdatebackport.mangrove;

import com.exiledradio.backportcore.waterlog.Waterlogging;
import com.exiledradio.wildupdatebackport.common.Owners;

import net.minecraft.block.Block;
import net.minecraft.block.BlockVine;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.feature.WorldGenAbstractTree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * 1.19's mangrove and tall mangrove trees: roots arching down from a raised trunk, branching logs, a loose
 * canopy, vines, hanging propagules and now and then a bee nest.
 *
 * <p>The rules and numbers are 1.19's {@code TreeFeatures.MANGROVE} and {@code TALL_MANGROVE}, rewritten
 * for 1.12.2's world: an upwards-branching trunk (2+0..1+0..4 logs, tall 4+0..1+0..9; a branch from 50% of
 * logs), random-spread foliage (70 tries within 2 blocks across and 1 up or down of each branch end), the
 * mangrove root placer (trunk raised 1-3 blocks, tall 3-7; roots grow down and out until they reach
 * ground, at most 15 long, skewing outwards past 5 blocks; in mud they become muddy roots), moss carpet on
 * half the roots' tops (Caves &amp; Cliffs' carpet, when installed), vines on 12.5% of leaf sides, hanging
 * propagules under 14% of leaves with two air blocks below, and a bee nest on 1% of trees (Buzzy Bees',
 * when installed; empty, since its bees are its own).
 *
 * <p>Like 1.19's, a tree whose roots find no ground within reach is not placed at all; anything else
 * places what fits.
 *
 * <p>Water (with Fluidlogged API): roots placed where there is any water hold a source (1.19's {@code
 * RootPlacer}: {@code FluidTags.WATER}), leaves keep a water source they are placed into (its {@code
 * FoliagePlacer}: a source only), and logs replace water as 1.19's do. Without Fluidlogged API every one of them
 * replaces the water.
 */
public class WorldGenMangroveTree extends WorldGenAbstractTree {

    private final boolean tall;
    /**
     * Block flags exactly as {@code setBlockAndNotifyAdequately} uses them: 3 for a grown tree; for generation 2, plus
     * 16 when Forge's {@code fixVanillaCascading} is on, so the blocks placed in water notify no neighbors and load no
     * further chunks.
     */
    private final int flags;

    public WorldGenMangroveTree(boolean notify, boolean tall) {
        super(notify);
        this.tall = tall;
        this.flags = notify ? 3 : net.minecraftforge.common.ForgeModContainer.fixVanillaCascading ? 2 | 16 : 2;
    }

    @Override
    public boolean generate(World world, Random random, BlockPos pos) {
        IBlockState log = MangroveContent.log();
        IBlockState leaves = MangroveContent.leavesForTree();
        if (log == null || leaves == null) {
            return false;
        }

        int height = (this.tall ? 4 : 2) + random.nextInt(2) + random.nextInt(this.tall ? 10 : 5);
        int rootOffset = this.tall ? 3 + random.nextInt(5) : 1 + random.nextInt(3);
        BlockPos trunk = pos.up(rootOffset);

        int minY = Math.min(pos.getY(), trunk.getY());
        int maxY = Math.max(pos.getY(), trunk.getY()) + height + 1;
        if (minY < 1 || maxY > world.getHeight()) {
            return false;
        }
        if (maxFreeHeight(world, height, trunk) < height) {
            return false;
        }

        List<BlockPos> roots = simulateRoots(world, random, pos, trunk);
        if (roots == null) {
            return false;
        }
        Set<BlockPos> rootSet = new LinkedHashSet<BlockPos>();
        for (BlockPos root : roots) {
            placeRoot(world, random, root, rootSet);
        }

        Set<BlockPos> logs = new LinkedHashSet<BlockPos>();
        List<BlockPos> attachments = placeTrunk(world, random, height, trunk, log, logs);
        Set<BlockPos> leafSet = new LinkedHashSet<BlockPos>();
        for (BlockPos at : attachments) {
            for (int i = 0; i < 70; i++) {
                int dx = random.nextInt(3) - random.nextInt(3);
                int dy = random.nextInt(2) - random.nextInt(2);
                int dz = random.nextInt(3) - random.nextInt(3);
                BlockPos p = at.add(dx, dy, dz);
                if (validTreePos(world, p)) {
                    Waterlogging.placeKeepingWater(world, p, leaves, this.flags);
                    leafSet.add(p);
                }
            }
        }
        if (logs.isEmpty() && leafSet.isEmpty()) {
            return false;
        }
        // 1.19 gives a tree's leaves their distance from its logs once it is placed; one more than 6 steps
        // away is left at 7 and decays on its next random tick. Ours would wait for a neighbor to change,
        // so the strays go now, leaving what a 1.19 tree settles to.
        for (java.util.Iterator<BlockPos> it = leafSet.iterator(); it.hasNext(); ) {
            BlockPos leaf = it.next();
            if (!BlockMangroveLeaves.logWithin(world, leaf, BlockMangroveLeaves.RANGE)) {
                this.setBlockAndNotifyAdequately(world, leaf, net.minecraft.init.Blocks.AIR.getDefaultState());
                it.remove();
            }
        }

        List<BlockPos> sortedLeaves = new ArrayList<BlockPos>(leafSet);
        sortedLeaves.sort(Comparator.comparingInt(BlockPos::getY));
        List<BlockPos> sortedLogs = new ArrayList<BlockPos>(logs);
        sortedLogs.sort(Comparator.comparingInt(BlockPos::getY));
        vines(world, random, sortedLeaves);
        hangingPropagules(world, random, sortedLeaves);
        beeNest(world, random, sortedLogs, sortedLeaves);
        return true;
    }

    // --- 1.19's block checks ------------------------------------------------------------------

    /** {@code TreeFeature.validTreePos}: air, leaves, replaceable plants, water. */
    static boolean validTreePos(World world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        if (block.isAir(state, world, pos) || block.isLeaves(state, world, pos)) {
            return true;
        }
        Material material = state.getMaterial();
        return material == Material.VINE || block == Blocks.WATER || block == Blocks.FLOWING_WATER;
    }

    /** {@code #mangrove_logs_can_grow_through}. */
    private static boolean logsGrowThrough(Block block) {
        return block == Blocks.VINE || isOurs(block, "mud", "muddy_mangrove_roots", "mangrove_roots", "mangrove_leaves",
                "mangrove_log", "mangrove_propagule") || isCavesAndCliffs(block, "moss_carpet");
    }

    /** {@code #mangrove_roots_can_grow_through}. */
    private static boolean rootsGrowThrough(Block block) {
        return block == Blocks.VINE || block == Blocks.SNOW_LAYER
                || isOurs(block, "mud", "muddy_mangrove_roots", "mangrove_roots", "mangrove_propagule")
                || isCavesAndCliffs(block, "moss_carpet");
    }

    private static boolean isOurs(Block block, String... names) {
        for (String name : names) {
            if (block == Owners.ourBlock(name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isCavesAndCliffs(Block block, String name) {
        Block theirs = Owners.present(Owners.CAVES_AND_CLIFFS) ? Owners.block(Owners.CAVES_AND_CLIFFS, name) : null;
        return theirs != null && block == theirs;
    }

    private static boolean trunkValid(World world, BlockPos pos) {
        return validTreePos(world, pos) || logsGrowThrough(world.getBlockState(pos).getBlock());
    }

    private static boolean trunkFree(World world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        return trunkValid(world, pos) || state.getBlock().isWood(world, pos);
    }

    private static boolean canPlaceRoot(World world, BlockPos pos) {
        return validTreePos(world, pos) || rootsGrowThrough(world.getBlockState(pos).getBlock());
    }

    /** {@code TwoLayersFeatureSize(limit, 0, 2)}: the trunk's first blocks need no room around them. */
    private int maxFreeHeight(World world, int height, BlockPos trunk) {
        int limit = this.tall ? 3 : 2;
        for (int y = 0; y <= height + 1; y++) {
            int size = y < limit ? 0 : 2;
            for (int dx = -size; dx <= size; dx++) {
                for (int dz = -size; dz <= size; dz++) {
                    if (!trunkFree(world, trunk.add(dx, y, dz))) {
                        return y - 2;
                    }
                }
            }
        }
        return height;
    }

    // --- roots -------------------------------------------------------------------------------

    /** @return every root position in placing order, or null if the roots cannot reach ground */
    private static List<BlockPos> simulateRoots(World world, Random random, BlockPos sapling, BlockPos trunk) {
        for (BlockPos p = sapling; p.getY() < trunk.getY(); p = p.up()) {
            if (!canPlaceRoot(world, p)) {
                return null;
            }
        }
        List<BlockPos> all = new ArrayList<BlockPos>();
        all.add(trunk.down());
        for (EnumFacing dir : new EnumFacing[] {EnumFacing.NORTH, EnumFacing.EAST, EnumFacing.SOUTH, EnumFacing.WEST}) {
            BlockPos start = trunk.offset(dir);
            List<BlockPos> arm = new ArrayList<BlockPos>();
            if (!simulateArm(world, random, start, dir, trunk, arm, 0)) {
                return null;
            }
            all.addAll(arm);
            all.add(start);
        }
        return all;
    }

    private static boolean simulateArm(World world, Random random, BlockPos pos, EnumFacing dir, BlockPos trunk,
                                       List<BlockPos> arm, int depth) {
        if (depth == 15 || arm.size() > 15) {
            return false;
        }
        for (BlockPos next : nextRootPositions(pos, dir, random, trunk)) {
            if (canPlaceRoot(world, next)) {
                arm.add(next);
                if (!simulateArm(world, random, next, dir, trunk, arm, depth + 1)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static List<BlockPos> nextRootPositions(BlockPos pos, EnumFacing dir, Random random, BlockPos trunk) {
        BlockPos below = pos.down();
        BlockPos side = pos.offset(dir);
        int distance = Math.abs(pos.getX() - trunk.getX()) + Math.abs(pos.getY() - trunk.getY()) + Math.abs(pos.getZ() - trunk.getZ());
        if (distance > 5 && distance <= 8) {
            return random.nextFloat() < 0.2F ? listOf(below, side.down()) : listOf(below);
        } else if (distance > 8) {
            return listOf(below);
        } else if (random.nextFloat() < 0.2F) {
            return listOf(below);
        }
        return random.nextBoolean() ? listOf(side) : listOf(below);
    }

    private static List<BlockPos> listOf(BlockPos... positions) {
        List<BlockPos> list = new ArrayList<BlockPos>(positions.length);
        Collections.addAll(list, positions);
        return list;
    }

    private void placeRoot(World world, Random random, BlockPos pos, Set<BlockPos> placed) {
        Block here = world.getBlockState(pos).getBlock();
        if (isOurs(here, "mud", "muddy_mangrove_roots")) {
            IBlockState muddy = MangroveContent.muddyRoots();
            if (muddy != null) {
                this.setBlockAndNotifyAdequately(world, pos, muddy);
                placed.add(pos);
            }
            return;
        }
        if (!canPlaceRoot(world, pos)) {
            return;
        }
        IBlockState roots = MangroveContent.roots();
        if (roots == null) {
            return;
        }
        if (Waterlogging.hasWater(world, pos)) {
            Waterlogging.placeWithWater(world, pos, roots, this.flags);
        } else {
            this.setBlockAndNotifyAdequately(world, pos, roots);
        }
        placed.add(pos);
        if (random.nextFloat() < 0.5F && world.isAirBlock(pos.up())) {
            Block moss = Owners.present(Owners.CAVES_AND_CLIFFS) ? Owners.block(Owners.CAVES_AND_CLIFFS, "moss_carpet") : null;
            if (moss != null) {
                this.setBlockAndNotifyAdequately(world, pos.up(), moss.getDefaultState());
            }
        }
    }

    // --- trunk -------------------------------------------------------------------------------

    private List<BlockPos> placeTrunk(World world, Random random, int height, BlockPos trunk, IBlockState log, Set<BlockPos> logs) {
        List<BlockPos> attachments = new ArrayList<BlockPos>();
        int maxSteps = this.tall ? 6 : 4;
        for (int i = 0; i < height; i++) {
            int y = trunk.getY() + i;
            BlockPos at = new BlockPos(trunk.getX(), y, trunk.getZ());
            if (placeLog(world, at, log, logs) && i < height - 1 && random.nextFloat() < 0.5F) {
                EnumFacing dir = EnumFacing.HORIZONTALS[random.nextInt(4)];
                int length = random.nextInt(2);
                int start = Math.max(0, length - random.nextInt(2) - 1);
                int steps = 1 + random.nextInt(maxSteps);
                placeBranch(world, height, attachments, at, y, dir, start, steps, log, logs);
            }
            if (i == height - 1) {
                attachments.add(new BlockPos(trunk.getX(), y + 1, trunk.getZ()));
            }
        }
        return attachments;
    }

    private void placeBranch(World world, int height, List<BlockPos> attachments, BlockPos from, int y, EnumFacing dir,
                             int start, int steps, IBlockState log, Set<BlockPos> logs) {
        int top = y + start;
        int x = from.getX();
        int z = from.getZ();
        for (int i = start; i < height && steps > 0; i++, steps--) {
            if (i >= 1) {
                int by = y + i;
                x += dir.getXOffset();
                z += dir.getZOffset();
                top = by;
                BlockPos p = new BlockPos(x, by, z);
                if (placeLog(world, p, log, logs)) {
                    top = by + 1;
                }
                attachments.add(p);
            }
        }
        if (top - y > 1) {
            BlockPos end = new BlockPos(x, top, z);
            attachments.add(end);
            attachments.add(end.down(2));
        }
    }

    private boolean placeLog(World world, BlockPos pos, IBlockState log, Set<BlockPos> logs) {
        if (!trunkValid(world, pos)) {
            return false;
        }
        this.setBlockAndNotifyAdequately(world, pos, log);
        logs.add(pos);
        return true;
    }

    // --- decorations -------------------------------------------------------------------------

    private void vines(World world, Random random, List<BlockPos> leaves) {
        for (BlockPos leaf : leaves) {
            vineAt(world, random, leaf.west(), BlockVine.EAST);
            vineAt(world, random, leaf.east(), BlockVine.WEST);
            vineAt(world, random, leaf.north(), BlockVine.SOUTH);
            vineAt(world, random, leaf.south(), BlockVine.NORTH);
        }
    }

    private void vineAt(World world, Random random, BlockPos pos, PropertyBool side) {
        if (random.nextFloat() >= 0.125F || !world.isAirBlock(pos)) {
            return;
        }
        IBlockState vine = Blocks.VINE.getDefaultState().withProperty(side, true);
        this.setBlockAndNotifyAdequately(world, pos, vine);
        BlockPos p = pos.down();
        for (int left = 4; world.isAirBlock(p) && left > 0; left--) {
            this.setBlockAndNotifyAdequately(world, p, vine);
            p = p.down();
        }
    }

    private void hangingPropagules(World world, Random random, List<BlockPos> leaves) {
        BlockPropagule propagule = MangroveContent.propagule();
        if (propagule == null) {
            return;
        }
        List<BlockPos> shuffled = new ArrayList<BlockPos>(leaves);
        Collections.shuffle(shuffled, random);
        Set<BlockPos> excluded = new HashSet<BlockPos>();
        for (BlockPos leaf : shuffled) {
            BlockPos target = leaf.down();
            if (excluded.contains(target) || random.nextFloat() >= 0.14F) {
                continue;
            }
            if (!world.isAirBlock(leaf.down(1)) || !world.isAirBlock(leaf.down(2))) {
                continue;
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    excluded.add(target.add(dx, 0, dz));
                }
            }
            this.setBlockAndNotifyAdequately(world, target, propagule.hanging(random.nextInt(5)));
        }
    }

    private void beeNest(World world, Random random, List<BlockPos> logs, List<BlockPos> leaves) {
        if (random.nextFloat() >= 0.01F || logs.isEmpty()) {
            return;
        }
        Block nest = Owners.present(Owners.BUZZY_BEES) ? Owners.block(Owners.BUZZY_BEES, "bee_nest") : null;
        if (nest == null) {
            return;
        }
        int y = !leaves.isEmpty()
                ? Math.max(leaves.get(0).getY() - 1, logs.get(0).getY() + 1)
                : Math.min(logs.get(0).getY() + 1 + random.nextInt(3), logs.get(logs.size() - 1).getY());
        List<BlockPos> spots = new ArrayList<BlockPos>();
        for (BlockPos log : logs) {
            if (log.getY() == y) {
                for (EnumFacing dir : new EnumFacing[] {EnumFacing.EAST, EnumFacing.SOUTH, EnumFacing.WEST}) {
                    spots.add(log.offset(dir));
                }
            }
        }
        Collections.shuffle(spots, random);
        for (BlockPos spot : spots) {
            if (world.isAirBlock(spot) && world.isAirBlock(spot.south())) {
                IBlockState state = nest.getDefaultState();
                for (net.minecraft.block.properties.IProperty<?> p : state.getPropertyKeys()) {
                    if (p instanceof PropertyDirection && p.getName().equals("facing")) {
                        state = state.withProperty((PropertyDirection) p, EnumFacing.SOUTH);
                    }
                }
                this.setBlockAndNotifyAdequately(world, spot, state);
                return;
            }
        }
    }
}
