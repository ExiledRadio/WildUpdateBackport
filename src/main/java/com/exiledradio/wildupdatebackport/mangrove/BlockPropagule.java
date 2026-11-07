package com.exiledradio.wildupdatebackport.mangrove;

import com.exiledradio.wildupdatebackport.common.Owners;

import net.minecraft.block.Block;
import net.minecraft.block.BlockBush;
import net.minecraft.block.IGrowable;
import net.minecraft.block.SoundType;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.util.Random;

/**
 * The mangrove propagule: 1.19's sapling for mangroves, standing on the ground or hanging from leaves.
 *
 * <h2>States</h2>
 * {@code hanging}, {@code age} 0-4 and {@code stage} 0-1, as 1.19 (the blockstate file names hanging and
 * age; stage is left out, as vanilla leaves a sapling's out). A standing propagule is always age 4 - that is
 * what 1.19 places - and grows in two stages like any sapling. A hanging one grows from age 0 to 4 under
 * mangrove leaves, one age per random tick, and drops itself only when fully grown.
 *
 * <p>Metadata: standing {@code 0 + stage}; hanging {@code 8 + age}.
 *
 * <h2>Growing</h2>
 * Standing: 1 in 7 random ticks advances it, with no light check (1.19 overrides the sapling's); bone meal
 * advances it 45% of the time; the second advance grows a mangrove (85% the tall kind). Hanging: random
 * ticks and bone meal add an age until 4; a hanging propagule never grows a tree.
 *
 * <h2>Where it stands</h2>
 * 1.19's {@code dirt} tag plus farmland and clay: grass, dirt, coarse dirt, podzol, mycelium, farmland, clay,
 * and by name our mud and muddy mangrove roots and Caves &amp; Cliffs' rooted dirt and moss. It can be placed
 * in water (1.19 waterlogs it; until the family's waterlogging pass, it takes the water's place).
 */
public class BlockPropagule extends BlockBush implements IGrowable {

    public static final PropertyInteger AGE = PropertyInteger.create("age", 0, 4);
    public static final PropertyBool HANGING = PropertyBool.create("hanging");
    public static final PropertyInteger STAGE = PropertyInteger.create("stage", 0, 1);

    /** 1.19's shapes by age (hanging); a standing propagule uses the last. Pixels: x/z 7-9, from y to 16. */
    private static final AxisAlignedBB[] SHAPE = {
            box(13), box(10), box(7), box(3), box(0)
    };

    private static final float TALL_CHANCE = 0.85F;

    public BlockPropagule() {
        super(net.minecraft.block.material.Material.PLANTS);
        this.setSoundType(SoundType.PLANT);
        this.setHardness(0.0F);
        this.setTickRandomly(true);
        this.setDefaultState(this.blockState.getBaseState().withProperty(AGE, 4).withProperty(HANGING, false).withProperty(STAGE, 0));
    }

    private static AxisAlignedBB box(int fromY) {
        return new AxisAlignedBB(7 / 16.0, fromY / 16.0, 7 / 16.0, 9 / 16.0, 1.0, 9 / 16.0);
    }

