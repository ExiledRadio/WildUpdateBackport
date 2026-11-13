package com.exiledradio.wildupdatebackport.warden.client;

import com.exiledradio.wildupdatebackport.warden.AnimationState;
import com.exiledradio.wildupdatebackport.warden.EntityWarden;
import com.exiledradio.wildupdatebackport.warden.client.anim.AnimatedPart;
import com.exiledradio.wildupdatebackport.warden.client.anim.AnimationDefinition;
import com.exiledradio.wildupdatebackport.warden.client.anim.KeyframeAnimations;
import com.exiledradio.wildupdatebackport.warden.client.anim.Vec3f;

import net.minecraft.client.model.ModelBase;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 1.19.2's {@code WardenModel}: its parts, walk and idle motion, tendrils, and the six keyframe animations. */
public class ModelWarden extends ModelBase {

    private static final float DEG = (float) (Math.PI / 180.0D);

    final AnimatedPart bone;
    final AnimatedPart body;
    final AnimatedPart head;
    final AnimatedPart rightTendril;
    final AnimatedPart leftTendril;
    final AnimatedPart leftLeg;
    final AnimatedPart leftArm;
    final AnimatedPart leftRibcage;
    final AnimatedPart rightArm;
    final AnimatedPart rightLeg;
    final AnimatedPart rightRibcage;
    final List<AnimatedPart> all;
    final List<AnimatedPart> tendrilsLayer;
    final List<AnimatedPart> heartLayer;
    final List<AnimatedPart> bioluminescentLayer;
    final List<AnimatedPart> pulsatingSpotsLayer;
    private final Map<String, AnimatedPart> byName = new HashMap<String, AnimatedPart>();
    private final Vec3f cache = new Vec3f(0.0F, 0.0F, 0.0F);

    public ModelWarden() {
        this.textureWidth = 128;
        this.textureHeight = 128;
        this.bone = part("bone", 0, 0).pose(0.0F, 24.0F, 0.0F);
        this.body = part("body", 0, 0).pose(0.0F, -21.0F, 0.0F);
        this.body.addBox(-9.0F, -13.0F, -4.0F, 18, 21, 11);
        this.bone.addChild(this.body);
        this.rightRibcage = part("right_ribcage", 90, 11).pose(-7.0F, -2.0F, -4.0F);
        this.rightRibcage.addBox(-2.0F, -11.0F, -0.1F, 9, 21, 0);
        this.body.addChild(this.rightRibcage);
        this.leftRibcage = part("left_ribcage", 90, 11).pose(7.0F, -2.0F, -4.0F);
        this.leftRibcage.mirror = true;
        this.leftRibcage.addBox(-7.0F, -11.0F, -0.1F, 9, 21, 0);
        this.body.addChild(this.leftRibcage);
        this.head = part("head", 0, 32).pose(0.0F, -13.0F, 0.0F);
        this.head.addBox(-8.0F, -16.0F, -5.0F, 16, 16, 10);
        this.body.addChild(this.head);
        this.rightTendril = part("right_tendril", 52, 32).pose(-8.0F, -12.0F, 0.0F);
        this.rightTendril.addBox(-16.0F, -13.0F, 0.0F, 16, 16, 0);
        this.head.addChild(this.rightTendril);
        this.leftTendril = part("left_tendril", 58, 0).pose(8.0F, -12.0F, 0.0F);
        this.leftTendril.addBox(0.0F, -13.0F, 0.0F, 16, 16, 0);
        this.head.addChild(this.leftTendril);
        this.rightArm = part("right_arm", 44, 50).pose(-13.0F, -13.0F, 1.0F);
        this.rightArm.addBox(-4.0F, 0.0F, -4.0F, 8, 28, 8);
        this.body.addChild(this.rightArm);
        this.leftArm = part("left_arm", 0, 58).pose(13.0F, -13.0F, 1.0F);
        this.leftArm.addBox(-4.0F, 0.0F, -4.0F, 8, 28, 8);
        this.body.addChild(this.leftArm);
        this.rightLeg = part("right_leg", 76, 48).pose(-5.9F, -13.0F, 0.0F);
        this.rightLeg.addBox(-3.1F, 0.0F, -3.0F, 6, 13, 6);
        this.bone.addChild(this.rightLeg);
        this.leftLeg = part("left_leg", 76, 76).pose(5.9F, -13.0F, 0.0F);
        this.leftLeg.addBox(-2.9F, 0.0F, -3.0F, 6, 13, 6);
        this.bone.addChild(this.leftLeg);

        this.all = Collections.unmodifiableList(Arrays.asList(this.bone, this.body, this.head, this.rightTendril, this.leftTendril,
                this.leftLeg, this.leftArm, this.leftRibcage, this.rightArm, this.rightLeg, this.rightRibcage));
        this.tendrilsLayer = Arrays.asList(this.leftTendril, this.rightTendril);
        this.heartLayer = Collections.singletonList(this.body);
        this.bioluminescentLayer = Arrays.asList(this.head, this.leftArm, this.rightArm, this.leftLeg, this.rightLeg);
        this.pulsatingSpotsLayer = Arrays.asList(this.body, this.head, this.leftArm, this.rightArm, this.leftLeg, this.rightLeg);
    }

