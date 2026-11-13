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
- **`sculk_patch_deep_dark`**: 256 tries, scanned down through air up to 12 blocks to a solid block, then up one, kept
  in the deep dark. It is stream S's `SculkPatch.DEEP_DARK`.

Two things keep it close to 1.19's generation in 1.12.2:

- **No neighbor updates** (`QuietGen`): Forge's block snapshot capture is on while features and cities are placed, and
  the snapshots are dropped afterwards rather than restored. 1.19's `WorldGenRegion` never updates neighbors either.
  Without this, sculk discharging next to ladders whose wall `block_rot` removed popped 55 ladders into items per city.
- **No cascading generation**: a try whose reach (veins 22, patches 16) touches a chunk not generated yet is left out
  (`DeepDarkFeatures.generated`). 1.19 decorates inside a 3 x 3 chunk region; 1.12.2 only guarantees the 2 x 2 below
  the populate offset. Before this guard, a 1.18 terrain world logged 199 cascading-generation warnings. The cost is
  less sculk where chunks generate in raster order: in the test city, 16,184 sculk blocks with the guard against
  38,160 without.

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

**Terrain**: 1.19 uses `beard_box`, a cavern the boxes cut out of the noise. Neither terrain has it (Caves & Cliffs
logs "not supported" and uses none). So `Start.generateStructure` clears each rigid piece's box above its floor in
the chunk, and fills up to two blocks under the floor with deepslate. The twelve-block falloff around the boxes is
left out (`CORE_REQUESTS.md` DD-3).

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
- **`beard_box`'s falloff** around the boxes: Caves & Cliffs has no 1.18.2 formula for it (DD-3).
- **Sculk features at 1.19's place in the feature order**: in 1.18 terrain they run after Caves & Cliffs' own
  decoration, with this populate random, not 1.19's per-feature seed (DD-3). So block-for-block sculk differs from
  vanilla's, and the city's beard is approximate; positions and layouts are exact.
- **Dropped items**: 1.12.2's own populate (a water spring's first tick) still updates blocks next to the city. A
  ladder whose wall `block_rot` removed then drops, about ten per city. Vanilla 1.19 leaves them floating.
