# Requests for Backport Core

What Wild Update Backport needs from Backport Core (`E:\MincraftModding\Workspaces\BackportCore`). This
repo never edits Core; each request names the API wanted, and where this mod carries a local shim until
Core has it. When Core ships one, the shim's package is swapped for Core's and the shim is deleted.

## 1. Game events and vibrations (`gameevent`)

**Why in Core**: Wild Update's sculk sensor, shrieker, warden and allay listen to vibrations; Trails &
Tales' calibrated sensor (1.20) and Tricky Trials' (1.21) use the same events and frequencies; any
backport's new blocks (Trails & Tales' decorated pots, Tricky Trials' copper bulbs and crafters) should
*emit* them so sensors hear them. One dispatcher per world, or sensors from two mods hear different worlds.

**Done** in Backport Core 1.4.0 (`gameevent`, branch `core-game-events`); this mod uses it from
`wu-core-game-events` and its own package is gone. Core added 1.20.6's rules beside these (`VibrationSystem`,
`GameEventListener.Rules`, `GameEvents.require(version, switch)`), see Core's `docs/API.md`.

**Local shim** (until then): `com.exiledradio.wildupdatebackport.gameevent` (stream G), written to exactly this API.

```java
package com.exiledradio.backportcore.gameevent;

/** One of 1.19's game events (and later ones), by name: "minecraft:step", "minecraft:block_place", ... */
public final class GameEvent {
    public static GameEvent register(ResourceLocation name, int radius, int frequency);  // frequency 1-15 (current Java), 0 = none
    public static GameEvent get(ResourceLocation name);                                    // null if unknown
    public ResourceLocation name();
    public int radius();      // notification radius (16; shriek 32; jukebox 10)
    public int frequency();   // vibration frequency, for comparators and calibrated sensors
    // The vanilla set, as constants: STEP, SWIM, FLAP, ..., SHRIEK, SCULK_SENSOR_TENDRILS_CLICKING, ...
}

/** Where the event came from: the entity that caused it and the block state involved, either may be null. */
public final class GameEventContext {
    public static GameEventContext of(@Nullable Entity source, @Nullable IBlockState state);
}

public final class GameEvents {
    /** Emit an event at a position. Server side; a no-op on the client. */
    public static void emit(World world, GameEvent event, Vec3d pos, GameEventContext context);
    public static void emit(World world, GameEvent event, BlockPos pos, GameEventContext context);

    /** Listeners: registered while loaded, removed on unload (tile entities in onLoad/invalidate, entities on join/leave). */
    public static void add(World world, GameEventListener listener);
    public static void remove(World world, GameEventListener listener);
    public static void move(World world, GameEventListener listener);  // entity listeners, when they change chunk section
}

public interface GameEventListener {
    PositionSource position();            // BlockPositionSource or EntityPositionSource
    int radius();
    /** @return true if this listener took the event (stops the closest-first search for vibration listeners). */
    boolean handle(WorldServer world, GameEvent event, Vec3d origin, GameEventContext context);
    /** Movement events the listener wants polled for living entities in its radius (step, swim, splash, flap, elytra_glide). */
    default boolean pollsMovement() { return false; }
}

/** 1.19's vibration rules: travel time, wool occlusion and dampening, sneaking, closest-first, the particle. */
public final class VibrationListener implements GameEventListener {
    public interface Config {
        boolean shouldListen(WorldServer world, GameEvent event, GameEventContext context);
        void onSignalReceive(WorldServer world, VibrationListener listener, GameEvent event,
                             @Nullable Entity source, @Nullable Entity projectileOwner, float distance);
        default boolean canTriggerAvoidVibration() { return false; }   // the "Sneak 100" advancement
    }
    public VibrationListener(PositionSource position, int radius, Config config);
    public void tick(World world);                                  // the owner's tick
    public NBTTagCompound writeToNBT(); public void readFromNBT(NBTTagCompound tag);  // a vibration in flight survives saving
}
```

- **Emission without patches**: Core subscribes to Forge's events (block place/break, neighbor notify,
  interact, item toss/pickup, use-item finish, projectile impact, explosion, damage, death, fall, bucket
  fill, lightning, ender teleport) and emits the matching game events; movement events are polled by the
  listeners that want them (`pollsMovement`). Stream G's doc lists the full mapping and its gaps.
- Tags as fixed sets in code (1.12.2 has no tags): `occludes_vibration_signals` (wool, and carpets as
  dampeners), `ignore_vibrations_sneaking`, `warden_can_listen`, `allay_can_listen`, `vibrations`.
