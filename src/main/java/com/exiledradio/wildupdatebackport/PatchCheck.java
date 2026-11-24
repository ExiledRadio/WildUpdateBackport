package com.exiledradio.wildupdatebackport;

import com.exiledradio.wildupdatebackport.asm.ChangesTransformer;
import com.exiledradio.wildupdatebackport.asm.DeepDarkTransformer;
import com.exiledradio.wildupdatebackport.asm.RulesTransformer;
import com.exiledradio.wildupdatebackport.asm.SwampTransformer;

import net.minecraftforge.fml.common.FMLCommonHandler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The release-jar check behind {@code tools/release/boot-server.sh}: with {@code -Dwildupdatebackport.loadPatched=true}
 * it loads and initializes every class this mod's coremod patches, once the server has started, so the JVM verifies
 * each one, and logs {@code PATCHCHECK ok <n>} or one {@code PATCHCHECK FAIL} line per class.
 *
 * <p>Why: Mojang's classes that Forge does not patch keep ProGuard's stack-map frames, which mark a local TOP once
 * vanilla stops reading it. A patch that loads such a local is a VerifyError only in the real game, and only once the
 * class loads: the dev runs recompile Minecraft with javac and never show it, and some targets (a command, an item)
 * may never load during a short boot. Without the property this does nothing.
 */
public final class PatchCheck {

    static final String PROPERTY = WildUpdateBackport.MOD_ID + ".loadPatched";

    private PatchCheck() {
    }

    /** Every class a transformer of this mod patches on the given side (OptiFine's classes left out). */
    public static List<String> targets(boolean client) {
        List<String> out = new ArrayList<String>();
        out.addAll(Arrays.asList(ChangesTransformer.TARGETS));
        out.addAll(Arrays.asList(RulesTransformer.TARGETS));
        out.addAll(Arrays.asList(SwampTransformer.TARGETS));
        out.addAll(Arrays.asList(DeepDarkTransformer.TARGETS));
        return out;
    }

    /** Runs the check when the property asks for it. */
    static void runIfAsked() {
        if (!Boolean.getBoolean(PROPERTY)) {
            return;
        }
        boolean client = FMLCommonHandler.instance().getSide().isClient();
        List<String> targets = targets(client);
        int failed = 0;
        for (String target : targets) {
            try {
                Class.forName(target, true, PatchCheck.class.getClassLoader());
            } catch (Throwable t) {
                failed++;
                WildUpdateBackport.LOGGER.error("PATCHCHECK FAIL {}: {}", target, t.toString().split("\n")[0], t);
            }
        }
        if (failed == 0) {
            WildUpdateBackport.LOGGER.info("PATCHCHECK ok {} patched classes loaded and verified ({}).", targets.size(),
                    client ? "client" : "server");
        }
    }
}
