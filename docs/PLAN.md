# Wild Update Backport: plan

Everything Java Edition 1.19 (The Wild Update) and its drops 1.19.1 to 1.19.4 added or changed outside
experiments, for 1.12.2 Forge, grouped into streams. One session builds them one at a time (the family's
machine limits), each on its own branch and worktree, merged into `main` by this repo's integrator.

Sources: minecraft.wiki's *Java Edition 1.19*, *1.19.1*, *1.19.3* and *1.19.4* pages and the pages they
link. Current Java behavior wins where it differs from 1.19 (marked **now:**). Numbers the wiki leaves out
come from 1.19.2's own code, decompiled with Mojang's mappings into `G:\MCDownloads\wu-1.19.2-src`
(reference only: vanilla code is read for numbers and rules, never pasted). Assets come from the 1.19.2
client jar (`G:\MCDownloads\wu-1.19.2\client.jar`) and the 1.19 asset index, through `tools/assets.py`.
Numbers in this plan are first reads; each stream checks them against 1.19.2's code and the current wiki
and records the values it used in its `docs/streams/<stream>.md`.

## Scope at a glance

| In | Out (and why) |
| --- | --- |
| Every new block, item, mob, effect, enchantment and biome of 1.19, the 1.19.x drops' visible changes, the game-event system the sculk needs | Chat signing and reporting, `/place`, `/locate poi`, data-driven world presets, the `filter` pack section, debug-screen and options changes: technical, and 1.12.2 cannot take them |
| Deep dark and ancient cities, mangrove swamps: placed as vanilla places them (see *Decided*) | The four 2x2 paintings (earth, wind, fire, water): not placeable in survival in 1.19 either, and 1.12.2's painting enum and atlas would need replacing |
| 1.19.3's and 1.19.4's non-experimental changes (golem spawn eggs, creepers and fire charges, jukeboxes and hoppers, armor swapping, foal stats) | Everything behind 1.19.3/1.19.4's *Update 1.20* toggle: Trails & Tales' (bamboo wood, hanging signs, chiseled bookshelves, camels, sniffers, cherry, trims) |
| Small changes that need bytecode patching are **in** (jukebox redstone output, no potion glint, door tops): our own ASM coremod, see *Patches* | The HUD armor-bar and other vanilla retextures: Core's texture packs (`CORE_REQUESTS.md`) |

## Who owns what (family Rule 1)

**Ours** (first shipped outside experiments in 1.19.x): sculk, sculk vein, sculk catalyst, sculk shrieker;
the warden and Darkness; the mangrove wood set, roots, muddy roots, propagule; mud, packed mud, mud bricks
and their stairs, slabs and walls; frogs, tadpoles, frogspawn, froglights, bucket of tadpole; the allay;
boats with chests (every wood); the recovery compass, echo shards, disc 5 and its fragments; Swift Sneak;
goat horns; reinforced deepslate; the deep dark and mangrove swamp biomes and ancient cities.

**The sculk sensor**: 1.17 shipped it only as a `/give` block (no generation, no drops, no recipe; the
wiki: *re-added to the Creative inventory* in 1.19). It became content in 1.19 with the rest of the sculk,
and this family's split gives it to Wild Update. Caves & Cliffs should not register one; a note to its
session is in the shared notes folder (`wild-update-for-caves-and-cliffs.md`).

**Owned by others, used here** (Rule 1: theirs when installed; ours otherwise as stated):

