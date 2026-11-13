package com.exiledradio.wildupdatebackport.sculk;

import net.minecraft.block.Block;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * 1.19.2's {@code SculkSpreader}: charges ("cursors") that creep through sculk from where a mob died near a
 * catalyst, turning blocks into sculk, laying veins and growing sensors and shriekers as they are spent.
 *
 * <p>Two kinds, as vanilla's: the catalyst's (growth costs 10, no growth within 4 blocks of the catalyst, decay
 * 1 in 10, extra decay 1 in 5) and world generation's (50, 1, 5, 10; also replaces the deepslate building blocks,
 * and its shriekers can summon). Saved as 1.19 saves it: a {@code cursors} list of {@code pos} (int array),
 * {@code charge}, {@code decay_delay}, {@code update_delay} and {@code facings} (direction names).
 */
public class SculkSpreader {

    public static final int MAX_GROWTH_RATE_RADIUS = 24;
    public static final int MAX_CHARGE = 1000;
    private static final int MAX_CURSORS = 32;
    public static final int SHRIEKER_PLACEMENT_RATE = 11;

    final boolean isWorldGeneration;
    private final int growthSpawnCost;
    private final int noGrowthRadius;
    private final int chargeDecayRate;
    private final int additionalDecayRate;
    private List<ChargeCursor> cursors = new ArrayList<ChargeCursor>();

    public SculkSpreader(boolean worldGen, int growthSpawnCost, int noGrowthRadius, int chargeDecayRate, int additionalDecayRate) {
        this.isWorldGeneration = worldGen;
        this.growthSpawnCost = growthSpawnCost;
        this.noGrowthRadius = noGrowthRadius;
        this.chargeDecayRate = chargeDecayRate;
        this.additionalDecayRate = additionalDecayRate;
    }

    public static SculkSpreader createLevelSpreader() {
        return new SculkSpreader(false, 10, 4, 10, 5);
    }

    public static SculkSpreader createWorldGenSpreader() {
        return new SculkSpreader(true, 50, 1, 5, 10);
    }

    public boolean replaceable(IBlockState state) {
        return SculkTags.replaceable(state, this.isWorldGeneration);
    }

    public int growthSpawnCost() {
        return this.growthSpawnCost;
    }

    public int noGrowthRadius() {
        return this.noGrowthRadius;
    }

    public int chargeDecayRate() {
        return this.chargeDecayRate;
    }

    public int additionalDecayRate() {
        return this.additionalDecayRate;
    }

    public boolean isWorldGeneration() {
        return this.isWorldGeneration;
    }

    public List<ChargeCursor> getCursors() {
        return this.cursors;
    }

    public void clear() {
        this.cursors.clear();
    }

    public void load(NBTTagCompound tag) {
        if (tag.hasKey("cursors", 9)) {
            this.cursors.clear();
            NBTTagList list = tag.getTagList("cursors", 10);
            for (int i = 0; i < Math.min(list.tagCount(), MAX_CURSORS); i++) {
                ChargeCursor c = ChargeCursor.read(list.getCompoundTagAt(i));
                if (c != null) {
                    addCursor(c);
                }
            }
        }
    }

    public void save(NBTTagCompound tag) {
        NBTTagList list = new NBTTagList();
        for (ChargeCursor c : this.cursors) {
            list.appendTag(c.write());
        }
        tag.setTag("cursors", list);
    }

    public void addCursors(BlockPos pos, int charge) {
        while (charge > 0) {
            int c = Math.min(charge, MAX_CHARGE);
            addCursor(new ChargeCursor(pos, c));
            charge -= c;
        }
    }

    private void addCursor(ChargeCursor cursor) {
        if (this.cursors.size() < MAX_CURSORS) {
            this.cursors.add(cursor);
        }
    }

