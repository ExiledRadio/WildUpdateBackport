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
| P6 | C | `ItemPotion.hasEffect` / `func_77636_d` (client) | `ChangesHooks.potionBegin(stack)` stashes the stack at the first line, and each returned value passes through `ChangesHooks.potionGlint(value)`: only an enchanted potion shines | 1.19.4 removed `PotionItem.isFoil`. The glint is the item's own answer; no event asks. |
| P7 | C | `BlockShulkerBox.getBlockFaceShape` / `func_193383_a` | `ChangesHooks.shulkerBegin(world, state, pos, face)` stashes the arguments at the first line, and each returned shape passes through `ChangesHooks.shulkerFace(shape)`: while not closed, only the face opposite the lid is `SOLID` | 1.19.3's `ShulkerBoxBlock.getBlockSupportShape` (a one-pixel plate on the base while open). The face shape is the block's own answer. The neighbor updates that make attached blocks check it are an event-side tick (`ShulkerBoxes`). |
| P9 | Sw | `GenLayerShore.getInts` / `func_75904_a` `(IIII)[I` | the biome id `k` loaded for its one swamp comparison (`k != Biome.getIdForBiome(Biomes.SWAMPLAND)`, the biomes that meet the ocean with no beach) passes through `SwampHooks.shoreBiome`, which answers the swamp's id for the mangrove swamp; the cell itself keeps `k` | The beach rule is inside the layer's loop over cells; `WorldTypeEvent.InitBiomeGens` can only replace the whole layer chain. Numbered P9 because stream C has P4-P8 planned. |
| P10 | W | OptiFine's `net.optifine.shaders.Shaders` (OptiFine 1.12.2 HD U G5; nothing without OptiFine) | adds 1.19 OptiFine's `uniform_darknessFactor` / `uniform_darknessLightFactor` (`ShaderUniform1f`, made in its static init right after `blindness`'s) and two floats `darknessFactor` / `darknessLightFactor`; `beginRender`'s first lines set the floats from `DarknessShaders.factor` / `lightFactor` (1.19.2's `getDarknessGamma * darknessEffectScale` and `calculateDarknessScale * darknessEffectScale`, once a frame); `useProgram` sets both uniforms after `blindness` | A shader pack replaces lighting and fog, so the lightmap and fog Darkness changes never show. G5 has no Darkness uniforms (log: "Model variable not found: darknessFactor"); packs written for 1.19+ (Complementary) read them unguarded. |
| P11 | W | OptiFine's `net.optifine.shaders.uniform.ShaderParameterFloat` | two enum constants, `DARKNESS_FACTOR` and `DARKNESS_LIGHT_FACTOR`, made as `BLINDNESS` is (on P10's uniforms, so `eval` reads their value), appended to `$VALUES` at the end of the static init; applied only once P10 is | `shaders.properties` custom uniforms (Complementary's `maxBlindnessDarkness = max(blindness, darknessFactor)`) resolve variables from this enum's values. |
| P12 | C | `Explosion.doExplosionB` / `func_77279_a` `(Z)V` | the drop chance `1.0F / this.size` passes through `RulesHooks.dropChance(chance, this)`: 1.0 when the explosion's decay rule is false (`tntExplosionDropDecay` for primed TNT and TNT minecarts, `blockExplosionDropDecay` for no exploder or an end crystal, `mobExplosionDropDecay` otherwise) | 1.19.3's `Explosion.finalizeExplosion` reads the rule for its `ExplosionInteraction`. `ExplosionEvent.Detonate` sees the blocks, not the chance each drops with. |
| P13 | C | `WorldServer.updateBlocks` / `func_147456_g` | its one `canSnowAt(pos, true)` becomes `RulesHooks.snowAt(world, pos, true)`: with `snowAccumulationHeight` above 1, a snow layer under that height (at most 8) gains one and the call answers false; 0, no snow; 1, 1.12.2's answer | 1.19.3's `ServerLevel.tickChunk`. Precipitation is inside the random block tick loop. |
| P14 | C | `World.playBroadcastSound` / `func_175669_a` | each listener's `broadcastSound` becomes `RulesHooks.broadcastSound(listener, id, pos, data, this)`: `playEvent` (heard near the place only) when `globalSoundEvents` is false | 1.19.3's `ServerLevel.globalLevelEvent` (the wither's spawn, the dragon's death, the end portal). No event sees world events. |
| P15 | C | `BlockVine.updateTick` / `func_180650_b` | its `worldIn.rand.nextInt(4)` passes through `RulesHooks.vineRoll(roll, world)`: 1 (never spreads) when `doVinesSpread` is false | 1.19.4's `VineBlock.randomTick`. Vines have no growth event. |
| P16 | C | `CommandFill.execute` and `CommandClone.execute` / `func_184881_a` | both `32768`s (the test and the message) become `RulesHooks.blockLimit()` (it finds the server itself; the `server` parameter is dead there in Mojang's frames): `commandModificationBlockLimit` | 1.19.4's rule; the limit is a constant inside the commands. |
| P17 | C | `MobSpawnerBaseLogic` | the field initializer's `new WeightedSpawnerEntity()` passes through `RulesHooks.newSpawnData` (an empty one), `isActivated` / `func_98279_f`'s result through `spawnerActive` (false with no mob), and (client) `getCachedEntity` / `func_184994_d`'s `AnvilChunkLoader.readWorldEntity` becomes `spawnerEntity` (none with no mob, so no warning every frame) | 1.19.3's `BaseSpawner` starts with an empty `SpawnData`. A spawner's default mob is set in its own constructor. |
| P18 | C | `ItemArmorStand.onItemUse` / `func_180614_a`; `EntityArmorStand.dropBlock` / `func_175421_A` | the item's `ItemMonsterPlacer.applyItemEntityDataToEntity` becomes `RulesHooks.armorStandData` (the same, then the item's name onto the stand); the dropped `new ItemStack(Items.ARMOR_STAND)` passes through `armorStandItem` (the stand's name onto the item) | 1.19.4 (23w03a). The stand is placed and dropped inside these methods; no event sees either. |
| P19 | DD | `WorldGenLiquids.generate` / `func_180709_b` | its first `worldIn.getBlockState(position.up())` (the stone a spring needs over it) becomes `CityLiquids.springBlock(worldIn, position.up())`: air where the spring's water or lava could reach an ancient city's space (its pieces and beard, 12 blocks, plus 8 for the run), so no spring there | 26.3's deep dark has no springs. 1.12.2's springs (and RTG's falls, which use the same class) are drawn inside `BiomeDecorator`; `DecorateBiomeEvent.Decorate` only turns all of a chunk's springs on or off, before their positions are drawn. |
| P20 | DD | `WorldGenLakes.generate` / `func_180709_b` | the height in `if (position.getY() <= 4) return false`, once the lake has gone down through the air, passes through `CityLiquids.lakeHeight(y, position, worldIn)`: 0 where its 16 x 8 x 16 could reach a city's space, so no lake there | 26.3's deep dark has no lakes. `PopulateChunkEvent.Populate` (LAKE, LAVA) comes before the lake's position is drawn. |

P1 to P3 are client classes: a dedicated server never loads them. P9 runs on both sides wherever 1.12.2's biome layers are made; the self-test (`-Pselftest=swamp`) patches the dev and SRG classes, checks the hook call and the stack, and runs the patched layer. The self-test (`-Pselftest=lookfeel`) runs each
patch on the dev jar's MCP-named class and on Forge's SRG-named jar from the Gradle cache, counts the hook calls and
runs ASM's stack verifier on every method.

P4, P5 and P7 run on both sides; P6's method is client-only, so on a dedicated server it is already stripped and nothing is
patched. The self-test (`-Pselftest=changes`) runs P4 to P7 on the dev jar's MCP-named classes and on Forge's SRG-named
jar, counts the hook calls, runs ASM's stack verifier on every method, and checks which patches the running server
applied. P5 names its new methods as the class names its own (MCP in dev, SRG in a real instance). Stream C's other
items need no patch: the door tops are 1.19's models (a client state mapper), the Nether's light limit is
`LivingSpawnEvent.CheckSpawn`.

P10 and P11 match each site strictly (exactly one hit) and leave a class of another shape alone with a log line;
inserted code has no jump and no new local, and only `beginRender`'s head reads a parameter (before any frame).
`tools/darkness/check.sh <OptiFine jar>` runs both against an OptiFine jar: sites, ASM's stack verifier and a
ProGuard-aware frame check on every method, patched and unpatched. The client test (`runClient -Pclienttest=darkness`,
or a file `selftest-darkness` in a test instance's game directory) checks and photographs Darkness with and without
a shader pack.

P12 to P18 (`asm.RulesTransformer`) run on the server (P17's `getCachedEntity` site on the client only). The self-test
(`-Pselftest=rules`) runs them on the dev jar's MCP-named classes and on Forge's SRG-named jar, counts the hook calls, runs
ASM's stack verifier on every method plus the JVM's own verifier on the SRG classes (they keep Mojang's ProGuard frames,
which ASM ignores), checks which classes the running server patched, and plays each rule and change.
`waterSourceConversion` and `lavaSourceConversion` need no patch (`BlockEvent.CreateFluidSourceEvent`), nor do the
chorus and scaffolding spawns (`LivingSpawnEvent.CheckSpawn`), leaves (Fluidlogged API's `FluidloggableEvent`) or the
bonus chest (`LootTableLoadEvent`).

P19 and P20 (`asm.DeepDarkTransformer`) run on the server wherever 1.12.2's springs and lakes generate. Neither
loads a local vanilla stops reading: P19 replaces a call with one of the same stack shape, P20 loads `position` and
`worldIn`, which vanilla reads right after. The self-test (`-Pselftest=deepdark`) patches the dev and SRG classes,
counts the hook calls, runs ASM's stack check and the JVM's verifier on the SRG classes, checks the running game
patched both, and asks both hooks near a city and far from one.

## Release check: Mojang's frames

Mojang's classes that Forge does not patch (here `CommandClone`, `ItemArmorStand`, `BlockJukebox`, `ItemPotion`,
`BlockShulkerBox`, two minecarts and `GenLayerShore`) keep ProGuard's stack-map frames, which mark a local TOP once
vanilla stops reading it, even a parameter. A patch that loads such a local further down is a `VerifyError` in the
real game only, when the class loads: dev runs recompile Minecraft with javac, which keeps every parameter in every
frame. The first P16 did that (`ALOAD 1`, the server, in `CommandClone.execute`) and stopped every dedicated server at
start. So:

- an inserted load reads only `this` or a value vanilla still reads after that point; a hook finds what else it
  needs itself (P16 asks Forge for the server);
- the `changes` and `rules` self-tests have the JVM verify each patched SRG class, and check that it refuses the first
  P16's dead load;
- before a release, `tools/release/boot-server.sh <server dir> <log> <Backport Core jar> build/libs/<this mod>.jar`
  boots the release jars on a plain dedicated Forge server. With `-Dwildupdatebackport.loadPatched=true` the mod
  (`PatchCheck`) loads and verifies every class it patches once the server has started, and the script fails without
  its `PATCHCHECK ok` line. The family's frame verifier (`family-notes/tools/verify/ScanAll.java`) checks both sides,
  client classes included, against the real client jar.
