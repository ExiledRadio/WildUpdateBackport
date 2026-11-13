package com.exiledradio.wildupdatebackport.items.client;

import com.exiledradio.wildupdatebackport.items.EnchantmentSwiftSneak;
import com.exiledradio.wildupdatebackport.items.ItemsContent;
import com.exiledradio.wildupdatebackport.items.LastDeathLocation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.item.Item;
import net.minecraft.util.MovementInput;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.InputUpdateEvent;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

/**
 * Stream I's client side: the recovery compass's needle, the goat horn's {@code tooting} model, the last death
 * location from the server, and Swift Sneak's sneaking speed.
 */
public final class ItemsClient {

    private ItemsClient() {
    }

    /** Called from {@code ClientSetup} in pre-init. */
    public static void init() {
        if (!ItemsContent.enabled()) {
            return;
        }
        MinecraftForge.EVENT_BUS.register(new ItemsClient());
        LastDeathLocation.Handler.client = where -> Minecraft.getMinecraft().addScheduledTask(() -> RecoveryCompassAngle.lastDeath = where);
    }

    @SubscribeEvent
    public void models(ModelRegistryEvent event) {
        Item compass = ItemsContent.recoveryCompass();
        if (compass != null) {
            compass.addPropertyOverride(new ResourceLocation("angle"), new RecoveryCompassAngle());
        }
        Item horn = ItemsContent.goatHorn();
        if (horn != null) {
            // 1.19's "tooting": 1 while this very stack is in use.
            horn.addPropertyOverride(new ResourceLocation("tooting"), (stack, world, entity) ->
                    entity != null && entity.isHandActive() && entity.getActiveItemStack() == stack ? 1.0F : 0.0F);
        }
    }

    @SubscribeEvent
    public void disconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        RecoveryCompassAngle.lastDeath = null;
    }

    /**
     * 1.19's {@code LocalPlayer.aiStep}: sneaking input is scaled by {@code clamp(0.3 + 0.15 * Swift Sneak, 0, 1)}
     * instead of 1.12.2's fixed 0.3. This event runs right after 1.12.2 applied its 0.3 and before an item in use
     * slows the input further, the same place in the order as 1.19's. Movement is the client's, as in vanilla, so
     * the server takes it as it is.
     */
    @SubscribeEvent
    public void input(InputUpdateEvent event) {
        MovementInput input = event.getMovementInput();
        EnchantmentSwiftSneak swiftSneak = ItemsContent.swiftSneak();
        if (!input.sneak || swiftSneak == null || !(event.getEntityPlayer() instanceof EntityPlayerSP)) {
            return;
        }
        float speed = EnchantmentSwiftSneak.sneakSpeed(swiftSneak, event.getEntityPlayer());
        float scale = speed / EnchantmentSwiftSneak.BASE;
        input.moveStrafe *= scale;
        input.moveForward *= scale;
    }
}