    /**
     * One step for every cursor, then cursors on the same block merge (up to 1000), and the charge particles are
     * sent (1.19's level event 3006).
     *
     * @param origin the catalyst (or the feature's origin)
     */
    public void updateCursors(World world, BlockPos origin, Random random, boolean spreadVeins) {
        if (this.cursors.isEmpty()) {
            return;
        }
        List<ChargeCursor> kept = new ArrayList<ChargeCursor>();
        Map<BlockPos, ChargeCursor> first = new HashMap<BlockPos, ChargeCursor>();
        Map<BlockPos, Integer> total = new LinkedHashMap<BlockPos, Integer>();
        List<SculkEffectPacket.Entry> effects = this.isWorldGeneration ? null : new ArrayList<SculkEffectPacket.Entry>();
        for (ChargeCursor cursor : this.cursors) {
            cursor.update(world, origin, random, this, spreadVeins);
            if (cursor.charge <= 0) {
                if (effects != null) {
                    effects.add(new SculkEffectPacket.Entry(SculkEffectPacket.CHARGE, cursor.getPos(), 0));
                }
            } else {
                BlockPos at = cursor.getPos();
                Integer sum = total.get(at);
                total.put(at, (sum == null ? 0 : sum) + cursor.charge);
                ChargeCursor there = first.get(at);
                if (there == null) {
                    first.put(at, cursor);
                    kept.add(cursor);
                } else if (!this.isWorldGeneration && cursor.charge + there.charge <= MAX_CHARGE) {
                    there.mergeWith(cursor);
                } else {
                    kept.add(cursor);
                    if (cursor.charge < there.charge) {
                        first.put(at, cursor);
                    }
                }
            }
        }
        if (effects != null) {
            for (Map.Entry<BlockPos, Integer> e : total.entrySet()) {
                ChargeCursor c = first.get(e.getKey());
                Set<EnumFacing> faces = c == null ? null : c.getFacingData();
                if (e.getValue() > 0 && faces != null) {
                    int strength = (int) (Math.log1p(e.getValue()) / 2.3F) + 1;
                    effects.add(new SculkEffectPacket.Entry(SculkEffectPacket.CHARGE, e.getKey(), (strength << 6) + BlockSculkVein.pack(faces)));
                }
            }
            SculkEffectPacket.send(world, origin, effects);
        }
        this.cursors = kept;
    }

    /** One charge on its way. */
    public static class ChargeCursor {
        /** The 18 neighbors that share a face or an edge (no corners). */
        private static final List<BlockPos> NON_CORNER_NEIGHBORS = new ArrayList<BlockPos>(18);

        static {
            for (BlockPos p : BlockPos.getAllInBox(new BlockPos(-1, -1, -1), new BlockPos(1, 1, 1))) {
                if ((p.getX() == 0 || p.getY() == 0 || p.getZ() == 0) && !p.equals(BlockPos.ORIGIN)) {
                    NON_CORNER_NEIGHBORS.add(p.toImmutable());
                }
            }
        }

        private BlockPos pos;
        int charge;
        private int updateDelay;
        private int decayDelay;
        @Nullable
        private Set<EnumFacing> facings;

        private ChargeCursor(BlockPos pos, int charge, int decayDelay, int updateDelay, @Nullable Set<EnumFacing> facings) {
            this.pos = pos;
            this.charge = charge;
            this.decayDelay = decayDelay;
            this.updateDelay = updateDelay;
            this.facings = facings;
        }

        public ChargeCursor(BlockPos pos, int charge) {
            this(pos.toImmutable(), charge, 1, 0, null);
        }

        public BlockPos getPos() {
            return this.pos;
        }

        public int getCharge() {
            return this.charge;
        }

        public int getDecayDelay() {
            return this.decayDelay;
        }

        @Nullable
        public Set<EnumFacing> getFacingData() {
            return this.facings;
        }

        private boolean shouldUpdate(World world, BlockPos origin, boolean worldGen) {
            if (this.charge <= 0) {
                return false;
            }
            return worldGen || world.isBlockLoaded(origin);
        }

        public void update(World world, BlockPos origin, Random random, SculkSpreader spreader, boolean spreadVeins) {
            if (!shouldUpdate(world, origin, spreader.isWorldGeneration)) {
                return;
            }
            if (this.updateDelay > 0) {
                this.updateDelay--;
                return;
            }
            IBlockState state = world.getBlockState(this.pos);
            SculkBehaviour behaviour = behaviour(state);
            if (spreadVeins && behaviour.attemptSpreadVein(world, this.pos, state, this.facings, spreader.isWorldGeneration())) {
                if (behaviour.canChangeBlockStateOnSpread()) {
                    state = world.getBlockState(this.pos);
                    behaviour = behaviour(state);
                }
                world.playSound(null, this.pos, SculkContent.SCULK_SPREAD, SoundCategory.BLOCKS, 1.0F, 1.0F);
            }
            this.charge = behaviour.attemptUseCharge(this, world, origin, random, spreader, spreadVeins);
            if (this.charge <= 0) {
                behaviour.onDischarged(world, state, this.pos, random);
            } else {
                BlockPos next = validMovementPos(world, this.pos, random);
                if (next != null) {
                    behaviour.onDischarged(world, state, this.pos, random);
                    this.pos = next.toImmutable();
                    if (spreader.isWorldGeneration()
                            && this.pos.distanceSq(origin.getX(), this.pos.getY(), origin.getZ()) >= 15.0D * 15.0D) {
                        this.charge = 0;
                        return;
                    }
                    state = world.getBlockState(next);
                }
                if (state.getBlock() instanceof SculkBehaviour) {
                    this.facings = BlockSculkVein.availableFaces(world, this.pos, state);
                }
                this.decayDelay = behaviour.updateDecayDelay(this.decayDelay);
                this.updateDelay = behaviour.sculkSpreadDelay();
            }
        }

