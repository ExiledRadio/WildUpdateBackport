package com.exiledradio.wildupdatebackport.mangrove.client;

import com.exiledradio.wildupdatebackport.WildUpdateBackport;
import com.exiledradio.wildupdatebackport.mangrove.BlockMangroveLeaves;
import com.exiledradio.wildupdatebackport.mangrove.MangroveContent;
import com.exiledradio.wildupdatebackport.mangrove.MangroveSign;

import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraftforge.client.event.ColorHandlerEvent;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

/**
 * The mangrove set's client side: the sign renderer, the leaves' fixed tint and their fancy/fast switch.
 * Forge loads this class only on the client.
 */
@Mod.EventBusSubscriber(modid = WildUpdateBackport.MOD_ID, value = Side.CLIENT)
public final class MangroveClient {

    /** 1.19's {@code FoliageColor.getMangroveColor()}. */
    public static final int LEAVES_COLOR = 0x92C648;

    private MangroveClient() {
    }

    @SubscribeEvent
    public static void models(ModelRegistryEvent event) {
        if (MangroveContent.enabled()) {
            ClientRegistry.bindTileEntitySpecialRenderer(MangroveSign.Tile.class, new MangroveSignRenderer());
        }
    }

    @SubscribeEvent
    public static void blockColors(ColorHandlerEvent.Block event) {
        BlockMangroveLeaves leaves = MangroveContent.leaves();
        if (leaves != null) {
            event.getBlockColors().registerBlockColorHandler((state, world, pos, tint) -> LEAVES_COLOR, leaves);
        }
    }

    @SubscribeEvent
    public static void itemColors(ColorHandlerEvent.Item event) {
        BlockMangroveLeaves leaves = MangroveContent.leaves();
        if (leaves != null) {
            event.getItemColors().registerItemColorHandler((stack, tint) -> LEAVES_COLOR, Item.getItemFromBlock(leaves));
        }
    }

    /** Vanilla tells its own leaves when graphics change; ours follow the setting every tick. */
    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        BlockMangroveLeaves leaves = MangroveContent.leaves();
        if (event.phase == TickEvent.Phase.START && leaves != null) {
            leaves.setFancy(Minecraft.getMinecraft().gameSettings.fancyGraphics);
        }
    }
}