- Vibration particle: one packet on Core's channel, a client particle from origin to listener.
- Advancement trigger `minecraft:avoid_vibration` (as a 1.12.2 `ICriterionTrigger` under Core's name).

## 2. Structure template loader (ancient cities)

**Why**: ancient cities are 1.19 jigsaw structures (about 50 templates, 1.19 NBT with block-state
palettes: `minecraft:sculk_shrieker[can_summon=true]`, `minecraft:deepslate_tiles`, ...). Update Aquatic's
ocean structures and Nether Update's bastions need the same loader; the brief already names it.

```java
/** A 1.13+ structure NBT read into 1.12.2 states, with a palette remapper for blocks other mods own. */
public final class ModernTemplate {
    public static ModernTemplate load(ResourceLocation id);   // assets/<ns>/structures/<path>.nbt, gzip NBT, DataVersion 1.13+
    public BlockPos size();
    public void place(World world, BlockPos origin, Rotation rotation, Mirror mirror, Random random, PlacementFilter filter);
    public List<JigsawConnector> jigsaws();                    // pool, name, target, final_state, joint, orientation
}
public final class ModernPalette {
    /** "minecraft:deepslate_tiles" -> the owner's block (Caves & Cliffs' or a copy), with property renames. */
    public static void map(String modernName, Function<Map<String, String>, IBlockState> to);
}
public final class JigsawAssembler {   // 1.16+'s pools and jigsaw expansion, depth 7 for ancient cities
    public static List<PlacedPiece> assemble(ResourceLocation startPool, BlockPos start, int maxDepth, Random random, TerrainHeights heights);
}
```

**Status (stream DD, 2026-11-13):** Core 1.1.0 has `ModernTemplate`, `ModernPalette`, `JigsawPools` and a 1.16.5
`Jigsaw`. DD builds on them and adds what ancient cities still need locally (`deepdark/city/`). Those additions are
DD-2 below.

## 3. Multiface blocks (sculk vein, glow lichen, resin clump)

**Why**: sculk vein (1.19), glow lichen (Caves & Cliffs, 1.17) and resin clumps (Tricky Trials, 1.21.4)
are "any of six faces" blocks: 6 booleans, 64 states, and 1.12.2 metadata holds 16. Each mod solving it
alone gives three different save formats for the same idea.

```java
/** A block that sits on any set of its six faces. Faces live in a tile entity; getActualState exposes them. */
public abstract class BlockMultiface extends Block implements ITileEntityProvider {
    public static final PropertyBool NORTH, EAST, SOUTH, WEST, UP, DOWN;
    public static Set<EnumFacing> faces(IBlockAccess world, BlockPos pos);
    public static boolean addFace(World world, BlockPos pos, EnumFacing face);   // placement and spreading
    public static boolean removeFace(World world, BlockPos pos, EnumFacing face);
    protected abstract boolean canAttachTo(IBlockAccess world, BlockPos support, EnumFacing face);
    // collision none, bounding box from faces, drops one per face, ItemBlock that adds a face to an existing one
}
```

**Local shim**: `sculk/BlockSculkVein` with its own tile entity, written so its NBT (`Faces`: a byte, bit
per `EnumFacing.getIndex()`) is the same layout Core's would use.

## 4. Wood types for boats and signs

**Why**: new woods arrive in four updates (mangrove 1.19; cherry and bamboo 1.20; pale oak 1.21.4).
1.12.2's boat type enum indexes a texture array, so no mod can add a wood to vanilla's boat; each needs
its own boat, chest boat and sign entity and renderer. One registry keeps their save format and their
chest-boat entity the same.

```java
public final class WoodTypes {
    public static WoodType register(String modid, String name, Supplier<Item> planks, Supplier<Item> boat,
                                    Supplier<Item> chestBoat, ResourceLocation boatTexture, ResourceLocation chestBoatTexture,
                                    boolean raft);
    public static WoodType get(String id);   // "minecraft:oak" ... "wildupdatebackport:mangrove"
}
// Core then owns one boat entity and one chest-boat entity that store the WoodType id, and their renderers.
```

**Local shim**: stream B's own boat and chest-boat entities, storing the wood as a string id
(`"minecraft:oak"`, `"wildupdatebackport:mangrove"`), so a later move to Core's entity is a remap of the
entity name only.

## 5. Textures and sounds for Core's packs (1.19's retextures of vanilla)

Vanilla things 1.19.x redrew, for Core's `modern` pack (and `classic` for our own copies): every boat
(wood-colored paddles, darker rims), the brewing stand, cocoa stage 2, end portal frame, end rod, mycelium
side, pistons and sticky pistons, smoker, the fence item model, leather cap, shield item model, llama
chests, spiders and cave spiders, evoker/illusioner/witch/villager/zombie villager robes (models: last two
rows), villager and zombie villager biome clothes, the vex (1.19.3), donkey/mule/skeleton horse/zombie
horse (1.19.4), the half armor icon, lighter empty-slot icons (1.19.4), knowledge book. Sounds: the
equip-armor-from-inventory sound. Model changes (robes, vex, shield spin) cannot be done by a texture
pack; they are listed for whoever decides Core's scope.

## 6. Goat horn hook (for Caves & Cliffs, through Core)

Not Core's code, but the place both mods can meet without depending on each other:

```java
/** Caves & Cliffs' goat calls this when a ram hits a horn-dropping block; Wild Update registers the supplier. */
public final class GoatHorns {
    public static void setDropper(BiFunction<Boolean /*screaming*/, Random, ItemStack> dropper);
    public static ItemStack drop(boolean screaming, Random random);   // EMPTY when no mod registered a dropper
}
```

Until it exists: Caves & Cliffs can call `wildupdatebackport:goat_horn` by name with an `instrument` NBT
string (`ponder_goat_horn` ... `dream_goat_horn`), which is the stack format stream I uses.

**From stream I (2026-11-13):** 1.19's `Goat.createHorn` seeds its random with the goat's UUID hash
(`new Random(goat.getUniqueID().hashCode())`, one draw of 4), so one goat always drops the same instrument. The
hook should pass that random, or take the UUID instead:
`public static ItemStack drop(boolean screaming, UUID goat)`. Wild Update's supplier is
`items.GoatHorns.create(boolean screaming, UUID goat)` (and `GoatHorns.drop(EntityLivingBase, boolean)`, which
also throws the item as 1.19's `dropHorn`). The stack is `wildupdatebackport:goat_horn` with the string tag
`instrument` = `minecraft:<name>_goat_horn` (1.19's format; a bare `<name>_goat_horn` reads too).

**Local shim until Caves & Cliffs calls it**: `items.GoatHorns.Events` watches Caves & Cliffs' goats (by
entity name) and adds 1.19's `RamTarget` block hit from outside, by reflection on their goat's
`isLoweringHead`, `hasLeftHorn`, `hasRightHorn`, `setHorns`, `isScreamingGoat`, `setLoweringHead`,
`setRamCooldown` and `nextRamCooldown`. A ram that charges into a `#snaps_goat_horn` block plays the ram
impact, drops a horn and ends with a full cooldown. When Caves & Cliffs' ram does this itself and calls the
hook, the shim goes.

## 7. Keyframe entity animations (from stream F, frogs)

1.19 and later animate their new mobs (frog, warden, allay's dance, camel, sniffer, armadillo, breeze,
creaking...) with keyframe definitions (`AnimationDefinition`, `KeyframeAnimations`, `AnimationState`)
on parts that have a scale and 1.19's x-y-z rotation order. Every backport with such a mob needs the same
small runtime. Stream F has it locally, written to this API, so moving it is a package rename:

```java
/** Client only. 1.19's ModelPart: initial pose, scale, 1.19's rotation order. */
public class Part extends ModelRenderer {
    public float xScale, yScale, zScale;
    public Part pose(float x, float y, float z);                         // PartPose.offset, remembered
    public Part box(int u, int v, float x, float y, float z, int w, int h, int d, float grow);
    public void reset();                                                 // resetPose
}
/** Common. 1.19's AnimationState (start, startIfStopped, stop, updateTime, accumulatedTime). */
public final class AnimationClock { ... }
/** Client only. Definitions read from assets/<mod>/animations/<entity>.json (generated from Mojang's code). */
public final class Keyframes {
    public static Map<String, Definition> load(ResourceLocation file);
    public static void animate(Map<String, Part> parts, AnimationClock clock, Definition def, float ageInTicks, float speed);
}
```

JSON format: `{"<animation>": {"length": s, "looping": b, "channels": [{"bone": name, "target":
"position|rotation|scale", "keyframes": [[time, x, y, z, "linear|catmullrom"], ...]}]}}`, values already in
model units (1.19's `posVec` y-flip, `degreeVec` radians, `scaleVec` minus one). Local copy:
`frog/AnimationClock`, `frog/client/Part`, `frog/client/Keyframes`; generator: `tools/frogs/generate.py`.

Also small and shared: 1.13's `minecraft:filled_bucket` advancement trigger (1.12.2 lacks it; Update Aquatic
and stream F each have their own, `wildupdatebackport:filled_bucket` here), with an `item` predicate.

## 8. Jigsaw pool elements and template entities (allays in Village & Pillage's outposts)

1.19's outposts add a third cage, `feature_cage_with_allays`, to the `pillager_outpost/features` pool, and
its two allays are template entities. Village & Pillage keeps its 1.14 pools and template entities private,
so stream A reaches in by name (`allay/AllayStructures`): it adds the element to the pool by reflection after
`feature_cage2`, ships the converted template where Village & Pillage's loader looks
(`assets/villagerbackport/structures/pillager_outpost/feature_cage_with_allays.nbt`), and spawns the two
allays itself after Village & Pillage builds each chunk. Wanted instead, in Core (Village & Pillage and any
later jigsaw structure using it):

```java
public final class JigsawPools {
    /** Adds an element to a pool after the element named {@code after} (or at the end), with its weight. */
    public static void addSingle(String pool, String location, int weight, @Nullable String after);
    /** Registers who builds a template entity of a kind ("allay" -> our entity), for template entity lists. */
    public static void registerEntityKind(String kind, BiFunction<World, NBTTagCompound, Entity> factory);
}
```

## 9. Biome music (stream Sw, the mangrove swamp)

**Why in Core**: 1.18 gave overworld biomes their own music (Caves & Cliffs plays it for its cave and mountain
regions through its own `MusicRegions`, a fixed enum), and every later backport adds biomes with music: 1.19's swamp
and mangrove swamp (*Aerie*, *Firebugs*, *Labyrinthine*, which this mod already ships for the title screen), 1.19's
deep dark (*Ancestry*), 1.20's cherry grove, bamboo jungle and others. One player, so two mods never play two tracks.

**Wanted**:

```java
package com.exiledradio.backportcore.music;

public final class BiomeMusic {
    /** 1.19's Musics.createGameMusic(event): min delay 12000, max 24000 ticks, does not replace the current track. */
    public static void register(Biome biome, SoundEvent music);
    /** Or by a test, for regions that are not 1.12 biomes (the deep dark under Wild Update's own placement). */
    public static void register(ResourceLocation id, BiPredicate<World, BlockPos> where, SoundEvent music);
}
```

1.19's rules: survival or adventure in the overworld plays the biome's music where it has one, otherwise the default
game music; creative plays the creative music unless the biome has its own.

**Local shim**: none. The mangrove swamp has no music of its own until Core has this (`docs/streams/swamp.md`).

## 10. Modern water and underwater fog colors for a mod's biome (stream Sw)

**Why in Core**: with Update Aquatic's 1.13 water texture in place, it gives every biome it does not know a filtered
default blue and the default underwater fog. Wild Update sets the mangrove swamp's water color itself (the
`GetWaterColor` event, last), but the fog color is Update Aquatic's alone, so the swamp's fog is 1.13's default
`0x050533`, not 1.19's `0x4D7A60`. Every later backport's biomes (the cherry grove, the pale garden) need the same.

**Wanted**: `com.exiledradio.backportcore.water.WaterColors.register(Biome biome, int water, int fog)`, read by Update
Aquatic's `WaterColors.known` before its own fallback.

**Local shim**: `swamp/SwampContent.waterColor` sets the water color; the fog waits for this.

## Lf. Look and feel (stream Lf): 1.19.x pieces only Core can build

Stream Lf registers 1.19, 1.19.1, 1.19.3 and 1.19.4 with Core's `presentation` module (panorama, splashes, menu music,
F3 + F's removal, four options, Auto-Jump's default, vanilla text; `docs/streams/lookfeel.md`). These are the 1.19.x
changes left, each a single slot or a shared screen that one owner must build, so they are asked of Core rather than
built here. Version is the one to register them under; Core applies them once that version is the newest installed.

