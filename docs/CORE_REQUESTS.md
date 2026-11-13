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
