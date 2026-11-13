# Stream DD: the deep dark, ancient cities and reinforced deepslate

Package `deepdark`, config `deepDark` (`enabled`), no entities or packets used (ids 20-24 and 60-64 stay free), dev
server port 25872. Branch `wu-deepdark`, cut from `playtest` (sculk, warden and items in).

## Changelog

- **Reinforced deepslate**: 1.19's block, the ancient city portal's frame. Strength 55, blast resistance 1200, drops
  nothing, cannot be pushed, withers and the dragon cannot break it. It has an item (creative only, as in 1.19).
- **The deep dark**: sculk veins and sculk patches on its cave floors, with catalysts. Nothing spawns there naturally.
  - In Caves & Cliffs' 1.18 terrain worlds it is 1.19's own biome, at 1.19's heights. A `deep_dark` biome is
    registered, so that terrain reports and spawns from it.
  - In other worlds it sits under high ground, y 1 to 40.
- **Ancient cities**: built from 1.19.2's own templates and pools, laid out piece for piece as 1.19.2 lays them out, on
  1.19's grid. Every city has its shriekers that can summon wardens, sculk, soul lanterns and 1.19's loot. Chests hold
  echo shards, disc fragments, Swift Sneak books, enchanted golden apples and the rest; the ice box has its own table.
  `/locate structure minecraft:ancient_city` finds them.
  - In Caves & Cliffs' 1.18 terrain worlds, cities stand where 1.19.2 puts them for the seed: the same chunks as
    Chunkbase's 1.19 map, with the floor at y -51.
  - In other worlds the floor is at y 8.
- **Deepslate**: the cities are built of Caves & Cliffs' deepslate. Without Caves & Cliffs, this mod registers its own
  copies: deepslate, cobbled, polished, bricks, tiles, chiseled, cracked bricks and tiles, and stairs, slabs and walls
  of the four sets. They match Caves & Cliffs' states, strength, sounds and recipes.
- The temporary stronghold and dungeon fallback for echo shards, disc fragments and Swift Sneak (*Decided* 3) is gone:
  they come from ancient cities now.

## How it works

### Reinforced deepslate (`BlockReinforcedDeepslate`)

Values are from 1.19.2's `Blocks.REINFORCED_DEEPSLATE`:

