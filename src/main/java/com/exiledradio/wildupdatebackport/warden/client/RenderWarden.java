package com.exiledradio.wildupdatebackport.warden.client;

import com.exiledradio.wildupdatebackport.WildUpdateBackport;
import com.exiledradio.wildupdatebackport.warden.EntityWarden;
import com.exiledradio.wildupdatebackport.warden.client.anim.AnimatedPart;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;

import java.util.List;
import java.util.function.Function;

/** 1.19.2's {@code WardenRenderer}: the model, shadow 0.9, and five glowing layers. */
public class RenderWarden extends RenderLiving<EntityWarden> {

    private static ResourceLocation tex(String name) {
        return new ResourceLocation(WildUpdateBackport.MOD_ID, "textures/entity/warden/" + name + ".png");
    }

    private static final ResourceLocation TEXTURE = tex("warden");
    private static final ResourceLocation BIOLUMINESCENT = tex("warden_bioluminescent_layer");
    private static final ResourceLocation HEART = tex("warden_heart");
    private static final ResourceLocation PULSATING_1 = tex("warden_pulsating_spots_1");
    private static final ResourceLocation PULSATING_2 = tex("warden_pulsating_spots_2");

    /** How bright a layer is, from the warden, the partial tick and its age in ticks. */
    interface Alpha {
        float apply(EntityWarden warden, float partialTicks, float ageInTicks);
    }

    public RenderWarden(RenderManager manager) {
        super(manager, new ModelWarden(), 0.9F);
        addLayer(new Emissive(BIOLUMINESCENT, (w, p, a) -> 1.0F, m -> m.bioluminescentLayer));
        addLayer(new Emissive(PULSATING_1, (w, p, a) -> Math.max(0.0F, MathHelper.cos(a * 0.045F) * 0.25F), m -> m.pulsatingSpotsLayer));
        addLayer(new Emissive(PULSATING_2, (w, p, a) -> Math.max(0.0F, MathHelper.cos(a * 0.045F + (float) Math.PI) * 0.25F),
                m -> m.pulsatingSpotsLayer));
        addLayer(new Emissive(TEXTURE, (w, p, a) -> w.getTendrilAnimation(p), m -> m.tendrilsLayer));
        addLayer(new Emissive(HEART, (w, p, a) -> w.getHeartAnimation(p), m -> m.heartLayer));
    }

    @Override
    protected ResourceLocation getEntityTexture(EntityWarden entity) {
        return TEXTURE;
    }

    /**
     * 1.19's {@code WardenEmissiveLayer}: some parts drawn again with another texture, translucent, full bright and
     * unshaded, at the layer's brightness.
     */
    final class Emissive implements LayerRenderer<EntityWarden> {
        private final ResourceLocation texture;
        private final Alpha alpha;
        private final Function<ModelWarden, List<AnimatedPart>> parts;

        Emissive(ResourceLocation texture, Alpha alpha, Function<ModelWarden, List<AnimatedPart>> parts) {
            this.texture = texture;
            this.alpha = alpha;
            this.parts = parts;
        }

        @Override
        public void doRenderLayer(EntityWarden warden, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks,
                                  float netHeadYaw, float headPitch, float scale) {
            if (warden.isInvisible()) {
                return;
            }
            float a = this.alpha.apply(warden, partialTicks, ageInTicks);
            if (a <= 0.0F) {
                return;
            }
            ModelWarden model = (ModelWarden) getMainModel();
            for (AnimatedPart p : model.all) {
                p.skipDraw = true;
            }
            for (AnimatedPart p : this.parts.apply(model)) {
                p.skipDraw = false;
            }
            bindTexture(this.texture);
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            GlStateManager.disableAlpha();
            GlStateManager.disableLighting();
            GlStateManager.depthMask(false);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);
            GlStateManager.color(1.0F, 1.0F, 1.0F, Math.min(1.0F, a));
            model.bone.render(scale);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            setLightmap(warden);
            GlStateManager.depthMask(true);
            GlStateManager.enableLighting();
            GlStateManager.enableAlpha();
            GlStateManager.disableBlend();
            for (AnimatedPart p : model.all) {
                p.skipDraw = false;
            }
        }

        @Override
        public boolean shouldCombineTextures() {
            return false;
        }
    }
}
