# Creative tabs

Wild Update Backport has no creative tab of its own. Each block and item is in the vanilla tab Minecraft 1.19.2 gives it, next to its vanilla neighbor in 1.19.2's order, through Backport Core's creative placement (`creative.CreativePlacement`; the rule and the table are in Backport Core's `docs/CREATIVE_TABS.md`). Items are placed by their vanilla name: `common/Content` places every block with an item and every item, and the main class's pre-init the spawn eggs.

"After" and "Before" are the stacks either side of it with this mod and Backport Core alone, from a dev client's `-Dbackportcore.creativeDump` (an item is put after its nearest 1.19.2 neighbor before it, or before the one after it, whichever is of its kind). With other backports installed an item moves to stay by its 1.19.2 neighbor: stripped logs follow the warped stem once the Nether Update adds it, and so on.

## Building Blocks

| Item | After | Before |
|---|---|---|
| `mud` | `podzol` | `cobblestone` |
| `mangrove_planks` | `dark_oak_planks` | `bedrock` |
| `mud_brick_slab` | `stone_brick_slab` | `nether_brick_slab` |
| `packed_mud` | `chiseled_stone_bricks` | `mud_bricks` (this mod) |
| `mud_bricks` | `packed_mud` (this mod) | `melon` |
| `mud_brick_stairs` | `stone_brick_stairs` | `mycelium` |
| `mangrove_slab` | `dark_oak_slab` | `sandstone_stairs` |
| `mangrove_log` | `dark_oak_log` | `mangrove_roots` (this mod) |
| `mangrove_roots` | `mangrove_log` (this mod) | `muddy_mangrove_roots` (this mod) |
| `muddy_mangrove_roots` | `mangrove_roots` (this mod) | `stripped_mangrove_log` (this mod) |
| `stripped_mangrove_log` | `muddy_mangrove_roots` (this mod) | `stripped_mangrove_wood` (this mod) |
| `stripped_mangrove_wood` | `stripped_mangrove_log` (this mod) | `mangrove_wood` (this mod) |
| `mangrove_wood` | `stripped_mangrove_wood` (this mod) | `acacia_stairs` |
| `mangrove_stairs` | `dark_oak_stairs` | `prismarine` |

## Decoration Blocks

| Item | After | Before |
|---|---|---|
| `mangrove_propagule` | `dark_oak_sapling` | `oak_leaves` |
| `mud_brick_wall` | `mossy_cobblestone_wall` | `anvil` |
| `mangrove_leaves` | `dark_oak_leaves` | `slime_block` |
| `mangrove_fence` | `dark_oak_fence` | `acacia_fence` |
| `mangrove_sign` | `oak_sign` | `white_bed` |

## Redstone

| Item | After | Before |
|---|---|---|
| `mangrove_pressure_plate` | `oak_pressure_plate` | `redstone_torch` |
| `mangrove_trapdoor` | `oak_trapdoor` | `oak_fence_gate` |
| `mangrove_button` | `oak_button` | `trapped_chest` |
| `mangrove_fence_gate` | `dark_oak_fence_gate` | `acacia_fence_gate` |
| `mangrove_door` | `dark_oak_door` | (end of the tab) |

## Transportation

| Item | After | Before |
|---|---|---|
| `oak_chest_boat` | `oak_boat` | `chest_minecart` |
| `spruce_chest_boat` | `spruce_boat` | `birch_boat` |
| `birch_chest_boat` | `birch_boat` | `jungle_boat` |
| `jungle_chest_boat` | `jungle_boat` | `acacia_boat` |
| `acacia_chest_boat` | `acacia_boat` | `dark_oak_boat` |
| `dark_oak_chest_boat` | `dark_oak_boat` | `mangrove_boat` (this mod) |
| `mangrove_boat` | `dark_oak_chest_boat` (this mod) | `mangrove_chest_boat` (this mod) |
| `mangrove_chest_boat` | `mangrove_boat` (this mod) | (end of the tab) |
