package com.exiledradio.wildupdatebackport.mangrove.client;

import com.exiledradio.wildupdatebackport.mangrove.RootsCrack;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import net.minecraft.entity.Entity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.property.IExtendedBlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Mangrove roots' block model, with the crack's quads for {@link RootsCrack}: for a state carrying a
 * {@link RootsCrack.View} (the crack pass only), every quad the block draws from that view, quartered, farthest
 * first, all unculled, with 1.19's crack coordinates. Any other state gets the model as it is.
 */
public final class RootsCrackModel implements IBakedModel {

    private final IBakedModel model;

    public RootsCrackModel(IBakedModel model) {
        this.model = model;
    }

    /** The client's {@link RootsCrack.Viewer}: the camera of this frame, and the sides the block would draw. */
    public static final RootsCrack.Viewer VIEWER = new RootsCrack.Viewer() {
        @Override
        public RootsCrack.View view(IBlockState state, IBlockAccess world, BlockPos pos) {
            Minecraft mc = Minecraft.getMinecraft();
            Entity camera = mc.getRenderViewEntity();
            if (camera == null || !mc.isCallingFromMinecraftThread()) {
                return null;
            }
            Vec3d eye = ActiveRenderInfo.projectViewFromEntity(camera, mc.getRenderPartialTicks());
            int sides = 0;
            for (EnumFacing side : EnumFacing.values()) {
                if (state.shouldSideBeRendered(world, pos, side)) {
                    sides |= 1 << side.getIndex();
                }
            }
            return new RootsCrack.View(eye.x - pos.getX(), eye.y - pos.getY(), eye.z - pos.getZ(), sides);
        }
    };

    @Override
    public List<BakedQuad> getQuads(IBlockState state, EnumFacing side, long rand) {
        RootsCrack.View view = state instanceof IExtendedBlockState ? ((IExtendedBlockState) state).getValue(RootsCrack.VIEW) : null;
        if (view == null) {
            return this.model.getQuads(state, side, rand);
        }
        if (side != null) {
            return Collections.emptyList();
        }
        return crack(state, view, rand);
    }

    private List<BakedQuad> crack(IBlockState state, RootsCrack.View view, long rand) {
        List<float[]> corners = new ArrayList<float[]>();
        List<BakedQuad> sources = new ArrayList<BakedQuad>();
        for (EnumFacing side : EnumFacing.values()) {
            if (view.draws(side)) {
                add(this.model.getQuads(state, side, rand), corners, sources);
            }
        }
        add(this.model.getQuads(state, null, rand), corners, sources);
        List<BakedQuad> out = new ArrayList<BakedQuad>(corners.size() * 4);
        for (RootsCrack.Piece<BakedQuad> piece : RootsCrack.order(corners, sources, view)) {
            out.add(quad(piece.source, piece.corners));
        }
        return out;
    }

    private static void add(List<BakedQuad> quads, List<float[]> corners, List<BakedQuad> sources) {
        for (BakedQuad q : quads) {
            corners.add(corners(q));
            sources.add(q);
        }
    }

    private static int positionOffset(VertexFormat format) {
        for (int i = 0; i < format.getElementCount(); i++) {
            if (format.getElement(i).getUsage() == VertexFormatElement.EnumUsage.POSITION) {
                return format.getOffset(i) / 4;
            }
        }
        return 0;
    }

    private static float[] corners(BakedQuad q) {
        int[] data = q.getVertexData();
        int stride = q.getFormat().getIntegerSize();
        int at = positionOffset(q.getFormat());
        float[] c = new float[12];
        for (int i = 0; i < 4; i++) {
            for (int k = 0; k < 3; k++) {
                c[i * 3 + k] = Float.intBitsToFloat(data[i * stride + at + k]);
            }
        }
        return c;
    }

    /** A quarter of {@code q}: its corners, and its crack coordinates in {@code q}'s own sprite. */
    private static BakedQuad quad(BakedQuad q, float[] c) {
        VertexFormat format = q.getFormat();
        int stride = format.getIntegerSize();
        int at = positionOffset(format);
        int uvAt = format.getUvOffsetById(0) / 4;
        TextureAtlasSprite sprite = q.getSprite();
        float[] uv = RootsCrack.decalUVs(q.getFace(), c);
        int[] data = q.getVertexData().clone();
        for (int i = 0; i < 4; i++) {
            for (int k = 0; k < 3; k++) {
                data[i * stride + at + k] = Float.floatToRawIntBits(c[i * 3 + k]);
            }
            // The crack pass maps these from the quad's sprite onto the crack's (BakedQuadRetextured).
            data[i * stride + uvAt] = Float.floatToRawIntBits(sprite.getInterpolatedU(uv[i * 2] * 16.0F));
            data[i * stride + uvAt + 1] = Float.floatToRawIntBits(sprite.getInterpolatedV(uv[i * 2 + 1] * 16.0F));
        }
        return new BakedQuad(data, q.getTintIndex(), q.getFace(), sprite, q.shouldApplyDiffuseLighting(), format);
    }

    @Override
    public boolean isAmbientOcclusion() {
        return this.model.isAmbientOcclusion();
    }

    @Override
    public boolean isAmbientOcclusion(IBlockState state) {
        return this.model.isAmbientOcclusion(state);
    }

    @Override
    public boolean isGui3d() {
        return this.model.isGui3d();
    }

    @Override
    public boolean isBuiltInRenderer() {
        return this.model.isBuiltInRenderer();
    }

    @Override
    public TextureAtlasSprite getParticleTexture() {
        return this.model.getParticleTexture();
    }

    @Override
    @SuppressWarnings("deprecation")
    public ItemCameraTransforms getItemCameraTransforms() {
        return this.model.getItemCameraTransforms();
    }

    @Override
    public ItemOverrideList getOverrides() {
        return this.model.getOverrides();
    }
}