**Lf-1. Menu track volume.** 1.19.3 plays *Aerie*, *Firebugs* and *Labyrinthine* at volume 0.4 on the title screen
(1.19.3's `sounds.json`, `music.menu`; 1.19 played them at 1.0). `MenuMusicPlayer` plays every added track at 1.0:

```java
public Contribution addMenuMusic(String soundName, int weight, float volume);   // the Sound's volume, as sounds.json's
public Contribution menuMusicVolume(String soundName, float volume);            // a later version changing an earlier track
```

Wanted as `version("1.19.3").menuMusicVolume("wildupdatebackport:music/game/swamp/aerie", 0.4F)` and so on. Until
then the three play at 1.0, as in 1.19 to 1.19.2.

**Lf-2. Options Core's screens own.**
- *Notification Time* (1.19.4, Accessibility, 0.5x to 10x, default 1.0x): how long toasts stay (every `IToast`'s
  5000 ms and the advancement/recipe toasts' times) and how long subtitles stay (`GuiSubtitleOverlay`'s 3000 ms).
  A Core slider plus one patch of `GuiToast`'s time base would serve every version's toasts.
- *High Contrast* (1.19.4, Accessibility, default OFF) and its built-in resource pack (menus' widgets with stronger
  outlines): Core's texture packs already own the GUI sprites.
- *Smooth Lighting* (1.19.3-rc1): Minimum removed, Maximum shown as ON. 1.12.2's `GameSettings.Options.AMBIENT_OCCLUSION`
  cycles 0/1/2; from 1.19.3 it should cycle OFF/ON (stored as 0/2), and an instance at 1 move to 2 once.
- *Directional Audio* (1.19, Sounds, default OFF): HRTF through OpenAL Soft's `ALC_SOFT_HRTF`. Needs the sound system's
  context re-created with attributes, which only one owner may do; may be out of reach on LWJGL 2.
- *Operator Items Tab* (1.19.3, Controls, default OFF) with the 1.19.3 creative tab overhaul: see Lf-3.
- *Key Binds* (1.19.4): a tooltip on a conflicting bind naming the binds it conflicts with.
- *Credits and Attribution* (1.19.4-pre4): a button on the Options screen; *Realms News & Invites* keeps its place.
- Option buttons whose text is too long scroll it (23w06a).

**Lf-3. Creative inventory, 1.19.3.** The overhaul: tabs and order as 1.19.3 (blocks by material, items in more than
one tab, recolored and narrower tab icons, the "Operator Utilities" tab for command blocks, barrier, structure block,
jigsaw, debug stick when the option is on and the player may use them), search grouped by tab. `creativeOrder` sorts
inside 1.12.2's tabs only; this needs Core to own the tab list. The tab names 1.19.3 gave ("Redstone Blocks",
"Tools & Utilities") are left out of our vanilla text until the tabs hold what those names say.