- strength 55, `setResistance(2000)` (1.12.2 reads that as 1200, as obsidian's);
- `PushReaction.BLOCK`, no loot table;
- in `#wither_immune` and `#dragon_immune` (`canEntityDestroy`);
- deepslate's sound type (`DeepslateCopies.deepslateSound()`, registered even when Caves & Cliffs is installed).

### Where the deep dark is (`DeepDark`)

- **1.18 terrain** (Backport Core 1.2.0's `ModernGeneration`, read by reflection, so the mod still compiles and runs
  against Core 1.1.0):
  - `DeepDark.at` uses the 3D biome Caves & Cliffs reports for the block.
  - The ancient city check (`noiseAt`) uses 1.19's `Structure.isValidBiome`: the *noise* biome of the stub's quart,
    not the zoomed biome a block reports, which can be a neighbor's. Caves & Cliffs keeps that biome in its terrain
    core's `BiomeSource`, reached through `ModernGenerator.biomes3d.source.biome(qx, qy, qz)` by reflection until
    Core offers it (`CORE_REQUESTS.md` DD-1).
  - With reflection, all 256 of seed 12345's grid candidates agree with vanilla, the 11 cities among them included.
    Without it the zoomed biome is used, with a warning.
- **Any other overworld**: 1.19 puts the deep dark at the bottom where erosion is low, under mountains and high ground.
  1.12.2 has no erosion, so the column's 1.12 biome stands in for it:
  - in: base height 0.45 or more, or Forge's `HILLS` or `MOUNTAIN`;
  - out: oceans, rivers and beaches;
  - heights: 1.19's -58 to -19, moved up 59 to y 1-40.
- `DeepDark.shift(world)` is 0 where the world starts below 0 (`WorldHeights.minY`), else 59.

### Deep dark features (`DeepDarkFeatures`, an `IWorldGenerator`)

1.19.2's `CavePlacements` place two features, a half chunk in as 1.12.2 populates:

- **`sculk_vein`**: 204-250 tries, x and z in the chunk, y uniform from the bottom to 256 (1.19 heights, shifted), kept
  in the deep dark. It is `MultifaceGrowthFeature` ported:
  - search range 20, on floors, walls and ceilings;
  - only on stone, andesite, diorite, granite, deepslate, tuff, calcite and dripstone;
  - one spread from the new face toward a random direction (`VeinSpreader.spreadFromFaceTowardRandomDirection`,
    added to stream S's spreader).
- **`sculk_patch_deep_dark`**: 256 tries, kept in the deep dark, each placed where it lands (1.19.2's placement has no
  environment scan; the patch starts only in air or water beside a full block). It is stream S's
  `SculkPatch.DEEP_DARK`. The first version scanned down to a floor, which placed about four times vanilla's patches:
  in the same city, 384 catalysts, 1,494 sensors and 190 shriekers against vanilla's 89, 392 and 53.

Two things keep it close to 1.19's generation in 1.12.2:

- **No neighbor updates** (`QuietGen`): Forge's block snapshot capture is on while features and cities are placed, and
  the snapshots are dropped afterwards rather than restored. 1.19's `WorldGenRegion` never updates neighbors either.
  Without this, sculk discharging next to ladders whose wall `block_rot` removed popped 55 ladders into items per city.
- **No cascading generation, no sculk lost** (`DeepDarkPending`): 1.19 decorates inside a 3 x 3 chunk region; 1.12.2
  only guarantees the 2 x 2 below the populate offset, and a vein's search (20 blocks) or a patch's charges reach
  further. So a populated chunk's deep dark decoration (the city's sculk patches in its area, then the two features)
  waits until the 4 x 4 chunks it can touch are generated, and then runs whole, from a random of the chunk's own: on
  its own populate if they are, else on a later populate nearby. The waiting list is saved with the world. The first
  version skipped the tries that reached too far instead, which lost half the sculk (16,184 blocks in the test city,
  against 35,882 now).

Spawns (`DeepDarkFeatures.Spawns`):

- In other worlds, natural spawns are denied in the deep dark (`CheckSpawn`); spawners and summoned wardens are not
  natural spawns.
- In 1.18 terrain the registered `BiomeDeepDark` has empty lists, which Caves & Cliffs' spawner reads.

### Ancient cities (`city/`)

**Placement** (`AncientCityStructure`, a Backport Core `ScatteredStructure`):

- `ScatteredPlacement(24, 8, 20083232)`, linear: 1.19.2's `StructureSets.ANCIENT_CITIES`.
- Start height: 1.19's `absolute(-27)`, shifted, so y 32 in a 0-255 world.
- Valid when the deep dark is at the generation stub (`noiseAt` above).
- Caves & Cliffs' `StartRule` does not answer for ancient cities, so this grid is used in 1.18 terrain too.

**Layout** (`CityLayout`): 1.19.2's `JigsawPlacement.addPieces` and `Placer`, over Core's pools, elements and templates.

- The start: a rotation, then a template from `ancient_city/city_center`. Its jigsaws are shuffled and the first named
  `minecraft:city_anchor` is put on the chunk corner. The piece then moves down by its ground level delta.
- The stub is the start box's middle at the anchor's height.
- Children: breadth first to depth 7, inside a cube 116 out from the stub.
- Every shuffle is 1.19's `Util.shuffle` (n - 1 draws, the same as `Collections.shuffle`). Core 1.1.0's `Jigsaw` is
  1.16.5's: its pool shuffle is fastutil's n draws, and it has neither a start jigsaw nor a distance.
- The random is `StructureRandoms.start` (`setLargeFeatureSeed`).

**Pieces** (`CityPiece`, a Core `JigsawPiece`) place their own templates so the processor lists run as 1.19's do. Core
1.1.0 reads `block_rot` without `rottable_blocks` and has no `protected_blocks` (`CORE_REQUESTS.md` DD-2).

`CityProcessors` holds the three lists:

- **start**: rule, protected;
- **generic**: rot 0.95 of `#ancient_city_replaceable`, rule, protected;
- **walls**: as generic, plus 30% of deepslate tile slabs gone.

The rule: 30% of deepslate bricks become cracked, 30% of deepslate tiles become cracked, 5% of soul lanterns are
removed. "Protected" means `#features_cannot_replace` blocks already in the world are kept. The `ancient_city/sculk`
pool's feature element is stream S's `SculkPatch.ANCIENT_CITY` (shriekers that can summon).

**Terrain**: 1.19 uses `beard_box`. Its Beardifier adds, for each rigid piece, air through the box above its ground and
ground under it, falling off past the sides, top and bottom, plus a lighter beard at every jigsaw junction. This goes
into the terrain's density, so the cavern runs into the caves around it.

- **1.18 terrain** (Caves & Cliffs from `cc-beardbox`): the city registers with Backport Core's `TerrainAdaptation`
  as `BEARD_BOX`. `AncientCityStructure.startsNear` lays out the starts that reach a chunk before it is filled, and
  `CityPiece` gives each piece's projection, ground level delta and junctions (recorded by `CityLayout` as 1.19.2's
  `Placer` does). Caves & Cliffs adds 1.19.2's `beard_box` to its noise, exactly: `tools/terrain/beardbox119.sh` in
  its repository compares it with vanilla's Beardifier over 3.3 million blocks, 0 differences.
