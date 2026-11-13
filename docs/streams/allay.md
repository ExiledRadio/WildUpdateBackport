# Stream A: the allay

Package `allay`. Config `allay` (`enabled`). Entity `wildupdatebackport:allay` (id 10). No packets (35-39
unused). Branch `wu-allay`. Port 25867.

## Changelog section

*   **Allay**, with its spawn egg. Give it an item and it collects matching items nearby (up to a stack) and
    brings them to you, or to a note block it heard played in the last 30 seconds. Right-click with an empty
    hand to take the item back. It heals over time, never despawns, takes no fall damage and cannot be hurt by
    the player it likes.
*   Allays **dance** near a jukebox that is playing. Give a dancing allay an **amethyst shard** to duplicate it
    (once every 5 minutes; needs Caves & Cliffs Backport's amethyst, or another mod's).
*   Allays live in **woodland mansions** (the four cells of one room, 1-3 each) and in a new **pillager outpost**
    cage (with Village & Pillage Backport).
*   Advancements **You've Got a Friend in Me** and **Birthday Song**.

## How it works

*   **Entity** (`EntityAllay`): 1.19.2's numbers, current Java's rules. 0.35 x 0.6, eyes at 0.6 of its height;
    health 20, flying and movement speed 0.1, attack 2, follow range 48 (1.19.2's; it paths to items 32 blocks
    away and to its player up to 64). 1.19's flight (`travel`: drag 0.91, water 0.8, lava 0.5) and
    `FlyingMoveControl(20, hovers)` (`AllayMoveHelper`): it hovers when idle instead of falling. Heals 1 every
    10 ticks. No fall damage, no step sounds, never despawns. The liked player cannot hurt it and is an ally
    (current Java). Ambient sound with or without an item, volume 0.4.