**Lf-4. Screens, 1.19.x.**
- *Create New World* (1.19.4, 23w05a): three tabs, Game / World / More, Ctrl+Tab and Ctrl+number switching, the save
  folder tooltip; "Import Settings" and "Export Settings" gone.
- *Welcome to Minecraft* onboarding screen on first launch (1.19.4, 23w03a): narrator, accessibility settings,
  language; arrow-key navigation of every menu.
- *Pause menu* (1.19.3): the "Game Menu" heading moved to the top.
- *Open to LAN* (1.19.3): a port field; game mode and cheats start from the world's.
- *Subtitles* (1.19.3-rc1): drawn with a shadow.
- *Tooltips* (1.19.3): a focused button's tooltip above or below it, winning over a hover tooltip; hover tooltips
  next to the cursor.
- *F3* (1.19): GPU utilization in the left column (needs GL timer queries).
- *Default skins* (1.19.3): the seven new default skins for players without one.

## DD. Deep dark and ancient cities (stream DD, 2026-11-13)

**DD-1. The noise biome at a quart, for structure biome checks.** 1.19's `Structure.isValidBiome` reads
`biomeSource.getNoiseBiome(quart)`. `ModernGeneration.biomeId(x, y, z)` gives the zoomed (fuzzed) biome a block
reports, which near a biome's edge is a neighbor's. Every 1.18+ structure that checks the biome at its start needs the
noise biome: trail ruins and trial chambers too.