| What | Owner | Here |
| --- | --- | --- |
| Goats (they drop goat horns) | Caves & Cliffs (`cavesandcliffsbackport`) | **Copied when it is absent** (family rule: every mod works on its own): an identical goat, built from Caves & Cliffs' own goat code once it exists, registered only without it, so its goats ram blocks and drop our horns as 1.19's do. With Caves & Cliffs installed its goats drop ours (by name, or Core's hook). Stream Gt. |
| Deepslate, cobbled deepslate, deepslate tiles (ancient cities are built of them), pointed dripstone (mud dries into clay under it), rooted dirt (water bottle makes mud), moss carpet (mangrove trees), glow lichen's multiface technique | Caves & Cliffs | Looked up by name (`Owners`). Without Caves & Cliffs, ancient cities build from our own copies of the deepslate set (copy-when-absent, exactly as Update Aquatic's `woodset`); the others are simply skipped. |
| Bee nests in mangrove trees | Buzzy Bees (`buzzybeesbackport`) | By name; none without it. |
| Stripped logs of the six old woods, axe stripping, sign dyeing, the wandering trader, pillager outposts, scaffolding, the composter | Village & Pillage (`villagerbackport`) | Axe stripping and our stripped mangrove: our own handler for our logs only. Trader (mangrove propagule) through Core's `TradeRegistry`. Goat horn loot in V&P's outpost chests by loot-table name. Scaffolding fuel by name. Composting through `Compat.compost`. |
| Lily pads and slimes | vanilla | Frogs walk on lily pads, eat small slimes. |
| The calibrated sculk sensor, vibration frequencies' comparator use beyond the plain sensor | Trails & Tales | The plain sensor follows **now:** (it outputs the frequency to comparators; amethyst resonance needs C&C's amethyst and is Rule 2). The calibrated sensor builds on the game-event API in Core (`CORE_REQUESTS.md`). |

## Streams

Each stream owns one package under `com.exiledradio.wildupdatebackport`, one config category with an
`enabled` switch (in `ModConfig`), one block of packet ids and one block of entity ids. Streams never call
each other's classes; they find each other's content by name with `Owners.ours(...)`, null-safe.
The game-event shim (`gameevent`) is the exception: every stream may call it, because it is the API
Backport Core is asked to take over unchanged.

| # | Stream | Package | Config | Entity ids | Packet ids | Port | Needs |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 0 | Scaffold, plan | `common` | - | - | 0-4 | 25860 | - |
| G | Game events and vibrations | `gameevent` | `vibrations` | none | 5-9 | 25861 | nothing (moves to Core later) |
| M | Mangrove wood | `mangrove` | `mangrove` | none | 10-14 | 25862 | nothing |
| D | Mud | `mud` | `mud` | none | 15-19 | 25863 | nothing |
| B | Boats (mangrove boat, boats with chests) | `boat` | `boats` | 1-4 | 20-24 | 25864 | M for the mangrove wood (by name) |
| S | Sculk | `sculk` | `sculk` | none | 25-29 | 25865 | G |
| F | Frogs | `frog` | `frogs` | 5-9 | 30-34 | 25866 | nothing |
| A | Allay | `allay` | `allay` | 10-14 | 35-39 | 25867 | nothing |
| W | Warden and Darkness | `warden` | `warden` | 15-19 | 40-44 | 25868 | G; S's shrieker calls it by name |
| I | Items: recovery compass, echo shards, disc 5, Swift Sneak, goat horns | `items` | `items` | none | 45-49 | 25869 | nothing |
| C | Other 1.19.x changes | `changes` | `changes` | none | 50-54 | 25870 | nothing |
| Sw | Mangrove swamp biome and its generation | `swamp` | `swamp` | none | 55-59 | 25871 | M, D, F |
| DD | Deep dark, ancient cities, reinforced deepslate | `deepdark` | `deepDark` | 20-24 | 60-64 | 25872 | S, W, I; local template loader (Core request 2) |
| Gt | Goats, copied when Caves & Cliffs is absent | `goat` | `goats` | 25-29 | 70-74 | 25874 | Caves & Cliffs' goat code to copy |
| Wl | Waterlogging pass | (Core's adapter) | - | - | 65-69 | 25873 | Core's `Waterloggable` adapter |
| Lf | Look and feel (1.19.x's panorama, splashes, menu music, options, F3, defaults) | `lookfeel` | none (Backport Core's `presentation` settings) | none | 75-79 (none used) | 25875 | Backport Core 1.1.0; the `asm` coremod (P1-P3) |

Order: M, D, B (done), then G, S, F, A, W, I, C (blocks, mobs and items first), then Sw and DD (with the
local template loader), Gt once Caves & Cliffs' goats exist to copy, then Wl once Core has the adapter.

### G. Game events and vibrations

1.19's `GameEvent` system, the one thing every sculk block, the warden and (in Trails & Tales) the
calibrated sensor stand on. Built here as a small local package with exactly the API asked of Backport
Core (`CORE_REQUESTS.md`, request 1), so moving it is a package rename.

- **Events**: 1.19.2's 46 game events with their notification radius (16, `shriek` 32,
  `jukebox_play` 10), plus the **now:** vibration frequency of each (1-15) for comparators and the
  calibrated sensor.
