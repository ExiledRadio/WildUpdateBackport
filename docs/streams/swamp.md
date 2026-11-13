# Stream Sw: mangrove swamps

Package `swamp`. Config `swamp` (`enabled`). Branch `wu-swamp`. Packet ids 55-59 (none used). Patch P9.

## Changelog section

*   **Mangrove swamps**: a new warm swamp biome with mud underfoot, shallow teal water, mangrove trees standing on
    their roots, hanging propagules, grass, dead bushes and lily pads. Warm frogs, slimes, bats and (with the
    water mobs installed) tropical fish live there; no farm animals.
*   Mangrove swamps generate in the warm parts of the world, beside deserts and savannas, as often as swamps do
    in the temperate parts, and meet the sea without a beach.
*   Grass, flowers, saplings, dead bushes and sugar cane grow on mud as they do on dirt.

## How it works

*   **The biome** (`BiomeMangroveSwamp`, `wildupdatebackport:mangrove_swamp`) is 1.19.2's `OverworldBiomes.mangroveSwamp`:
    temperature 0.8, downfall 0.9, rain; water `0x3A7A6A`, foliage `0x8DB127`, grass by the swamp modifier (`0x4C763C`
    or `0x6A7039` by the biome noise, as 1.12.2's swamp). Height is 1.12.2's swamp's (-0.2, 0.1). Forge biome
    dictionary: `SWAMP`, `WET`.
*   **Water color**: 1.12.2's water texture is blue and the biome color multiplies it, so the biome's own color is the
    tint `0xEAFF76` (1.19's color divided by 1.13's blue, channel by channel). When something replaces that tint (Update
    Aquatic's 1.13 gray water texture), the `GetWaterColor` event, run last, gives 1.19's exact `0x3A7A6A`. The
    underwater fog stays Update Aquatic's default: `CORE_REQUESTS.md` 10.
*   **Spawns**: the eight common monsters (1.12.2's defaults are 1.19's), a second slime entry (1, 1-1), frogs (10, 2-5;
    the frog stream adds them by the biome's name, warm variant), bats. No squid, no farm animals. Tropical fish (25,
    8-8) go into Update Aquatic's water ambient list here when its fish spawn. **Slimes** spawn on the surface as in
    the swamp (1.19's `#allows_surface_slime_spawns`): 1.12.2's `EntitySlime` names the swamp outright, so the same
    rule (y 50-70, half the time, by the moon, light at most a random 0-7) is given through `LivingSpawnEvent.CheckSpawn`.
*   **Surface** (1.19's `SurfaceRuleData`): mud as top and filler, so the floor is mud above and below water. Where the
    swamp noise is above 0, a floor at y 60-62 becomes water (1.19's mangrove swamp rule; 1.12.2's swamp only fills
    y 62). 1.12.2 has no separate swamp noise; the swamp's own (the biome noise at a quarter of x and z) stands in.
*   **Decoration** (`MangroveSwampDecorator`), 1.19.2's placed features with their numbers:

    | 1.19.2 placed feature | Here |
    | --- | --- |
    | default ores, underground variety, springs | 1.12.2's own (`generateOres`, 50 water and 20 lava springs) |
    | `disk_grass`: 1, on the top solid block if it is mud | radius 2-6, 2 up and down: dirt and mud become grass with nothing solid or water above, else dirt |
    | `disk_clay`: 1, on the top solid block under water | radius 2-3, 1 up and down: dirt and clay become clay |
    | `trees_mangrove`: 25, water at most 5 deep, on the ocean floor, where a propagule would stand | `tall_mangrove` 85%, else `mangrove` (stream M's `WorldGenMangroveTree`), the propagule checked again |
    | `patch_grass_normal`: 5 on the world surface | 32 tries, 7 across and 3 up or down, grass on air where it can stand |
    | `patch_dead_bush`: 1 | 4 tries |
    | `patch_waterlily`: 4 | 10 tries |
    | `seagrass_swamp`: 64, 60% tall | Update Aquatic's, which places exactly that in every `SWAMP` biome |
    | fossils | 1.12.2's swamp fossil, 1 in 64 chunks |

    Heightmaps as 1.19's: world surface is above the highest non-air block (plants and water included), ocean floor
    above the highest block that blocks movement. Positions are 1.12.2's decoration square (the chunk offset by 8).
    1.12.2's own swamp decoration (swamp oaks, blue orchids, mushrooms, sugar cane, sand and gravel, pumpkins) is not
    placed. Each step fires its Forge decorate event (`TREE`, `GRASS`, `DEAD_BUSH`, `LILYPAD`, `CLAY`, `CUSTOM` for
    the grass disk, `LAKE_WATER`, `LAKE_LAVA`, `FOSSIL`), so mods that cancel them still can.
*   **Mud and plants**: `BlockMud.canSustainPlant` answers as dirt does (1.19's `#dirt` holds mud), except cactus.
    Without it, grass and dead bushes placed on mud would drop at the first neighbor update.

### Where it generates

*   **1.12.2 terrain** (*Decided* 2): 1.19 puts the mangrove swamp in the swamp's slot of the hot climates, so it
    joins Forge's hot band (`BiomeManager.BiomeType.DESERT`: desert 30, savanna 20, plains 10) at the swamp's own
    weight, 10. Like the swamp, it meets the ocean with no beach: `GenLayerShore` skips the swamp by id, and patch P9
    (`asm/SwampTransformer`, `docs/PATCHES.md`) has the mangrove swamp answer as the swamp for that one test.
    `swamp.enabled` is read before each server starts (`FMLServerAboutToStartEvent`): off, worlds made or loaded after
    get no new mangrove swamps; chunks already made keep theirs. The biome itself is always registered, so a world
    with mangrove swamps always loads them.
*   **Seeds**: adding a biome to Forge's hot band changes where hot biomes fall, so a 1.12.2 seed's hot regions differ
    from the same seed without this mod (temperate, cold and icy ones and the oceans do not move). This is the plan's
    decision; 1.19's own layout comes with Caves & Cliffs' terrain, below.
*   **Caves & Cliffs' 1.18 terrain** (`cc118-all`): its world chooses, when created, the newer biomes the installed
    backports have blocks for (`ModernTerrain.installedEditions`: our mud, mangrove log, leaves, roots, muddy roots and
    propagule), and places 1.19's mangrove swamp by 1.19's climate parameters. Its `BiomeMap` takes a biome a mod
    registers under the modern name in any namespace, so its mangrove swamp columns are this biome: our spawns, colors
    and frogs. Its generator places 1.19.2's features itself, `trees_mangrove` included (its own port of 1.19.2's
    tree, the same numbers as ours), writing our blocks by name through its `StateMap` (the propagule's `age` and
    `hanging` carry over). 1.12.2's biome decorator does not run in those worlds, so this biome's decorator never
    adds a second set; it also asks Backport Core's `DecorationSource` (1.2.0, by name) and steps aside for any
    generator that says it places a feature.

## Tested headlessly

`./gradlew runServer -Pselftest=swamp` (port 25871, seed 1013): **`SELFTEST DONE pass=198 fail=0`** (the swamp test alone).

*   Registration under `wildupdatebackport:mangrove_swamp`; temperature, downfall, rain, height; `SWAMP` and `WET`.
*   Water: the tint `0xEAFF76`; with the color replaced (as Update Aquatic does), the event gives `0x3A7A6A`; other
    biomes untouched. Grass takes both swamp colors over 108 positions; foliage `0x8DB127`.
*   Spawns: 9 monster entries (two slimes, 100 and 1), frogs only among creatures (10, 2-5, warm), bats, no squid; the
    slime surface rule refuses y 80 and y 45.
*   Placement: in the hot band at 10 (the swamp's weight in its band is 10 too), in no other band; `swamp.enabled` off
    removes it, on puts it back.
*   P9: patched as the world's layers loaded; the patched dev class and Forge's SRG class each call the hook once and
    pass ASM's stack check; the patched layer leaves a mangrove swamp (and a swamp) beside the ocean as itself while
    plains get a beach.
*   Surface on a chunk primer over 8 chunks: floors at y 61 under the sea become water or mud by the noise, with mud
    under the pools; land is mud on top and below.
*   Plants on mud: grass, dead bush, sapling, flower stand; cactus does not; grass survives a neighbor update.
*   Features on test plots: the grass disk (grass in the open, dirt under water and under the new block, mud below its
    height and beyond its radius); the clay disk (dirt under water to clay, mud left); both heightmaps; grass and lily
    pad patches; dead bushes refused on water; the propagule check on mud and stone.
*   A real mangrove swamp: the nearest to 0, 0 on seed 1013 is at -1520, 1228. Its 3 x 3 chunks (2,304 columns, all
    mangrove swamp, all decorated): mud is the ground in 1,581 columns (the rest grass and dirt from the grass disks,
    clay), 669 mangrove logs, 1,665 roots, 10,954 leaves, 209 propagules, 42 grass, 12 lily pads, water at y 60-62 over
    999 columns; no oak logs (in the middle chunk, which only mangrove swamp chunks decorate), sugar cane, pumpkins,
    orchids or mushrooms.

With Caves & Cliffs (`cc118-all` release jar in `eclipse/mods`, compiled against Backport Core `cc118-all`, which
has `DecorationSource`), 198 pass, 0 fail, twice:

*   `level-type=cavesandcliffs` (1.18 terrain): the world chose "1.18 terrain with 1.19's biomes"; Caves & Cliffs'
    `BiomeMap` returns our biome for 1.19's mangrove swamp, as a registered modern biome; its generator says it places
    `trees_mangrove` itself. The nearest mangrove swamp (104, 3116): 1,962 of the 3 x 3 chunks' columns are our biome,
    1,587 with mud ground, 363 of our logs, 903 roots, 6,053 leaves, 108 propagules, 116 grass, 39 lily pads, none of
    1.12.2's swamp decoration.
*   A Default world with Caves & Cliffs installed (1.12.2 terrain): the same as without it.

`python tools/check_assets.py`: 37 blocks, 364 states, 47 item models, 594 sound files, 0 errors. The full suite (`-Pselftest=all`): **`SELFTEST DONE pass=1779 fail=0`** on a fresh Default world.

## Not tested

*   Anything visual: the water, grass and foliage colors on screen, the water with Update Aquatic's texture.
*   Tropical fish spawning (needs Update Aquatic), Update Aquatic's seagrass in the biome.
*   Slimes actually spawning at night (random; the rule's numbers are checked).

## Not done

*   **Biome music**: 1.19's swamp music (*Aerie*, *Firebugs*, *Labyrinthine*) needs a biome music player shared by the
    family (`CORE_REQUESTS.md` 9); this mod ships the tracks already, for the title screen.
*   **Underwater fog color** `0x4D7A60` with Update Aquatic's water (`CORE_REQUESTS.md` 10).
*   **Waterlogged roots, propagules and leaves**: the family's waterlogging pass (*Decided* 4); until then a root or
    propagule placed into water takes the water's place, and the trees' roots in pools stand in air pockets' stead
    exactly as stream M describes.
