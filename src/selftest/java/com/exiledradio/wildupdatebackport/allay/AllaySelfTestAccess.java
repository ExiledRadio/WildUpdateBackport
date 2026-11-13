package com.exiledradio.wildupdatebackport.allay;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.gen.structure.StructureComponent;
import net.minecraft.world.gen.structure.template.PlacementSettings;
import net.minecraft.world.gen.structure.template.Template;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/** The self-test's way into the allay's package-private state. Compiled only into test builds. */
public final class AllaySelfTestAccess {

    private AllaySelfTestAccess() {
    }

    public static boolean canDespawn(EntityAllay allay) {
        return allay.canDespawn();
    }

    public static boolean interact(EntityAllay allay, EntityPlayer player, EnumHand hand) {
        return allay.processInteract(player, hand);
    }

    @Nullable
    public static UUID likedPlayer(EntityAllay allay) {
        return allay.brain.likedPlayer();
    }

    public static void setLikedPlayer(EntityAllay allay, UUID id) {
        allay.brain.setLikedPlayer(id);
    }

    public static void pickUp(EntityAllay allay, EntityItem item) {
        allay.pickUp(item);
    }

    public static boolean panicking(EntityAllay allay) {
        return allay.brain.isPanicking();
    }

    public static int pickupCooldown(EntityAllay allay) {
        return allay.brain.pickupCooldown();
    }

    @Nullable
    public static BlockPos likedNoteBlock(EntityAllay allay) {
        return allay.brain.likedNoteBlock();
    }

    public static int noteBlockCooldown(EntityAllay allay) {
        return allay.brain.noteBlockCooldown();
    }

    /** Runs the brain's cooldowns down past the note block's 600 ticks. */
    public static void expireNoteBlock(EntityAllay allay) {
        NBTTagCompound tag = allay.brain.writeToNBT();
        tag.removeTag("LikedNoteBlockCooldown");
        allay.brain.readFromNBT(tag);
    }

    @Nullable
    public static BlockPos depositBlock(EntityAllay allay) {
        AllayBrain.Tracker t = allay.brain.depositTarget();
        return t == null ? null : t.block();
    }

    /** The vibration listener's own tick, which delivers a vibration in flight. */
    public static void tickListener(EntityAllay allay) {
        try {
            java.lang.reflect.Field f = EntityAllay.class.getDeclaredField("vibrationListener");
            f.setAccessible(true);
            ((com.exiledradio.wildupdatebackport.gameevent.VibrationListener) f.get(allay)).tick(allay.world);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    public static int cageIndex() {
        return AllayStructures.cageIndex;
    }

    public static List<BlockPos> mansionMarkers(StructureComponent room) {
        NBTTagCompound tag = room.createStructureBaseNBT();
        BlockPos origin = new BlockPos(tag.getInteger("TPX"), tag.getInteger("TPY"), tag.getInteger("TPZ"));
        PlacementSettings settings = new PlacementSettings().setRotation(Rotation.valueOf(tag.getString("Rot")))
                .setMirror(Mirror.valueOf(tag.getString("Mi")));
        List<BlockPos> out = new ArrayList<BlockPos>();
        for (BlockPos m : AllayStructures.MANSION_MARKERS) {
            out.add(Template.transformedBlockPos(settings, m).add(origin));
        }
        return out;
    }

    public static int spawnCageAllays(World world, StructureComponent piece, StructureBoundingBox box) {
        return AllayStructures.spawnCageAllays(world, piece, box);
    }

    public static int spawnMansionAllays(World world, StructureComponent room, StructureBoundingBox box, Random random) {
        return AllayStructures.spawnMansionAllays(world, room, box, random);
    }
}
