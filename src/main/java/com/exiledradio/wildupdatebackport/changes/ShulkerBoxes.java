package com.exiledradio.wildupdatebackport.changes;

import com.exiledradio.wildupdatebackport.ModConfig;
import com.exiledradio.wildupdatebackport.WildUpdateBackport;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityShulkerBox;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 1.19.3's shulker box tells its neighbors whenever its lid starts or stops moving ({@code doNeighborUpdates}: when
 * it starts opening or closing and when it is fully open or closed), so a torch or other attached block on a face that
 * no longer supports it (patch P7 gives the open box 1.19.3's support faces) pops off. 1.12.2's never told them.
 *
 * <p>Every shulker box tile is remembered when Forge gathers its capabilities; each server world tick compares each
 * loaded box's animation status with the last one seen.
 */
@Mod.EventBusSubscriber(modid = WildUpdateBackport.MOD_ID)
public final class ShulkerBoxes {

    private static final Map<TileEntityShulkerBox, TileEntityShulkerBox.AnimationStatus> SEEN =
            new WeakHashMap<TileEntityShulkerBox, TileEntityShulkerBox.AnimationStatus>();

    private ShulkerBoxes() {
    }

    @SubscribeEvent
    public static void attach(AttachCapabilitiesEvent<TileEntity> event) {
        if (ModConfig.changes.enabled && event.getObject() instanceof TileEntityShulkerBox) {
            synchronized (SEEN) {
                SEEN.put((TileEntityShulkerBox) event.getObject(), null);
            }
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.world.isRemote || !ChangesHooks.on(ModConfig.changes.shulkerBoxes)) {
            return;
        }
        List<TileEntityShulkerBox> tiles;
        synchronized (SEEN) {
            tiles = new ArrayList<TileEntityShulkerBox>(SEEN.keySet());
        }
        for (TileEntityShulkerBox te : tiles) {
            if (te.getWorld() != event.world || te.isInvalid() || !event.world.isBlockLoaded(te.getPos())
                    || event.world.getTileEntity(te.getPos()) != te) {
                continue;
            }
            TileEntityShulkerBox.AnimationStatus now = te.getAnimationStatus();
            TileEntityShulkerBox.AnimationStatus before;
            synchronized (SEEN) {
                before = SEEN.put(te, now);
            }
            if (before != null && before != now) {
                event.world.notifyNeighborsOfStateChange(te.getPos(), te.getBlockType(), false);
            }
        }
    }
}