    private AnimatedPart part(String name, int u, int v) {
        AnimatedPart p = new AnimatedPart(this, u, v);
        this.byName.put(name, p);
        return p;
    }

    @Override
    public void render(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, float scale) {
        setRotationAngles(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale, entity);
        this.bone.render(scale);
    }

    @Override
    public void setRotationAngles(float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch,
                                  float scale, Entity entity) {
        for (AnimatedPart p : this.all) {
            p.resetPose();
        }
        if (!(entity instanceof EntityWarden)) {
            return;
        }
        EntityWarden warden = (EntityWarden) entity;
        float partialTicks = ageInTicks - warden.ticksExisted;
        this.head.rotateAngleX = headPitch * DEG;
        this.head.rotateAngleY = netHeadYaw * DEG;
        animateWalk(limbSwing, limbSwingAmount);
        animateIdlePose(ageInTicks);
        float tendril = warden.getTendrilAnimation(partialTicks) * (float) (Math.cos(ageInTicks * 2.25D) * Math.PI * 0.1F);
        this.leftTendril.rotateAngleX = tendril;
        this.rightTendril.rotateAngleX = -tendril;
        animate(warden.attackAnimationState, WardenAnimations.WARDEN_ATTACK, ageInTicks);
        animate(warden.sonicBoomAnimationState, WardenAnimations.WARDEN_SONIC_BOOM, ageInTicks);
        animate(warden.diggingAnimationState, WardenAnimations.WARDEN_DIG, ageInTicks);
        animate(warden.emergeAnimationState, WardenAnimations.WARDEN_EMERGE, ageInTicks);
        animate(warden.roarAnimationState, WardenAnimations.WARDEN_ROAR, ageInTicks);
        animate(warden.sniffAnimationState, WardenAnimations.WARDEN_SNIFF, ageInTicks);
    }

    private void animate(AnimationState state, AnimationDefinition def, float ageInTicks) {
        state.updateTime(ageInTicks, 1.0F);
        if (state.isStarted()) {
            KeyframeAnimations.animate(this.byName, def, state.getAccumulatedTime(), 1.0F, this.cache);
        }
    }

    private void animateIdlePose(float ageInTicks) {
        float t = ageInTicks * 0.1F;
        float cos = MathHelper.cos(t);
        float sin = MathHelper.sin(t);
        this.head.rotateAngleZ += 0.06F * cos;
        this.head.rotateAngleX += 0.06F * sin;
        this.body.rotateAngleZ += 0.025F * sin;
        this.body.rotateAngleX += 0.025F * cos;
    }

    private void animateWalk(float limbSwing, float limbSwingAmount) {
        float amount = Math.min(0.5F, 3.0F * limbSwingAmount);
        float t = limbSwing * 0.8662F;
        float cos = MathHelper.cos(t);
        float sin = MathHelper.sin(t);
        float small = Math.min(0.35F, amount);
        this.head.rotateAngleZ += 0.3F * sin * amount;
        this.head.rotateAngleX += 1.2F * MathHelper.cos(t + (float) (Math.PI / 2)) * small;
        this.body.rotateAngleZ = 0.1F * sin * amount;
        this.body.rotateAngleX = 1.0F * cos * small;
        this.leftLeg.rotateAngleX = 1.0F * cos * amount;
        this.rightLeg.rotateAngleX = 1.0F * MathHelper.cos(t + (float) Math.PI) * amount;
        this.leftArm.rotateAngleX = -(0.8F * cos * amount);
        this.leftArm.rotateAngleZ = 0.0F;
        this.rightArm.rotateAngleX = -(0.8F * sin * amount);
        this.rightArm.rotateAngleZ = 0.0F;
        this.leftArm.rotateAngleY = 0.0F;
        this.leftArm.rotationPointZ = 1.0F;
        this.leftArm.rotationPointX = 13.0F;
        this.leftArm.rotationPointY = -13.0F;
        this.rightArm.rotateAngleY = 0.0F;
        this.rightArm.rotationPointZ = 1.0F;
        this.rightArm.rotationPointX = -13.0F;
        this.rightArm.rotationPointY = -13.0F;
    }
}
