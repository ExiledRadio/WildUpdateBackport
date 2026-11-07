package com.exiledradio.wildupdatebackport.gameevent.client;

import com.exiledradio.wildupdatebackport.WildUpdateBackport;
import com.exiledradio.wildupdatebackport.gameevent.VibrationPacket;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

import javax.annotation.Nullable;

/**
 * The vibration particle: 1.19's animated {@code particle/vibration} sprite travelling from where the
 * vibration happened to the listener, arriving when the vibration does. A moving listener (a warden, an allay)
 * is followed.
 */
@Mod.EventBusSubscriber(modid = WildUpdateBackport.MOD_ID, value = Side.CLIENT)
public final class VibrationClient {

    private static final ResourceLocation SPRITE = new ResourceLocation(WildUpdateBackport.MOD_ID, "particle/vibration");
    private static TextureAtlasSprite sprite;

    private VibrationClient() {
    }

    /** From the client's pre-init. */
    public static void init() {
        VibrationPacket.Handler.client = packet -> Minecraft.getMinecraft().addScheduledTask(() -> spawn(packet));
    }

    @SubscribeEvent
    public static void stitch(TextureStitchEvent.Pre event) {
        sprite = event.getMap().registerSprite(SPRITE);
    }

    private static void spawn(VibrationPacket packet) {
        World world = Minecraft.getMinecraft().world;
        if (world == null || sprite == null) {
            return;
        }
        Minecraft.getMinecraft().effectRenderer.addEffect(new Signal(world, packet.from, packet.target, Math.max(1, packet.ticks)));
    }

    /** The travelling signal. */
    static final class Signal extends Particle {
        private final Vec3d from;
        private final NBTTagCompound target;

        Signal(World world, Vec3d from, NBTTagCompound target, int ticks) {
            super(world, from.x, from.y, from.z);
            this.from = from;
            this.target = target;
            this.particleMaxAge = ticks;
            this.particleScale = 3.0F;
            this.particleGravity = 0.0F;
            this.canCollide = false;
            this.setParticleTexture(sprite);
            this.motionX = this.motionY = this.motionZ = 0.0D;
        }

        @Nullable
        private Vec3d targetPos() {
            if ("entity".equals(this.target.getString("type"))) {
                Entity e = this.world.getEntityByID(this.target.getInteger("id"));
                return e == null ? null : new Vec3d(e.posX, e.posY + this.target.getFloat("y"), e.posZ);
            }
            return new Vec3d(this.target.getInteger("x") + 0.5D, this.target.getInteger("y") + 0.5D, this.target.getInteger("z") + 0.5D);
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
            Vec3d to = targetPos();
            if (to == null) {
                this.setExpired();
                return;
            }
            double f = (double) this.particleAge / this.particleMaxAge;
            this.setPosition(this.from.x + (to.x - this.from.x) * f, this.from.y + (to.y - this.from.y) * f,
                    this.from.z + (to.z - this.from.z) * f);
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
