# Stream D: mud

Package `mud`. Config `mud` (`enabled`). Branch `wu-mud`.

## Changelog section

*   **Mud**: use a water bottle on dirt or coarse dirt (or rooted dirt, with Caves & Cliffs) to make mud; a
    dispenser with a water bottle does it too, keeping the glass bottle. You sink into mud a little.
*   **Packed mud** (mud and wheat), **mud bricks** (four packed mud), and **mud brick stairs, slabs and walls**.
*   With Caves & Cliffs installed, mud on a block that has pointed dripstone hanging under it slowly dries into
    clay.
*   **Muddy mangrove roots** (mud and mangrove roots) when the mangrove wood is on.

## How it works

*   **Mud**: dirt's material and hardness (0.5, shovel), a 14-pixel collision box but a full block for
    drawing, holding blocks and redstone, as 1.19's `MudBlock`.
*   **Clay under dripstone**: 1.19 does this from pointed dripstone's random tick (each stalactite segment
    17.6% per tick, never in the Nether). Pointed dripstone is Caves & Cliffs', so mud does it from its own
    random tick at the same combined rate, `1 - (1 - 0.17578125)^n` for `n` segments (at most 11) hanging under
    its support. It looks the dripstone up by name and reads its `vertical_direction` (or `tip_direction`)
    property by name.
*   **Dispensers**: the water bottle's existing dispense behavior is kept and wrapped; only a water bottle
    facing convertible dirt is handled here.
*   **Shared building blocks**: stairs, slab (half and double) and wall come from `common/ModBuilding`, for
    every stream. The wall's cobblestone `variant` stays pinned; 1.19's `none/low/tall` sides become 1.12.2's
    booleans (`low` only), and the never-reached state with neither post nor sides is drawn as a post.
*   **Numbers** (1.19.2): packed mud hardness 1, resistance 3; mud bricks, stairs, slab, wall hardness 1.5,
    resistance 3, pickaxe. Map colors: mud cyan terracotta, packed mud dirt, bricks light gray terracotta,
    slab brown.
*   **Not waterlogged yet**: the slab, stairs and wall, for the family's waterlogging pass.

## Tested headlessly

`./gradlew runServer -Pselftest=scaffold,mangrove,mud` (port 25863), then `python tools/check_assets.py`:
**`SELFTEST DONE pass=572 fail=0`**, **28 blocks, 282 states, 24 item models, 158 sound files, 0 errors**.
`python tools/lang/vanilla_keys.py`: all names are 1.19's.

*   All seven blocks registered; mud's collision 14 pixels; mud bricks' blast resistance 3.
*   Recipes: packed mud, mud bricks, muddy mangrove roots (with the mangrove stream on).
*   A real player's water bottle turns coarse dirt into mud and leaves a glass bottle; podzol is refused.

## Not tested

*   The dispenser behavior (wired at init; not driven by the test), clay under dripstone (needs Caves &
    Cliffs), rooted dirt.
*   Anything visual or audible: models, sounds, splash particles.
*   The 18 language files (a later languages pass).