- **Emitting without patches**: 1.12.2 has no game-event calls, so the shim emits from Forge events: block
  place/break/multi-place, neighbor notify (doors, trapdoors, gates, buttons, levers, pistons by state
  change), right-click on blocks and entities (open/close containers, note blocks, item use), item toss
  and pickup, eat/drink (`LivingEntityUseItemEvent.Finish`), projectile shoot and land, explosions, entity
  damage and death, fall (`LivingFallEvent`, `hit_ground`), lightning, buckets (`FillBucketEvent`), shears,
  equipping, mounting, teleports (ender pearls, `EnderTeleportEvent`). **Movement** (`step`, `swim`,
  `splash`, `flap`, `elytra_glide`) has no event: each listener polls the living entities in its radius
  every tick it is listening and emits for those that moved, sneaking ones excepted, the way vanilla's
  `Entity.move` does (step distance, in water, on wool).
- **Listeners**: registered per chunk section, as 1.19's `EuclideanGameEventDispatcher`; a listener has a
  position source (block or entity), a radius and a handler. Vibration listeners (sensor, shrieker,
  warden, allay) add 1.19's rules: travel time = distance ticks, occlusion by wool
  (`#occludes_vibration_signals`: wool and carpets), dampening by wool (`#dampens_vibrations`), the
  closest of several vibrations in one tick wins, a vibration from a sneaking entity is ignored except
  `hit_ground`... exactly the rules in `VibrationListener` and its config.
- **The vibration particle**: a packet (id 5) and a client particle travelling from source to listener.
- **Advancement trigger** `avoid_vibration` (*Sneak 100*: sneak near a sculk sensor or warden).

### M. Mangrove wood

