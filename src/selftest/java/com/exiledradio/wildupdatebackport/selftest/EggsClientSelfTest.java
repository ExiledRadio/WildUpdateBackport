package com.exiledradio.wildupdatebackport.selftest;

import com.exiledradio.wildupdatebackport.WildUpdateBackport;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.entity.EntityList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * The golems' spawn eggs on a client: both are in {@code EntityList.ENTITY_EGGS} when the block atlas is stitched,
 * before any world is loaded, which is when Backport Core gives each egg its own texture ({@code SpawnEggModels}).
 *
 * <pre>    ./gradlew runClient -Pclienttest=eggs</pre>
 */
public final class EggsClientSelfTest {

    private static final ResourceLocation[] GOLEMS = {new ResourceLocation("minecraft", "villager_golem"),
            new ResourceLocation("minecraft", "snowman")};

    private int stitches;
    private int pass;
    private int fail;
    private int ticks;

    public static void start() {
        MinecraftForge.EVENT_BUS.register(new EggsClientSelfTest());
        WildUpdateBackport.LOGGER.info("CLIENT SELFTEST eggs requested");
    }

    @SubscribeEvent
    public void stitch(TextureStitchEvent.Pre event) {
        if (event.getMap() != Minecraft.getMinecraft().getTextureMapBlocks()) {
            return;
        }
        this.stitches++;
        for (ResourceLocation id : GOLEMS) {
            check(EntityList.ENTITY_EGGS.containsKey(id), id + " has an egg at stitch " + this.stitches);
        }
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(Minecraft.getMinecraft().currentScreen instanceof GuiMainMenu)
                || ++this.ticks < 40) {
            return;
        }
        check(this.stitches > 0, "the block atlas was stitched after the test started (" + this.stitches + ")");
        WildUpdateBackport.LOGGER.info("CLIENT SELFTEST DONE pass={} fail={}", this.pass, this.fail);
        MinecraftForge.EVENT_BUS.unregister(this);
        Minecraft.getMinecraft().shutdown();
    }

    private void check(boolean ok, String what) {
        if (ok) {
            this.pass++;
            WildUpdateBackport.LOGGER.info("CLIENT SELFTEST PASS {}", what);
        } else {
            this.fail++;
            WildUpdateBackport.LOGGER.error("CLIENT SELFTEST FAIL {}", what);
        }
    }
}
