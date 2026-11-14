package com.exiledradio.wildupdatebackport.goat.client;

import com.exiledradio.wildupdatebackport.common.Content;
import com.exiledradio.wildupdatebackport.goat.EntityGoat;

import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;

/** Draws the goat with 1.17's model and texture. */
public class RenderGoat extends RenderLiving<EntityGoat> {

    private static final ResourceLocation TEXTURE = Content.id("textures/entity/goat/goat.png");

    public RenderGoat(RenderManager manager) {
        super(manager, new ModelGoat(), 0.7F);
    }

    @Override
    protected ResourceLocation getEntityTexture(EntityGoat goat) {
        return TEXTURE;
    }
}
