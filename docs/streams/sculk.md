# Stream S: sculk

Package `sculk` (generation in `sculk.gen`, client effects in `sculk.client`). Config `sculk` (`enabled`). Packet 25.
Port 25865. Branch `wu-sculk`. Built on stream G's game events (`gameevent`).

## Changelog section

*   **Sculk**, **sculk veins**, **sculk catalysts**, **sculk sensors** and **sculk shriekers**, as in 1.19. All five
    drop only with Silk Touch; otherwise sculk gives 1 experience and the catalyst, sensor and shrieker 5. Hoes mine
    them fastest.
*   When a mob dies within 8 blocks of a sculk catalyst, the catalyst blooms and takes the mob's experience: instead of
    orbs, a charge creeps out through the blocks around it, turning stone, dirt, sand, netherrack and the like into
    sculk, covering edges with veins, and spending itself on new sculk sensors and, rarely, shriekers.
*   **Sculk sensors** hear vibrations within 8 blocks and give a redstone signal for 2 seconds, stronger the closer
    the vibration; a comparator reads what kind of vibration it was. Walking on one sets it off, sneaking or not.
*   **Sculk shriekers** shriek when a player steps on one or sets off a sensor near it. A shrieker made by the world
    warns everyone near it; the fourth warning summons a warden and every shriek that answers a warning brings
    Darkness. Placed shriekers only shriek. New game rule `doWardenSpawning`.
*   Advancements **It Spreads** (kill a mob near a sculk catalyst) and **Sneak 100** (sneak near a sculk sensor or a
    warden so it does not hear you).

## How it works

### Numbers (1.19.2's code, `G:\MCDownloads\wu-1.19.2-src`)

| Block | Hardness / resistance | Light | Drops | Shape |
| --- | --- | --- | --- | --- |
| sculk | 0.2 / 0.2 | 0 | Silk Touch, else 1 xp | full |
| sculk vein | 0.2 / 0.2 | 0 | Silk Touch: one per face; else nothing | faces, no collision, destroyed by pistons |
| sculk catalyst | 3 / 3 | 6 | Silk Touch, else 5 xp (**now:** 1.19.1) | full |
| sculk sensor | 1.5 / 1.5 | 1 | Silk Touch, else 5 xp | half block, not pathfindable |
| sculk shrieker | 3 / 3 | 0 | Silk Touch, else 5 xp | half-block collision |

All five are 1.19's `Material.SCULK` (no tool needed) and `#mineable/hoe`: 1.12.2's hoes are not tools, so
`SculkContent.breakSpeed` gives a hoe its material's efficiency (wood 2 ... gold 12) plus Efficiency's
`level^2 + 1`. Sounds are 1.19's five sound types and events.

### Sculk veins (`BlockSculkVein`, `TileSculkVein`, `ItemSculkVein`)

Six faces are 64 states; 1.12.2's metadata holds 16, so the faces are a byte in the tile entity (`Faces`, bit per
`EnumFacing.getIndex()`, the layout asked of Core as request 3) and come back through `getActualState`; the blockstate
is 1.19's multipart file. `BlockSculkVein.set` is the one way faces change (it sends the tile entity to clients).
A face whose support goes is removed (no drop), as 1.19's `updateShape`. The item places as 1.19's multiface
placement: clicked face first, then the player's looking directions nearest first; into a vein with a free face it
adds a face. Any other block placed into a vein replaces it.

### The spread (`SculkSpreader`, `SculkBehaviour`, `VeinSpreader`, `BlockSculk`)

A line-for-line port of 1.19.2's `SculkSpreader`, `SculkBehaviour`, `SculkBlock`, `SculkVeinBlock`'s charge methods
and `MultifaceSpreader` with the vein's spreader config:

*   The catalyst's spreader: growth cost 10, no growth within 4 blocks, decay roll 1 in 10, extra decay 1 in 5. World
    generation's: 50, 1, 5, 10. At most 32 cursors; a charge over 1000 splits; cursors on one block merge up to 1000
    (not in world generation).
*   A cursor on any block first tries to grow veins there (into the same space when it has no faces yet, or regrow
    the faces it travelled on); on a vein it turns the block the vein sits on into sculk if that block is
    `#sculk_replaceable` (cost 1; the vein then retreats from faces touching sculk), else halves on a roll; on sculk
    it grows a sensor (one time in 11 a shrieker: `can_summon` only in world generation) above if the spot is air and
    fewer than three growths are within 4 across and 2 up, paying the growth cost, else decays with distance from the
    catalyst (`max(1, charge * min(1, (d - 4)^2 / 20^2) * 0.5)`), or by 1 inside the no-growth radius.
