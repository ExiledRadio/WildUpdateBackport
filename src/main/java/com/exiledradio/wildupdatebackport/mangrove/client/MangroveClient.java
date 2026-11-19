package com.exiledradio.wildupdatebackport.mangrove.client;

import com.exiledradio.wildupdatebackport.WildUpdateBackport;
import com.exiledradio.wildupdatebackport.mangrove.BlockMangroveLeaves;
import com.exiledradio.wildupdatebackport.mangrove.MangroveContent;
import com.exiledradio.wildupdatebackport.mangrove.MangroveSign;
import com.exiledradio.wildupdatebackport.mangrove.RootsCrack;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.client.event.ColorHandlerEvent;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

/**
 * The mangrove set's client side: the sign renderer, the leaves' fixed tint and their fancy/fast switch, and the
 * roots' crack ({@link RootsCrackModel}).
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

    /** Mangrove roots' model, with the crack's quads drawn as 1.19 draws them. */
    @SubscribeEvent
    public static void bake(ModelBakeEvent event) {
        IBlockState roots = MangroveContent.roots();
        if (roots == null) {
            return;
        }
        ModelResourceLocation where = new ModelResourceLocation(roots.getBlock().getRegistryName(), "normal");
        IBakedModel model = event.getModelRegistry().getObject(where);
        if (model != null && !(model instanceof RootsCrackModel)) {
            event.getModelRegistry().putObject(where, new RootsCrackModel(model));
            RootsCrack.viewer = RootsCrackModel.VIEWER;
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
