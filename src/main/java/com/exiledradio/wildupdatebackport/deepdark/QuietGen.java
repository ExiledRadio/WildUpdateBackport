package com.exiledradio.wildupdatebackport.deepdark;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.util.BlockSnapshot;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Block changes as 1.19's world generation makes them: {@code WorldGenRegion.setBlock} never updates neighbors, so a
 * ladder whose wall rotted away ({@code block_rot}) or a mushroom next to new sculk stays where it is. 1.12.2 has no such
 * region; Forge's block snapshot capture does the same ({@code World.setBlockState} skips {@code markAndNotifyBlock},
 * {@code Chunk.setBlockState} skips {@code onBlockAdded}), and the snapshots taken are dropped afterwards rather than
 * restored. Nested inside someone else's capture, it leaves their snapshots alone.
 *
 * <p>Skipping {@code markAndNotifyBlock} also skips telling clients ({@code notifyBlockUpdate}). A city piece or a
 * deep dark decoration can land in a chunk a player already has (a neighbor's populate, or decoration that waited for
 * its chunks), and the player would keep seeing the old block until something resent it. So each change made with
 * flag 2, in a populated chunk, is sent afterwards, as {@code markAndNotifyBlock} would have; neighbors still are not
 * told.
 */
public final class QuietGen {

    private QuietGen() {
    }

    public static void run(World world, Runnable body) {
        boolean was = world.captureBlockSnapshots;
        List<BlockSnapshot> captured = world.capturedBlockSnapshots;
        int before = captured.size();
        world.captureBlockSnapshots = true;
        Map<BlockPos, IBlockState> changed = new LinkedHashMap<BlockPos, IBlockState>();
        try {
            body.run();
        } finally {
            world.captureBlockSnapshots = was;
            for (int i = before; i < captured.size(); i++) {
                BlockSnapshot s = captured.get(i);
                if ((s.getFlag() & 2) != 0 && !changed.containsKey(s.getPos())) {
                    changed.put(s.getPos(), s.getReplacedBlock());
                }
            }
            while (captured.size() > before) {
                captured.remove(captured.size() - 1);
            }
        }
        if (world.isRemote) {
            return;
        }
        for (Map.Entry<BlockPos, IBlockState> e : changed.entrySet()) {
            BlockPos pos = e.getKey();
            if (!world.isBlockLoaded(pos)) {
                continue;
            }
            Chunk chunk = world.getChunk(pos);
            if (chunk.isPopulated()) {
                world.notifyBlockUpdate(pos, e.getValue(), world.getBlockState(pos), 3);
            }
        }
    }
}
