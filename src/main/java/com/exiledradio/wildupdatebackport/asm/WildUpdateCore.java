package com.exiledradio.wildupdatebackport.asm;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * This mod's own small coremod: the bytecode patches 1.19's behavior needs where 1.12.2 has no hook. One
 * transformer per patch group, each patch documented in {@code docs/PATCHES.md}. No MixinBooter.
 *
 * <p>Named in the jar's manifest ({@code FMLCorePlugin}, with {@code FMLCorePluginContainsFMLMod}) and, in the dev
 * environment, by {@code -Dfml.coreMods.load} ({@code build.gradle}). Sorting index above 1000, so every class reaches
 * the transformers with SRG member names in a real instance (MCP names in dev); each transformer matches both.
 *
 * <p>Transformers only rewrite bytecode and name hook classes as strings: nothing here loads a Minecraft class or a
 * class of the mod itself. A stream adding patches adds its transformer to {@link #getASMTransformerClass}.
 */
@IFMLLoadingPlugin.Name("Wild Update Backport Core")
@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.TransformerExclusions("com.exiledradio.wildupdatebackport.asm.")
@IFMLLoadingPlugin.SortingIndex(1001)
public final class WildUpdateCore implements IFMLLoadingPlugin {

    @Override
    public String[] getASMTransformerClass() {
        return new String[] {
                LookTransformer.class.getName(),
                SwampTransformer.class.getName(),
        };
    }

    @Nullable
    @Override
    public String getModContainerClass() {
        return null;
    }

    @Nullable
    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
    }

    @Nullable
    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
