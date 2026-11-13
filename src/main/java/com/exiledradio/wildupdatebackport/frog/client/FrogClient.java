package com.exiledradio.wildupdatebackport.frog.client;

import com.exiledradio.wildupdatebackport.common.Content;
import com.exiledradio.wildupdatebackport.frog.EntityFrog;
import com.exiledradio.wildupdatebackport.frog.EntityTadpole;
import com.exiledradio.wildupdatebackport.frog.FrogContent;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Renderers for frogs (1.19's model, animations and three textures, shadow 0.3) and tadpoles (shadow 0.14). */
@SideOnly(Side.CLIENT)
public final class FrogClient {

    private static final ResourceLocation TADPOLE_TEXTURE = Content.id("textures/entity/tadpole/tadpole.png");

    private FrogClient() {
    }

    /** From the client's pre-init. */
    public static void init() {
        if (!FrogContent.enabled()) {
            return;
        }
        RenderingRegistry.registerEntityRenderingHandler(EntityFrog.class, FrogRenderer::new);
        RenderingRegistry.registerEntityRenderingHandler(EntityTadpole.class, TadpoleRenderer::new);
    }

    public static class FrogRenderer extends RenderLiving<EntityFrog> {
        public FrogRenderer(RenderManager manager) {
            super(manager, new ModelFrog(), 0.3F);
        }

        @Override
        protected ResourceLocation getEntityTexture(EntityFrog entity) {
            return entity.getVariant().texture;
        }
    }

    public static class TadpoleRenderer extends RenderLiving<EntityTadpole> {
        public TadpoleRenderer(RenderManager manager) {
            super(manager, new ModelTadpole(), 0.14F);
        }

        @Override
        protected ResourceLocation getEntityTexture(EntityTadpole entity) {
            return TADPOLE_TEXTURE;
        }
    }

    /** 1.19's {@code TadpoleModel}: a 3 x 2 x 3 body and a flat tail that wags, faster out of water. */
    public static class ModelTadpole extends ModelBase {
        private final Part body;
        private final Part tail;

        public ModelTadpole() {
            this.textureWidth = 16;
            this.textureHeight = 16;
            this.body = new Part(this, "body", 0, 0).pose(0.0F, 22.0F, -3.0F).box(0, 0, -1.5F, -1.0F, 0.0F, 3, 2, 3, 0.0F);
            this.tail = new Part(this, "tail", 0, 0).pose(0.0F, 22.0F, 0.0F).box(0, 0, 0.0F, -1.0F, 0.0F, 0, 2, 7, 0.0F);
        }

        @Override
        public void render(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, float scale) {
            this.setRotationAngles(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale, entity);
            this.body.render(scale);
            this.tail.render(scale);
        }

        @Override
        public void setRotationAngles(float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch,
                                      float scale, Entity entity) {
            float k = entity.isInWater() ? 1.0F : 1.5F;
            this.tail.rotateAngleY = -k * 0.25F * (float) Math.sin(0.3F * ageInTicks);
        }
    }
}
