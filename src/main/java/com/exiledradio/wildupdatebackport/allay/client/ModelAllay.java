package com.exiledradio.wildupdatebackport.allay.client;

import com.exiledradio.wildupdatebackport.allay.EntityAllay;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Current Java's {@code AllayModel}: 32 x 32 texture, a head, a body with an inner layer, two arms and two
 * flat wings; flapping, idle bobbing, arms raised while holding an item, and the jukebox dance (sway,
 * head tilt and a double spin every 55 ticks).
 */
@SideOnly(Side.CLIENT)
public class ModelAllay extends ModelBase {

    private static final float ROOT_Y = 23.5F;

    final ModelRenderer root;
    final ModelRenderer head;
    final ModelRenderer body;
    final ModelRenderer rightArm;
    final ModelRenderer leftArm;
    final ModelRenderer rightWing;
    final ModelRenderer leftWing;

    private float partial;

    public ModelAllay() {
        this.textureWidth = 32;
        this.textureHeight = 32;
        this.root = new ModelRenderer(this);
        this.root.setRotationPoint(0.0F, ROOT_Y, 0.0F);

        this.head = new ModelRenderer(this, 0, 0);
        this.head.addBox(-2.5F, -5.0F, -2.5F, 5, 5, 5, 0.0F);
        this.head.setRotationPoint(0.0F, -3.99F, 0.0F);
        this.root.addChild(this.head);

        this.body = new ModelRenderer(this, 0, 10);
        this.body.addBox(-1.5F, 0.0F, -1.0F, 3, 4, 2, 0.0F);
        this.body.setTextureOffset(0, 16).addBox(-1.5F, 0.0F, -1.0F, 3, 5, 2, -0.2F);
        this.body.setRotationPoint(0.0F, -4.0F, 0.0F);
        this.root.addChild(this.body);

        this.rightArm = new ModelRenderer(this, 23, 0);
        this.rightArm.addBox(-0.75F, -0.5F, -1.0F, 1, 4, 2, -0.01F);
        this.rightArm.setRotationPoint(-1.75F, 0.5F, 0.0F);
        this.body.addChild(this.rightArm);

        this.leftArm = new ModelRenderer(this, 23, 6);
        this.leftArm.addBox(-0.25F, -0.5F, -1.0F, 1, 4, 2, -0.01F);
        this.leftArm.setRotationPoint(1.75F, 0.5F, 0.0F);
        this.body.addChild(this.leftArm);

        this.rightWing = new ModelRenderer(this, 16, 14);
        this.rightWing.addBox(0.0F, 1.0F, 0.0F, 0, 5, 8, 0.0F);
        this.rightWing.setRotationPoint(-0.5F, 0.0F, 0.6F);
        this.body.addChild(this.rightWing);

        this.leftWing = new ModelRenderer(this, 16, 14);
        this.leftWing.addBox(0.0F, 1.0F, 0.0F, 0, 5, 8, 0.0F);
        this.leftWing.setRotationPoint(0.5F, 0.0F, 0.6F);
        this.body.addChild(this.leftWing);
    }

    @Override
    public void render(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, float scale) {
        this.setRotationAngles(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale, entity);
        this.root.render(scale);
    }

    @Override
    public void setLivingAnimations(EntityLivingBase entity, float limbSwing, float limbSwingAmount, float partialTicks) {
        this.partial = partialTicks;
    }

