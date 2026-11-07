# Stream G: game events and vibrations

Package `gameevent`. Config `vibrations` (`enabled`). Packet 5. Branch `wu-vibrations`.

The API asked of Backport Core in `docs/CORE_REQUESTS.md` (request 1), built here so the sculk, warden and
allay streams can start. When Core has it, this package is swapped for Core's.

## Changelog section

*   Things that happen in the world now make **vibrations**, as in 1.19: footsteps, swimming, splashing, falls,
    placing and breaking blocks, opening doors and chests, eating and drinking, arrows, explosions, note blocks,
    pistons, lightning and more. Sneaking players make no footstep, fall, swim or item vibrations; wool absorbs
    vibrations and wool walls block them. (They are heard by the sculk sensors, shriekers, wardens and allays of
    later parts of this mod.)

## How it works

*   **Events** (`GameEvent`): 1.19.2's 46 game events with their radius (16; `shriek` 32; the jukebox's 10) and
    current Java's vibration frequency (step 1 ... entity die and explode 15) for comparators and calibrated
    sensors. Events 1.19.2 had that current Java folded into others keep their frequency (piston extend = block
    activate 10, piston contract = block deactivate 9, roar and shake = entity action 4).
*   **Tags** (`GameEventTags`): `#vibrations`, `#warden_can_listen`, `#shrieker_can_listen`,
    `#allay_can_listen`, `#ignore_vibrations_sneaking`, wool occluding and wool/carpet dampening, as fixed sets.
*   **Dispatcher** (`GameEvents`): listeners per world by chunk; an event reaches every listener within the
    listener's radius; immediate listeners (the catalyst) hear it at once, the rest at the end of the world's
    tick, closest first, as 1.19.2's `ServerLevel` does.
*   **Vibration rules** (`VibrationListener`): 1.19.2's: listenable set, spectators silent, sneaking ignored for
    its six events (earning *Sneak 100* through `wildupdatebackport:avoid_vibration` when the listener allows),
    wool and carpets dampen, occlusion when every one of the six lines from just off the event's block center
    to the listener passes through wool (a voxel walk), one vibration at a time, one tick of travel per block, the
    particle (packet 5), saved in flight.
*   **Emitters** (`GameEventEmitters`): 1.12.2 has no game events, so each comes from the nearest hook. Movement
    comes from each living entity's update: when its walked distance passes the next whole block (vanilla's own
    step-sound rule) it steps, swims in water, or flaps if it flies; entering water splashes; elytra flight glides
    every 10 ticks. A player's right-click is compared with the block at the end of the tick: doors, trapdoors and
    gates open or close, buttons and levers activate, anything else is a block change, jukebox discs play and
    stop, a used-up boat, minecart, armor stand, painting, crystal or spawn egg places an entity. Forge's events
    give the rest; pistons come from neighbor notifications (Forge 2847 has no piston event); lightning from new
    bolts in the weather list.
*   **Client** (`client/VibrationClient`): 1.19's animated vibration sprite, stitched into the block atlas,
    flying from the event to the listener in the travel time, following a moving listener.

## Gaps (no hook without patches; candidates for `asm` if parity needs them)

Blocks changed by redstone or other non-player causes (a door opened by a button is heard as the button only),
`dispense_fail`, `entity_shake`, vanilla mobs' `entity_roar`, block attach/detach, and step sounds of silent
entities. The per-entity step rule and the emitted positions match 1.19's closely but not tick for tick.

## Tested headlessly

`./gradlew runServer -Pselftest=all` (port 25861), then `python tools/check_assets.py`:
**`SELFTEST DONE pass=644 fail=0`** (28 of them this stream's), assets 0 errors.

*   Frequencies and radii; a listener registers and is removed.
*   Two vibrations in one tick at 3 and 6 blocks: the closer is heard, after exactly 3 ticks; the farther never.
*   Not heard: beyond the listener's 8 blocks; placing wool; a sneaking player's step (their block placing is);
    through a wool wall; anything with vibrations switched off.
*   A vibration in flight is saved, read back into a new listener and still arrives.
*   Emitters: a pig walking a block steps (with itself as the source); on wool it does not; killing it is heard; a
    real player placing a block, opening a door and opening a chest make block_place, block_open and
    container_open.

## Not tested

*   The vibration particle (client only).
*   Emitters not driven by the test: eating, projectiles, explosions, pistons, buckets, lightning, elytra,
    equipping, teleports, creepers and TNT, note blocks (covered by streams that use them: sculk, allay).
*   *Sneak 100* itself (the advancement comes with the sculk stream).