- **1.12.2 terrain**, or a Caves & Cliffs without `beard_box` (`DeepDark.terrainCarvesCities`): `CityBeard` computes
  the same 1.19.2 beard and adds it to a density read off the blocks, `squeeze(1)` (0.458, the final density of
  solid rock deep down in 1.18 and later) for rock and minus that for open space. It carves into each chunk's primer
  as the chunk is generated (Forge's `ReplaceBiomeBlocks`, before 1.12's carvers), so it is there before any chunk
  around is decorated. A generator that does not fire that event gets it at populate instead.

The first version cleared each piece's whole box and filled two blocks under it as the chunk was populated. That
looked pasted in: sheer walls up to the top of every box, and none of the caves above the city opened into it. The
carve came after neighboring chunks were decorated, so it also left sculk veins floating where it took away the
rock they had grown on.

**Height**: 1.12.2's `MapGenStructure` clips each chunk to y 1-255. The city's start widens the clip to the world's
whole height, otherwise a 1.18 terrain city at y -51 placed nothing.

**Assets** (`tools/deepdark/generate.py`):

- the 58 templates, copied unchanged from the 1.19.2 jar;
- the seven pools, written from 1.19.2's `AncientCityStructurePools`, which builds them in code;
- named-only processor lists;
- empty loot table files.

`ancient_city/walls/no_corners` names `intact_horizontal_wall_stairs_5`, which 1.19.2 does not ship. It stays in the
pool so the shuffle draws as 1.19.2's does; it loads as an empty template with no jigsaws, so it never joins (Core logs
it once as missing).

**Blocks** come through Core's `ModernPalette`:

- this mod is a source: `reinforced_deepslate`, `sculk_sensor`, and the deepslate copies under 1.19's names;
- Caves & Cliffs' deepslate set and candles, and Village & Pillage's lantern, lectern and campfire, by the ownership
  table;
- stand-ins only when nobody provides a block: `soul_lantern` becomes a lantern, `polished_basalt` becomes smooth
  basalt, `blue_ice` becomes packed ice;
- anything else nobody provides is left out (Core logs it once).

### Loot (`AncientCityLoot`)

1.19.2's `chests/ancient_city` (5-10 rolls, total weight 88) and `chests/ancient_city_ice_box` (4-10 rolls), entry for
entry, built at load:

- Swift Sneak is held as the enchantment object, so the book's id is the world's.
- 1.12.2 differences: the enchanted golden apple is metadata 1; the potion is `minecraft:strong_regeneration` by NBT;
  the suspicious stew's effect is written in 1.14's `Effects` layout, which Village & Pillage reads.
