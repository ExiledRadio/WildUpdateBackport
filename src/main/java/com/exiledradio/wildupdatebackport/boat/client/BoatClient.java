package com.exiledradio.wildupdatebackport.boat.client;

import com.exiledradio.wildupdatebackport.boat.BoatContent;
import com.exiledradio.wildupdatebackport.boat.EntityWoodBoat;
import com.exiledradio.wildupdatebackport.boat.EntityWoodChestBoat;

import net.minecraft.client.renderer.entity.RenderBoat;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Renderers for our boats: vanilla's boat renderer with the wood's texture, and the chest boat's model. */
@SideOnly(Side.CLIENT)
public final class BoatClient {

    private BoatClient() {
    }

    /** From the client's pre-init. */
    public static void init() {
        if (!BoatContent.enabled()) {
            return;
        }
        RenderingRegistry.registerEntityRenderingHandler(EntityWoodBoat.class, WoodBoatRenderer::new);
        RenderingRegistry.registerEntityRenderingHandler(EntityWoodChestBoat.class, ChestBoatRenderer::new);
    }

    /** A boat of a new wood. */
    public static class WoodBoatRenderer extends RenderBoat {
        public WoodBoatRenderer(RenderManager manager) {
            super(manager);
        }

        @Override
        protected ResourceLocation getEntityTexture(EntityBoat entity) {
            return ((EntityWoodBoat) entity).getWood().boatTexture;
        }
    }

    /** A boat with chest of any wood: 1.19's texture and model. */
    public static class ChestBoatRenderer extends RenderBoat {
        public ChestBoatRenderer(RenderManager manager) {
            super(manager);
            this.modelBoat = new ModelChestBoat();
        }

        @Override
        protected ResourceLocation getEntityTexture(EntityBoat entity) {
            return ((EntityWoodBoat) entity).getWood().chestBoatTexture;
        }
    }
}
