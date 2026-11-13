package com.exiledradio.wildupdatebackport.warden.client.anim;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;

/**
 * A model part as 1.19's {@code ModelPart}: a resettable pose that animations add to, and {@link #skipDraw}, which
 * hides this part's own boxes but still draws its children (the warden's glowing layers draw only some parts).
 */
public class AnimatedPart extends ModelRenderer {

    public boolean skipDraw;
    private float initX;
    private float initY;
    private float initZ;

    public AnimatedPart(ModelBase model, int u, int v) {
        super(model, u, v);
    }

    /** Sets the pivot and remembers it as the pose {@link #resetPose()} returns to (1.19's initial pose). */
    public AnimatedPart pose(float x, float y, float z) {
        setRotationPoint(x, y, z);
        this.initX = x;
        this.initY = y;
        this.initZ = z;
        return this;
    }

    public void resetPose() {
        this.rotationPointX = this.initX;
        this.rotationPointY = this.initY;
        this.rotationPointZ = this.initZ;
        this.rotateAngleX = 0.0F;
        this.rotateAngleY = 0.0F;
        this.rotateAngleZ = 0.0F;
    }

    public void offsetPos(Vec3f v) {
        this.rotationPointX += v.x;
        this.rotationPointY += v.y;
        this.rotationPointZ += v.z;
    }

    public void offsetRotation(Vec3f v) {
        this.rotateAngleX += v.x;
        this.rotateAngleY += v.y;
        this.rotateAngleZ += v.z;
    }

    /** 1.19's {@code ModelPart.render}: translate, rotate Z then Y then X, own boxes unless skipped, then children. */
    @Override
    public void render(float scale) {
        if (this.isHidden || !this.showModel) {
            return;
        }
        GlStateManager.pushMatrix();
        GlStateManager.translate(this.rotationPointX * scale, this.rotationPointY * scale, this.rotationPointZ * scale);
        if (this.rotateAngleZ != 0.0F) {
            GlStateManager.rotate(this.rotateAngleZ * (180F / (float) Math.PI), 0.0F, 0.0F, 1.0F);
        }
        if (this.rotateAngleY != 0.0F) {
            GlStateManager.rotate(this.rotateAngleY * (180F / (float) Math.PI), 0.0F, 1.0F, 0.0F);
        }
        if (this.rotateAngleX != 0.0F) {
            GlStateManager.rotate(this.rotateAngleX * (180F / (float) Math.PI), 1.0F, 0.0F, 0.0F);
        }
        if (!this.skipDraw) {
            for (ModelBox box : this.cubeList) {
                box.render(Tessellator.getInstance().getBuffer(), scale);
            }
        }
        if (this.childModels != null) {
            for (ModelRenderer child : this.childModels) {
                child.render(scale);
            }
        }
        GlStateManager.popMatrix();
    }
}
