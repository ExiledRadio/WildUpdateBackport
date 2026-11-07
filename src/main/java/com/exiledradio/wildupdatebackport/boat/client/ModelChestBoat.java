package com.exiledradio.wildupdatebackport.boat.client;

import net.minecraft.client.model.ModelBoat;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 1.19's chest boat model: vanilla's boat with every part re-made for a 128 x 128 texture (1.19's chest boat
 * textures are twice as tall, the chest drawn below the boat), plus the chest's bottom, lid and lock.
 * Part sizes and offsets are the ones 1.12.2's and 1.19's boat models share; the chest's are 1.19's.
 */
@SideOnly(Side.CLIENT)
public class ModelChestBoat extends ModelBoat {

    private final ModelRenderer chestBottom;
    private final ModelRenderer chestLid;
    private final ModelRenderer chestLock;

    public ModelChestBoat() {
        this.boatSides[0] = part(0, 0, -14.0F, -9.0F, -3.0F, 28, 16, 3, 0.0F, 3.0F, 1.0F);
        this.boatSides[1] = part(0, 19, -13.0F, -7.0F, -1.0F, 18, 6, 2, -15.0F, 4.0F, 4.0F);
        this.boatSides[2] = part(0, 27, -8.0F, -7.0F, -1.0F, 16, 6, 2, 15.0F, 4.0F, 0.0F);
        this.boatSides[3] = part(0, 35, -14.0F, -7.0F, -1.0F, 28, 6, 2, 0.0F, 4.0F, -9.0F);
        this.boatSides[4] = part(0, 43, -14.0F, -7.0F, -1.0F, 28, 6, 2, 0.0F, 4.0F, 9.0F);
        this.boatSides[0].rotateAngleX = (float) Math.PI / 2F;
        this.boatSides[1].rotateAngleY = (float) Math.PI * 3F / 2F;
        this.boatSides[2].rotateAngleY = (float) Math.PI / 2F;
        this.boatSides[3].rotateAngleY = (float) Math.PI;
        this.paddles[0] = paddle(true);
        this.paddles[0].setRotationPoint(3.0F, -5.0F, 9.0F);
        this.paddles[1] = paddle(false);
        this.paddles[1].setRotationPoint(3.0F, -5.0F, -9.0F);
        this.paddles[1].rotateAngleY = (float) Math.PI;
        this.paddles[0].rotateAngleZ = 0.19634955F;
        this.paddles[1].rotateAngleZ = 0.19634955F;
        this.noWater = part(0, 0, -14.0F, -9.0F, -3.0F, 28, 16, 3, 0.0F, -3.0F, 1.0F);
        this.noWater.rotateAngleX = (float) Math.PI / 2F;

        this.chestBottom = part(0, 76, 0.0F, 0.0F, 0.0F, 12, 8, 12, -2.0F, -5.0F, -6.0F);
        this.chestLid = part(0, 59, 0.0F, 0.0F, 0.0F, 12, 4, 12, -2.0F, -9.0F, -6.0F);
        this.chestLock = part(0, 59, 0.0F, 0.0F, 0.0F, 2, 4, 1, -1.0F, -6.0F, -1.0F);
        for (ModelRenderer r : new ModelRenderer[] {this.chestBottom, this.chestLid, this.chestLock}) {
            r.rotateAngleY = (float) -Math.PI / 2F;
        }
    }

    private ModelRenderer part(int u, int v, float x, float y, float z, int w, int h, int d, float px, float py, float pz) {
        ModelRenderer r = new ModelRenderer(this, u, v).setTextureSize(128, 128);
        r.addBox(x, y, z, w, h, d, 0.0F);
        r.setRotationPoint(px, py, pz);
        return r;
    }

    private ModelRenderer paddle(boolean left) {
        ModelRenderer r = new ModelRenderer(this, 62, left ? 0 : 20).setTextureSize(128, 128);
        r.addBox(-1.0F, 0.0F, -5.0F, 2, 2, 18);
        r.addBox(left ? -1.001F : 0.001F, -3.0F, 8.0F, 1, 6, 7);
        return r;
    }

    @Override
    public void render(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch,
                       float scale) {
        // Vanilla's render turns the frame 90 degrees and draws the hull and paddles; the chest goes in the
        // same frame, as 1.19 draws it.
        super.render(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale);
        this.chestBottom.render(scale);
        this.chestLid.render(scale);
        this.chestLock.render(scale);
    }
}
