package com.exiledradio.wildupdatebackport.deepdark.city;

import com.exiledradio.backportcore.structure.JigsawElement;
import com.exiledradio.backportcore.structure.JigsawPools;
import com.exiledradio.wildupdatebackport.WildUpdateBackport;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.structure.StructureBoundingBox;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * 1.19.2's jigsaw assembly ({@code JigsawPlacement.addPieces} and its {@code Placer}) for the ancient city: the start
 * piece turned and moved so its {@code minecraft:city_anchor} jigsaw lands on the start position, then breadth first
 * to depth 7, each jigsaw trying its pool's elements (and then its fallback's) in shuffled order, each in shuffled
 * rotations, joining the first that has a matching jigsaw and fits in the free space, all inside a cube 116 blocks
 * out from the start. Every piece of the city is rigid, so the ground is never asked.
 *
 * <p>The random is drawn as 1.19.2 draws it: {@code Util.shuffle}, a Fisher-Yates of n - 1 draws, for jigsaws,
 * rotations and pools alike (Backport Core 1.1.0's {@code Jigsaw} is 1.16.5's, whose pool shuffle is fastutil's n
 * draws, and has no start jigsaw or distance). Pools, elements, templates and the pieces themselves are Core's.
 */
public final class CityLayout {

    /** {@code Structures.ANCIENT_CITY}'s settings. */
    public static final ResourceLocation START_POOL = new ResourceLocation("minecraft", "ancient_city/city_center");
    public static final String START_JIGSAW = "minecraft:city_anchor";
    public static final int MAX_DEPTH = 7;
    public static final int MAX_DISTANCE = 116;
    /** {@code ConstantHeight.of(VerticalAnchor.absolute(-27))}. */
    public static final int START_Y_119 = -27;

    /** 1.19's {@code GenerationStub}: where the biome is checked, and the pieces. */
    public static final class Stub {
        public final BlockPos position;
        final CityPiece start;
        final BlockPos anchorOffset;

        Stub(BlockPos position, CityPiece start, BlockPos anchorOffset) {
            this.position = position;
            this.start = start;
            this.anchorOffset = anchorOffset;
        }
    }

    private CityLayout() {
    }

    /**
     * The start piece and the stub's position, as {@code JigsawPlacement.addPieces} computes them before any child.
     *
     * @param origin the chunk's corner at the start height
     * @return null when the start pool gives nothing or the template has no anchor
     */
    @Nullable
    public static Stub start(BlockPos origin, Random random) {
        JigsawPools.Pool pool = JigsawPools.pool(START_POOL);
        if (pool == null || pool.size() == 0) {
            WildUpdateBackport.LOGGER.warn("The ancient city start pool {} is missing.", START_POOL);
            return null;
        }
        Rotation rotation = Rotation.values()[random.nextInt(4)];
        JigsawElement element = pool.random(random);
        if (element.isEmpty()) {
            return null;
        }
        BlockPos anchor = null;
        for (JigsawElement.Connector c : shuffled(element.connectors(origin, rotation), random)) {
            if (START_JIGSAW.equals(full(c.name))) {
                anchor = c.pos;
                break;
            }
        }
        if (anchor == null) {
            WildUpdateBackport.LOGGER.error("No starting jigsaw {} found in start pool {}", START_JIGSAW, START_POOL);
            return null;
        }
        BlockPos offset = anchor.subtract(origin);
        BlockPos at = origin.subtract(offset);
        CityPiece first = new CityPiece(element, at, element.groundLevelDelta(), rotation, element.box(at, rotation));
        StructureBoundingBox box = first.getBoundingBox();
        int midX = (box.maxX + box.minX) / 2;
        int midZ = (box.maxZ + box.minZ) / 2;
        int y = at.getY();
        int floor = box.minY + first.groundLevelDelta();
        first.offset(0, y - floor, 0);
        return new Stub(new BlockPos(midX, y + offset.getY(), midZ), first, offset);
    }

    /** The whole city: {@link #start}, then its children. Empty when there is no start. */
    public static List<CityPiece> assemble(BlockPos origin, Random random) {
        List<CityPiece> pieces = new ArrayList<CityPiece>();
        Stub stub = start(origin, random);
        if (stub == null) {
            return pieces;
        }
        CityPiece first = stub.start;
        pieces.add(first);
        BlockPos c = stub.position;
        Space cube = new Space(new StructureBoundingBox(c.getX() - MAX_DISTANCE, c.getY() - MAX_DISTANCE, c.getZ() - MAX_DISTANCE,
                c.getX() + MAX_DISTANCE, c.getY() + MAX_DISTANCE, c.getZ() + MAX_DISTANCE));
        cube.taken.add(new StructureBoundingBox(first.getBoundingBox()));
        Deque<State> placing = new ArrayDeque<State>();
        placing.addLast(new State(first, cube, 0));
        while (!placing.isEmpty()) {
            State s = placing.removeFirst();
            tryPlacingChildren(s.piece, s.free, s.depth, pieces, placing, random);
        }
        // StructureStart.placeInChunk: every piece's pivot is the first piece's middle at its floor.
        StructureBoundingBox b = first.getBoundingBox();
        BlockPos pivot = new BlockPos(b.minX + (b.maxX - b.minX + 1) / 2, b.minY, b.minZ + (b.maxZ - b.minZ + 1) / 2);
        for (CityPiece piece : pieces) {
            piece.setPivot(pivot);
        }
        return pieces;
    }

    // ---- free space: VoxelShapes of whole-block boxes, as Core's Jigsaw keeps them

    private static final class Space {
        @Nullable
        final StructureBoundingBox outside;
        final List<StructureBoundingBox> taken = new ArrayList<StructureBoundingBox>();

