# Stream Wl: waterlogging

Backport Core's `waterlog` API (`Waterloggable`, `Waterlogging`) over Fluidlogged API 3.3.3, which is optional. No
block has a `waterlogged` property: the water is Fluidlogged API's, and Core's patch makes our `Waterloggable`
classes Fluidlogged API's `IFluidloggable`. Core's `WaterRules` lets them take water sources only, never flowing
water or lava, and keeps every other block of ours dry. No package of its own (each stream's blocks say it
themselves), no config, no packets (65-69 unused). Branch `wu-goatwl`. Port 25874 (shared with Gt).

## Changelog section

*   With **Fluidlogged API** installed, these hold water as in 1.19: mangrove roots, leaves and propagules (standing
    or hanging), mangrove stairs, slabs, fences, trapdoors and signs, mud brick stairs, slabs and walls, and sculk
    veins, sensors and shriekers (and the deepslate stairs, slabs and walls this mod brings without Caves & Cliffs).
*   A waterlogged sculk sensor makes no clicking sound.
*   Mangroves growing in water keep it around their roots and leaves; sculk veins and sculk sensors and shriekers
    spread into water.
*   Without Fluidlogged API nothing changes: these blocks stay dry, and sculk does not spread into water.

## How it works

| Block | 1.19.2 rule kept |
| --- | --- |
| `common/ModBuilding` stairs, slab, wall (mud bricks; the deepslate copies extend them) | plain `Waterloggable`; `canHoldWater = !isDouble()` |
| `mangrove/MangroveBuilding` stairs, fence, trapdoor, slab; `MangroveSign` standing and wall | plain `Waterloggable`; slab `!isDouble()`; door, fence gate, button and pressure plate stay dry (1.19.2's are) |
| `mangrove/BlockMangroveRoots.Roots`, `BlockMangroveLeaves` | plain `Waterloggable` (muddy roots stay dry) |
| `mangrove/BlockPropagule` | `Waterloggable`, standing and hanging; placed into water it keeps the source; a hanging one still needs its leaves and, dropping, leaves its water; growing into a tree it leaves its water where it stood (1.19's `TreeGrower`) |
| `mangrove/WorldGenMangroveTree` (trees from propagules and the mangrove swamp) | roots where there is any water: `placeWithWater` (1.19's `RootPlacer`, `FluidTags.WATER`); leaves: `placeKeepingWater` (its `FoliagePlacer`, a source only); logs replace water; the block flags are `setBlockAndNotifyAdequately`'s (16 added with Forge's `fixVanillaCascading`) |
| `sculk/BlockSculkSensor` | `Waterloggable`; `activate` and `deactivate` play `sculk_clicking` / `sculk_clicking_stop` only when dry |
| `sculk/BlockSculkShrieker` | plain `Waterloggable` |
| `sculk/BlockSculkVein` | `Waterloggable`; `set` places a new vein with `placeKeepingWater`; a vein left with no face becomes its water, else air (1.19's `onDischarged`); `waterSource`: a water source block, only with Fluidlogged API |
| `sculk/VeinSpreader`, `SculkBehaviour.DEFAULT`, `BlockSculk`, `gen/SculkPatch` | veins spread and regrow into water sources (never flowing water or other fluids); growths rise into a water source above sculk and keep it; deep dark patches start from air or a water source (1.19's `SculkPatchFeature.canSpreadFrom`) |

Every one of these checks `Waterlogging.isAvailable()` through Core: without Fluidlogged API, veins and growths stay
out of water exactly as before (they could not keep it), and generation places dry.

Vanilla leaves: 1.19 waterlogs every leaf. Fluidlogged API's own built-in list already waterlogs every
`BlockLeaves`, and Core leaves blocks outside the family to Fluidlogged API and its config, so nothing is added for
them (the test checks all 24 vanilla leaf states hold a source).

Worldgen: mangrove swamps and propagule trees go through `WorldGenMangroveTree` (above); the deep dark's sculk
patches through the spreader (above); ancient cities through Core's `ModernTemplate`, which already places a
template's waterlogged blocks with `placeWithWater`. Nothing else we generate is waterloggable (swamp patches only
go into air; disks are mud and clay).

`build.gradle`: `-PnoFluidlogged` runs `runServer`/`runClient` without Fluidlogged API on the classpath (compiled
against it, as the release jar is), as Caves & Cliffs' and Nether's builds do.

## Differences from 1.19.2

*   Water kept in **leaves** (ours and vanilla's) flows out of them: Fluidlogged API's list gives every
    `BlockLeaves` `canFluidFlow: true`, and it is asked before the block. 1.19's full-cube leaves keep it. Asked of
    Backport Core (`docs/CORE_REQUESTS.md`, "Wl"). Roots keep their water, as 1.19's.

## Tested

`-Pselftest=waterlog` (`WaterlogSelfTest`, `WaterlogFluidChecks`), with Fluidlogged API:

*   Exactly the blocks 1.19.2 waterlogs are `Waterloggable` (door, fence gate, button, pressure plate, frogspawn,
    potted propagule, muddy roots, catalyst and mud are not).
*   All 1,276 states of ours: 1,112 take a water source, every other state none, and none takes flowing water or
    lava. All 24 vanilla leaf states take a source.
*   Placed into a source, each kind keeps it (stairs, fence, trapdoor, both signs, leaves, roots, mud brick stairs
    and wall, sensor, shrieker, both slabs, oak leaves, a vein); double slabs, the fence gate, the door and the
    catalyst replace it; breaking waterlogged stairs leaves the source.
*   Placed into flowing water, stairs, roots, leaves, sensor and wall replace it; a spring does not run into a dry
    fence, leaves or shrieker; waterlogged roots keep their water in; a bottom slab lets it out (leaves: logged).
*   Buckets fill and empty stairs, roots, a sensor and leaves; on a double slab the water goes on top.
*   A dry sensor clicks on activating and stopping; a waterlogged one does neither, but activates and keeps its
    water.
*   Propagules: placed into water on mud and kept; a hanging one under submerged leaves keeps its source and hangs
    on; without its leaves it drops and leaves its water.
*   A mangrove grown in a three-deep pool over mud: every root below the surface holds a source, no log does (one
    run: 19 waterlogged roots, 13 waterlogged leaves).
*   Sculk: a vein regrows into a water source and keeps it; with its last face gone it leaves the water; flowing
    water takes no vein; a growth may rise into a source above sculk but not into flowing water.

Without Fluidlogged API (`-PnoFluidlogged`): no block of ours, nor vanilla leaves, holds water; stairs placed into
water replace it; no vein or growth goes into water and the water stays; `placeKeepingWater` places roots dry.

Whole suite, fresh world each run (`-Pselftest=all`, then `python -I tools/check_assets.py`):

| Run | Result | Failures |
| --- | --- | --- |
| Fluidlogged API, no Caves & Cliffs | 2431 pass, 1 fail | deepdark: floating veins (below) |
| `-PnoFluidlogged`, no Caves & Cliffs | 2350 pass, 3 fail | deepdark: floating veins, ladders/carpets dropped, 7 rock in the beard (all three on main too, below) |
| Fluidlogged API, Caves & Cliffs `cc-goat-remap` jar | 2413 pass, 11 fail | scaffold 1, allay 7, lookfeel 1 (they assert Caves & Cliffs is absent), deepdark 2 (floating veins; Caves & Cliffs' geodes counted as beard rock) |
| `-PnoFluidlogged`, Caves & Cliffs jar | 2333 pass, 12 fail | the same, plus deepdark's dropped items |

`goats` and `waterlog` have no failure in any of the four. After the last fix (the tree's block flags, identical
with Forge's default config): `waterlog,mangrove,swamp,goats` 887 pass, 0 fail. `tools/check_assets.py`: 0 errors
with Caves & Cliffs absent (62 blocks, 1,195 states, 68 item models, 787 sound files) and present (38 blocks, 365
states, 48 item models).

**The deep dark's floating veins** (`deepdark`: "sculk veins rest on something", at most 1 in 200). The run is
deterministic per build, so main 22c8339 was measured beside this branch on the same seed and Core jar:

| Build | Fluidlogged API | Veins | Floating | Result |
| --- | --- | --- | --- | --- |
| main 22c8339 | yes | 17,726 | 85 (limit 88) | pass |
| Gt only (2abba15) | yes | 17,726 | | pass |
| this branch | yes | 17,028 | 95 (limit 85) | fail |
| this branch, sculk and mangrove changes rolled back | no | 14,538 | 96 | fail (3) |
| this branch | no | 14,538 | 96 | fail (3) |

Without Fluidlogged API the result does not depend on this branch at all: main never had a `-PnoFluidlogged`
option, and run truly without Fluidlogged API (the rolled-back row) it gives the same 14,538 veins and the same three
failures. With it, Wl changes the deep dark as 1.19 does (veins and growths take water sources, templates' waterlogged
sculk keeps its water), and that moves a check main passes by 3 veins over its line. Every floating vein is the same
case: a vein at chunk-local x = 7 resting east on rock across the populate edge that the city's beard carved away
afterwards (the stream DD note's "carved after neighboring chunks were decorated"); none is in or next to water.
Left as it is for stream DD, not loosened here.

## Not tested

*   Client: water drawn inside our models (Fluidlogged API draws it).
*   Water sources forming between two waterloggable blocks (Core's tests cover source forming for its own blocks).
*   A real deep dark or mangrove swamp generated around water: the generators' placement calls are tested through
    the tree and the sculk spreader, not over a generated region.
*   Waterlogged blocks outside y 0-255 in Caves & Cliffs' extended worlds (Core's work).