    @Override
    public void setRotationAngles(float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch,
                                  float scale, Entity entity) {
        reset(this.root, 0.0F, ROOT_Y, 0.0F);
        reset(this.head, 0.0F, -3.99F, 0.0F);
        reset(this.body, 0.0F, -4.0F, 0.0F);
        reset(this.rightArm, -1.75F, 0.5F, 0.0F);
        reset(this.leftArm, 1.75F, 0.5F, 0.0F);
        reset(this.rightWing, -0.5F, 0.0F, 0.6F);
        reset(this.leftWing, 0.5F, 0.0F, 0.6F);

        EntityAllay allay = entity instanceof EntityAllay ? (EntityAllay) entity : null;
        float pi = (float) Math.PI;
        float flapSpeed = ageInTicks * 20.0F * (pi / 180.0F) + limbSwing;
        float flapAmount = MathHelper.cos(flapSpeed) * pi * 0.15F + limbSwingAmount;
        float idleBobSpeed = ageInTicks * 9.0F * (pi / 180.0F);
        float flyingFactor = Math.min(limbSwingAmount / 0.3F, 1.0F);
        float idleBobFactor = 1.0F - flyingFactor;
        float holding = allay == null ? 0.0F : allay.getHoldingItemAnimationProgress(this.partial);
        if (allay != null && allay.isDancing()) {
            float danceSpeed = ageInTicks * 8.0F * (pi / 180.0F) + limbSwingAmount;
            float sway = MathHelper.cos(danceSpeed) * 16.0F * (pi / 180.0F);
            float spin = allay.getSpinningProgress(this.partial);
            float tiltZ = MathHelper.cos(danceSpeed) * 14.0F * (pi / 180.0F);
            float tiltY = MathHelper.cos(danceSpeed) * 30.0F * (pi / 180.0F);
            this.root.rotateAngleY = allay.isSpinning() ? pi * 4.0F * spin : this.root.rotateAngleY;
            this.root.rotateAngleZ = sway * (1.0F - spin);
            this.head.rotateAngleY = tiltY * (1.0F - spin);
            this.head.rotateAngleZ = tiltZ * (1.0F - spin);
        } else {
            this.head.rotateAngleX = headPitch * (pi / 180.0F);
            this.head.rotateAngleY = netHeadYaw * (pi / 180.0F);
        }
        this.rightWing.rotateAngleX = 0.43633232F * (1.0F - flyingFactor);
        this.rightWing.rotateAngleY = -pi / 4.0F + flapAmount;
        this.leftWing.rotateAngleX = 0.43633232F * (1.0F - flyingFactor);
        this.leftWing.rotateAngleY = pi / 4.0F - flapAmount;
        this.body.rotateAngleX = flyingFactor * (pi / 4.0F);
        float armX = holding * lerp(flyingFactor, -pi / 3.0F, -1.134464F);
        this.root.rotationPointY += MathHelper.cos(idleBobSpeed) * 0.25F * idleBobFactor;
        this.rightArm.rotateAngleX = armX;
        this.leftArm.rotateAngleX = armX;
        float armBob = idleBobFactor * (1.0F - holding);
        float armBobAmount = 0.43633232F - MathHelper.cos(idleBobSpeed + pi * 3.0F / 2.0F) * pi * 0.075F * armBob;
        this.leftArm.rotateAngleZ = -armBobAmount;
        this.rightArm.rotateAngleZ = armBobAmount;
        this.rightArm.rotateAngleY = 0.27925268F * holding;
        this.leftArm.rotateAngleY = -0.27925268F * holding;
    }

    /** Current Java's {@code translateToHand}: the held item sits between the hands. */
    void translateToHand(float scale) {
        this.root.postRender(scale);
        this.body.postRender(scale);
        GlStateManager.translate(0.0F, 0.0625F, 0.1875F);
        GlStateManager.rotate(this.rightArm.rotateAngleX * (180.0F / (float) Math.PI), 1.0F, 0.0F, 0.0F);
        GlStateManager.scale(0.7F, 0.7F, 0.7F);
        GlStateManager.translate(0.0625F, 0.0F, 0.0F);
    }

    private static float lerp(float t, float a, float b) {
        return a + t * (b - a);
    }

    private static void reset(ModelRenderer part, float x, float y, float z) {
        part.setRotationPoint(x, y, z);
        part.rotateAngleX = 0.0F;
        part.rotateAngleY = 0.0F;
        part.rotateAngleZ = 0.0F;
    }
}
