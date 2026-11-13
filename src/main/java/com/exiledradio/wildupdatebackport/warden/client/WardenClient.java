package com.exiledradio.wildupdatebackport.warden.client;

import com.exiledradio.wildupdatebackport.WildUpdateBackport;
import com.exiledradio.wildupdatebackport.warden.EntityWarden;
import com.exiledradio.wildupdatebackport.warden.SonicBoomPacket;
import com.exiledradio.wildupdatebackport.warden.WardenContent;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

/** The warden's renderer and the sonic boom particle. Darkness is {@link DarknessClient}. */
@Mod.EventBusSubscriber(modid = WildUpdateBackport.MOD_ID, value = Side.CLIENT)
public final class WardenClient {

    private static final TextureAtlasSprite[] SONIC_BOOM = new TextureAtlasSprite[16];

    private WardenClient() {
    }

    /** From the client's pre-init. */
    public static void init() {
        if (!WardenContent.enabled()) {
            return;
        }
        RenderingRegistry.registerEntityRenderingHandler(EntityWarden.class, RenderWarden::new);
        SonicBoomPacket.Handler.client = packet -> Minecraft.getMinecraft().addScheduledTask(() -> spawn(packet));
    }

    @SubscribeEvent
    public static void stitch(TextureStitchEvent.Pre event) {
        if (!WardenContent.enabled()) {
            return;
        }
        for (int i = 0; i < SONIC_BOOM.length; i++) {
            SONIC_BOOM[i] = event.getMap().registerSprite(new ResourceLocation(WildUpdateBackport.MOD_ID, "particle/sonic_boom_" + i));
        }
    }

    private static void spawn(SonicBoomPacket packet) {
        World world = Minecraft.getMinecraft().world;
        if (world == null || SONIC_BOOM[0] == null) {
            return;
        }
        for (int i = 1; i < packet.count; i++) {
            Vec3d p = packet.from.add(packet.dir.scale(i));
            Minecraft.getMinecraft().effectRenderer.addEffect(new SonicBoom(world, p.x, p.y, p.z));
        }
    }

    /** 1.19's {@code SonicBoomParticle}: a huge-explosion-style sprite sequence, 16 ticks, 3 blocks across, full bright. */
    static final class SonicBoom extends Particle {
        SonicBoom(World world, double x, double y, double z) {
            super(world, x, y, z, 0.0D, 0.0D, 0.0D);
            this.particleMaxAge = 16;
            float gray = this.rand.nextFloat() * 0.6F + 0.4F;
            this.particleRed = gray;
            this.particleGreen = gray;
            this.particleBlue = gray;
            // 1.19's quad size 1.5 is the half width; 1.12 draws 0.1 x scale either side.
            this.particleScale = 15.0F;
            this.motionX = this.motionY = this.motionZ = 0.0D;
            this.canCollide = false;
            setParticleTexture(SONIC_BOOM[0]);
        }

        @Override
        public int getFXLayer() {
            return 1;
        }

        @Override
        public int getBrightnessForRender(float partialTicks) {
            return 15728880;
        }

        @Override
        public void onUpdate() {
            this.prevPosX = this.posX;
            this.prevPosY = this.posY;
            this.prevPosZ = this.posZ;
            if (this.particleAge++ >= this.particleMaxAge) {
                setExpired();
            } else {
                setParticleTexture(SONIC_BOOM[this.particleAge * (SONIC_BOOM.length - 1) / this.particleMaxAge]);
            }
        }
    }
}
