package com.exiledradio.wildupdatebackport.selftest;

import com.exiledradio.wildupdatebackport.sculk.BlockSculkVein;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.IWorldEventListener;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * What a client would see of some chunks: each chunk as the server would send it (its blocks, as state ids, and its
 * sculk veins' faces) once it is populated, then every block the server tells clients about since
 * ({@code notifyBlockUpdate}, sent with its current state and tile entity). {@link #mismatches} compares that with the
 * world: a block that differs is one a player sees wrong until something resends it.
 */
final class ClientMirror implements IWorldEventListener {

    private final WorldServer world;
    private final int minY, maxY;
    private final Map<Long, int[]> sent = new HashMap<Long, int[]>();
    private final Map<BlockPos, Integer> sentFaces = new HashMap<BlockPos, Integer>();
    private final Set<BlockPos> notified = new HashSet<BlockPos>();

    ClientMirror(WorldServer world, int minY, int maxY) {
        this.world = world;
        this.minY = Math.max(0, minY);
        this.maxY = Math.min(255, maxY);
        world.addEventListener(this);
    }

    void close() {
        this.world.removeEventListener(this);
    }

    /** Sends every loaded, populated chunk in the range that has not been sent yet, as the player chunk map would (ticking it first). */
    void sendPopulated(int minCx, int minCz, int maxCx, int maxCz) {
        for (int x = minCx; x <= maxCx; x++) {
            for (int z = minCz; z <= maxCz; z++) {
                long k = ChunkPos.asLong(x, z);
                if (this.sent.containsKey(k) || !this.world.getChunkProvider().chunkExists(x, z)) {
                    continue;
                }
                Chunk chunk = this.world.getChunk(x, z);
                if (!chunk.isTerrainPopulated()) {
                    continue;
                }
                // A chunk in a player's view ticks (and lights) before the player chunk map sends it.
                chunk.onTick(false);
                if (!chunk.isPopulated()) {
                    continue;
                }
                int h = this.maxY - this.minY + 1;
                int[] ids = new int[16 * 16 * h];
                for (int y = 0; y < h; y++) {
                    for (int dz = 0; dz < 16; dz++) {
                        for (int dx = 0; dx < 16; dx++) {
                            BlockPos p = new BlockPos((x << 4) + dx, this.minY + y, (z << 4) + dz);
                            IBlockState s = chunk.getBlockState(p);
                            ids[(y * 16 + dz) * 16 + dx] = Block.getStateId(s);
                            if (BlockSculkVein.isVein(s)) {
                                this.sentFaces.put(p, BlockSculkVein.faces(this.world, p));
                            }
                        }
                    }
                }
                this.sent.put(k, ids);
            }
        }
    }

    int sentChunks() {
        return this.sent.size();
    }

    /** @return by block name (what the client has, then what the server has), how many differ, and the first one */
    Map<String, String> mismatches() {
        Map<String, Integer> counts = new TreeMap<String, Integer>();
        Map<String, BlockPos> first = new HashMap<String, BlockPos>();
        for (Map.Entry<Long, int[]> e : this.sent.entrySet()) {
            int cx = (int) (long) e.getKey(), cz = (int) (e.getKey() >> 32);
            int[] ids = e.getValue();
            for (int i = 0; i < ids.length; i++) {
                BlockPos p = new BlockPos((cx << 4) + (i & 15), this.minY + i / 256, (cz << 4) + ((i >> 4) & 15));
                if (this.notified.contains(p)) {
                    continue;
                }
                IBlockState now = this.world.getBlockState(p);
                IBlockState client = Block.getStateById(ids[i]);
                String key = null;
                if (Block.getStateId(now) != ids[i]) {
                    key = client.getBlock().getRegistryName() + " -> " + now.getBlock().getRegistryName();
                } else if (BlockSculkVein.isVein(now) && !Integer.valueOf(BlockSculkVein.faces(this.world, p)).equals(this.sentFaces.get(p))) {
                    key = "sculk vein faces";
                }
                if (key != null) {
                    counts.put(key, counts.containsKey(key) ? counts.get(key) + 1 : 1);
                    if (!first.containsKey(key)) {
                        first.put(key, p.toImmutable());
                    }
                }
            }
        }
        Map<String, String> out = new TreeMap<String, String>();
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            out.put(e.getKey(), e.getValue() + " (first at " + first.get(e.getKey()) + ")");
        }
        return out;
    }

    @Override
    public void notifyBlockUpdate(World w, BlockPos pos, IBlockState oldState, IBlockState newState, int flags) {
        if (this.sent.containsKey(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4))) {
            this.notified.add(pos.toImmutable());
        }
    }

    @Override
    public void notifyLightSet(BlockPos pos) {
    }

    @Override
    public void markBlockRangeForRenderUpdate(int x1, int y1, int z1, int x2, int y2, int z2) {
    }

    @Override
    public void playSoundToAllNearExcept(@Nullable EntityPlayer player, SoundEvent sound, SoundCategory category, double x, double y, double z,
                                         float volume, float pitch) {
    }

    @Override
    public void playRecord(SoundEvent sound, BlockPos pos) {
    }

    @Override
    public void spawnParticle(int id, boolean ignoreRange, double x, double y, double z, double xs, double ys, double zs, int... params) {
    }

    @Override
    public void spawnParticle(int id, boolean ignoreRange, boolean minimise, double x, double y, double z, double xs, double ys, double zs,
                              int... params) {
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
}
