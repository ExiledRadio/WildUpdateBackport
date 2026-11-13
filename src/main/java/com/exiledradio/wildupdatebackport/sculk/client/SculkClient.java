package com.exiledradio.wildupdatebackport.sculk.client;

import com.exiledradio.wildupdatebackport.WildUpdateBackport;
import com.exiledradio.wildupdatebackport.sculk.BlockSculkShrieker;
import com.exiledradio.wildupdatebackport.sculk.BlockSculkVein;
import com.exiledradio.wildupdatebackport.sculk.SculkContent;
import com.exiledradio.wildupdatebackport.sculk.SculkEffectPacket;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.entity.Entity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.util.Random;

/**
 * Sculk's client effects from packet 25, as 1.19's level renderer draws them: the charge's particles creeping
 * over the faces it travels on (with its bubbling sound), the pop when a charge is spent, the souls of a
 * blooming catalyst, and the shriek's rising rings. The sprites are 1.19's, stitched into the block atlas.
 */
@Mod.EventBusSubscriber(modid = WildUpdateBackport.MOD_ID, value = Side.CLIENT)
public final class SculkClient {

    private static final TextureAtlasSprite[] CHARGE = new TextureAtlasSprite[7];
    private static final TextureAtlasSprite[] POP = new TextureAtlasSprite[4];
    private static final TextureAtlasSprite[] SOUL = new TextureAtlasSprite[11];
    private static TextureAtlasSprite shriek;

    private SculkClient() {
    }

    /** From the client's pre-init. */
    public static void init() {
        SculkEffectPacket.Handler.client = packet -> Minecraft.getMinecraft().addScheduledTask(() -> {
            World world = Minecraft.getMinecraft().world;
            if (world != null && shriek != null) {
                for (SculkEffectPacket.Entry e : packet.entries) {
                    play(world, e);
                }
            }
        });
    }

    @SubscribeEvent
    public static void stitch(TextureStitchEvent.Pre event) {
        for (int i = 0; i < CHARGE.length; i++) {
            CHARGE[i] = event.getMap().registerSprite(sprite("sculk_charge_" + i));
        }
        for (int i = 0; i < POP.length; i++) {
            POP[i] = event.getMap().registerSprite(sprite("sculk_charge_pop_" + i));
        }
        for (int i = 0; i < SOUL.length; i++) {
            SOUL[i] = event.getMap().registerSprite(sprite("sculk_soul_" + i));
        }
        shriek = event.getMap().registerSprite(sprite("shriek"));
    }

    private static ResourceLocation sprite(String name) {
        return new ResourceLocation(WildUpdateBackport.MOD_ID, "particle/" + name);
    }

    private static void add(Particle p) {
        Minecraft.getMinecraft().effectRenderer.addEffect(p);
    }

    private static double between(Random r, double a, double b) {
        return a + r.nextDouble() * (b - a);
    }

    static void play(World world, SculkEffectPacket.Entry e) {
        Random r = world.rand;
        BlockPos pos = e.pos;
        switch (e.type) {
            case SculkEffectPacket.CHARGE:
                charge(world, pos, e.data, r);
                break;
            case SculkEffectPacket.SHRIEK:
                for (int i = 0; i < 10; i++) {
                    add(new Shriek(world, pos.getX() + 0.5D, pos.getY() + BlockSculkShrieker.TOP_Y, pos.getZ() + 0.5D, i * 5));
                }
                world.playSound(pos.getX() + 0.5D, pos.getY() + BlockSculkShrieker.TOP_Y, pos.getZ() + 0.5D, SculkContent.SHRIEKER_SHRIEK,
                        SoundCategory.BLOCKS, 2.0F, 0.6F + r.nextFloat() * 0.4F, false);
                break;
            case SculkEffectPacket.SOUL:
                for (int i = 0; i < e.data; i++) {
                    add(new Animated(world, pos.getX() + 0.5D + r.nextGaussian() * 0.2D, pos.getY() + 1.15D,
                            pos.getZ() + 0.5D + r.nextGaussian() * 0.2D, SOUL, (int) (8.0D / (Math.random() * 0.8D + 0.2D)) + 4, 1.5F, true));
                }
                break;
            default:
                break;
        }
    }

