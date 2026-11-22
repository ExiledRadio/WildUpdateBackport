package com.exiledradio.wildupdatebackport.selftest;

import com.exiledradio.wildupdatebackport.WildUpdateBackport;
import com.exiledradio.wildupdatebackport.mangrove.MangroveContent;
import com.exiledradio.wildupdatebackport.mangrove.RootsCrack;
import com.exiledradio.wildupdatebackport.mangrove.client.RootsCrackModel;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * The roots' crack on a client ({@link RootsCrack}): mangrove roots (one alone, two stacked, one against stone) and
 * muddy roots cracked at stage 6, photographed uncracked, with the crack drawn as 1.19 does and with 1.12.2's own (the
 * viewer off), from four places. Screenshots: {@code screenshots/wu_roots_crack_<none|fixed|old>_<view>.png}.
 *
 * <pre>    ./gradlew runClient -Pclienttest=rootscrack</pre>
 */
public final class RootsCrackClientSelfTest {

    private static final String WORLD = "wu_roots_crack_selftest";

    private final List<String> failures = new ArrayList<String>();
    private int passed;
    private int state;
    private int wait;
    private int ground;
    private String shot;
    private int view;

    public static void start() {
        MinecraftForge.EVENT_BUS.register(new RootsCrackClientSelfTest());
        WildUpdateBackport.LOGGER.info("CLIENT SELFTEST rootscrack requested");
    }

