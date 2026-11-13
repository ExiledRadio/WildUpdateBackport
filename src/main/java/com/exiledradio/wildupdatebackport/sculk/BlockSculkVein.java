package com.exiledradio.wildupdatebackport.sculk;

import net.minecraft.block.Block;
import net.minecraft.block.material.EnumPushReaction;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Enchantments;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.stats.StatList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.NonNullList;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Random;
import java.util.Set;

/**
 * The sculk vein: a thin layer on any of the six faces of its block, as 1.19's multiface {@code SculkVeinBlock}.
 *
 * <h2>Faces in a tile entity</h2>
 * Six booleans make 64 states, and 1.12.2's metadata holds 16, so the faces live in {@link TileSculkVein} (a byte,
 * one bit per {@link EnumFacing#getIndex()}, under {@code Faces}, the layout asked of Backport Core as request 3)
 * and come back through {@link #getActualState}. Everything that reads or writes faces goes through
 * {@link #faces} and {@link #set}.
 *
 * <p>No collision; hardness 0.2; destroyed by pistons; with Silk Touch drops one vein per face, otherwise nothing.
 * Charges travelling through a vein turn the blocks it sits on into sculk (the vein then retreats from every face
 * that now touches sculk).
 */
public class BlockSculkVein extends Block implements SculkBehaviour {

    /** By {@link EnumFacing#getIndex()}: down, up, north, south, west, east. */
    public static final PropertyBool[] FACES = {
            PropertyBool.create("down"), PropertyBool.create("up"), PropertyBool.create("north"),
            PropertyBool.create("south"), PropertyBool.create("west"), PropertyBool.create("east")};

    private static final double T = 1.0D / 16.0D;
    private static final AxisAlignedBB[] BOXES = {
            new AxisAlignedBB(0, 0, 0, 1, T, 1), new AxisAlignedBB(0, 1 - T, 0, 1, 1, 1),
            new AxisAlignedBB(0, 0, 0, 1, 1, T), new AxisAlignedBB(0, 0, 1 - T, 1, 1, 1),
            new AxisAlignedBB(0, 0, 0, T, 1, 1), new AxisAlignedBB(1 - T, 0, 0, 1, 1, 1)};

    public static final int ALL = 63;

    private final VeinSpreader veinSpreader = new VeinSpreader(VeinSpreader.DEFAULT_SPREAD_ORDER);
    private final VeinSpreader sameSpaceSpreader = new VeinSpreader(VeinSpreader.Type.SAME_POSITION);

    public BlockSculkVein() {
        super(SculkContent.SCULK_MATERIAL);
        this.setHardness(0.2F);
        this.setSoundType(SculkContent.VEIN_SOUND);
        this.setLightOpacity(0);
        IBlockState s = this.blockState.getBaseState();
        for (PropertyBool p : FACES) {
            s = s.withProperty(p, false);
        }
        this.setDefaultState(s);
    }

    public VeinSpreader spreader() {
        return this.veinSpreader;
    }

    public VeinSpreader sameSpaceSpreader() {
        return this.sameSpaceSpreader;
    }

    // --- faces -------------------------------------------------------------------------------

    public static boolean isVein(IBlockState state) {
        return state.getBlock() instanceof BlockSculkVein;
    }

    public static int bit(EnumFacing face) {
        return 1 << face.getIndex();
    }

    public static boolean has(int mask, EnumFacing face) {
        return (mask & bit(face)) != 0;
    }

    public static byte pack(Collection<EnumFacing> faces) {
        int mask = 0;
        for (EnumFacing f : faces) {
            mask |= bit(f);
        }
        return (byte) mask;
    }

    public static Set<EnumFacing> unpack(int mask) {
        Set<EnumFacing> set = EnumSet.noneOf(EnumFacing.class);
        for (EnumFacing f : EnumFacing.VALUES) {
            if (has(mask, f)) {
                set.add(f);
            }
        }
        return set;
    }

    /** @return the faces of the vein at a position (0 when it is not a vein) */
    public static int faces(IBlockAccess world, BlockPos pos) {
        TileEntity te = world.getTileEntity(pos);
        return te instanceof TileSculkVein ? ((TileSculkVein) te).faces() : 0;
    }

    /** 1.19's {@code MultifaceBlock.availableFaces}: a vein's faces, an empty set for anything else. */
    public static Set<EnumFacing> availableFaces(IBlockAccess world, BlockPos pos, IBlockState state) {
        return isVein(state) ? unpack(faces(world, pos)) : EnumSet.noneOf(EnumFacing.class);
    }