```java
public interface ModernGeneration {
    /** The 1.18+ noise biome at a quart position (BiomeSource.getNoiseBiome), today's id. */
    String noiseBiomeId(int quartX, int quartY, int quartZ);
}
```

Local shim: `deepdark.DeepDark.noiseBiome` reaches Caves & Cliffs' `ModernGenerator.biomes3d.source.biome(qx, qy, qz)`
by reflection, and falls back to `biomeId` with a warning.

**DD-2. 1.18+ jigsaw assembly in `Jigsaw`.** Ancient cities (and trail ruins, trial chambers) need these from 1.19:

- `startJigsawName(String)`: the start piece moved so that jigsaw lands on the origin.
- `maxDistanceFromCenter(int)`: in place of the fixed 80.
- The generation stub: the start box's middle at the anchor's height, where the biome is checked, without assembling
  the rest.
- 1.19's `Util.shuffle` for pools (n - 1 draws, like `Collections.shuffle`). `Pool.shuffled` is fastutil's n draws,
  which is right for 1.16-1.18 only, so the shuffle has to be chosen by version.

Processors:

- `block_rot`'s `rottable_blocks` tag (1.19), with a way to register block tags (`ModernProcessors.tag` exists);
  without it, 1.1.0 rots every block, chests included;
- `protected_blocks` (`ProtectedBlockProcessor`, with `#features_cannot_replace`).