*   Cursors move to one of the 18 face- and edge-neighbours that is sculk or a vein (preferring a vein on replaceable
    ground) when the move is not blocked by sturdy faces; charge particles (level event 3006) go to clients in one
    packet per step.
*   `#sculk_replaceable` (`SculkTags`): stone, granite, diorite, andesite, dirt (all three), grass, mycelium,
    terracotta (plain and stained), netherrack, sand (both), gravel, soul sand, clay, end stone, plain sandstone and
    red sandstone, our mud and muddy mangrove roots, and Caves & Cliffs' deepslate, tuff, rooted dirt, moss, calcite,
    smooth basalt and dripstone block by name. World generation adds the deepslate building blocks.
*   Saved as 1.19 saves it (`cursors`: `pos` int array, `charge`, `decay_delay`, `update_delay`, `facings`).

### The catalyst (`BlockSculkCatalyst`, `TileSculkCatalyst`, `SculkExperience`)

An immediate game-event listener (radius 8) for `entity_die`, so it hears the death before anything drops. A living
thing that has experience to give gives all of it to a new charge at its feet: monsters always (baby zombies too),
the tadpole never, anything else when it is not a baby; whether a player killed it does not matter (1.19). The
amount is the mob's own `getExperiencePoints` (SRG `func_70693_a`); 1.12.2 drops orbs 20 ticks after death, and
`LivingExperienceDropEvent` cancels that drop for mobs a catalyst took. The player who last hurt it earns *It Spreads*
(`wildupdatebackport:kill_mob_near_sculk_catalyst`). The catalyst blooms for 8 ticks with two souls and its sound.
Only the closest catalyst takes a death.

### The sensor (`BlockSculkSensor`, `TileSculkSensor`)

