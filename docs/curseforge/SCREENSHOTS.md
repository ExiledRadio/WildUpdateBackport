# Wild Update Backport: screenshots

Taken in the Prism instance **Backport Showcase** (OptiFine), world `Wild Update Showcase` (seed 2832, Caves & Cliffs
world type, y -64 to 319, made with 1.19's biomes so mangrove swamps and the deep dark generate; it matches
Chunkbase's 1.19 map for the seed). Every scene is generated terrain: a real ancient city, deep dark and mangrove swamp.
Mobs are posed: frozen (no AI), silent, invulnerable, and they stay put. Paste the teleport command; it puts you on the
scene's redstone block with the exact facing. Time, weather and mob spawning are frozen in this world, and
`doWardenSpawning` is off, so walking past the shriekers summons nothing. Shaders on, F1 to hide the HUD. Save each as
the name below; they get converted to JPEG under 2 MB afterwards. Full notes per scene:
`Releases/screenshot-scenes/SCENES.md`, scenes 44-52.

## Featured image

The first picture on the project page. Upload it to the gallery and mark it as featured. It is not used in the
description.

| File | Teleport | Time | In frame |
| --- | --- | --- | --- |
| `wu-featured` | `/tp @p 210.5 -37 -7.5 58 3` | any | Across an ancient city at y -40 to the reinforced deepslate frame at its center; the warden stands on the plaza in front of it among candles, soul lanterns and sculk. Leave it dark |

## Description images

| # | File | Section | Teleport | Time | In frame |
| --- | --- | --- | --- | --- | --- |
| 1 | `wu-deep-dark` | The deep dark, after the sculk paragraph | `/tp @p 243.5 -48 -78.5 -8.7 15` | any | A deep dark cave: sculk on the floor and walls, sensors, shriekers and catalysts, the city at the far end |
| 2 | `wu-warden` | The deep dark, after the warden paragraph | `/tp @p 191.5 -38 11.5 90 17` | any | The warden close up at the foot of the portal frame, candles and sculk at its feet |
| 3 | `wu-ancient-city` | The deep dark, after the ancient city paragraph | `/tp @p 99.5 -44 13.5 -105.3 3` | any | Down the city's west street: deepslate walls, soul lanterns, candles, sculk, the center rising at the end |
| 4 | `wu-mangrove-swamp` | Mangrove swamps, under the paragraph | `/tp @p 950.5 63 -104.5 170 -3` | `/time set 2000` | From the bay into a mangrove swamp: trees on arching roots in the water, hanging propagules, vines, mud under the canopy |
| 5 | `wu-blocks` | Mangrove swamps, at the end | `/tp @p 885.5 89 -146.5 -12 8` | `/time set 4500` | An adobe hut at the swamp's edge: mud bricks, packed mud, mangrove logs, planks, door, trapdoors, fence and gate, a potted propagule, mud and roots |
| 6 | `wu-frogs` | Mobs, after the frog paragraph | `/tp @p 942.5 63 -112.5 180 8` | `/time set 12800` | Dusk at the swamp's edge: the three frogs by mangrove posts topped with the three froglights, one on a lily pad, frogspawn and tadpoles |
| 7 | `wu-allay` | Mobs, after the allay paragraph | `/tp @p 917.5 63 -115.5 -151.7 -6` | `/time set 3000` | Two allays by a note block on the meadow between the bay and the swamp, one holding an amethyst shard, one a cookie |
| 8 | `wu-items` | Items, under the list | `/tp @p 918.5 63 -105.5 -145 6` | `/time set 10500` | A mangrove dock: a mangrove boat with a chest and a mangrove boat, an armor stand with a goat horn, frames with the recovery compass, echo shard, music disc 5 and a disc fragment |
| 9 | `wu-settings` | The rest of 1.19, at the end | (your own screen) | | Backport Core settings screen on the Wild Update page |

## Markers for DESCRIPTION.md

The integrator puts these where the table says, and replaces each with the image link once the pictures are on the
project's Media page:

```
<!-- PENDING image: wu-deep-dark -->
<!-- PENDING image: wu-warden -->
<!-- PENDING image: wu-ancient-city -->
<!-- PENDING image: wu-mangrove-swamp -->
<!-- PENDING image: wu-blocks -->
<!-- PENDING image: wu-frogs -->
<!-- PENDING image: wu-allay -->
<!-- PENDING image: wu-items -->
<!-- PENDING image: wu-settings -->
```

## Layout on the page

* **The deep dark**: #1, #2 and #3, one under each of its three paragraphs.
* **Mangrove swamps**: #4 under the paragraph, #5 after it (the blocks).
* **Mobs**: #6 then #7. **Items**: #8.
* **The rest of 1.19**: #9, the settings screen, at the end, as on the other pages.