`JigsawPiece.setPivot` is package-private, and `JigsawElement.place` is not public.

Local shim: `deepdark/city/CityLayout` (the assembler), `CityPiece` and `CityProcessors`. They are tested piece for
piece against vanilla 1.19.2 (`tools/deepdark/oracle.sh`).

**DD-3. Terrain adaptation and decoration in 1.18 terrain (Caves & Cliffs, through Core).**

- `beard_box`: **done in Caves & Cliffs' branch `cc-beardbox`** (2026-11-13): `StructureBeards` maps
  `TerrainAdaptation.Kind.BEARD_BOX` to a new `Beardifier.BEARD_BOX`, 1.19.2's formula and kernel, checked against
  vanilla's Beardifier (`tools/terrain/beardbox119.sh`, 0 differences). DD registers the city with
  `TerrainAdaptation` and its pieces are `AdaptingPiece`s (junctions included). Without that branch, DD carves the same
  beard into the blocks itself (`CityBeard`).
- The deep dark's `sculk_vein` and `sculk_patch_deep_dark` at their place in 1.19's feature order, with their
  per-feature seed. Caves & Cliffs' decorator lists them and skips them. Wanted: a way for a backport to supply a
  placed feature's placer to the generator's decorator by name (with the world and the feature's random). Until then
  DD places them from an `IWorldGenerator` after the generator's decoration.
- `MapGenStructure.generateStructure` clips each chunk to y 1-255. `ScatteredStructure`'s driver could pass the world's
  full height (DD widens it in its own `StructureStart`).

**DD-5. Ask whether the terrain does a terrain adaptation.** `TerrainAdaptation` has no way to ask whether a world's
generator carves a given `Kind` (an older Caves & Cliffs logs `BEARD_BOX` as unsupported and skips it). DD checks by
reflection for `Beardifier.BEARD_BOX` in Caves & Cliffs' terrain core (`DeepDark.terrainCarvesCities`). Wanted:
`TerrainAdaptation.supported(World, Kind)`, answered by the world's generator.

**DD-4. Biome music: Ancestry.** 1.19's deep dark plays `music.overworld.deep_dark` (*Ancestry*). This belongs with
the shared biome music player stream Sw asks for (request 9). It would need to work for 1.18 terrain's 3D biome, and for a
1.12.2 world's stand-in region, which only the server knows.

## Wl. Waterlogging: water kept in full-block leaves (stream Wl, 2026-11-13)

Fluidlogged API's built-in list (`whitelist.jsonc`) gives every `BlockLeaves` subclass `"canFluidFlow": true`: water
kept in leaves flows out of every side. That list's flow override is asked before a block's own
`IFluidloggable.canFluidFlow`, so Core's `FluidloggedWaterloggable` (which answers by face shape, the right answer for
a full cube) is never asked for our `mangrove_leaves`. In 1.19.2 waterlogged leaves keep their water: a fluid passes
between two blocks only where their collision faces do not both fill the side (`FlowingFluid.canPassThroughWall`), and
leaves are a full cube. Mangrove roots, which extend no listed class, already keep it.

Asked: as `WaterRules` already puts 1.13+'s rules over Fluidlogged API's list for *which* fluids a `Waterloggable`
block takes, put them over its list for *where the water flows* too:

```java
public interface Waterloggable {
    /** @return whether water kept here may flow out through this side; by default, unless the side is a full face */
    default boolean waterFlowsOut(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) { ... }
}
```

with Core clearing Fluidlogged API's per-state `ICanFluidFlowHandler` override for `Waterloggable` states after
Fluidlogged API (re)loads its configs, or answering from the adapter first. Until then our leaves follow Fluidlogged
API's rule (the self-test logs it), which is also what it already does for vanilla leaves.