    @SubscribeEvent
    public void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || this.shot == null) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        String name = "wu_roots_crack_" + this.shot + ".png";
        this.shot = null;
        ScreenShotHelper.saveScreenshot(mc.gameDir, name, mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
        WildUpdateBackport.LOGGER.info("CLIENT SELFTEST screenshot {}", name);
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || this.state == 99) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (this.wait > 0) {
            this.wait--;
            return;
        }
        try {
            step(mc);
        } catch (Throwable t) {
            WildUpdateBackport.LOGGER.error("CLIENT SELFTEST threw", t);
            fail("threw " + t);
            finish(mc);
        }
    }

    private static final double[][] VIEWS = {{-0.9D, -1.4D, -28.0F, 32.0F}, {3.6D, -0.9D, 40.0F, 28.0F}, {-0.2D, -0.15D, -20.0F, 42.0F},
            {1.75D, -0.35D, 25.0F, 40.0F}};
    private static final String[] VIEW_NAMES = {"front", "side", "close", "close_stack"};

    private void step(Minecraft mc) {
        switch (this.state) {
            case 0:
                if (!(mc.currentScreen instanceof GuiMainMenu)) {
                    return;
                }
                mc.gameSettings.pauseOnLostFocus = false;
                this.wait = 40;
                this.state = 1;
                return;
            case 1:
                mc.getSaveLoader().deleteWorldDirectory(WORLD);
                mc.launchIntegratedServer(WORLD, WORLD, new WorldSettings(1719L, GameType.CREATIVE, false, false, WorldType.FLAT));
                this.state = 2;
                return;
            case 2: {
                if (mc.world == null || mc.player == null || mc.getIntegratedServer() == null) {
                    return;
                }
                IBlockState roots = MangroveContent.roots();
                IBlockState muddy = MangroveContent.muddyRoots();
                IBakedModel model = mc.getBlockRendererDispatcher().getModelForState(roots);
                ok(model instanceof RootsCrackModel, "mangrove roots' model is the crack model: " + model.getClass().getName());
                ok(RootsCrack.viewer != null, "the client's crack viewer is set");
                IntegratedServer server = mc.getIntegratedServer();
                int[] y = new int[1];
                server.addScheduledTask(() -> {
                    WorldServer w = server.worlds[0];
                    w.getWorldInfo().setDifficulty(EnumDifficulty.PEACEFUL);
                    w.getGameRules().setOrCreateGameRule("doDaylightCycle", "false");
                    w.getGameRules().setOrCreateGameRule("doMobSpawning", "false");
                    w.setWorldTime(6000L);
                    w.getChunk(0, 0);
                    int g = w.getHeight(new BlockPos(0, 0, 0)).getY();
                    y[0] = g;
                    w.setBlockState(new BlockPos(0, g, 1), roots);
                    w.setBlockState(new BlockPos(2, g, 1), roots);
                    w.setBlockState(new BlockPos(2, g + 1, 1), roots);
                    w.setBlockState(new BlockPos(4, g, 1), roots);
                    w.setBlockState(new BlockPos(4, g, 2), Blocks.STONE.getDefaultState());
                    w.setBlockState(new BlockPos(-2, g, 1), muddy);
                });
                this.wait = 60;
                this.state = 3;
                this.ground = -1;
                this.yRef = y;
                return;
            }
            case 3: {
                this.ground = this.yRef[0];
                place(mc, this.view);
                this.wait = 80;
                this.state = 4;
                return;
            }
            case 4:
                this.shot = "none_" + VIEW_NAMES[this.view];
                this.wait = 5;
                this.state = 40;
                return;
            case 40: {
                int g = this.ground;
                BlockPos[] cracked = {new BlockPos(0, g, 1), new BlockPos(2, g, 1), new BlockPos(2, g + 1, 1), new BlockPos(4, g, 1),
                        new BlockPos(-2, g, 1)};
                for (int i = 0; i < cracked.length; i++) {
                    mc.renderGlobal.sendBlockBreakProgress(1000 + i, cracked[i], 6);
                }
                RootsCrack.viewer = RootsCrackModel.VIEWER;
                this.wait = 2;
                this.state = 41;
                return;
            }
            case 41: {
                this.shot = "fixed_" + VIEW_NAMES[this.view];
                this.wait = 5;
                this.state = 5;
                return;
            }
            case 5:
                RootsCrack.viewer = null;
                this.shot = "old_" + VIEW_NAMES[this.view];
                this.wait = 5;
                this.state = 6;
                return;
            case 6: {
                RootsCrack.viewer = RootsCrackModel.VIEWER;
                int g = this.ground;
                BlockPos[] cracked = {new BlockPos(0, g, 1), new BlockPos(2, g, 1), new BlockPos(2, g + 1, 1), new BlockPos(4, g, 1),
                        new BlockPos(-2, g, 1)};
                for (int i = 0; i < cracked.length; i++) {
                    mc.renderGlobal.sendBlockBreakProgress(1000 + i, cracked[i], -1);
                }
                this.view++;
                if (this.view < VIEWS.length) {
                    place(mc, this.view);
                    this.wait = 40;
                    this.state = 4;
                    return;
                }
                this.state = 7;
                return;
            }
            case 7: {
                // Break particles: a dry root and a waterlogged one, broken by the server as a player's break is.
                net.minecraft.client.renderer.texture.TextureAtlasSprite sprite = mc.getBlockRendererDispatcher().getBlockModelShapes()
                        .getTexture(MangroveContent.roots());
                WildUpdateBackport.LOGGER.info("CLIENT SELFTEST roots particle sprite {}", sprite.getIconName());
                ok(sprite.getIconName().endsWith("mangrove_roots_side"), "the roots' particle sprite is the side: " + sprite.getIconName());
                place(mc, 2);
                IntegratedServer server = mc.getIntegratedServer();
                int g = this.ground;
                server.addScheduledTask(() -> {
                    WorldServer w = server.worlds[0];
                    BlockPos wet = new BlockPos(0, g, 1);
                    boolean logged = git.jbredwards.fluidlogged_api.api.util.FluidloggedUtils.setFluidState(w, wet, w.getBlockState(wet),
                            git.jbredwards.fluidlogged_api.api.util.FluidState.of(net.minecraftforge.fluids.FluidRegistry.WATER), false);
                    WildUpdateBackport.LOGGER.info("CLIENT SELFTEST waterlogged the root: {}", logged);
                });
                this.wait = 60;
                this.state = 8;
                return;
            }
            case 8: {
                IntegratedServer server = mc.getIntegratedServer();
                int g = this.ground;
                server.addScheduledTask(() -> {
                    WorldServer w = server.worlds[0];
                    w.destroyBlock(new BlockPos(0, g, 1), false);
                    w.destroyBlock(new BlockPos(2, g + 1, 1), false);
                });
                this.wait = 3;
                this.state = 9;
                return;
            }
            case 9:
                this.shot = "break";
                this.wait = 10;
                this.state = 10;
                return;
            default:
                finish(mc);
        }
    }

    private int[] yRef;

    private void place(Minecraft mc, int v) {
        IntegratedServer server = mc.getIntegratedServer();
        double[] at = VIEWS[v];
        int g = this.ground;
        mc.gameSettings.hideGUI = true;
        server.addScheduledTask(() -> {
            EntityPlayerMP p = server.getPlayerList().getPlayers().get(0);
            p.capabilities.isFlying = true;
            p.sendPlayerAbilities();
            p.connection.setPlayerLocation(at[0], g + 0.4D, at[1], (float) at[2], (float) at[3]);
        });
    }

    private void ok(boolean condition, String what) {
        if (condition) {
            this.passed++;
        } else {
            fail(what);
        }
    }

    private void fail(String what) {
        this.failures.add(what);
        WildUpdateBackport.LOGGER.error("CLIENT SELFTEST FAIL {}", what);
    }

    private void finish(Minecraft mc) {
        this.state = 99;
        WildUpdateBackport.LOGGER.info("CLIENT SELFTEST DONE pass={} fail={}", this.passed, this.failures.size());
        try {
            PrintWriter out = new PrintWriter(new File(mc.gameDir, "selftest-client-report.txt"), "UTF-8");
            out.println("pass=" + this.passed + " fail=" + this.failures.size());
            for (String f : this.failures) {
                out.println(f);
            }
            out.close();
        } catch (Exception ignored) {
        }
        mc.shutdown();
    }
}
