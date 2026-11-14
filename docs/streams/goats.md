# Stream Gt: goats, copied when Caves & Cliffs is absent

Package `goat`. Config `goats` (`enabled`, `spawnWeight`, `spawnBiomes`, `ramming`: the owner's four settings).
Entity `wildupdatebackport:goat` (id 25; 26-29 unused). No packets (70-74 unused). Branch `wu-goatwl`. Port 25874.
Caves & Cliffs side: branch `cc-goat-remap` (one commit, `GoatContent.remapCopy`).

## Changelog section

*   **Goats** without Caves & Cliffs Backport: when it is not installed, Wild Update Backport brings Caves & Cliffs'
    own goat (and screaming goat), so goat horns can be had in any game. It is the same goat: ramming, long jumps,
    milk, wheat, kids, 10 less fall damage, its sounds, its spawning in the mountains and its settings, and the
    *Whatever Floats Your Goat!* advancement. With Caves & Cliffs installed, its goats are used and none of ours are
    registered.
*   Adding Caves & Cliffs Backport to a world later turns these goats into its own, with their horns, screams,
    names and cooldowns.
*   **Goat horns** from goats: a goat that rams a log, stone, packed ice, or iron, coal, copper or emerald ore loses
    a horn (1.19). Works with either goat.

## How it works

Family rule 1: Caves & Cliffs (1.17) owns goats; Wild Update needs them for goat horns, and every family mod works
on its own. So this stream registers an identical copy only when `cavesandcliffsbackport` is not loaded
(`GoatContent.enabled()`: `goats.enabled && !Owners.present(CAVES_AND_CLIFFS)`), the way `deepdark.DeepslateCopies`
copies deepslate and Update Aquatic's `woodset` copies Village & Pillage's woods.

*   **Code**: Caves & Cliffs' `goat` package (main, 7311c71) copied with only the mod id changed: `EntityGoat`,
    `EntityAIGoatRam`, `EntityAIGoatLongJump`, `GoatCondition`, `client/ModelGoat`, `RenderGoat`, `GoatClient`. What
    differs: `GoatContent` registers our sound events itself (Wild Update has no `ModSounds`) and grants the boat
    advancement itself (no `Advancements` class); the spawn check leaves out powder snow, which is Caves & Cliffs'
    and never present while the copy is registered.
*   **Ids that remap in place**: the copy is `wildupdatebackport:goat` with the owner's entity class layout, data
    parameters and saved tags (`IsScreamingGoat`, `HasLeftHorn`, `HasRightHorn`, `Brain.memories` with
    `minecraft:ram_cooldown_ticks` and `minecraft:long_jump_cooling_down`). Caves & Cliffs' new
    `GoatContent.remapCopy` (`RegistryEvent.MissingMappings<EntityEntry>`) remaps `wildupdatebackport:goat` onto its
    own goat; Forge keeps the old name as an alias (`ForgeRegistry.addAlias`), so chunks and spawn eggs naming our
    goat load the owner's, and the tags read as they were written. Entities have no states or metadata, so this
    is the whole remap.
*   **Assets** (`tools/goats/generate.py`, from Caves & Cliffs' own assets): the 17 `entity.goat.*` sound events
    with their files, volumes and subtitles, `textures/entity/goat/goat.png`, the English lines, the boat
    advancement (`husbandry/ride_a_boat_with_a_goat`, condition `wildupdatebackport:goats`, true only while the
    copy is registered).
*   **Horns** (`items.GoatHorns`): 1.19.2's `RamTarget` block hit, added from outside to whichever goat the game
    has, found by registry name (`cavesandcliffsbackport:goat` or `wildupdatebackport:goat`) with reflection on its
    horn and ram methods, so neither goat's code needs Wild Update's classes and the two streams never call each
    other. While a goat charges with its head lowered and no mob in its box (1.19 hits a mob first), a
    `#snaps_goat_horn` block ahead (or above that) plays the goat's ram impact (its own namespace's sound), takes a
    horn (the one left, or either), drops our goat horn (`GoatHorns.drop`, 1.19's throw; instrument from the goat's
    UUID, screaming or regular) with 1.19's horn-break sound, and ends the ram with a full cooldown. With goat horns
    switched off no horn is lost.

`build.gradle`: `-PqueryConfirm` answers Forge's startup questions with yes on `runServer` (for the two-run
carry-over test, where Caves & Cliffs' arrival leaves our deepslate copies missing).

## Tested

`-Pselftest=goats` (`GoatSelfTest`), with and without Caves & Cliffs' `cc-goat-remap` jar in `eclipse/mods`:

*   Without Caves & Cliffs: our goat is registered as `EntityGoat`, id 25, tracked 80/3/velocity, with a spawn egg,
    the boat advancement and the 17 sound events; spawns in extreme hills at weight 10, groups 1-3; 0.9 x 1.3; saves
    and reads the owner's tags; milked into a milk bucket; breeds with wheat into a goat kid.
*   With Caves & Cliffs: nothing of ours is registered (no goat, advancement or sounds); Caves & Cliffs' goat reads a
    saved copy's tags (screaming, horns, both cooldowns) as its own; Caves & Cliffs has `remapCopy`.
*   Both ways, for a regular and a screaming goat: ramming stone loses one horn, dropping one goat horn of the
    right kind, the goat's own instrument by UUID; the ram ends (head up, cooldown); a second ram takes the other
    horn; a hornless goat drops nothing and still ends its ram; planks snap nothing; with an armor stand in its box
    the log is not rammed, without it the log snaps a horn.

`-Pselftest=goatcarry` (`GoatCarrySelfTest`, not in `all`), one world over two runs: without Caves & Cliffs a
screaming, right-horned goat named "carried" is saved; then with Caves & Cliffs' jar and `-PqueryConfirm` it loads
as `cavesandcliffsbackport:goat` with those tags and name, and `wildupdatebackport:goat` resolves to the owner's
entry. Run 1 (`goats,goatcarry`): 40 pass, 4 fail (a test bug since fixed: killed horn items were still counted
before the next tick); run 2: 43 pass, 1 fail (the goats check that our name was unregistered, while in a carried world
it is the owner's alias; since fixed), and every carry check passed.

In the whole suite (`-Pselftest=all`, Fluidlogged API on and off, Caves & Cliffs absent and present) `goats` never
failed; the counts are in `docs/streams/waterlog.md` (both streams were tested together).

## Not tested

*   Client: the copied renderer and model draw (they are Caves & Cliffs' unchanged); sounds heard in game.
*   A goat ramming by its own AI into a block in a live world: the test drives the ram's block half directly (head
    lowered, moving), as the AI leaves it, rather than waiting for a ram cooldown.
*   Caves & Cliffs' goat advancement progress does not carry over from ours (different advancement ids).
*   Caves & Cliffs does not yet remap our other copies (the deepslate set) onto its blocks; that is the same kind
    of entry for blocks and items, a follow-up for its owner.
