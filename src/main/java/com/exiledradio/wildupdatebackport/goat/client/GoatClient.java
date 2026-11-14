package com.exiledradio.wildupdatebackport.goat.client;

import com.exiledradio.wildupdatebackport.goat.EntityGoat;
import com.exiledradio.wildupdatebackport.goat.GoatContent;

import net.minecraftforge.fml.client.registry.RenderingRegistry;

/** Client half of the goat stream: the renderer, when our goat is registered (Caves &amp; Cliffs absent). */
public final class GoatClient {

    private GoatClient() {
    }

    public static void init() {
        if (!GoatContent.enabled()) {
            return;
        }
        RenderingRegistry.registerEntityRenderingHandler(EntityGoat.class, RenderGoat::new);
    }
}
