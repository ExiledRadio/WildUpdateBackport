# Bytecode patches

The mod's own coremod, `com.exiledradio.wildupdatebackport.asm.WildUpdateCore` (sorting index 1001, so every class
arrives with SRG member names in a real instance and MCP names in dev; each patch matches both). One transformer per
patch group, no MixinBooter, nothing loaded from inside a transformer. A target of another shape than expected (another
coremod got there first and changed it) is left alone and logged, so vanilla's behavior stays. Every patch below only
puts a static call between a vanilla value and its use: no jump, no new local, no new frame, so it coexists with
Fluidlogged API's transformers and any other that leaves those values in place. The one exception, P5, adds three
methods to a class that has none of them (if another coremod already added one, P5 is skipped); each only calls the
superclass and a hook, with no jump either.

| # | Stream | Target (MCP / SRG) | Change | Why no event does it |
| --- | --- | --- | --- | --- |
| P1 | Lf | `EntityRenderer.hurtCameraEffect` / `func_78482_e` `(F)V` | its one `14.0F` (the hurt tilt, degrees) becomes `LookHooks.damageTilt(14.0F)`: times Damage Tilt (1.19.4's `GameRenderer.bobHurt`) | The tilt is a GL rotation applied before `EntityViewRenderEvent.CameraSetup` fires; nothing can scale it from there. |
| P2 | Lf | `RenderItem.renderEffect` / `func_191966_a` `(IBakedModel)V` | each of its two `Minecraft.getSystemTime()` results passes through `LookHooks.glintTime` (times Glint Speed) and each of its two `-8372020` colors through `LookHooks.glintColor` (RGB times Glint Strength, alpha kept) | No event around the item glint. 1.19.4's `setupGlintTexturing` runs its clock at `millis * glintSpeed * 8` (1.19.3: `millis * 8`) and its shader multiplies the glint by `GlintAlpha`. |
| P3 | Lf | `LayerArmorBase.renderEnchantedGlint` / `func_188364_a` | its clock `ticksExisted + partialTicks` passes through `LookHooks.glintTicks` before it is stored, and the color constants `0.38F, 0.19F, 0.608F` through `LookHooks.glintComponent` | As P2, for armor and elytra (`LayerElytra` calls the same method). |
| P4 | C | `EntityMinecart.killMinecart` / `func_94095_a`; the same method of `EntityMinecartChest`, `EntityMinecartHopper`, `EntityMinecartFurnace`, `EntityMinecartTNT` | `EntityMinecart`'s `Items.MINECART` passes through `ChangesHooks.cartItem(item, this)` (the cart's own item for the four vanilla block carts); the chest and hopper carts' `Item.getItemFromBlock(...)` through `cartBlockItem`, the furnace and TNT carts' `Blocks.FURNACE`/`Blocks.TNT` through `cartBlock` (air: nothing drops) | 1.19's `AbstractMinecart.destroy` drops `getDropItem()`. 1.12.2 drops a minecart and then the block, inside `killMinecart`; no event sees a minecart's drops. |
| P5 | C | `BlockJukebox` | adds `canProvidePower` / `func_149744_f`, `getWeakPower` / `func_180656_a` and Forge's `isSideSolid`, each `return hook(super.x(...))`: a signal source while the change is on, 15 while a disc plays (`Jukeboxes`), still passing on the power a 1.12.2 conductor would, sides still solid | 1.19.4's `JukeboxBlock.isSignalSource`/`getSignal`. Redstone asks the block itself; nothing in Forge lets a mod answer for a vanilla block. |
| P6 | C | `ItemPotion.hasEffect` / `func_77636_d` (client) | each returned value passes through `ChangesHooks.potionGlint(value, stack)`: only an enchanted potion shines | 1.19.4 removed `PotionItem.isFoil`. The glint is the item's own answer; no event asks. |
| P7 | C | `BlockShulkerBox.getBlockFaceShape` / `func_193383_a` | each returned shape passes through `ChangesHooks.shulkerFace(shape, world, state, pos, face)`: while not closed, only the face opposite the lid is `SOLID` | 1.19.3's `ShulkerBoxBlock.getBlockSupportShape` (a one-pixel plate on the base while open). The face shape is the block's own answer. The neighbor updates that make attached blocks check it are an event-side tick (`ShulkerBoxes`). |

P1 to P3 are client classes: a dedicated server never loads them. The self-test (`-Pselftest=lookfeel`) runs each
patch on the dev jar's MCP-named class and on Forge's SRG-named jar from the Gradle cache, counts the hook calls and
runs ASM's stack verifier on every method.

P4, P5 and P7 run on both sides; P6's method is client-only, so on a dedicated server it is already stripped and nothing is
patched. The self-test (`-Pselftest=changes`) runs P4 to P7 on the dev jar's MCP-named classes and on Forge's SRG-named
jar, counts the hook calls, runs ASM's stack verifier on every method, and checks which patches the running server
applied. P5 names its new methods as the class names its own (MCP in dev, SRG in a real instance). Stream C's other
items need no patch: the door tops are 1.19's models (a client state mapper), the Nether's light limit is
`LivingSpawnEvent.CheckSpawn`.