    /** 1.19's level event 3006. */
    private static void charge(World world, BlockPos pos, int data, Random r) {
        int strength = data >> 6;
        if (strength > 0) {
            if (r.nextFloat() < 0.3F + strength * 0.1F) {
                float volume = 0.15F + 0.02F * strength * strength * r.nextFloat();
                float pitch = 0.4F + 0.3F * strength * r.nextFloat();
                world.playSound(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, SculkContent.SCULK_CHARGE, SoundCategory.BLOCKS,
                        volume, pitch, false);
            }
            int faces = data & 63;
            if (faces == 0) {
                for (EnumFacing f : EnumFacing.VALUES) {
                    onFace(world, pos, f, strength, f.getAxis() == EnumFacing.Axis.Y ? 0.65D : 0.57D, r);
                }
            } else {
                for (EnumFacing f : BlockSculkVein.unpack(faces)) {
                    onFace(world, pos, f, strength, 0.35D, r);
                }
            }
        } else {
            world.playSound(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, SculkContent.SCULK_CHARGE, SoundCategory.BLOCKS,
                    1.0F, 1.0F, false);
            IBlockState state = world.getBlockState(pos);
            boolean full = state.isFullCube() && state.getCollisionBoundingBox(world, pos) != null;
            int count = full ? 40 : 20;
            float spread = full ? 0.45F : 0.25F;
            for (int i = 0; i < count; i++) {
                float dx = 2.0F * r.nextFloat() - 1.0F;
                float dy = 2.0F * r.nextFloat() - 1.0F;
                float dz = 2.0F * r.nextFloat() - 1.0F;
                Animated p = new Animated(world, pos.getX() + 0.5D + dx * spread, pos.getY() + 0.5D + dy * spread,
                        pos.getZ() + 0.5D + dz * spread, POP, r.nextInt(4) + 6, 1.0F, false);
                p.speed(dx * 0.07F, dy * 0.07F, dz * 0.07F);
                add(p);
            }
        }
    }

    /** 1.19's {@code ParticleUtils.spawnParticleOnFace}, {@code UniformInt.of(0, strength)} of them. */
    private static void onFace(World world, BlockPos pos, EnumFacing face, int strength, double offset, Random r) {
        int n = r.nextInt(strength + 1);
        for (int i = 0; i < n; i++) {
            int sx = face.getXOffset();
            int sy = face.getYOffset();
            int sz = face.getZOffset();
            double x = pos.getX() + 0.5D + (sx == 0 ? between(r, -0.5D, 0.5D) : sx * offset);
            double y = pos.getY() + 0.5D + (sy == 0 ? between(r, -0.5D, 0.5D) : sy * offset);
            double z = pos.getZ() + 0.5D + (sz == 0 ? between(r, -0.5D, 0.5D) : sz * offset);
            Animated p = new Animated(world, x, y, z, CHARGE, r.nextInt(12) + 8, 1.5F, false);
            p.speed(sx == 0 ? between(r, -0.005D, 0.005D) : 0.0D, sy == 0 ? between(r, -0.005D, 0.005D) : 0.0D,
                    sz == 0 ? between(r, -0.005D, 0.005D) : 0.0D);
            add(p);
        }
    }

    /** A full-bright particle whose sprite follows its age, friction 0.96 and no gravity or collision (charge, pop, soul). */
    static final class Animated extends Particle {
        private final TextureAtlasSprite[] sprites;

        Animated(World world, double x, double y, double z, TextureAtlasSprite[] sprites, int lifetime, float scale, boolean rising) {
            super(world, x, y, z, 0.0D, 0.0D, 0.0D);
            this.sprites = sprites;
            this.particleMaxAge = lifetime;
            this.particleScale *= scale;
            this.particleGravity = 0.0F;
            this.canCollide = false;
            if (rising) {
                this.motionX *= 0.01D;
                this.motionY *= 0.01D;
                this.motionZ *= 0.01D;
                this.posX += (this.rand.nextFloat() - this.rand.nextFloat()) * 0.05F;
                this.posY += (this.rand.nextFloat() - this.rand.nextFloat()) * 0.05F;
                this.posZ += (this.rand.nextFloat() - this.rand.nextFloat()) * 0.05F;
            } else {
                this.motionX = this.motionY = this.motionZ = 0.0D;
            }
            this.setSprite();
        }

        void speed(double x, double y, double z) {
            this.motionX = x;
            this.motionY = y;
            this.motionZ = z;
        }

        private void setSprite() {
            int i = this.particleAge * (this.sprites.length - 1) / Math.max(1, this.particleMaxAge);
            this.setParticleTexture(this.sprites[MathHelper.clamp(i, 0, this.sprites.length - 1)]);
        }