    /** 1.19's {@code MultifaceBlock.canAttachTo}: the support's touching face is full. */
    public static boolean canAttachTo(IBlockAccess world, EnumFacing face, BlockPos support) {
        IBlockState s = world.getBlockState(support);
        return s.getBlockFaceShape(world, support, face.getOpposite()) == BlockFaceShape.SOLID;
    }

    /**
     * Makes the block at a position a vein with exactly these faces (air when none).
     *
     * @param flags {@link World#setBlockState(BlockPos, IBlockState, int)}'s
     * @return true if anything changed
     */
    public static boolean set(World world, BlockPos pos, int mask, int flags) {
        BlockSculkVein vein = SculkContent.veinBlock();
        if (mask == 0) {
            return world.setBlockState(pos, Blocks.AIR.getDefaultState(), flags);
        }
        IBlockState current = world.getBlockState(pos);
        boolean was = current.getBlock() == vein;
        if (!was && !world.setBlockState(pos, vein.getDefaultState(), flags)) {
            return false;
        }
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileSculkVein)) {
            return false;
        }
        TileSculkVein tile = (TileSculkVein) te;
        if (was && tile.faces() == mask) {
            return false;
        }
        tile.setFaces(mask);
        IBlockState state = world.getBlockState(pos);
        // Clients get the faces in the tile entity's update packet, sent with the block change.
        world.notifyBlockUpdate(pos, state, state, flags | 2);
        if (was && (flags & 1) != 0) {
            world.notifyNeighborsOfStateChange(pos, vein, false);
        }
        return true;
    }

    /** 1.19's {@code SculkVeinBlock.regrow}: a vein on every listed face that can hold one. */
    public static boolean regrow(World world, BlockPos pos, Collection<EnumFacing> faces) {
        int mask = 0;
        for (EnumFacing f : faces) {
            if (canAttachTo(world, f, pos.offset(f))) {
                mask |= bit(f);
            }
        }
        return mask != 0 && set(world, pos, mask, 3);
    }

    /** 1.19's {@code hasSubstrateAccess}: a vein that sits on a block sculk can replace. */
    public static boolean hasSubstrateAccess(World world, IBlockState state, BlockPos pos) {
        if (!isVein(state)) {
            return false;
        }
        int mask = faces(world, pos);
        for (EnumFacing f : EnumFacing.VALUES) {
            if (has(mask, f) && SculkTags.replaceable(world.getBlockState(pos.offset(f)), false)) {
                return true;
            }
        }
        return false;
    }

    // --- the charge --------------------------------------------------------------------------

    @Override
    public void onDischarged(World world, IBlockState state, BlockPos pos, Random random) {
        if (!isVein(state)) {
            return;
        }
        int mask = faces(world, pos);
        for (EnumFacing f : EnumFacing.VALUES) {
            if (has(mask, f) && world.getBlockState(pos.offset(f)).getBlock() == SculkContent.sculkBlock()) {
                mask &= ~bit(f);
            }
        }
        set(world, pos, mask, 3);
    }

    @Override
    public int attemptUseCharge(SculkSpreader.ChargeCursor cursor, World world, BlockPos catalyst, Random random,
                                SculkSpreader spreader, boolean spreadVeins) {
        if (spreadVeins && attemptPlaceSculk(spreader, world, cursor.getPos(), random)) {
            return cursor.getCharge() - 1;
        }
        return random.nextInt(spreader.chargeDecayRate()) == 0 ? (int) Math.floor(cursor.getCharge() * 0.5F) : cursor.getCharge();
    }

    private boolean attemptPlaceSculk(SculkSpreader spreader, World world, BlockPos pos, Random random) {
        IBlockState state = world.getBlockState(pos);
        int mask = isVein(state) ? faces(world, pos) : 0;
        java.util.List<EnumFacing> dirs = new java.util.ArrayList<EnumFacing>(java.util.Arrays.asList(EnumFacing.VALUES));
        java.util.Collections.shuffle(dirs, random);
        for (EnumFacing dir : dirs) {
            if (!has(mask, dir)) {
                continue;
            }
            BlockPos target = pos.offset(dir);
            IBlockState there = world.getBlockState(target);
            if (spreader.replaceable(there)) {
                IBlockState sculk = SculkContent.sculkBlock().getDefaultState();
                world.setBlockState(target, sculk, 3);
                world.playSound(null, target, SculkContent.SCULK_SPREAD, SoundCategory.BLOCKS, 1.0F, 1.0F);
                this.veinSpreader.spreadAll(world, target, spreader.isWorldGeneration());
                EnumFacing back = dir.getOpposite();
                for (EnumFacing f : EnumFacing.VALUES) {
                    if (f != back) {
                        BlockPos n = target.offset(f);
                        IBlockState ns = world.getBlockState(n);
                        if (isVein(ns)) {
                            this.onDischarged(world, ns, n, random);
                        }
                    }
                }
                return true;
            }
        }
        return false;
    }

    // --- the block ---------------------------------------------------------------------------

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, (IProperty<?>[]) FACES);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return 0;
    }

    @Override
    @SuppressWarnings("deprecation")
    public IBlockState getStateFromMeta(int meta) {
        return this.getDefaultState();
    }

    @Override
    @SuppressWarnings("deprecation")
    public IBlockState getActualState(IBlockState state, IBlockAccess world, BlockPos pos) {
        int mask = faces(world, pos);
        for (EnumFacing f : EnumFacing.VALUES) {
            state = state.withProperty(FACES[f.getIndex()], has(mask, f));
        }
        return state;
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileSculkVein();
    }

    @Override
    @SuppressWarnings("deprecation")
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        int mask = faces(source, pos);
        AxisAlignedBB box = null;
        for (EnumFacing f : EnumFacing.VALUES) {
            if (has(mask, f)) {
                box = box == null ? BOXES[f.getIndex()] : box.union(BOXES[f.getIndex()]);
            }
        }
        return box == null ? FULL_BLOCK_AABB : box;
    }

    @Override
    @Nullable
    @SuppressWarnings("deprecation")
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return NULL_AABB;
    }

    @Override
    public boolean isPassable(IBlockAccess world, BlockPos pos) {
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockFaceShape getBlockFaceShape(IBlockAccess world, IBlockState state, BlockPos pos, EnumFacing face) {
        return BlockFaceShape.UNDEFINED;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT;
    }

    /** Any block placed against a vein takes its place (the vein item adds a face instead, in {@link ItemSculkVein}). */
    @Override
    public boolean isReplaceable(IBlockAccess world, BlockPos pos) {
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public EnumPushReaction getPushReaction(IBlockState state) {
        return EnumPushReaction.DESTROY;
    }

    /** 1.19's {@code updateShape}: a face whose support is gone goes; with none left the vein goes (no drop). */
    @Override
    @SuppressWarnings("deprecation")
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block block, BlockPos from) {
        if (world.isRemote) {
            return;
        }
        int mask = faces(world, pos);
        int kept = mask;
        for (EnumFacing f : EnumFacing.VALUES) {
            if (has(mask, f) && !canAttachTo(world, f, pos.offset(f))) {
                kept &= ~bit(f);
            }
        }
        if (kept != mask || mask == 0) {
            set(world, pos, kept, 3);
        }
    }

    // --- drops -------------------------------------------------------------------------------

    @Override
    public Item getItemDropped(IBlockState state, Random rand, int fortune) {
        return Items.AIR;
    }

    @Override
    public boolean canSilkHarvest(World world, BlockPos pos, IBlockState state, EntityPlayer player) {
        return true;
    }

    /** Silk Touch: one vein per face (1.19's loot table); anything else: nothing. */
    @Override
    public void harvestBlock(World world, EntityPlayer player, BlockPos pos, IBlockState state, @Nullable TileEntity te, ItemStack stack) {
        player.addStat(StatList.getBlockStats(this));
        player.addExhaustion(0.005F);
        if (EnchantmentHelper.getEnchantmentLevel(Enchantments.SILK_TOUCH, stack) <= 0) {
            return;
        }
        int count = te instanceof TileSculkVein ? Integer.bitCount(((TileSculkVein) te).faces()) : 1;
        NonNullList<ItemStack> drops = NonNullList.create();
        drops.add(new ItemStack(this, Math.max(1, count)));
        ForgeEventFactory.fireBlockHarvesting(drops, world, pos, state, 0, 1.0F, true, player);
        for (ItemStack drop : drops) {
            spawnAsEntity(world, pos, drop);
        }
    }

    @Override
    public ItemStack getPickBlock(IBlockState state, RayTraceResult target, World world, BlockPos pos, EntityPlayer player) {
        return new ItemStack(this);
    }
}
