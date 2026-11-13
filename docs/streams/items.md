# Stream I: items

Package `items`. Config `items` (`enabled`). Packet 45. Branch `wu-items`.

## Changelog section

*   **Recovery compass**: a compass ringed by eight echo shards. It points to where you last died, in the
    dimension you died in; anywhere else, or before your first death, it spins. Your last death location is
    remembered through death and saved with you.
*   **Echo shards**, **disc fragments** and **Swift Sneak** books: found in stronghold and dungeon chests until
    ancient cities arrive (at the ancient city's rate).
*   **Music Disc 5** (Samuel Åberg), crafted from nine disc fragments.
*   **Swift Sneak** I-III on leggings: you sneak at 45%, 60% or 75% of walking speed instead of 30%. It is a
    treasure enchantment: never from enchanting tables, fishing, random loot or librarians.
*   **Goat horns**: eight instruments (Ponder, Sing, Seek, Feel, Admire, Call, Yearn, Dream), heard 256 blocks
    away, 7 seconds between toots. Found in pillager outpost chests (with Village & Pillage), and dropped by
    goats (with Caves & Cliffs) that ram into stone, logs, packed ice, or iron, coal, copper or emerald ore:
    regular goats drop the first four, screaming goats the last four.

## How it works

*   **Last death location** (`LastDeathLocation`): set on `LivingDeathEvent` (lowest priority, not when
    cancelled; fake players never) to the player's block and dimension, as 1.19's `die`; copied on
    `PlayerEvent.Clone` (death and the End's exit alike, as 1.19's `restoreFrom`); saved in the player's Forge
    data as `LastDeathLocation: {dimension: int, pos: [I; x, y, z]}` (1.19's tag with 1.12.2's numeric
    dimension); sent to the player's own client (packet 45) on login, respawn and dimension change, as 1.19's
    login and respawn packets carry it.
*   **Recovery compass needle** (`client/RecoveryCompassAngle`): 1.19's `CompassItemPropertyFunction` with its
    wobble, on 1.12.2's `angle` property and 1.19's 32 models. Only the local player's own location exists on
    a client (as in 1.19), so held by another player or in an item frame it spins.
*   **Music disc 5**: 1.12.2's `ItemRecord` (jukebox, "Now Playing", rare name, record stat), description
    `item.record.5.desc`. Its comparator signal (15) is kept in `comparatorSignal()`, the name Caves & Cliffs'
    discs use; 1.12.2's jukebox computes the signal from item ids, so a modded disc's signal is wrong until the
    family's jukebox patch reads it (stream C's jukebox work).
*   **Swift Sneak** (`EnchantmentSwiftSneak`): 1.19.2's numbers (very rare, leggings, enchantability
    25 x level to +50, max III, treasure). Movement is client-side in both versions: `InputUpdateEvent` runs
    right after 1.12.2 multiplied sneaking input by 0.3 and before an item in use slows it, and rescales it to
    `clamp(0.3 + 0.15 x level, 0, 1)`, 1.19's `LocalPlayer.aiStep`.
*   **Where Swift Sneak cannot come from** (`Discovery`): 1.19's `isDiscoverable = false` and
    `isTradeable = false`. 1.12.2 has neither flag, so:
    *   enchanting tables and `enchant_with_levels` (fishing treasure, mob gear, `addRandomEnchantment`) go
        through `EnchantmentHelper.getEnchantmentDatas`, which asks `canApplyAtEnchantingTable` (false) and,
        for books, `isAllowedOnBooks` (false). Anvils ask `canApply`, still true on leggings;
    *   list-less `enchant_randomly` (mineshaft, temple, mansion books...) picks any enchantment in 1.12.2:
        every such function in every loot table is swapped as the table loads (`LootTableLoadEvent`, by
        reflection on the table's pools, entries and functions) for one that skips hidden enchantments.
        Functions that list their enchantments are left alone;
    *   trades: 1.12.2's librarian (and Village & Pillage's, which runs the same trade) picks any enchantment.
        Before a merchant's offers are shown (`PlayerInteractEvent.EntityInteract` on an `IMerchant`), a book
        naming Swift Sneak is re-rolled in place with 1.12.2's book formula (random tradeable enchantment,
        random level, `2 + rand(5 + 10 x level) + 3 x level` emeralds, doubled for treasure, at most 64).
        `ItemFishedEvent` is not needed: fishing already goes through `getEnchantmentDatas`.
