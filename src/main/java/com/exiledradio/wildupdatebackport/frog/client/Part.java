package com.exiledradio.wildupdatebackport.frog.client;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * A model part as 1.19's {@code ModelPart}: a pose it resets to before each frame's animation, a scale
 * (1.12.2's parts have none), and 1.19's rotation order (x, then y, then z, applied as one quaternion),
 * so keyframe animations made for 1.19's models look as they do there.
 */
@SideOnly(Side.CLIENT)
public class Part extends ModelRenderer {

    public final String name;
    public float xScale = 1.0F;
    public float yScale = 1.0F;
    public float zScale = 1.0F;
    private float px;
    private float py;
    private float pz;

    public Part(ModelBase model, String name, int texX, int texY) {
        super(model, name);
        this.name = name;
        this.setTextureOffset(texX, texY);
    }

    /** Sets and remembers the initial pose (1.19's {@code PartPose.offset}). */
    public Part pose(float x, float y, float z) {
        this.setRotationPoint(x, y, z);
        this.px = x;
        this.py = y;
        this.pz = z;
        return this;
    }

    public Part box(int texX, int texY, float x, float y, float z, int w, int h, int d, float grow) {
        this.cubeList.add(new ModelBox(this, texX, texY, x, y, z, w, h, d, grow));
        return this;
    }

    /** 1.19's {@code resetPose}. */
    public void reset() {
        this.rotationPointX = this.px;
        this.rotationPointY = this.py;
        this.rotationPointZ = this.pz;
        this.rotateAngleX = 0.0F;
        this.rotateAngleY = 0.0F;
        this.rotateAngleZ = 0.0F;
        this.xScale = 1.0F;
        this.yScale = 1.0F;
        this.zScale = 1.0F;
    }

    @Override
    public void render(float scale) {
        if (this.isHidden || !this.showModel) {
            return;
        }
        GlStateManager.pushMatrix();
        GlStateManager.translate(this.rotationPointX * scale, this.rotationPointY * scale, this.rotationPointZ * scale);
        if (this.rotateAngleX != 0.0F) {
            GlStateManager.rotate(this.rotateAngleX * (180F / (float) Math.PI), 1.0F, 0.0F, 0.0F);
        }
        if (this.rotateAngleY != 0.0F) {
            GlStateManager.rotate(this.rotateAngleY * (180F / (float) Math.PI), 0.0F, 1.0F, 0.0F);
        }
        if (this.rotateAngleZ != 0.0F) {
            GlStateManager.rotate(this.rotateAngleZ * (180F / (float) Math.PI), 0.0F, 0.0F, 1.0F);
        }
        if (this.xScale != 1.0F || this.yScale != 1.0F || this.zScale != 1.0F) {
            GlStateManager.scale(this.xScale, this.yScale, this.zScale);
        }
        for (ModelBox box : this.cubeList) {
            box.render(Tessellator.getInstance().getBuffer(), scale);
        }
        if (this.childModels != null) {
            for (ModelRenderer child : this.childModels) {
                child.render(scale);
            }
        }
        GlStateManager.popMatrix();
    }
}
