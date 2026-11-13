package com.exiledradio.wildupdatebackport.frog.client;

import com.exiledradio.wildupdatebackport.frog.EntityFrog;

import net.minecraft.client.model.ModelBase;
import net.minecraft.entity.Entity;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.HashMap;
import java.util.Map;

/** 1.19's {@code FrogModel}: its parts, 48 x 48 texture, and its six keyframe animations. */
@SideOnly(Side.CLIENT)
public class ModelFrog extends ModelBase {

    private final Map<String, Part> parts = new HashMap<String, Part>();
    private final Part root;
    private final Part croakingBody;

    public ModelFrog() {
        this.textureWidth = 48;
        this.textureHeight = 48;
        this.root = part("root", 0.0F, 24.0F, 0.0F);
        Part body = part("body", 0.0F, -2.0F, 4.0F)
                .box(3, 1, -3.5F, -2.0F, -8.0F, 7, 3, 9, 0.0F)
                .box(23, 22, -3.5F, -1.0F, -8.0F, 7, 0, 9, 0.0F);
        this.root.addChild(body);
        Part head = part("head", 0.0F, -2.0F, -1.0F)
                .box(23, 13, -3.5F, -1.0F, -7.0F, 7, 0, 9, 0.0F)
                .box(0, 13, -3.5F, -2.0F, -7.0F, 7, 3, 9, 0.0F);
        body.addChild(head);
        Part eyes = part("eyes", -0.5F, 0.0F, 2.0F);
        head.addChild(eyes);
        eyes.addChild(part("right_eye", -1.5F, -3.0F, -6.5F).box(0, 0, -1.5F, -1.0F, -1.5F, 3, 2, 3, 0.0F));
        eyes.addChild(part("left_eye", 2.5F, -3.0F, -6.5F).box(0, 5, -1.5F, -1.0F, -1.5F, 3, 2, 3, 0.0F));
        this.croakingBody = part("croaking_body", 0.0F, -1.0F, -5.0F).box(26, 5, -3.5F, -0.1F, -2.9F, 7, 2, 3, -0.1F);
        body.addChild(this.croakingBody);
        body.addChild(part("tongue", 0.0F, -1.01F, 1.0F).box(17, 13, -2.0F, 0.0F, -7.1F, 4, 0, 7, 0.0F));
        Part leftArm = part("left_arm", 4.0F, -1.0F, -6.5F).box(0, 32, -1.0F, 0.0F, -1.0F, 2, 3, 3, 0.0F);
        body.addChild(leftArm);
        leftArm.addChild(part("left_hand", 0.0F, 3.0F, -1.0F).box(18, 40, -4.0F, 0.01F, -4.0F, 8, 0, 8, 0.0F));
        Part rightArm = part("right_arm", -4.0F, -1.0F, -6.5F).box(0, 38, -1.0F, 0.0F, -1.0F, 2, 3, 3, 0.0F);
        body.addChild(rightArm);
        rightArm.addChild(part("right_hand", 0.0F, 3.0F, 0.0F).box(2, 40, -4.0F, 0.01F, -5.0F, 8, 0, 8, 0.0F));
        Part leftLeg = part("left_leg", 3.5F, -3.0F, 4.0F).box(14, 25, -1.0F, 0.0F, -2.0F, 3, 3, 4, 0.0F);
        this.root.addChild(leftLeg);
        leftLeg.addChild(part("left_foot", 2.0F, 3.0F, 0.0F).box(2, 32, -4.0F, 0.01F, -4.0F, 8, 0, 8, 0.0F));
        Part rightLeg = part("right_leg", -3.5F, -3.0F, 4.0F).box(0, 25, -2.0F, 0.0F, -2.0F, 3, 3, 4, 0.0F);
        this.root.addChild(rightLeg);
        rightLeg.addChild(part("right_foot", -2.0F, 3.0F, 0.0F).box(18, 32, -4.0F, 0.01F, -4.0F, 8, 0, 8, 0.0F));
    }

    private Part part(String name, float x, float y, float z) {
        Part p = new Part(this, name, 0, 0).pose(x, y, z);
        this.parts.put(name, p);
        return p;
    }

    @Override
    public void render(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, float scale) {
        this.setRotationAngles(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale, entity);
        this.root.render(scale);
    }

    @Override
    public void setRotationAngles(float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch,
                                  float scale, Entity entity) {
        for (Part p : this.parts.values()) {
            p.reset();
        }
        if (!(entity instanceof EntityFrog)) {
            return;
        }
        EntityFrog frog = (EntityFrog) entity;
        Map<String, Keyframes.Definition> anims = Keyframes.load("frog");
        double m2 = frog.motionX * frog.motionX + frog.motionY * frog.motionY + frog.motionZ * frog.motionZ;
        float walkSpeed = (float) Math.min(m2 * 200.0D, 8.0D);
        Keyframes.animate(this.parts, frog.jumpAnimation, anims.get("jump"), ageInTicks, 1.0F);
        Keyframes.animate(this.parts, frog.croakAnimation, anims.get("croak"), ageInTicks, 1.0F);
        Keyframes.animate(this.parts, frog.tongueAnimation, anims.get("tongue"), ageInTicks, 1.0F);
        Keyframes.animate(this.parts, frog.walkAnimation, anims.get("walk"), ageInTicks, walkSpeed);
        Keyframes.animate(this.parts, frog.swimAnimation, anims.get("swim"), ageInTicks, 1.0F);
        Keyframes.animate(this.parts, frog.swimIdleAnimation, anims.get("idle_water"), ageInTicks, 1.0F);
        this.croakingBody.showModel = frog.croakAnimation.isStarted();
    }
}