        Space(@Nullable StructureBoundingBox outside) {
            this.outside = outside;
        }

        /** "The box, shrunk by a quarter block, is all inside the free shape." */
        boolean fits(StructureBoundingBox box) {
            if (this.outside != null && !(this.outside.minX <= box.minX && box.maxX <= this.outside.maxX
                    && this.outside.minY <= box.minY && box.maxY <= this.outside.maxY
                    && this.outside.minZ <= box.minZ && box.maxZ <= this.outside.maxZ)) {
                return false;
            }
            for (StructureBoundingBox t : this.taken) {
                if (t.intersectsWith(box)) {
                    return false;
                }
            }
            return true;
        }
    }

    private static final class State {
        final CityPiece piece;
        final Space free;
        final int depth;

        State(CityPiece piece, Space free, int depth) {
            this.piece = piece;
            this.free = free;
            this.depth = depth;
        }
    }

    private static void tryPlacingChildren(CityPiece parent, Space outerFree, int depth, List<CityPiece> pieces, Deque<State> placing,
                                           Random random) {
        JigsawElement element = parent.element();
        BlockPos position = parent.position();
        Rotation rotation = parent.rotation();
        boolean parentRigid = element.rigid();
        Space inner = null;
        StructureBoundingBox parentBox = parent.getBoundingBox();
        int parentMinY = parentBox.minY;

        connectorLoop:
        for (JigsawElement.Connector connector : shuffled(element.connectors(position, rotation), random)) {
            EnumFacing front = connector.front;
            BlockPos at = connector.pos;
            BlockPos joint = at.offset(front);
            int connectorY = at.getY() - parentMinY;
            ResourceLocation poolId = new ResourceLocation(connector.pool);
            JigsawPools.Pool pool = JigsawPools.pool(poolId);
            if (pool == null || (pool.size() == 0 && !poolId.equals(JigsawPools.EMPTY))) {
                WildUpdateBackport.LOGGER.warn("Empty or non-existent pool: {}", poolId);
                continue;
            }
            ResourceLocation fallbackId = pool.fallback();
            JigsawPools.Pool fallback = JigsawPools.pool(fallbackId);
            if (fallback == null || (fallback.size() == 0 && !fallbackId.equals(JigsawPools.EMPTY))) {
                WildUpdateBackport.LOGGER.warn("Empty or non-existent fallback pool: {}", fallbackId);
                continue;
            }
            Space free;
            if (parentBox.isVecInside(joint)) {
                if (inner == null) {
                    inner = new Space(new StructureBoundingBox(parentBox));
                }
                free = inner;
            } else {
                free = outerFree;
            }
            List<JigsawElement> candidates = new ArrayList<JigsawElement>();
            if (depth != MAX_DEPTH) {
                candidates.addAll(shuffled(pool.elements(), random));
            }
            candidates.addAll(shuffled(fallback.elements(), random));

            for (JigsawElement candidate : candidates) {
                if (candidate.isEmpty()) {
                    break;
                }
                for (Rotation r : shuffled(Arrays.asList(Rotation.values()), random)) {
                    List<JigsawElement.Connector> theirs = shuffled(candidate.connectors(BlockPos.ORIGIN, r), random);
                    for (JigsawElement.Connector their : theirs) {
                        if (!connector.canAttach(their)) {
                            continue;
                        }
                        BlockPos theirPos = their.pos;
                        BlockPos offset = joint.subtract(theirPos);
                        StructureBoundingBox childBox = candidate.box(offset, r);
                        boolean childRigid = candidate.rigid();
                        int theirY = theirPos.getY();
                        int delta = connectorY - theirY + front.getYOffset();
                        int targetMinY;
                        if (parentRigid && childRigid) {
                            targetMinY = parentMinY + delta;
                        } else {
                            // No ancient city piece is terrain matching; the joint's own height stands in.
                            targetMinY = at.getY() - theirY;
                        }
                        int dy = targetMinY - childBox.minY;
                        StructureBoundingBox moved = new StructureBoundingBox(childBox);
                        moved.offset(0, dy, 0);
                        BlockPos childPos = offset.add(0, dy, 0);
                        if (free.fits(moved)) {
                            free.taken.add(new StructureBoundingBox(moved));
                            int childGround = childRigid ? parent.groundLevelDelta() - delta : candidate.groundLevelDelta();
                            CityPiece child = new CityPiece(candidate, childPos, childGround, r, moved);
                            // The junctions, both ways: the terrain's beard follows them (1.19's Beardifier).
                            int sourceGroundY;
                            if (parentRigid) {
                                sourceGroundY = parentMinY + connectorY;
                            } else if (childRigid) {
                                sourceGroundY = targetMinY + theirY;
                            } else {
                                sourceGroundY = at.getY() + delta / 2;
                            }
                            parent.addJunction(joint.getX(), sourceGroundY - connectorY + parent.groundLevelDelta(), joint.getZ());
                            child.addJunction(at.getX(), sourceGroundY - theirY + childGround, at.getZ());
                            pieces.add(child);
                            if (depth + 1 <= MAX_DEPTH) {
                                placing.addLast(new State(child, free, depth + 1));
                            }
                            continue connectorLoop;
                        }
                    }
                }
            }
        }
    }

    /** 1.19's {@code Util.shuffledCopy}: a copy, shuffled with n - 1 draws (the same draws as Collections.shuffle). */
    static <T> List<T> shuffled(List<T> in, Random random) {
        List<T> out = new ArrayList<T>(in);
        Collections.shuffle(out, random);
        return out;
    }

    static String full(String name) {
        return name.indexOf(':') < 0 ? "minecraft:" + name : name;
    }
}
