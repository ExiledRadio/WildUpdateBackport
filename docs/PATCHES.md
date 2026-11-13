# Bytecode patches

The mod's own coremod, `com.exiledradio.wildupdatebackport.asm.WildUpdateCore` (sorting index 1001, so every class
arrives with SRG member names in a real instance and MCP names in dev; each patch matches both). One transformer per
patch group, no MixinBooter, nothing loaded from inside a transformer. A target of another shape than expected (another
coremod got there first and changed it) is left alone and logged, so vanilla's behavior stays. Every patch below only
puts a static call between a vanilla value and its use: no jump, no new local, no new frame, so it coexists with
Fluidlogged API's transformers and any other that leaves those values in place.

| # | Stream | Target (MCP / SRG) | Change | Why no event does it |
| --- | --- | --- | --- | --- |
| P1 | Lf | `EntityRenderer.hurtCameraEffect` / `func_78482_e` `(F)V` | its one `14.0F` (the hurt tilt, degrees) becomes `LookHooks.damageTilt(14.0F)`: times Damage Tilt (1.19.4's `GameRenderer.bobHurt`) | The tilt is a GL rotation applied before `EntityViewRenderEvent.CameraSetup` fires; nothing can scale it from there. |
| P2 | Lf | `RenderItem.renderEffect` / `func_191966_a` `(IBakedModel)V` | each of its two `Minecraft.getSystemTime()` results passes through `LookHooks.glintTime` (times Glint Speed) and each of its two `-8372020` colors through `LookHooks.glintColor` (RGB times Glint Strength, alpha kept) | No event around the item glint. 1.19.4's `setupGlintTexturing` runs its clock at `millis * glintSpeed * 8` (1.19.3: `millis * 8`) and its shader multiplies the glint by `GlintAlpha`. |
| P3 | Lf | `LayerArmorBase.renderEnchantedGlint` / `func_188364_a` | its clock `ticksExisted + partialTicks` passes through `LookHooks.glintTicks` before it is stored, and the color constants `0.38F, 0.19F, 0.608F` through `LookHooks.glintComponent` | As P2, for armor and elytra (`LayerElytra` calls the same method). |
| P9 | Sw | `GenLayerShore.getInts` / `func_75904_a` `(IIII)[I` | the biome id `k` loaded for its one swamp comparison (`k != Biome.getIdForBiome(Biomes.SWAMPLAND)`, the biomes that meet the ocean with no beach) passes through `SwampHooks.shoreBiome`, which answers the swamp's id for the mangrove swamp; the cell itself keeps `k` | The beach rule is inside the layer's loop over cells; `WorldTypeEvent.InitBiomeGens` can only replace the whole layer chain. Numbered P9 because stream C has P4-P8 planned. |

P1 to P3 are client classes: a dedicated server never loads them. P9 runs on both sides wherever 1.12.2's biome layers are made; the self-test (`-Pselftest=swamp`) patches the dev and SRG classes, checks the hook call and the stack, and runs the patched layer. The self-test (`-Pselftest=lookfeel`) runs each
patch on the dev jar's MCP-named class and on Forge's SRG-named jar from the Gradle cache, counts the hook calls and
runs ASM's stack verifier on every method.