        void mergeWith(ChargeCursor other) {
            this.charge += other.charge;
            other.charge = 0;
            this.updateDelay = Math.min(this.updateDelay, other.updateDelay);
        }

        static SculkBehaviour behaviour(IBlockState state) {
            Block block = state.getBlock();
            return block instanceof SculkBehaviour ? (SculkBehaviour) block : SculkBehaviour.DEFAULT;
        }

        @Nullable
        private static BlockPos validMovementPos(World world, BlockPos from, Random random) {
            List<BlockPos> offsets = new ArrayList<BlockPos>(NON_CORNER_NEIGHBORS);
            java.util.Collections.shuffle(offsets, random);
            BlockPos found = from;
            for (BlockPos offset : offsets) {
                BlockPos p = from.add(offset);
                IBlockState s = world.getBlockState(p);
                if (s.getBlock() instanceof SculkBehaviour && isMovementUnobstructed(world, from, p)) {
                    found = p;
                    if (BlockSculkVein.hasSubstrateAccess(world, s, p)) {
                        break;
                    }
                }
            }
            return found.equals(from) ? null : found;
        }

        private static boolean isMovementUnobstructed(World world, BlockPos from, BlockPos to) {
            int dx = to.getX() - from.getX();
            int dy = to.getY() - from.getY();
            int dz = to.getZ() - from.getZ();
            if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) == 1) {
                return true;
            }
            EnumFacing x = dx < 0 ? EnumFacing.WEST : EnumFacing.EAST;
            EnumFacing y = dy < 0 ? EnumFacing.DOWN : EnumFacing.UP;
            EnumFacing z = dz < 0 ? EnumFacing.NORTH : EnumFacing.SOUTH;
            if (dx == 0) {
                return isUnobstructed(world, from, y) || isUnobstructed(world, from, z);
            }
            return dy == 0 ? isUnobstructed(world, from, x) || isUnobstructed(world, from, z)
                    : isUnobstructed(world, from, x) || isUnobstructed(world, from, y);
        }

        private static boolean isUnobstructed(World world, BlockPos from, EnumFacing dir) {
            BlockPos p = from.offset(dir);
            return !sturdy(world, p, dir.getOpposite());
        }

        static boolean sturdy(World world, BlockPos pos, EnumFacing face) {
            IBlockState s = world.getBlockState(pos);
            return s.getBlockFaceShape(world, pos, face) == BlockFaceShape.SOLID;
        }

        NBTTagCompound write() {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setTag("pos", new NBTTagIntArray(new int[]{this.pos.getX(), this.pos.getY(), this.pos.getZ()}));
            tag.setInteger("charge", this.charge);
            tag.setInteger("decay_delay", this.decayDelay);
            tag.setInteger("update_delay", this.updateDelay);
            if (this.facings != null) {
                NBTTagList list = new NBTTagList();
                for (EnumFacing f : this.facings) {
                    list.appendTag(new NBTTagString(f.getName()));
                }
                tag.setTag("facings", list);
            }
            return tag;
        }

        @Nullable
        static ChargeCursor read(NBTTagCompound tag) {
            int[] p = tag.getIntArray("pos");
            if (p.length != 3) {
                return null;
            }
            Set<EnumFacing> facings = null;
            if (tag.hasKey("facings", 9)) {
                facings = EnumSet.noneOf(EnumFacing.class);
                NBTTagList list = tag.getTagList("facings", 8);
                for (int i = 0; i < list.tagCount(); i++) {
                    EnumFacing f = EnumFacing.byName(list.getStringTagAt(i));
                    if (f != null) {
                        facings.add(f);
                    }
                }
            }
            int decay = tag.hasKey("decay_delay", 99) ? MathHelper.clamp(tag.getInteger("decay_delay"), 0, 1) : 1;
            return new ChargeCursor(new BlockPos(p[0], p[1], p[2]), MathHelper.clamp(tag.getInteger("charge"), 0, MAX_CHARGE), decay,
                    Math.max(0, tag.getInteger("update_delay")), facings);
        }
    }
}