- Other backports' items: Caves & Cliffs' Otherside disc, candles, amethyst shards and glow berries; Nether Update's
  soul torches; Village & Pillage's suspicious stew. Any that are absent become empty entries of the same weight, so
  every other entry keeps 1.19's chance.

### Deepslate copies (`DeepslateCopies`, `tools/deepdark/deepslate.py`)

Registered only when `deepDark.enabled` is on and Caves & Cliffs is absent. Everything matches Caves & Cliffs'
`DeepslateContent`:

- names, metadata (pillar axis 0/4/8, slabs bottom 0 and top 8), strength (3; 3.5), sounds;
- the crafting and smelting recipes, and ore dictionary entries (`cobblestone`, `stone`);
- walls with `up` and none/low/tall sides.

## Tested

The oracle is `tools/deepdark/oracle.sh`: vanilla 1.19.2's own code (Mojang-named client jar, JDK 17). It runs
`Structures.ANCIENT_CITY.findGenerationPoint` and the overworld noise biome for seeds 42, 12345, -7346534567 and
8675309, over 8 x 8 regions each, and writes `tools/deepdark/oracle119.txt`: 1,024 grid candidates, 23 of them cities
in the deep dark, and the full piece lists of 12 cities.

`-Pselftest=deepdark` passed 42 of 42 in a fresh Default world (seed 1013):

- **Oracle checks**:
  - all 1,024 grid chunks are vanilla's;
  - all 1,024 generation stubs are vanilla's;
  - all 12 cities lay out piece for piece as 1.19.2's: same order, element, rotation and box (76 to 106 pieces each).
- **Blocks**: reinforced deepslate's numbers, no drops, cannot be pushed, has an item. The deep dark biome is
  registered with no spawns. The deepslate copies exist exactly without Caves & Cliffs, all 24 resolve, and their
  strengths and drops match.
- **Loot**: over 4,000 chests, echo shards, disc fragments, Swift Sneak books and enchanted golden apples per chest match
  1.19's rates; never more than 10 rolls; the ice box has snowballs.
- **A built city**: `/locate structure minecraft:ancient_city` finds one at 848, 912, starting in the located chunk with
  its stub in the deep dark. Its 97 pieces sit at y 7-37. After every chunk of it is generated:
  - 56 reinforced deepslate;
  - 29 of 29 chests with the city's loot table;
  - 5,012 deepslate tiles;
  - 16,184 sculk, and 36 shriekers, all able to summon;
  - 9,931 sculk veins around it;
  - at most a quarter as many dropped items as pieces (see below);
  - natural spawns denied at the stub, and no deep dark at y 100.
  - No cascading-generation warnings.

The same test in a **`cavesandcliffs` world, seed 12345** passed 42 of 42. Setup: Caves & Cliffs' `cc118-all`
release jar in `eclipse/mods`, `-Pcore_version=cc118all` with Core `cc118-all`'s dev jar, `level-type=cavesandcliffs`.

- Every one of the seed's 256 oracle candidates starts a city exactly when 1.19.2 does (11 cities). These are the
  positions Chunkbase shows for 1.19.
