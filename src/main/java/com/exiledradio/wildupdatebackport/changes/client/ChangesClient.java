package com.exiledradio.wildupdatebackport.changes.client;

import com.exiledradio.wildupdatebackport.ModConfig;
import com.exiledradio.wildupdatebackport.WildUpdateBackport;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.statemap.StateMapperBase;
import net.minecraft.init.Blocks;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Stream C on the client: 1.19's door models (MC-67308, door tops no longer flip when opened) for 1.12.2's seven
 * doors. Each vanilla door's states are mapped to a blockstate file of ours carrying 1.19's variants, whose models are
 * 1.19's templates with 1.12.2's own door textures ({@code tools/changes/generate.py}). The powered property is left
 * out, as vanilla's mapper leaves it out.
 */
public final class ChangesClient {

    private ChangesClient() {
    }

    public static void init() {
        MinecraftForge.EVENT_BUS.register(ChangesClient.class);
    }

    /** The vanilla doors and their blockstate files of ours (1.19's names). */
    public static Map<Block, String> doors() {
        Map<Block, String> out = new LinkedHashMap<Block, String>();
        out.put(Blocks.OAK_DOOR, "oak_door");
        out.put(Blocks.SPRUCE_DOOR, "spruce_door");
        out.put(Blocks.BIRCH_DOOR, "birch_door");
        out.put(Blocks.JUNGLE_DOOR, "jungle_door");
        out.put(Blocks.ACACIA_DOOR, "acacia_door");
        out.put(Blocks.DARK_OAK_DOOR, "dark_oak_door");
        out.put(Blocks.IRON_DOOR, "iron_door");
        return out;
    }

    @SubscribeEvent
    public static void registerModels(ModelRegistryEvent event) {
        if (!ModConfig.changes.enabled || !ModConfig.changes.doorModels) {
            return;
        }
        for (Map.Entry<Block, String> door : doors().entrySet()) {
            final String file = WildUpdateBackport.MOD_ID + ":" + door.getValue();
            ModelLoader.setCustomStateMapper(door.getKey(), new StateMapperBase() {
                @Override
                protected ModelResourceLocation getModelResourceLocation(IBlockState state) {
                    Map<IProperty<?>, Comparable<?>> props = new LinkedHashMap<IProperty<?>, Comparable<?>>(state.getProperties());
                    props.remove(BlockDoor.POWERED);
                    return new ModelResourceLocation(file, getPropertyString(props));
                }
            });
        }
    }
}
