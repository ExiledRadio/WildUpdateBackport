package com.exiledradio.wildupdatebackport.deepdark;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The deep dark's decoration of populated chunks, waiting until it can run without generating a chunk.
 *
 * <p>1.19 decorates a chunk with the 3 x 3 chunks around it in hand, and a sculk vein's search (20 blocks) or a patch's
 * charges reach into them. 1.12.2 populates a half chunk in, with only the 2 x 2 chunks under the area sure to exist;
 * reaching further generates chunks in the middle of populating (cascading generation), and leaving those tries out
 * loses half the sculk. So each populated chunk's deep dark decoration (the city's sculk patches in its area, then the
 * two features) waits here until the 4 x 4 chunks its reach can touch ({@link #REACH}) are generated, and runs then,
 * whole, from the chunk's own random: on the chunk's populate if they are, else on a later populate nearby. Kept with
 * the world, so a chunk left waiting at the edge of the explored area is decorated when exploring resumes.
 */
public final class DeepDarkPending extends WorldSavedData {

    static final String NAME = "wildupdatebackport_deepdark_pending";
    /** Chunks a populate area's decoration can touch: one before it to two after, each way. */
    static final int REACH_BEFORE = 1, REACH_AFTER = 2;
    /** How far from a populated chunk to look for decoration that may have become ready. */
    private static final int LOOK = 3;

    /** By populate chunk: whether to run the features, and the city's sculk patches in its area. */
    private final Map<Long, Entry> entries = new LinkedHashMap<Long, Entry>();
    private boolean running;

    static final class Entry {
        boolean features;
        final List<BlockPos> patches = new ArrayList<BlockPos>();
    }

    /** What runs a ready chunk's decoration. */
    interface Decorator {
        void decorate(World world, int chunkX, int chunkZ, Entry entry);
    }

    public DeepDarkPending(String name) {
        super(name);
    }

    public static DeepDarkPending of(World world) {
        MapStorage storage = world.getPerWorldStorage();
        DeepDarkPending d = (DeepDarkPending) storage.getOrLoadData(DeepDarkPending.class, NAME);
        if (d == null) {
            d = new DeepDarkPending(NAME);
            storage.setData(NAME, d);
        }
        return d;
    }

    /** @return the chunk whose populate area (a half chunk in) holds this block */
    static ChunkPos populateChunk(BlockPos p) {
        return new ChunkPos((p.getX() - 8) >> 4, (p.getZ() - 8) >> 4);
    }

    /** A city sculk patch to place with its populate chunk's decoration. */
    public void cityPatch(BlockPos pos) {
        ChunkPos c = populateChunk(pos);
        entry(c.x, c.z).patches.add(pos.toImmutable());
        markDirty();
    }

    /** The chunk's deep dark features, to place with its decoration. */
    void features(int chunkX, int chunkZ) {
        entry(chunkX, chunkZ).features = true;
        markDirty();
    }

    /** @return true if the populate chunk's decoration is waiting */
    public boolean isWaiting(int chunkX, int chunkZ) {
        return this.entries.containsKey(ChunkPos.asLong(chunkX, chunkZ));
    }

    /** @return the number of populate chunks waiting */
    public int waiting() {
        return this.entries.size();
    }

    private Entry entry(int x, int z) {
        long k = ChunkPos.asLong(x, z);
        Entry e = this.entries.get(k);
        if (e == null) {
            e = new Entry();
            this.entries.put(k, e);
        }
        return e;
    }

    /** Runs every waiting decoration near chunk (chunkX, chunkZ) whose chunks are all generated. */
    void runReady(World world, int chunkX, int chunkZ, Decorator decorator) {
        if (this.running) {
            // A chunk loaded by a decoration populated a neighbor; the loop below looks again.
            return;
        }
        this.running = true;
        try {
            boolean again = true;
            while (again) {
                again = false;
                for (int x = chunkX - LOOK; x <= chunkX + LOOK; x++) {
                    for (int z = chunkZ - LOOK; z <= chunkZ + LOOK; z++) {
                        long k = ChunkPos.asLong(x, z);
                        Entry e = this.entries.get(k);
                        if (e != null && ready(world, x, z)) {
                            this.entries.remove(k);
                            markDirty();
                            decorator.decorate(world, x, z, e);
                            again = true;
                        }
                    }
                }
            }
        } finally {
            this.running = false;
        }
    }

    /** @return true when every chunk a populate area's decoration can touch is generated */
    static boolean ready(World world, int chunkX, int chunkZ) {
        IChunkProvider provider = world.getChunkProvider();
        for (int x = chunkX - REACH_BEFORE; x <= chunkX + REACH_AFTER; x++) {
            for (int z = chunkZ - REACH_BEFORE; z <= chunkZ + REACH_AFTER; z++) {
                if (!provider.isChunkGeneratedAt(x, z)) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        this.entries.clear();
        NBTTagList list = tag.getTagList("chunks", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound c = list.getCompoundTagAt(i);
            Entry e = entry(c.getInteger("x"), c.getInteger("z"));
            e.features = c.getBoolean("features");
            NBTTagList patches = c.getTagList("patches", 4);
            for (int j = 0; j < patches.tagCount(); j++) {
                e.patches.add(BlockPos.fromLong(((net.minecraft.nbt.NBTTagLong) patches.get(j)).getLong()));
            }
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        NBTTagList list = new NBTTagList();
        for (Map.Entry<Long, Entry> m : this.entries.entrySet()) {
            NBTTagCompound c = new NBTTagCompound();
            long k = m.getKey();
            c.setInteger("x", (int) k);
            c.setInteger("z", (int) (k >> 32));
            c.setBoolean("features", m.getValue().features);
            NBTTagList patches = new NBTTagList();
            for (BlockPos p : m.getValue().patches) {
                patches.appendTag(new net.minecraft.nbt.NBTTagLong(p.toLong()));
            }
            c.setTag("patches", patches);
            list.appendTag(c);
        }
        tag.setTag("chunks", list);
        return tag;
    }
}
