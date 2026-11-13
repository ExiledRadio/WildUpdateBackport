# Requests for Backport Core

What Wild Update Backport needs from Backport Core (`E:\MincraftModding\Workspaces\BackportCore`). This
repo never edits Core; each request names the API wanted, and where this mod carries a local shim until
Core has it. When Core ships one, the shim's package is swapped for Core's and the shim is deleted.

## 1. Game events and vibrations (`gameevent`)

**Why in Core**: Wild Update's sculk sensor, shrieker, warden and allay listen to vibrations; Trails &
Tales' calibrated sensor (1.20) and Tricky Trials' (1.21) use the same events and frequencies; any
backport's new blocks (Trails & Tales' decorated pots, Tricky Trials' copper bulbs and crafters) should
*emit* them so sensors hear them. One dispatcher per world, or sensors from two mods hear different worlds.

**Local shim**: `com.exiledradio.wildupdatebackport.gameevent` (stream G), written to exactly this API.

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

Until then, stream DD does not start.

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
