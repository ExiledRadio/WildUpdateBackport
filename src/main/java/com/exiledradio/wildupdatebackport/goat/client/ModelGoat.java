package com.exiledradio.wildupdatebackport.goat.client;

import com.exiledradio.wildupdatebackport.goat.EntityGoat;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;

/**
 * 1.17's goat model: its boxes, texture offsets and pivots, and the way it draws a kid (head at 0.6, body at
 * half size). The horns are separate parts, hidden on kids and on a goat that has lost one.
 */
public class ModelGoat extends ModelBase {

    private final ModelRenderer head, leftHorn, rightHorn, nose, body;
    private final ModelRenderer leftHindLeg, rightHindLeg, leftFrontLeg, rightFrontLeg;

    public ModelGoat() {
        this.textureWidth = 64;
        this.textureHeight = 64;

        this.head = new ModelRenderer(this);
        this.head.setRotationPoint(1.0F, 14.0F, 0.0F);
        this.head.setTextureOffset(2, 61).addBox(-6.0F, -11.0F, -10.0F, 3, 2, 1); // right ear
        // 1.17 mirrors the left ear and, since the switch stays on, the goatee after it.
        this.head.mirror = true;
        this.head.setTextureOffset(2, 61).addBox(2.0F, -11.0F, -10.0F, 3, 2, 1); // left ear
        this.head.setTextureOffset(23, 52).addBox(-0.5F, -3.0F, -14.0F, 0, 7, 5); // goatee
        this.head.mirror = false;

        this.leftHorn = new ModelRenderer(this, 12, 55);
        this.leftHorn.addBox(-0.01F, -16.0F, -10.0F, 2, 7, 2);
        this.head.addChild(this.leftHorn);
        this.rightHorn = new ModelRenderer(this, 12, 55);
        this.rightHorn.addBox(-2.99F, -16.0F, -10.0F, 2, 7, 2);
        this.head.addChild(this.rightHorn);
        this.nose = new ModelRenderer(this, 34, 46);
        this.nose.addBox(-3.0F, -4.0F, -8.0F, 5, 7, 10);
        this.nose.setRotationPoint(0.0F, -8.0F, -8.0F);
        this.nose.rotateAngleX = 0.9599F;
        this.head.addChild(this.nose);

        this.body = new ModelRenderer(this);
        this.body.setRotationPoint(0.0F, 24.0F, 0.0F);
        this.body.setTextureOffset(1, 1).addBox(-4.0F, -17.0F, -7.0F, 9, 11, 16);
        this.body.setTextureOffset(0, 28).addBox(-5.0F, -18.0F, -8.0F, 11, 14, 11);

        this.leftHindLeg = new ModelRenderer(this, 36, 29);
        this.leftHindLeg.addBox(0.0F, 4.0F, 0.0F, 3, 6, 3);
        this.leftHindLeg.setRotationPoint(1.0F, 14.0F, 4.0F);
        this.rightHindLeg = new ModelRenderer(this, 49, 29);
        this.rightHindLeg.addBox(0.0F, 4.0F, 0.0F, 3, 6, 3);
        this.rightHindLeg.setRotationPoint(-3.0F, 14.0F, 4.0F);
        this.leftFrontLeg = new ModelRenderer(this, 49, 2);
        this.leftFrontLeg.addBox(0.0F, 0.0F, 0.0F, 3, 10, 3);
        this.leftFrontLeg.setRotationPoint(1.0F, 14.0F, -6.0F);
        this.rightFrontLeg = new ModelRenderer(this, 35, 2);
        this.rightFrontLeg.addBox(0.0F, 0.0F, 0.0F, 3, 10, 3);
        this.rightFrontLeg.setRotationPoint(-3.0F, 14.0F, -6.0F);
    }

    @Override
    public void render(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, float scale) {
        this.setRotationAngles(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale, entity);
        if (this.isChild) {
            // 1.17's kid: head at 1.5 / 2.5, moved 19 down and 1 back; the rest at half size, moved 24 down.
            GlStateManager.pushMatrix();
            GlStateManager.scale(0.6F, 0.6F, 0.6F);
            GlStateManager.translate(0.0F, 19.0F * scale, 1.0F * scale);
            this.head.render(scale);
            GlStateManager.popMatrix();
            GlStateManager.pushMatrix();
            GlStateManager.scale(0.5F, 0.5F, 0.5F);
            GlStateManager.translate(0.0F, 24.0F * scale, 0.0F);
            this.renderBody(scale);
            GlStateManager.popMatrix();
        } else {
            this.head.render(scale);
            this.renderBody(scale);
        }
    }

    private void renderBody(float scale) {
        this.body.render(scale);
        this.leftHindLeg.render(scale);
        this.rightHindLeg.render(scale);
        this.leftFrontLeg.render(scale);
        this.rightFrontLeg.render(scale);
    }

    @Override
    public void setRotationAngles(float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, float scale, Entity entity) {
        EntityGoat goat = (EntityGoat) entity;
        this.leftHorn.showModel = !goat.isChild() && goat.hasLeftHorn();
        this.rightHorn.showModel = !goat.isChild() && goat.hasRightHorn();
        this.head.rotateAngleX = headPitch * 0.017453292F;
        this.head.rotateAngleY = netHeadYaw * 0.017453292F;
        this.rightHindLeg.rotateAngleX = MathHelper.cos(limbSwing * 0.6662F) * 1.4F * limbSwingAmount;
        this.leftHindLeg.rotateAngleX = MathHelper.cos(limbSwing * 0.6662F + (float) Math.PI) * 1.4F * limbSwingAmount;
        this.rightFrontLeg.rotateAngleX = MathHelper.cos(limbSwing * 0.6662F + (float) Math.PI) * 1.4F * limbSwingAmount;
        this.leftFrontLeg.rotateAngleX = MathHelper.cos(limbSwing * 0.6662F) * 1.4F * limbSwingAmount;
        float ramming = goat.getRammingXHeadRot();
        if (ramming != 0.0F) {
            this.head.rotateAngleX = ramming;
        }
    }
}