Stream G's `VibrationListener` (radius 8, `#vibrations`, *Sneak 100* allowed). It ignores its own block being placed
or broken and listens only while inactive. Heard: `active` for 40 ticks with power `max(1, 15 - floor(d / 8 * 15))`,
then `cooldown` 1 tick, then `inactive` (1.19.2's 40 and 1; current Java's 30 and 10 come with 1.20's calibrated
sensor rework and are Trails & Tales'). The comparator reads the last vibration's frequency, current Java's table
(`GameEvent.frequency`, **now:**). Stepped on: full power, step's frequency; not by a warden (by name). 1.12.2 skips
`onEntityWalk` for sneaking players, so the player tick steps them on sensors and shriekers too. The power lives in
the tile entity (phase x power is 48 states; no model uses power). Active sensors give off dust in sculk's cyan
(1.12.2's dust has one color, not 1.19's cyan-to-red fade).

### The shrieker (`BlockSculkShrieker`, `TileSculkShrieker`, `WardenSpawnTracker`)

Listens (radius 8) to `sculk_sensor_tendrils_clicking` caused by a player (directly, riding, by projectile, by a thrown
item). Stepped on by a player, or hearing that: if it can respond (`can_summon`, not peaceful, `doWardenSpawning`) it
first needs a warning, else it just shrieks. A warning (1.19.2's `WardenSpawnTracker`): no warden in the 48-block cube
and no player within 16 on cooldown; every player within 16 gets the highest level among them plus one (at most 4),
and a 200-tick cooldown; levels fall one per 12000 ticks without a warning. Stored in each player's Forge data
(`wildupdatebackport:warden_spawn_tracker`; gone on death, as in 1.19). A shriek: `shrieking` 90 ticks, rings and
sound (level event 3007), a `shriek` game event (radius 32). At its end (or when a shrieking one is broken): levels 1-3
play a warden sound somewhere within 10 blocks, level 4 summons a warden (20 tries, 5 across, 6 up and down, standing
on a full top with nothing solid above, no entity in the way) or, if none can be placed, plays the angry one; and
Darkness 13 s to survival and adventure players within 40 blocks.

**Stream W contract**: the shrieker finds the warden as `wildupdatebackport:warden` (entity registry) and Darkness as
`wildupdatebackport:darkness` (potion registry); either absent and that part does nothing. A summoned warden has
`wildupdatebackport:summoned_by_shrieker = true` in its Forge data before it joins the world (1.19's `TRIGGERED`
spawn: W should emerge it). The four warden reply sounds (`entity.warden.nearby_close`, `nearby_closer`,
`nearby_closest`, `listening_angry`) are registered by this stream; W must use them, not register them again.

### Generation (`sculk.gen.SculkPatch`)

1.19.2's `SculkPatchFeature` with `sculk_patch_deep_dark` and `sculk_patch_ancient_city`, world generation's spreader,
the catalyst (1 in 2 on a full block) and the city's rare shriekers that can summon. It needs only a `World` and a
position, so the deep dark (stream DD) and any terrain (Caves & Cliffs' 1.18 worlds included) call this one class;
placement (256 a chunk, cave floors, the deep dark's own vein feature) is DD's.

### Client (`sculk.client.SculkClient`, packet 25)

1.19's sprites stitched into the block atlas: charge particles creeping over the faces a charge travels on with the
bubbling sound, the pop when one is spent, the catalyst's souls, and the shriek's rising rings (two quads tilted 60
degrees, as 1.19 draws them).

## Gaps

*   Waterlogging (veins, sensors and shriekers): stream Wl. Until then veins never spread into water and growths need
    air above, where 1.19 also uses water.
*   1.16's nylium, basalt, blackstone and soul soil are not in `#sculk_replaceable` (no 1.12.2 blocks to name).
*   The active sensor does not glow (1.19's emissive rendering is per state); the charge particle's roll on up and
    down faces is not drawn.
*   Amethyst resonance (calibrated sensors) and the 1.20 sensor timings: Trails & Tales.

## Tested headlessly

`./gradlew runServer -Pselftest=all` (port 25865), then `python tools/check_assets.py`:
**`SELFTEST DONE pass=798 fail=0`** (154 of them this stream's), assets 0 errors (33 blocks, 354 states). The release
jar (`./gradlew build --rerun-tasks`) carries SRG names and no test classes.

*   Blocks: hardness, resistance, light, shapes, items, metadata round trips, the game rule, the trigger and both
    advancements loaded, `#sculk_replaceable` samples, a diamond hoe's 8x speed.
*   Drops: no item without Silk Touch and the right experience; itself with Silk Touch; a three-faced vein gives
    three; a `can_summon` shrieker drops a plain one.
*   Veins: placed by the item on the clicked face, a second face added, `getActualState`, `Faces` saved and read, a
    face lost with its support, the vein gone with its last face, no drop.
*   Spread rules (each with a fixed random): growth costs 10 and grows a non-summoning shrieker on the first roll; no
    growth near the catalyst (decay 1); three growths near stop growth and the distance decay applies (100 -> 96 at
    10 blocks); no roll keeps the charge; a vein turns stone to sculk for 1; on obsidian it halves; discharge retreats
    from sculk; splitting at 1000, the 32-cursor cap, saving; a full run of 60 from a catalyst lays sculk and veins
    (60 sculk, 80 veins with the test's seed), grows nothing within 4 blocks and ends.
*   Catalyst: a zombie killed 2 blocks away gives its 5 as a charge at its feet, the catalyst blooms and stops, no
    orbs drop 20 ticks later; a baby animal gives nothing (bloom still), a baby zombie its 12; 12 blocks away is not
    heard; cursors saved.
*   Sensor: a vibration 3 blocks away gives power 10 and the comparator eat's frequency, neighbors powered, deaf while
    active, cooldown then inactive, ignores its own placing, stepping gives 15 and step's frequency, a sneaking player
    standing on it sets it off, a sneaking player's step nearby is not heard, a vibration in flight is saved.
*   Shrieker: a step makes it shriek and a `shriek` event is heard 10 blocks away; placed ones never warn; generated
    ones warn 1-4 with the 200-tick cooldown, stop at 4, fall to 3 after 12000 ticks, share the highest level with a
    second player; the level-4 response without a warden does not fail; `doWardenSpawning` off shrieks without warning;
    a sensor clicking because of a player makes it shriek, without a player it does not; the warden spawn spot search.
*   Generation: an ancient-city patch on a cave floor spreads sculk and veins (235 and 105 with the test's seed, and a
    shrieker that can summon); a patch in open air is not placed.

## Not tested

*   Everything drawn: models, the vein's faces on the client, particles, the shriek rings (client only).
*   A real warden summon and Darkness (stream W does not exist yet; both paths are null-safe and the level-4 response
    is run).
*   Players in multiplayer sharing warning levels over time, beyond the two-player check.