- The city found at -288, -288: 90 pieces at y -52 to -22, 56 reinforced deepslate, 18 of 19 chests with city loot
  (one of 1.19.2's template chests has no table, in vanilla too), 5,206 tiles.
- Spawns come from our biome (empty).
- No cascading-generation warnings.

**Full suite** (`-Pselftest=all`, Default world): 1,619 pass, 0 fail. One earlier full run had 9 allay failures (item
throw timing, duplication). Allay alone passed 98 of 98, and the next full run passed everything, so that run was
flaky; nothing in this stream touches allays.

`python tools/check_assets.py`: 62 blocks, 1,195 states, 68 item models, 704 sound files, 0 errors.
`tools/lang/vanilla_keys.py`: the four deepslate double slabs are mapped to their slabs' vanilla names.

### The terrain and sculk fix (2026-11-13)

A player reported that a city in a `cavesandcliffs` world looked pasted into the world: a flat, box-like cavern, sculk
mostly on the city, and floating sculk veins. There were three causes:

- the box stand-in for `beard_box`;
- the cascade guard skipping half the sculk;
- the patch's environment scan, which 1.19.2 does not have and which, once nothing was skipped, placed four times
  vanilla's patches.

All three are fixed as described above, on branch `wu-deepdark-terrain`, with Caves & Cliffs' `cc-beardbox`.

**Census** (`tools/deepdark/census.py`, `tools/deepdark/anvil.py`). These scripts read region files of either format.
The area is the player's city on seed 852203380978249405 (start -564, -27, -1504): 191 x 191 blocks, y -64 to 0.

| | vanilla 1.19.2 | before (the player's world) | after |
|---|---|---|---|
| open space | 26.8% | 17.7% | 26.7% |
| sculk family | 41,924 | 20,493 | 42,790 |
| floors under open space with sculk | 30.0% | 16.1% | 28.4% |
| sculk / veins | 33,324 / 14,005 | 13,711 / 8,703 | 32,315 / 15,127 |
| catalysts / sensors / shriekers | 89 / 392 / 53 | 67 / 215 / 17 | 79 / 379 / 63 |

Vanilla's world came from 1.19.2's own server (the Mojang-named client jar), with the area force-loaded. The census
picture (the floor from above and two slices through the start) shows the after-fix cavern opening into the caves
above it, as vanilla's does. The before picture shows flat-topped boxes.

**Tests**:

- `tools/deepdark/oracle.sh` now also dumps vanilla's Beardifier around the cities it lays out, using their pieces,
  ground level deltas and junctions. It adds the player's seed: 5 seeds, 1,280 candidates, 15 layouts, 4,800 beard
  values. The self-test computes `CityBeard.density` over our layouts at the same blocks: every value is equal, bit for
  bit.
- `-Pselftest=deepdark`, Default world, seed 1013: 48 of 48.
  - City at 848, 912: 35,882 sculk and 18,745 veins, with sculk on 43% of the floors.
  - No chunk of the city left waiting.
  - No rock where the beard carves.
  - Fewer than 1 vein in 200 floating.
  - No cascading-generation warnings.
- The same in a `cavesandcliffs` world on the player's seed, at the player's city (`-PselftestNear=-564,-1504`):
  - Caves & Cliffs `cc-beardbox` in `eclipse/mods`, `-Pcore_version=playtest` with Core playtest's dev jar.
  - All 256 of the seed's candidates start a city exactly where 1.19.2 does.
  - The beard carves the cavern: of the 28,863 blocks it carves past the pieces, 460 are rock (1.6%: aquifer
    barriers, tuff and gravel blobs) and some dripstone grew into it. At the same blocks, vanilla 1.19.2's world has
    327 rock (1.1%). The self-test writes the blocks to `eclipse/beard-carved.txt`, so they can be looked up in
    another world.
  - No cascading-generation warnings.

## Not tested

- Anything visual: the models and textures of reinforced deepslate and the deepslate copies, and how a city looks.
- A city's portal, wardens summoned in a city (the shrieker and warden are streams S and W, tested there), and
  hand-opening chests.
- A world with Farmer's Future Delight or another extended-height mod without `ModernGeneration`. Heights there are
  1.19's (shift 0), with the high-ground stand-in.

## Not done

- **Ancestry** (`music.overworld.deep_dark`) and the deep dark's mood: biome music needs a biome music player shared by the
  family, which stream Sw asks of Core on its branch; DD-4 adds the deep dark's track to that request.
- **F3 shows `deep_dark`** only in 1.18 terrain. The 1.12.2 stand-in region has no biome of its own on the client.
- **Sculk features at 1.19's place in the feature order**: in 1.18 terrain they run after Caves & Cliffs' own
  decoration, with a random of the chunk's own, not 1.19's per-feature seed (DD-3). So block-for-block sculk differs
  from vanilla's; its amounts match (see the census), and positions, layouts and the beard are exact.
- **Dropped items**: 1.12.2's own populate (a water spring's first tick) still updates blocks next to the city. A
  ladder whose wall `block_rot` removed then drops, about ten per city. Vanilla 1.19 leaves them floating.