        @Override
        public void onUpdate() {
            this.prevPosX = this.posX;
            this.prevPosY = this.posY;
            this.prevPosZ = this.posZ;
            if (this.particleAge++ >= this.particleMaxAge) {
                this.setExpired();
                return;
            }
            this.move(this.motionX, this.motionY, this.motionZ);
            this.motionX *= 0.96D;
            this.motionY *= 0.96D;
            this.motionZ *= 0.96D;
            this.setSprite();
        }

        @Override
        public int getFXLayer() {
            return 1;
        }

        @Override
        public int getBrightnessForRender(float partialTicks) {
            return 0xF000F0;
        }
    }

    /**
     * 1.19's {@code ShriekParticle}: after its delay, a ring rising 0.1 a tick for 30 ticks, growing and fading,
     * drawn as two quads tilted 60 degrees either way about the horizontal.
     */
    static final class Shriek extends Particle {
        private static final float TILT = 1.0472F;
        private int delay;

        Shriek(World world, double x, double y, double z, int delay) {
            super(world, x, y, z);
            this.delay = delay;
            this.particleMaxAge = 30;
            this.particleGravity = 0.0F;
            this.canCollide = false;
            this.motionX = 0.0D;
            this.motionY = 0.1D;
            this.motionZ = 0.0D;
            this.setParticleTexture(shriek);
        }

        @Override
        public void onUpdate() {
            if (this.delay > 0) {
                this.delay--;
                return;
            }
            this.prevPosX = this.posX;
            this.prevPosY = this.posY;
            this.prevPosZ = this.posZ;
            if (this.particleAge++ >= this.particleMaxAge) {
                this.setExpired();
                return;
            }
            this.move(this.motionX, this.motionY, this.motionZ);
            this.motionX *= 0.98D;
            this.motionY *= 0.98D;
            this.motionZ *= 0.98D;
        }

        @Override
        public void renderParticle(BufferBuilder buffer, Entity entity, float partialTicks, float rx, float rz, float ryz, float rxy, float rxz) {
            if (this.delay > 0) {
                return;
            }
            float age = (this.particleAge + partialTicks) / this.particleMaxAge;
            float alpha = 1.0F - MathHelper.clamp(age, 0.0F, 1.0F);
            float size = 0.85F * MathHelper.clamp(age * 0.75F, 0.0F, 1.0F);
            float x = (float) (this.prevPosX + (this.posX - this.prevPosX) * partialTicks - interpPosX);
            float y = (float) (this.prevPosY + (this.posY - this.prevPosY) * partialTicks - interpPosY);
            float z = (float) (this.prevPosZ + (this.posZ - this.prevPosZ) * partialTicks - interpPosZ);
            quad(buffer, x, y, z, size, alpha, -TILT, false);
            quad(buffer, x, y, z, size, alpha, TILT, true);
        }

        /** A square rotated about X by {@code tilt}, then (for the second) half a turn about Y, as 1.19's quaternions. */
        private void quad(BufferBuilder buffer, float x, float y, float z, float size, float alpha, float tilt, boolean turn) {
            float[][] corners = {{-1, -1}, {-1, 1}, {1, 1}, {1, -1}};
            float cos = MathHelper.cos(tilt);
            float sin = MathHelper.sin(tilt);
            float u0 = this.particleTexture.getMinU();
            float u1 = this.particleTexture.getMaxU();
            float v0 = this.particleTexture.getMinV();
            float v1 = this.particleTexture.getMaxV();
            float[][] uv = {{u1, v1}, {u1, v0}, {u0, v0}, {u0, v1}};
            for (int i = 0; i < 4; i++) {
                float cx = corners[i][0];
                float cy = corners[i][1];
                // about X: (x, y, 0) -> (x, y cos, y sin)
                float vx = cx;
                float vy = cy * cos;
                float vz = cy * sin;
                if (turn) {
                    vx = -vx;
                    vz = -vz;
                }
                buffer.pos(x + vx * size, y + vy * size, z + vz * size).tex(uv[i][0], uv[i][1])
                        .color(this.particleRed, this.particleGreen, this.particleBlue, alpha).lightmap(240, 240).endVertex();
            }
        }

        @Override
        public int getFXLayer() {
            return 1;
        }

        @Override
        public int getBrightnessForRender(float partialTicks) {
            return 0xF000F0;
        }
    }
}
