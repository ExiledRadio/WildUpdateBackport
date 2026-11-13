package com.exiledradio.wildupdatebackport.allay.client;

import com.exiledradio.wildupdatebackport.allay.AllayContent;
import com.exiledradio.wildupdatebackport.allay.EntityAllay;
import com.exiledradio.wildupdatebackport.common.Content;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** The allay's renderer: translucent, full block light, shadow 0.4, its held item between its hands. */
@SideOnly(Side.CLIENT)
public final class AllayClient {

    static final ResourceLocation TEXTURE = Content.id("textures/entity/allay/allay.png");

    private AllayClient() {
    }

    /** From the client's pre-init. */
    public static void init() {
        if (AllayContent.enabled()) {
            RenderingRegistry.registerEntityRenderingHandler(EntityAllay.class, RenderAllay::new);
        }
    }

    public static class RenderAllay extends RenderLiving<EntityAllay> {
        public RenderAllay(RenderManager manager) {
            super(manager, new ModelAllay(), 0.4F);
            this.addLayer(new HeldItem(this));
        }

        @Override
        protected ResourceLocation getEntityTexture(EntityAllay entity) {
            return TEXTURE;
        }

        @Override
        protected void renderModel(EntityAllay entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
                                   float headPitch, float scale) {
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            super.renderModel(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale);
            GlStateManager.disableBlend();
        }
    }

    /** Current Java's {@code ItemInHandLayer} with the allay model's hand. */
    static final class HeldItem implements LayerRenderer<EntityAllay> {
        private final RenderAllay renderer;

        HeldItem(RenderAllay renderer) {
            this.renderer = renderer;
        }

        @Override
        public void doRenderLayer(EntityAllay entity, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks,
                                  float netHeadYaw, float headPitch, float scale) {
            ItemStack stack = entity.getHeldItemMainhand();
            if (stack.isEmpty()) {
                return;
            }
            GlStateManager.pushMatrix();
            ((ModelAllay) this.renderer.getMainModel()).translateToHand(scale);
            GlStateManager.rotate(-90.0F, 1.0F, 0.0F, 0.0F);
            GlStateManager.rotate(180.0F, 0.0F, 1.0F, 0.0F);
            GlStateManager.translate(1.0F / 16.0F, 0.125F, -0.625F);
            Minecraft.getMinecraft().getItemRenderer().renderItemSide(entity, stack,
                    ItemCameraTransforms.TransformType.THIRD_PERSON_RIGHT_HAND, false);
            GlStateManager.popMatrix();
        }

        @Override
        public boolean shouldCombineTextures() {
            return false;
        }
    }
}
