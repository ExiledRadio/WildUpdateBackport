package com.exiledradio.wildupdatebackport.deepdark;

import net.minecraft.world.World;

/**
 * Block changes as 1.19's world generation makes them: {@code WorldGenRegion.setBlock} never updates neighbors, so a
 * ladder whose wall rotted away ({@code block_rot}) or a mushroom next to new sculk stays where it is. 1.12.2 has no such
 * region; Forge's block snapshot capture does the same ({@code World.setBlockState} skips {@code markAndNotifyBlock},
 * {@code Chunk.setBlockState} skips {@code onBlockAdded}), and the snapshots taken are dropped afterwards rather than
 * restored. Nested inside someone else's capture, it leaves their snapshots alone.
 */
public final class QuietGen {

    private QuietGen() {
    }

    public static void run(World world, Runnable body) {
        boolean was = world.captureBlockSnapshots;
        int before = world.capturedBlockSnapshots.size();
        world.captureBlockSnapshots = true;
        try {
            body.run();
        } finally {
            world.captureBlockSnapshots = was;
            while (world.capturedBlockSnapshots.size() > before) {
                world.capturedBlockSnapshots.remove(world.capturedBlockSnapshots.size() - 1);
            }
        }
    }
}