*   **Items**: right-click with an item gives it one (it likes you now, `item_given`); empty main hand takes it back
    (`item_taken`, its inventory is thrown out, it forgets you; with a full inventory the item is lost, as in 1.19).
    It wants an item when holding the same item (1.12.2 subtypes must match, since 1.19's colors are separate
    items; damage is ignored; potions must match, current Java), its one-slot inventory has room, and
    `mobGriefing` is on. It picks items up from 1 block around its box, never right after throwing one (60 ticks).
    On death the inventory and held item drop (always, as 1.19's `dropEquipment`), except a held item with Curse
    of Vanishing.
*   **Brain** (`AllayBrain`): 1.19's `AllayAi`, its memories and behaviors in vanilla's order each tick. Swim;
    panic at 2.5x while the last damage is remembered (40 ticks); go to the nearest visible wanted item within 32
    (1.75x, sensed every 20 ticks); go to the deposit target and throw one item per tick from within 3 blocks of
    it (2.25x, 20-tick runs, current Java's timeout; items thrown with 1.19's speed 0.2/0.3/0.2 from 0.2 under
    the eyes, the throw sound at 1.19's pitches on 1 tick in 7 with 90%); stay within 16 blocks of the deposit
    target unless an item is wanted; look at a mob within 6 blocks every 30-60 ticks; otherwise fly about (10
    across, 7 up or down, 2 low, toward where it faces), fly to what it looks at, or wait 30-60 ticks (2:2:1).
    Walk targets are forgotten once within their distance (Manhattan, as `MoveToTargetSink`).
*   **Deposit target**: the liked note block (the block above it) while it heard it in the last 600 ticks, it is
    still a note block and within 1024 blocks; else the liked player if within 64 blocks, alive and not a
    spectator.
*   **Hearing** (stream G's game events): a vibration listener (radius 16, `#allay_can_listen` = note block play;
    only the liked note block once it has one) and a jukebox listener (radius 10, play and stop). Current Java's
    jukebox tells listeners it is playing every second, so the allay also looks for a jukebox holding a disc within
    10 blocks every 20 ticks (1.12.2's jukebox keeps no song length: holding a disc is playing). It stops dancing
    when the jukebox is gone, empty or over 10 blocks away (checked every 20 ticks), or when it panics.
*   **Duplication** (1.19.1): dancing, given a duplication item, not on cooldown: a new persistent allay in the same
    place, both on a 6000-tick cooldown, three hearts, the amethyst chime (2.0, 1.0). The item is Caves & Cliffs'
    `amethyst_shard` (by name); without it any `gemAmethyst`; with neither there is none. The chime is Caves &
    Cliffs' `block.amethyst_block.chime`, or ours (same sound, registered only without Caves & Cliffs).
*   **Saving**: `Inventory`, `listener` (a vibration in flight), `DuplicationCooldown`, and `Brain`: the liked
    player, liked note block (with its dimension) and the two cooldowns.
*   **Advancements**: `wildupdatebackport:husbandry/allay_deliver_item_to_player` (*You've Got a Friend in Me*:
    pick up an item an allay threw; trigger `wildupdatebackport:thrown_item_picked_up_by_player`, `entity`) and
    `.../allay_deliver_cake_to_note_block` (*Birthday Song*: the liked player's allay throws a cake onto a note
    block; trigger `wildupdatebackport:allay_drop_item_on_block`, `item` and `block`). Both hidden, both tasks
    (current Java made *Birthday Song* a normal advancement).
*   **Woodland mansions** (`AllayStructures`): 1.19 added four "Group of Allays" markers to the room `2x2_a1`
    (one in each cell), 1-3 persistent allays each; 1.12.2's room is otherwise the same room, with air there.
    After each chunk is populated, every `2x2_a1` piece of a mansion in it gets its allays at the transformed
    marker positions inside that chunk's population box, as vanilla handles markers. The mansions are found as
    any `MapGenStructure` field named `Mansion` in the chunk generator, so other generators that build vanilla's
    mansions get them too.
*   **Pillager outposts** (Village & Pillage, by name): 1.19's `feature_cage_with_allays` (weight 1, after
    `feature_cage2`, as 1.19's pool orders it) is added to Village & Pillage's `pillager_outpost/features` pool by
    reflection, its template converted by `tools/allay/generate.py` into Village & Pillage's format and shipped
    where its loader reads it. After Village & Pillage builds a chunk, our world generator spawns the cage's two
    persistent allays at 1.19's positions and angles, rotated as Village & Pillage rotates template entities.
    Anything missing or renamed: a log line and outposts without allays. `CORE_REQUESTS.md` request 7 asks for a
    real API.
*   **Client** (`client/`): current Java's `AllayModel` (32 x 32, flapping, idle bob, arms up while holding, the
    dance: sway, head tilt and a double spin every 55 ticks), translucent, full block light, shadow 0.4, the held
    item between its hands.

## Not done

*   Leashed allays walk toward the holder (1.12.2's `EntityCreature` leash pull); 1.19's allays only get pulled.
*   Allays are not given to dispensers' equipping (1.12.2 dispensers do not equip mobs anyway).

## Tested headlessly

`./gradlew runServer -Pselftest=all` (port 25867), then `python tools/check_assets.py`:
**`SELFTEST DONE pass=742 fail=0`**, assets 0 errors (180 sound files). With Village & Pillage 2.5.1's release
jar in the test server, `-Pselftest=allay`: **`pass=130 fail=0`**. `vanilla_keys.py`: the entity name, the
advancements and the subtitles are 1.19's (it now maps `entity.` and `advancements.` keys too).

*   Registration: the entity and its egg, the seven sounds and our chime (without Caves & Cliffs), both triggers,
    both advancements load.
*   Numbers: health, flying and movement speed, attack, follow range, size, eye height; never despawns.
*   A real player gives an item (the allay holds one, the player loses one, it likes them), cannot hurt it (an
    ally), other damage can and makes it panic; an empty hand takes the item back, throws out the inventory and
    ends the liking.
*   What it wants: the same item, the same wool color only, a sword however damaged, the same potion only;
    picks up a whole stack and no more.
*   Delivery: within 3 blocks of its player's eyes it throws one item per tick, the item marked as the allay's,
    then waits 60 ticks to pick up.
*   Note blocks: a note block played 5 blocks away is heard through game events, liked for 600 ticks and
    delivered to (the block above it); a second note block is ignored meanwhile; after the 600 ticks it is
    forgotten and the player is the target again.
*   Dancing: a jukebox holding a disc 3 blocks away starts it within 20 ticks; emptied, it stops. A
    `gemAmethyst` (registered for the test) duplicates a dancing allay once, using it up: both persistent, both
    on 6000-tick cooldown; a second shard does nothing.
*   Saving: inventory, liked player, duplication cooldown.
*   Death: the inventory and the held item drop; a held item with Curse of Vanishing does not.
*   Mansions, every rotation: a placed 1.12.2 `2x2_a1` room has air over a floor at all four transformed marker
    positions, and gets 1-3 persistent allays at each.
*   Outposts with Village & Pillage, every rotation: its pool has the cage; a Village & Pillage piece of it builds
    the converted cage (the fences are there); its two allays are spawned in the open with bars on all four sides.

## Not tested

*   Anything visual: the model, its animations, the held item, translucency.
*   Flight paths and the idle behaviors over time (vanilla's rules; only single ticks are tested).
*   A real mansion or outpost generating in a world (the pieces are placed and checked directly).
*   The advancements being granted to a player (the triggers and files load).
*   The 18 language files (a later languages pass).