    /** @return a new hanging propagule of this age */
    public IBlockState hanging(int age) {
        return this.getDefaultState().withProperty(HANGING, true).withProperty(AGE, Math.max(0, Math.min(4, age)));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, AGE, HANGING, STAGE);
    }

    @Override
    @SuppressWarnings("deprecation")
    public IBlockState getStateFromMeta(int meta) {
        if ((meta & 8) != 0) {
            return hanging(meta & 7);
        }
        return this.getDefaultState().withProperty(STAGE, meta & 1);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(HANGING) ? 8 + state.getValue(AGE) : state.getValue(STAGE);
    }

    @Override
    public EnumOffsetType getOffsetType() {
        return EnumOffsetType.XZ;
    }

    @Override
    @SuppressWarnings("deprecation")
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        AxisAlignedBB shape = state.getValue(HANGING) ? SHAPE[state.getValue(AGE)] : SHAPE[4];
        return shape.offset(state.getOffset(world, pos));
    }

    // --- placement and support ----------------------------------------------------------------

    @Override
    protected boolean canSustainBush(IBlockState state) {
        return isSoil(state.getBlock());
    }

    static boolean isSoil(Block block) {
        if (block == Blocks.GRASS || block == Blocks.DIRT || block == Blocks.FARMLAND || block == Blocks.MYCELIUM
                || block == Blocks.CLAY) {
            return true;
        }
        if (block == Owners.ourBlock("mud") || block == Owners.ourBlock("muddy_mangrove_roots")) {
            return block != null;
        }
        return block != null && (block == Owners.block(Owners.CAVES_AND_CLIFFS, "rooted_dirt")
                || block == Owners.block(Owners.CAVES_AND_CLIFFS, "moss_block"));
    }

    @Override
    public boolean canPlaceBlockAt(World world, BlockPos pos) {
        // A propagule placed by hand stands; only bone-mealed leaves and trees make hanging ones.
        return world.getBlockState(pos).getBlock().isReplaceable(world, pos) && isSoil(world.getBlockState(pos.down()).getBlock());
    }

    @Override
    public boolean canBlockStay(World world, BlockPos pos, IBlockState state) {
        if (state.getBlock() == this && state.getValue(HANGING)) {
            return world.getBlockState(pos.up()).getBlock() instanceof BlockMangroveLeaves;
        }
        return isSoil(world.getBlockState(pos.down()).getBlock());
    }

    // --- growth -------------------------------------------------------------------------------

    @Override
    public void updateTick(World world, BlockPos pos, IBlockState state, Random random) {
        this.checkAndDropBlock(world, pos, state);
        if (world.isRemote || world.getBlockState(pos) != state) {
            return;
        }
        if (!state.getValue(HANGING)) {
            if (random.nextInt(7) == 0) {
                advance(world, pos, state, random);
            }
        } else if (state.getValue(AGE) < 4) {
            world.setBlockState(pos, state.withProperty(AGE, state.getValue(AGE) + 1), 2);
        }
    }

    @Override
    public boolean canGrow(World world, BlockPos pos, IBlockState state, boolean isClient) {
        return !state.getValue(HANGING) || state.getValue(AGE) < 4;
    }

    @Override
    public boolean canUseBonemeal(World world, Random random, BlockPos pos, IBlockState state) {
        return state.getValue(HANGING) ? state.getValue(AGE) < 4 : random.nextFloat() < 0.45F;
    }

    @Override
    public void grow(World world, Random random, BlockPos pos, IBlockState state) {
        if (state.getValue(HANGING)) {
            if (state.getValue(AGE) < 4) {
                world.setBlockState(pos, state.withProperty(AGE, state.getValue(AGE) + 1), 2);
            }
        } else {
            advance(world, pos, state, random);
        }
    }

    /** A sapling's two steps: stage 0 to 1, then a tree. */
    private void advance(World world, BlockPos pos, IBlockState state, Random random) {
        if (state.getValue(STAGE) == 0) {
            world.setBlockState(pos, state.withProperty(STAGE, 1), 4);
            return;
        }
        if (!net.minecraftforge.event.terraingen.TerrainGen.saplingGrowTree(world, random, pos)) {
            return;
        }
        WorldGenMangroveTree tree = new WorldGenMangroveTree(true, random.nextFloat() < TALL_CHANCE);
        world.setBlockState(pos, Blocks.AIR.getDefaultState(), 4);
        if (!tree.generate(world, random, pos)) {
            world.setBlockState(pos, state, 4);
        }
    }

    // --- drops --------------------------------------------------------------------------------

    @Override
    public void getDrops(NonNullList<ItemStack> drops, IBlockAccess world, BlockPos pos, IBlockState state, int fortune) {
        // 1.19's loot table: only a fully grown propagule drops (every standing one is age 4).
        if (state.getValue(AGE) == 4) {
            drops.add(new ItemStack(this));
        }
    }

    @Override
    public int damageDropped(IBlockState state) {
        return 0;
    }

    @Override
    public ItemStack getItem(World world, BlockPos pos, IBlockState state) {
        return new ItemStack(this);
    }
}