- **Mangrove log, wood, stripped log, stripped wood, planks**, stairs, slab (double slab), fence, fence
  gate, door, trapdoor, button, pressure plate, **sign** (standing and wall; tile entity extending
  `TileEntitySign`, our renderer, so Village & Pillage's sign dyeing finds it), all with 1.19's hardness,
  flammability, fuel values, recipes, ore dictionary (`logWood`, `plankWood`, `slabWood`, `stairWood`,
  `fenceWood`, `fenceGateWood`, `doorWood`, `trapdoorWood`), and axe stripping of our two logs.
- **Mangrove leaves** (decay as 1.12.2 leaves; bone meal grows a hanging propagule below, **now:**), and
  **mangrove propagule**: standing (stage 0-4) and hanging (age 0-4); grows hanging under leaves (only a
  fully grown one drops when broken); placeable on grass, dirt, mud, clay, farmland, and underwater; grows a
  mangrove tree (two kinds: normal and tall) from random ticks and bone meal; fits a flower pot
  (`potted_mangrove_propagule`).
- **Mangrove roots** (axe-mined, can be powered meaning they conduct) and **muddy mangrove roots**
  (mud + roots, shapeless).
- **Mangrove tree** feature: trunk with up to four root arches (`mangrove_roots` through water and mud,
  `muddy_mangrove_roots` in mud), leaves with hanging propagules, vines, moss carpet on top of roots when
  Caves & Cliffs is installed, a bee nest (1% normal / tall) when Buzzy Bees is installed.
- Wandering trader sells 1 propagule for 5 emeralds (Core's `TradeRegistry`, a no-op without a trader).
- Bonus chest may hold mangrove logs: no (1.12.2's bonus chest is fixed code); listed as not done.
- Waterloggable in 1.19 and listed for **Wl**: propagule, roots, leaves, slab, stairs, fence, trapdoor,
  sign. Until then roots and propagules generate in water by leaving it beside them, and the propagule
  can be placed in water (as Update Aquatic's plants are, with `BlockPseudoWater` if needed; decided in
  the stream).

### D. Mud

- **Mud**: from a water bottle used on dirt, coarse dirt, and rooted dirt (Caves & Cliffs', by name),
  returning a glass bottle; 14/16 collision height, a shovel block with 1.19's sounds (`mud`). On top of a
  block with a downward pointed dripstone below it (Caves & Cliffs', by name) it dries into clay on a
  random tick (1.19's chance), with the dripping particle. Dispensers with water bottles make mud
  (1.19.? **now:**).
- **Packed mud** (mud + wheat), **mud bricks** (4 packed mud), mud brick stairs, slab, wall (Village &
  Pillage's stonecutter reads our recipes on its own).
- Sounds: `mud`, `packed_mud`, `mud_bricks` sound types.

### B. Boats

- **Mangrove boat**: an `EntityBoat` subclass with its own wood and renderer (1.12.2's boat type enum
  indexes a texture array, so new woods cannot join it).
- **Boat with chest** for the six old woods and mangrove: 27 slots, opened by sneaking + right-click (or
  right-click when the seat is taken), one seat, drops its contents and itself when broken; hoppers fill and
  empty it, as 1.19's (an `IInventory` entity, as minecarts with chests are). Crafted chest + boat
  (shapeless). Opening is a `container_open` game event (G); piglins are a Nether Update concern.
- 1.19's boat retextures (paddles in their wood's color) are vanilla's look: they go to Core's texture
  packs (`CORE_REQUESTS.md`), ours ship with the new look.
- A small wood registry so Trails & Tales (cherry, bamboo raft) and Tricky Trials (pale oak) can add
  their chest boats the same way; asked of Core as request 4.

### S. Sculk (needs G)

- **Sculk**: drops itself only with Silk Touch, else 1 experience; the block charges travel through.
- **Sculk vein**: multiface (any of six faces); 6 booleans are 64 states and 1.12.2 metadata has 16, so
  the faces live in a tile entity and come back through `getActualState` (the same problem Caves & Cliffs'
  glow lichen has; asked of Core as request 3, a shared multiface block).
- **Sculk catalyst**: blooms (`bloom` state, particles, sound) when a mob that drops experience dies within
  8 blocks, turns its experience into a charge (no orb drops), and runs 1.19's `SculkSpreader`: charges
  creep through sculk-replaceable blocks, place veins, grow sculk, and on spending grow sensors and (rarely)
  shriekers, as 1.19's numbers. Drops 5 experience (**now:**, 1.19.1).
- **Sculk sensor**: listens within 8 blocks (**now:** 8; 9 in 1.19 originally), `inactive` -> `active`
  (40 ticks, redstone power 15 decreasing with distance as 1.19) -> `cooldown` (1 tick, **now:**), the
  tendrils' clicking event, comparator output = last vibration's frequency (**now:**), stepped on always
  activates, cannot hear sneaking or wool-dampened ones; Silk Touch only, otherwise 5 experience.
- **Sculk shrieker**: `can_summon` (only generated ones; placed ones never summon), `shrieking`; triggered
  by a sculk sensor activation nearby (the tendrils' event) or a player stepping on it; per-player
  warning level 0-4 (1.19's `WardenSpawnTracker`: +1 per shriek with a 10-s cooldown, decays one level per
  12000 ticks without shrieks), Darkness to players within 40 blocks, and at level 4 summons a warden
  (W's entity by name; nothing if W is off) on a valid spot within 5x6x5. Game rule `doWardenSpawning`
  (added as a 1.12.2 game rule).
- Advancement *It Spreads* (a mob dies near a catalyst).
- Particles `sculk_soul`, `sculk_charge`, `sculk_charge_pop`, `shriek`: client particles (packet 25).
- Waterlogged in 1.19, listed for Wl: vein, sensor, shrieker.

### F. Frogs

- **Frog**: three variants (temperate, warm, cold) chosen from the biome where the tadpole grew up (warm:
  hot biomes and mangrove swamps; cold: snowy and cold ones; temperate: everything else, by Forge's
  biome dictionary); jumps, swims, croaks, walks on lily pads, leashable; tongue attack on small slimes
  and small magma cubes (size 1) and nothing else; a small magma cube eaten drops a froglight by variant
  (temperate -> ochre, warm -> pearlescent, cold -> verdant), a slime drops a slimeball; breeds with
  slimeballs, the mother lays **frogspawn** on water (lily-pad-like block, hatches 2-6 tadpoles after
  3600-12000 ticks, broken by anything entering it).
- **Tadpole**: water mob, grows into a frog after 24000 ticks (slimeballs speed it by 10%), dies on land,
  **bucket of tadpole** keeps its age.
- **Froglights** (three, light 15, `froglight` sound, **now:** 1.19.4's quieter sounds, axis like logs).
- Spawning: temperate frogs in swamps (and mangrove swamps' warm frogs, with Sw), groups of 2-5.
- Advancements *Bukkit Bukkit* (tadpole bucket), *When the Squad Hops into Town* (leash one of each
  variant), *With Our Powers Combined!* (one of each froglight).

### A. Allay

- Flies, follows its liked player, picks up items matching the one it holds (up to a stack), delivers
  them to the player, or to a note block played within 16 blocks in the last 30 s (`NoteBlockEvent.Play`);
  right-click with an item to give, empty hand to take back; does not despawn while holding; heals 2/s.
- **Now:** dances near a jukebox playing a disc (1.12.2 jukeboxes keep no song length: the allay dances
  while a jukebox within 10 blocks holds a disc), and while dancing duplicates when given an amethyst
  shard (Caves & Cliffs' item, by name; without it no duplication), with a 6000-tick cooldown.
- Spawns in pillager outposts' cages and woodland mansions (Village & Pillage's outposts by name; 1.12.2
  mansions through their structure events if reachable without patches, else noted).
- Advancements *You've Got a Friend in Me* (give an allay an item), *Birthday Song* (an allay drops a
  cake at a note block; a normal advancement, **now:** 1.19.3).

### W. Warden and Darkness (needs G)

- **Darkness**: the effect, its pulsing gloom on the client without patches: the lightmap is recomputed by
  our own code before vanilla's frame (vanilla's update is skipped via its update flag) with 1.19's
  darkness factor, plus fog distance through Forge's fog events; an option to tone the pulse down
  (1.19's *Darkness Pulsing*, a client config entry).
- **Warden**: 500 health, 30 attack (melee, 1.19's per-difficulty), knockback resistance 1, follow range 16 (its sensor reaches 24),
  speed 0.3; blind (no line of sight targeting), hears vibrations (G), sniffs for players and mobs, anger
  per entity (0-150: 80 "agitated", 1.19's `AngerManagement` increase/decay), attacks at max anger;
  **emerging** (134 ticks, invulnerable), **digging** back (100 ticks after 1200 ticks without a
  disturbance), **roaring**, **sniffing**, **sonic boom** (range 15 horizontal, 20 vertical, 10 damage
  ignoring armor and resistance-free shields, knockback 2.5/0.5, 40-tick cooldown, 34-tick charge, shown
  by a particle line), heartbeat speeding with anger, Darkness to players within 20 blocks every 6 s,
  no drops except the sculk catalyst (1), immune to fire and lava sinking, never despawns once spawned
  by a shrieker unless digging away, ignores creative players, tendrils glow.
- Death message for the sonic boom (`death.attack.sonic_boom`, `.player`, `.item`).
- Parrots imitate wardens (1.12.2's parrot imitation map, set once by reflection; `#parrot` sounds).
- Spawn egg.

### I. Items

- **Recovery compass**: 8 echo shards around a compass; points to the player's last death location in
  that dimension, spins elsewhere or with none; the location is stored per player (1.19's
  `LastDeathLocation`, kept through death and saved) and sent to the client.
- **Echo shard**: ancient-city loot (DD); until DD exists, the temporary chest fallback (*Decided* 3).
- **Music disc 5** (crafted from 9 **disc fragments**, the disc's sound and description); fragments are
  ancient-city loot.
- **Swift Sneak** I-III on leggings: sneaking speed 0.3 + 0.15 per level (client movement input through
  `InputUpdateEvent`, the server accepts it); a treasure enchantment that 1.19 never offers from tables,
  fishing or trades (1.12.2 has no flag for the last two: fished and traded books are re-rolled through
  `ItemFishedEvent` and merchant recipe checks, decided in the stream).
- **Goat horns**: the eight instruments (Ponder, Sing, Seek, Feel, Admire, Call, Yearn, Dream), 140-tick
  cooldown per horn, heard 256 blocks, an `instrument_play` game event; from pillager outposts
  (Village & Pillage's chest loot, the four regular ones, by loot-table name) and Caves & Cliffs' goats
  through the hook in the note (four regular from regular goats, four screaming from screaming goats).

### C. Other 1.19.x changes

Each behind its own switch in `changes`:

- Minecarts with chest, furnace, TNT and hopper drop themselves when broken (not the cart and the block)
  and are crafted shapeless.
- Ores and spawners blown up by player-lit TNT drop experience.
- Iron golem and snow golem spawn eggs (1.19.3; 1.12.2 spawn eggs are an entity-list entry).
- Creepers are ignited with a fire charge (1.19.3).
- Endermen's carried block drops as if mined with a Silk Touch tool (1.19.3); rabbits always drop raw
  rabbit, cooked when on fire (1.19.3).
- Jukeboxes: note particles while holding a disc (client), and hoppers and droppers put discs in and take
  them out (an item-handler capability attached to the jukebox tile, 1.19.4).
- Chestplates, elytra and other armor swap with what is worn on use (1.19.4).
- Foals' stats vary around their parents' average (1.19.4, `BabyEntitySpawnEvent`).
- Potion colors: 1.19.4's thirteen new effect colors (1.12.2's effects keep their color in a field set
  once by reflection).
- Scaffolding burns as long as bamboo: Village & Pillage's scaffolding by name.
- Through patches (`asm`, see *Patches*): jukeboxes give a redstone signal of 15 while playing (1.19.4),
  potions lose their enchantment glint (1.19.4), door tops no longer flip (1.19), shulker boxes pop the blocks
  attached to their opening faces (1.19.3), 1.19.3's Nether light limit of 7 for endermen and skeletons.
- Left to their owners: 1.18's light rules are Caves & Cliffs'.

### Sw. Mangrove swamp

The biome (warm, teal water `0x3A7A6A`, fog, grass and foliage colors of 1.19, warm frogs and slimes),
mud floors, mangrove trees (M's feature), Lena Raine's swamp music. A real biome, placed where vanilla
places it: see *Decided* 2.
Temperate frogs in swamps come with F, independent of this.

### DD. Deep dark and ancient cities

The deep dark (sculk patches, sensors and shriekers that can summon, no natural mob spawning, *Ancestry*),
ancient cities (the 1.19 templates, loot: echo shards, disc fragments, Swift Sneak books, disc 5; the
reinforced deepslate frame, which is unobtainable and unbreakable in survival), built properly from 1.19's
templates and jigsaw pools. Heights: *Decided* 1. The template loader and jigsaw assembly are built here as
a local package written to Core's requested API (`CORE_REQUESTS.md` request 2), as the game events are, so
DD does not wait for Core. The deepslate set the cities are built of is Caves & Cliffs'; copied when it is
absent (Rule 1).

**Built** (stream DD, `docs/streams/deepdark.md`): 1.19.2's layouts piece for piece, its grid, and in 1.18 terrain its
biome check, all against vanilla 1.19.2's own code; Core's template loader with a local 1.19 jigsaw assembler
(`CORE_REQUESTS.md` DD-1 to DD-5). Later fixed (branch `wu-deepdark-terrain`, with Caves & Cliffs' `cc-beardbox`):
1.19.2's `beard_box` in the terrain's noise, or carved into a 1.12 chunk's primer, and the sculk features run whole,
once their neighbors exist. A census of the same city matches vanilla 1.19.2's open space and sculk.

### Lf. Look and feel

1.19.x's changes beyond its content, handed to Backport Core's `presentation` module, which shows the newest
installed backport's (family Rule 6, `family-notes/PRESENTATION_PLAN.md`). Versions 1.19, 1.19.1, 1.19.3 and 1.19.4
are registered. The mangrove swamp panorama and 1.19.4's title assets; 1.19.1's and 1.19.3's splashes; *Aerie*,
*Firebugs* and *Labyrinthine* on the title screen in place of 1.18's five tracks; F3 + F removed and the F3
"Allocation rate" line; Darkness Pulsing (read by W), Damage Tilt, Glint Speed and Glint Strength (patches P1-P3,
the mod's first coremod); Auto-Jump off by default once per instance (1.19.4); vanilla's own text as 1.19.4 words
it. Everything shared that only Core can build (Notification Time, High Contrast, the 1.19.3 creative tabs and
Operator Items Tab, the 1.19.4 Create World tabs and onboarding screen, Smooth Lighting's values, key-conflict
tooltips, the Credits button, the pause menu heading, Open to LAN's port, subtitle shadows, the menu tracks'
volume) is `CORE_REQUESTS.md` Lf-1 to Lf-4. Details: `docs/streams/lookfeel.md`.

## Decided (the user, 2026-11-07: closest to vanilla, always)

1. **Deep dark and ancient cities.** Caves & Cliffs is building real extended height. With it installed,
   the deep dark and ancient cities use 1.19's own heights (deep dark down to y -64, city floors at y -51)
   through Core's `WorldHeights`. Without it, in 1.12.2's 0-255 world: the deep dark is an underground
   region chosen as 1.19 chooses it (under inland high ground, away from oceans), y 1-40, with sculk on the
   cave floors, no natural spawns, *Ancestry*, and `deep_dark` reported as the biome there; ancient cities
   sit in it with their floor at y 8 (1.19's -51 shifted by +59), their top under y 40. Sensors and
   shriekers that can summon generate only in the deep dark.
2. **Mangrove swamps**: a real biome, placed where vanilla places it: 1.19 puts the mangrove swamp in the
   swamp slot of the hot climate band, so in 1.12.2's biome map it joins the hot band (Forge's `DESERT`
   biome list, with desert, savanna and plains) at swampland's weight. With Caves & Cliffs' 1.18 terrain
   installed, its climate parameters place it instead, exactly as 1.19's do.
3. **Echo shards, disc fragments and Swift Sneak** come from ancient cities, as in vanilla. Only while DD
   is not built, a temporary fallback adds them to stronghold and dungeon chests at ancient-city rates; it
   is removed in the commit that adds ancient cities.
4. **Waterlogging** of the blocks listed under M, D and S waits for Core's adapter (the family decision).

## Patches

Allowed wherever vanilla parity needs them (the family rule of 2026-11-07): our own small ASM coremod,
package `com.exiledradio.wildupdatebackport.asm`, one transformer per patch, each documented in
`docs/PATCHES.md` (target class and method by SRG and MCP name, what changes, why no event does it), no
MixinBooter, written to coexist with Fluidlogged API's transformers. Everything an event or a subclass can
do is still done that way. Stream C's are P4 to P7 (minecart drops, jukebox signal, potion glint, shulker box
faces); of the five planned under C, the door tops turned out to be 1.19's models (a client state mapper) and the
Nether's light limit an event (`docs/streams/changes.md`). Darkness's lightmap if the patch-free version proves
visibly different from 1.19's (decided in W).

The coremod is `asm.WildUpdateCore` (manifest `FMLCorePlugin`; `-Dfml.coreMods.load` in the run tasks), added by
stream Lf with patches P1-P3 (`docs/PATCHES.md`). A stream with patches adds its own transformer class to
`WildUpdateCore.getASMTransformerClass` and its patches to `PATCHES.md`, numbered on from the last.

## Shared files and rules

As Update Aquatic's plan, with this repo's names:

- **`ModConfig.java`**: every category already exists; a stream edits only its own class; categories
  extend `Object`; every field has `@Config.LangKey("config.wildupdatebackport.<category>.<field>")`.
- **`NetworkHandler.java`**: packets only from your id block, in id order.
- **`WildUpdateBackport.java`**: at most one line per lifecycle hook per stream.
- **`selftest/SelfTestRunner.java`**: one line in `TESTS`.
- **`en_us.lang`, `sounds.json`**: written only by your generator through `tools/assets.py`
  (`merge_lang` / `merge_sounds`), which replaces your own `# --- <stream> ---` block.
- **`common/`**: read freely; changes are the integrator's (here: the same session, on `main`).
- **Registration**: from your own `@Mod.EventBusSubscriber` class through `common/Content`.
- **Other streams' content**: `Owners.ours("name")`; other mods' with `Owners.block(owner, name)` /
  `Owners.item(owner, name)`.
- **Languages (Rule 4)**: every user-facing string is an `en_us.lang` key; vanilla names through
  `assets.name(...)` from 1.19.2's `en_us.json`; `python tools/lang/vanilla_keys.py` lists them as vanilla;
  the 18 languages come from the family tool (`BackportCore/tools/lang/build_langs.py`, branch
  `languages`), never by hand.
- **Docs**: `docs/streams/<stream>.md`: changelog section, how it works, tested, not tested.

## Build and test

- `./gradlew build` never deploys unless `wubDeployDir` is set in `~/.gradle/gradle.properties`.
- `./gradlew runServer -Pselftest=<stream>` on the stream's port (set in its worktree's
  `eclipse/server.properties`), then `python tools/check_assets.py` with 0 errors, then `./gradlew build`
  last (run tasks leave an unobfuscated jar in `build/libs`).
- Before any Gradle run: at least 8 GB free RAM. Gradle `-Xmx2G`, daemon off. Never `cleanRunServer`. Kill
  only our own server, by PID.
- Client-only types only in `client/` classes. No mixins; ASM only in the `asm` coremod (*Patches*).

## Integrations with other mods of the family (Rule 2)

All soft, by `Loader.isModLoaded` and names:

- **Village & Pillage**: trader's propagule (Core `TradeRegistry`), goat horns and allays in its outposts,
  scaffolding fuel, composting (mangrove leaves 30%, propagule 30%, roots 30%, muddy roots? no: as 1.19's
  table), sign dyeing on mangrove signs (our tile extends `TileEntitySign`, as V&P's own wood signs do).
- **Caves & Cliffs**: goats drop goat horns (hook), mud from rooted dirt, clay under pointed dripstone,
  moss carpet on mangrove roots, amethyst shards duplicate allays, deepslate for ancient cities, its
  terrain heights for the deep dark.
- **Buzzy Bees**: bee nests in mangrove trees.
- **Update Aquatic**: the sea pickle/kelp rules are unaffected; mangrove propagules and roots in water use
  the same pseudo-water technique until waterlogging.
- **Trails & Tales**: the calibrated sensor on Core's game events; its chest boats through Core's boat-wood
  registry.
