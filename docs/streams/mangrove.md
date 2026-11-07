# Stream M: mangrove wood

Package `mangrove`. Config `mangrove` (`enabled`). Branch `wu-mangrove`.

## Changelog section

*   **Mangrove wood**: mangrove logs, wood, stripped logs and stripped wood, planks, stairs, slabs, fences,
    fence gates, doors, trapdoors, buttons, pressure plates and signs. An axe strips mangrove logs and wood.
*   **Mangrove leaves** drop only sticks, never a sapling. Bone meal on leaves with air below grows a new
    hanging propagule.
*   **Mangrove propagules** hang from mangrove leaves and grow there in five stages; a fully grown one can be
    picked. Planted on grass, dirt, clay, farmland or mud - in water too - a propagule grows into a mangrove
    (usually the tall kind), with roots arching down into the water and the mud. Propagules fit in flower pots.
*   **Mangrove roots** and **muddy mangrove roots** (mud and roots, shapeless).
*   The wandering trader sells a mangrove propagule for 5 emeralds.
*   With a composter installed, leaves, propagules and roots compost at 30%.

## How it works

*   **Vanilla classes underneath**: logs extend `BlockLog`, leaves `BlockLeaves`, the propagule `BlockBush`,
    and the stairs, fence, gate, door, trapdoor, button and plate are one-line subclasses of the vanilla
    classes oak's are, so every rule they follow is vanilla's code.
*   **The tree** (`WorldGenMangroveTree`) is 1.19.2's `mangrove` and `tall_mangrove` features rewritten for
    1.12.2: upwards-branching trunk (2-7 logs, tall 4-14, a branch off half the logs), random-spread
    canopy (70 leaves tried around each branch end), the mangrove root placer (trunk raised 1-3 blocks, tall
    3-7; four root arms grow down and out until they reach ground, 15 blocks at most, skewing outwards past 5;
    roots in mud become muddy roots), vines on 12.5% of leaf sides, hanging propagules (random age) under 14%
    of leaves with two air blocks below, moss carpet on half the roots' tops when Caves & Cliffs is installed,
    an (empty) bee nest on 1% of trees when Buzzy Bees is installed. Roots that find no ground in reach cancel
    the whole tree, as 1.19's.
*   **Leaf range**: a mangrove canopy reaches 6 blocks from its logs, past 1.12.2's 4. Our leaves use 1.19's
    rule (a log within 6 steps through leaves), start a check when a neighbor changes, so felling a tree still
    clears its canopy; the generator removes the few strays beyond 6 that 1.19 would leave to decay.
*   **Propagule states**: `hanging`, `age` (0-4), `stage` (0-1, not in the blockstate file); metadata standing
    `stage`, hanging `8 + age`. A standing propagule is age 4, as 1.19 places it. Growth: 1 in 7 random ticks
    with no light check (1.19's override); bone meal 45%; 85% of trees are tall. Hanging: one age per random
    tick; only age 4 drops.
*   **Signs**: tile `MangroveSign.Tile extends TileEntitySign` under its own class for the renderer
    (`client/MangroveSignRenderer`, vanilla's drawing with the standing test made an `instanceof`); dyeing and
    other sign tools see a vanilla sign.
*   **Potted propagule**: 1.12.2's flower pot draws its plant from a fixed list, so, as 1.13+ does for every
    potted plant, a propagule turns an empty vanilla pot into `potted_mangrove_propagule`; an empty hand turns
    it back.
*   **Vanilla recipes**: Forge turns vanilla's sign, trapdoor, button and pressure plate recipes into "any
    planks" recipes registered before ours, so mangrove planks would craft oak ones. `common/VanillaRecipes`
    makes those four refuse mangrove planks in place (same recipe, same name); sticks, chests and crafting
    tables still take any planks.
*   **Numbers** (1.19.2): planks, slab, fence, gate hardness 2 / resistance 3; logs and wood 2 / 2; door and
    trapdoor 3; button and plate 0.5; sign 1; leaves 0.2; roots and muddy roots 0.7. Flammability: planks set
    5/20, logs 5/5, roots 5/20, leaves 30/60. Fuel: slab 150, door and sign 200, button and propagule 100,
    everything else wooden 300 (roots included). Map colors: red; log bark and roots podzol.
*   **Not waterlogged yet**: 1.19 waterlogs propagules, roots, leaves, slab, stairs, fence, trapdoor and signs.
    Until the family's waterlogging pass, a propagule or root placed in water takes the water's place.

## Tested headlessly

`./gradlew runServer -Pselftest=scaffold,mangrove` (port 25862), then `python tools/check_assets.py`:
**`SELFTEST DONE pass=558 fail=0`**, **21 blocks, 172 states, 18 item models, 68 sound files, 0 errors**.
`python tools/lang/vanilla_keys.py`: all 24 block and item names are 1.19's own.

*   Registration of all 21 blocks and their items (none for the double slab and wall sign); every metadata value
    round-trips; signs stack to 16.
*   Flammability, fuel times, blast resistance, charcoal from logs, ore dictionary names.
*   Every recipe, including vanilla sticks and the crafting table from mangrove planks and the four vanilla
    recipes that must not take them.
*   A real player: an axe strips logs and wood (axis kept, axe worn); a door places both halves and drops one
    door; two slabs make a double slab that drops two; signs stand on top and hang on a side with a
    `TileEntitySign` tile; a propagule goes into a flower pot and back out.
*   Leaves: about 2% drop 1-2 sticks and nothing else; shears take the block; placed leaves never decay; bone meal
    grows a hanging propagule, which grows one age per random tick, drops only at age 4, and falls without its
    leaves.
*   Soil: propagules stand on grass, dirt, clay and farmland, not stone or sand.
*   Six propagules in water grown with bone meal: all became trees with logs, leaves and roots reaching the
    floor; no leaf of a fresh tree decays; a felled tree's canopy decays completely. The generator on dry grass:
    10 of 10; with no ground in reach: refused.
*   The wandering trader trade is registered with Backport Core (5 emeralds, 8 uses).

## Not tested

*   Anything visual: models, the leaves' tint and fancy/fast switch, the sign renderer and its editing screen.
*   Sounds playing; the wandering trader actually offering it (needs Village & Pillage); composting (needs the
    composter); moss carpet and bee nests in trees (need Caves & Cliffs and Buzzy Bees).
*   Mangrove trees in world generation: placing them is stream Sw (mangrove swamps).
*   The 18 language files: generated with the family's language tool in a later languages pass.