*   **Goat horns** (`ItemGoatHorn`, `Instrument`): 1.19's `InstrumentItem`. The `instrument` tag holds
    `minecraft:<name>_goat_horn`; no tag plays Ponder (the first of `#goat_horns`). Use: the instrument's sound
    at volume 256 / 16 in the Records category (1.12.2's attenuation and its server send radius both scale by
    16 x volume, so 256 blocks), the `instrument_play` game event (stream G), the hand in use for 140 ticks, and
    a 140-tick cooldown on the goat horn item (all horns, as 1.19's is per item). The creative menu lists all
    eight. The `tooting` model override (1.19's) shows while the horn is in use.
*   **Goat horns from goats** (`GoatHorns`): `create(screaming, uuid)` is 1.19's `Goat.createHorn` exactly
    (`new Random(uuid.hashCode())`, one of the four regular or screaming instruments), `drop` throws it as
    `dropHorn`. This is the supplier for Core request 6 and for stream Gt's goat. Caves & Cliffs' goat does not
    yet end a ram on a block, so until it calls the hook a local shim adds 1.19's `RamTarget` block hit from
    outside: by entity name and reflection (`CORE_REQUESTS.md` request 6 lists the methods), a charging goat
    whose next block (or the one above) is in 1.19.2's `#snaps_goat_horn` (natural logs incl. mangrove, stone,
    packed ice, iron/coal/copper/emerald ore) plays its ram impact, loses a horn (random side if both; with
    1.19's horn-break sound), and ends the ram with a full cooldown. Any missing method: logged once, no drops.
*   **Loot** (`ItemsLoot`, everything in one place):
    *   Village & Pillage's `villagerbackport:chests/pillager_outpost`, by name: 1.19's pool, 0-1 rolls of a
        regular goat horn (`wildupdatebackport:set_instrument`, a JSON loot function too, with 1.19's
        `options`: `#minecraft:regular_goat_horns`, `#minecraft:screaming_goat_horns`, `#minecraft:goat_horns`).
    *   *Decided* 3's temporary ancient-city fallback: stronghold corridor, crossing and library chests and
        dungeon chests get a pool of 5-10 rolls (the city chest's) over echo shards (weight 4, 1-3), disc
        fragments (4, 1-3), a Swift Sneak book (3, I-III) and nothing (77): with the city chest's total weight
        of 88, exactly its rate for these three. `ItemsLoot.FALLBACK` and `addAncientCityFallback` are removed
        in the commit that adds ancient cities. Disc 5 itself is never loot (1.19.2: crafted only).
*   **Numbers** (1.19.2): echo shard, fragment and recovery compass stack to 64; disc and horn to 1; disc rare.

## Tested headlessly

`./gradlew runServer -Pselftest=all` (port 25869), then `python tools/check_assets.py`:
**`SELFTEST DONE pass=743 fail=0`** (the `items` test alone: 99 checks), **28 blocks, 282 states, 37 item models,
171 sound files, 0 errors**. `python tools/lang/vanilla_keys.py`: every name, description, instrument and the
enchantment are 1.19's. Release jar (`./gradlew build --rerun-tasks`) carries `func_` names.

*   All five items, Swift Sneak and the ten sound events registered; stack sizes; disc 5 rare, signal 15, plays
    `music_disc.5`.
*   Recipes: recovery compass (compass + 8 echo shards), disc 5 (9 fragments).
*   Last death location: set on death to the player's block, kept by the respawned player (`Clone`), saved and
    read back with the player, carried by packet 45 (and its "none"); fake players keep none.
*   Swift Sneak: max III, very rare, leggings, enchantability 50-100 at II, treasure, hidden; an anvil may put it
    on leggings, not a chestplate; `getEnchantmentDatas` never offers it (levels 1-100, books and leggings,
    treasure allowed); sneaking speed 0.3 / 0.45 / 0.6 / 0.75 with none / I / II / III, boots not counted.
*   3000 mineshaft chests: random enchanted books still drop, never Swift Sneak. A merchant offer selling a Swift
    Sneak book is re-rolled to another enchantment at 1.12.2's price; other offers untouched.
*   Goat horns: no tag plays Ponder; the tag is `minecraft:call_goat_horn` and reads back; a bare name reads;
    140-tick use; a goat's horn is 1.19's UUID-seeded draw and the same every time; screaming goats give
    screaming instruments; `#snaps_goat_horn` (stone yes, granite no, logs yes, six-sided wood no, packed ice and
    emerald ore yes, planks no, mangrove log yes). A real player playing a horn: in use, full cooldown; a second
    horn is refused during it; the cooldown ends after 140 ticks.
*   Loot: `set_instrument` is registered for JSON; the outpost pool gives a regular horn about half the time
    (2000 chests); the fallback pool is in all four tables; over 4000 chests, echo shards, fragments and Swift
    Sneak books per chest are within a few percent of the ancient city's (0.68, 0.68, 0.26), books I-III.

## Not tested

*   Anything client-side: the compass needle (pointing, spinning, wobble), the `tooting` model, Swift Sneak's
    actual movement speed (the formula is tested, the `InputUpdateEvent` handler is not driven), the
    packet's arrival on a client, sounds, the subtitle, the jukebox playing disc 5.
*   Caves & Cliffs' goats dropping horns (the shim needs Caves & Cliffs in the run); Village & Pillage's
    outpost table (its pool is tested on an empty table; the by-name hook is not run without V&P).
*   The trade re-roll through a real villager GUI (the re-roll itself is tested on an offer list).
*   The horn's raised-arm pose: 1.12.2 has no horn animation (`EnumAction.NONE`); only the item model changes.
*   The 18 language files (a later languages pass).
