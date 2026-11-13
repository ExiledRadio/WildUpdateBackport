# Stream W: the warden and Darkness

Package `warden` (client parts in `warden.client`). Config `warden` (`enabled`, client `darknessEffectScale`). Entity id
15. Packet 40. Branch `wu-warden`. Built on stream G's game events (`gameevent`). Behavior and numbers from 1.19.2's
decompiled `Warden`, `WardenAi`, `AngerManagement`, the `ai.behavior.warden` behaviors, `LightTexture`, `FogRenderer`
and `MobEffectInstance.FactorData`; the model and animations from `WardenModel`, `WardenRenderer` and
`WardenAnimation`.

## Changelog section

*   Added the **warden**. It is blind: it finds you by the vibrations you make, by sniffing, and by bumping into you,
    and grows angry at each creature on its own. Once angry enough it roars and attacks, in melee or, if it cannot
    reach you, with a **sonic boom** that goes through armor and shields. It emerges from the ground when a sculk
    shrieker summons it, gives Darkness to players nearby, and digs back down after a minute without disturbance.
    500 health, drops a sculk catalyst. Parrots imitate it. Spawn egg in Miscellaneous.
*   Added the **Darkness** effect: the world's light pulses darker and the fog closes in. Night Vision does not lift
    it. *Darkness Pulsing* (the Accessibility slider from stream Lf, `LookAndFeel.darknessScale()`, 0 to 1) tones the pulse down.

## How it works

*   **The entity** (`EntityWarden`, an `EntityMob`): 1.19.2's attributes (500 health, 30 attack, knockback 1.5,
    knockback resistance 1, speed 0.3, follow range 16), 0.9 x 2.9 (1 block tall while digging or emerging), fire
    immune, never despawns, 5 experience, one sculk catalyst (stream S's item, by name; nothing when sculk is off).
    Invulnerable to everything but the void and immune to explosions while digging or emerging. Cannot ride. Steps
    every 0.55 blocks (its own step sounds, 1.12's whole-block cadence switched off).
*   **The brain** (`WardenBrain`): 1.19's `Brain` reduced to what the warden uses: timed memories (present up to and
    including the tick their time runs out), the warden's entity sensor every 20 ticks (living entities within 24,
    the visible ones within 16, the nearest attackable: a player first), behaviors with durations that start in
    priority order when their memory conditions hold and keep running across activity changes, and the activity
    order emerge, dig, roar, fight, investigate, sniff, idle, with each left activity's memory erased. Every behavior
    of `WardenAi` is there: swim, look and walk sinks (1.12's navigator, 1.19's repath and give-up rules), emerging,
    digging, roar target, sniffing, idle stroll or wait, roar, the fight set (dig cooldown, stop when not angry,
    look, chase at 1.2, sonic boom, melee every 18 ticks) and going to a disturbance. Nothing touches 1.12's AI task
    lists.
*   **Anger** (`AngerManagement`, `AngerLevel`): per suspect, 0-150; -1 every 20 ticks; a vibration +35, a first
    projectile +10 (then +35 within 5 s), a touch +35 (20-tick cooldown), a hurt +100, a roar +20, a sniff within 6
    across and 20 up/down +35. Calm < 40 <= agitated < 80 <= angry, each with its ambient and listening sound;
    suspects sorted angry first, players first, then by anger; saved by UUID and found again when they come back.
*   **Vibrations**: a `VibrationListener` (radius 16, `#warden_can_listen`) on the warden's eyes, moved with it.
    It does not listen while digging, emerging, dead, in its 40-tick vibration cooldown, or to a source it cannot
    target. Its own vibrations are dampened (`GameEventTags.dampens`, as 1.19's `dampensVibrations`). Hearing one:
    tendrils twitch (entity event 61, the clicking sound), anger, and, unless angry, it goes to look.
*   **Fighting**: an angry warden roars (84 ticks, the roar sound at 25), then fights. The sonic boom waits 200 ticks
    after the target is set and 40 after each melee hit, charges 34 ticks (entity event 62 and the charge sound), then
    shoots a particle line (packet 40) and deals 10 to a target within 15 across and 20 up/down: damage type
    `sonic_boom`, through armor and shields (1.12's unblockable), past protection enchantments (1.12's
    creative-harming sources skip them), magic; knockback 2.5 across and 0.5 up, less knockback resistance. A melee
    hit a player blocks with a shield disables the shield for 5 seconds (1.19's `canDisableShield`).
*   **Emerging and digging**: a warden whose Forge data has `wildupdatebackport:summoned_by_shrieker` when
    `onInitialSpawn` runs (stream S's shrieker sets it) emerges (pose, `IS_EMERGING` 134 ticks, agitated then emerge
    sounds, block particles from the client). Any warden has a 1200-tick dig cooldown that every disturbance renews;
    when it runs out with no target and nothing to walk to, it digs (100 ticks) and is gone. Name-tagged (persistent)
    or riding wardens keep renewing it.
*   **Darkness** (`PotionDarkness`, `DarknessFactor`, `client/DarknessClient`): a harmful effect (color 2696993) with
    its own icon. The warden gives it every 120 ticks to survival and adventure players within 20 blocks
    (`WardenContent.applyDarknessAround`, 260 ticks, no particles, unless they have 200 ticks or more left). On the
    client: 1.19's factor (up over the first 22 ticks, down over the last 22), the lightmap pulse
    `max(0, cos((age - partial) * pi * 0.025) * 0.45 * factor)` taken off after Night Vision and the brightness setting
    lowered by the factor. 1.12 has no hook inside its lightmap update, so before each frame the client computes
    1.12's lightmap itself with those two steps, uploads it and clears vanilla's update flag (no patch). Fog: the end
    moves from the view distance to 15 blocks (start at 3/4, 0 for the sky) through Forge's fog events, ahead of water
    fog, behind blindness and lava; the fog color darkens by (1 - factor)^2.
*   **Client** (`client/`): 1.19.2's model built from `ModelRenderer` parts that can skip their own boxes
    (`AnimatedPart`), its walk, idle and tendril motion, and the six keyframe animations run by a small port of 1.19's
    keyframe engine (`anim/`); `WardenAnimations` is generated from 1.19.2's keyframe data by `tools/warden/generate.py`.
    Five glowing layers (bioluminescent, two pulsating spot layers, tendrils, heart) drawn translucent and full bright.
    The heartbeat speeds up with anger (40 ticks calm, 10 at 80). The sonic boom particle is 1.19's 16-frame sprite.
*   **Parrots** imitate wardens: 1.12.2's imitation table, keyed by the warden's entity id, is filled once by
    reflection in init (`entity.parrot.imitate.warden`).
*   **Assets**: `python -I tools/warden/generate.py` copies the textures, sounds (and the parrot's imitation, pointed
    at our event), names, subtitles, death messages and the setting's name from 1.19.2, and regenerates
    `WardenAnimations.java`.

## For the integrator (stream S, the shrieker)

*   The shrieker's names match: entity `wildupdatebackport:warden`, potion `wildupdatebackport:darkness`, the
    `summoned_by_shrieker` flag (`WardenContent.SUMMONED`, the same string as `TileSculkShrieker.SUMMONED`).
*   `entity.warden.listening_angry` is shared. Stream S registers its sound event and sounds.json entry; stream W
    registers the sound event only if it is not there yet (lowest priority), and its generator writes the sounds.json
    entry only while `tools/sculk/.sounds` does not list it. After merging both, run `tools/sculk/generate.py` then
    `tools/warden/generate.py`. The warden's three `nearby_*` replies are stream S's only.
*   `TileSculkShrieker.applyDarknessAround` and `WardenContent.applyDarknessAround` are the same 1.19 method; the
    shrieker may call W's (radius 40) once both are in, or keep its copy.
*   Shared files touched by one line each: `WildUpdateBackport.init` (`WardenContent.init`), `ClientSetup.init`,
    `NetworkHandler` (packet 40), `SelfTestRunner`, `GameEventTags.dampens` (the warden), `ModConfig.Warden`
    (`darknessEffectScale`); `en_us.lang` and `sounds.json` through the generator's markers.

## Not like 1.19, and why

*   **Pathfinding**: 1.12's navigator and node evaluator. 1.19's warden scores path nodes by horizontal distance only,
    and its powder snow and rail penalties have no 1.12 counterpart.
*   **Fog color under Night Vision**: 1.19 does not brighten the fog color by Night Vision while Darkness runs; 1.12
    brightens it before Forge's fog color event, where it cannot be undone. With the factor near 1 the fog color is
    nearly black either way.
*   **Fog color low in the world**: 1.19 replaces the void darkening with Darkness's; here Darkness's multiplies 1.12's
    (which only applies below y 32).
*   **Darkness's lightmap** is computed by this mod while the effect runs; another mod that also replaces the lightmap
    (shader packs) wins, and then the pulse is not seen.
*   **Emerging**: kept exactly as 1.19.2's brain has it. The idle activity runs on a warden's very first tick, before
    the emerge activity takes over, so a summoned warden sometimes walks a short random stroll before its emerge
    behavior starts (still invulnerable, still in the emerging pose).

## Tested headlessly

`./gradlew runServer -Pselftest=all` (port 25868), then `python -I tools/check_assets.py`:
**`SELFTEST DONE pass=767 fail=0`** (123 of them this stream's), assets 0 errors.

*   Registration: the shrieker's names find the warden and Darkness; spawn egg; attributes, size, fire immunity;
    `listening_angry` registered once; the warden's vibrations dampened; it hears shrieks.
*   Anger: increase, the 150 cap, levels at 40 and 80, sorting (angry first, then players), clearing, the decay, a
    suspect at 1 or untargetable forgotten, saving by UUID and finding it again.
*   Targeting: survival players and mobs yes; creative, spectator, armor stands, other wardens, invulnerable mobs no.
*   Vibrations: a pig's step reaches it after its travel time, +35, cooldown 40 ticks, a disturbance to investigate;
    nothing during the cooldown, nothing from a creative player or another warden. Projectiles: +10, then +35.
*   Hurt: +100, attacks the attacker at once, the sonic boom 200 ticks away. Touch: +35, 20-tick cooldown.
*   Sniffing: the sensor's nearest attackable, the sniff within 100-200 ticks, 84 ticks, +35 for a mob within 6.
*   Roar and fight: angry at 80, roar target, roaring pose, +20, the attack after 84 ticks, fight activity; a target
    it is no longer angry at is dropped.
*   Sonic boom: starts at 10 blocks, lands at tick 35 (not 34) for exactly 10 through full diamond armor, knockback
    over 2 blocks/tick, the damage type's flags and death message key, the 40-tick cooldown; nothing at 15.5 across.
*   Melee: 30 damage, 18-tick cooldown, the sonic boom put 40 ticks away.
*   Emerging: a summoned warden's pose, height, memory and activity; invulnerable and explosion-proof; at least 134
    ticks; then standing, full height, hurtable. A warden from an egg does not emerge and has the 1200-tick cooldown.
*   Digging: with the cooldown gone it digs, is invulnerable, and is gone after exactly 100 ticks; a disturbance
    renews the cooldown; a persistent warden keeps renewing it.
*   Darkness: 260 ticks without particles within 20 blocks; not beyond, not in creative; not replaced while 200+ ticks
    remain; the warden's pulse every 120 ticks. The factor: 0, 0.5 after 11 ticks, 1 after 22, unchanged when
    lengthened, 0.5 eleven ticks before the end, 0 at the end. The light pulse: 0.45 peak, 0 at rest, scaled.
*   Saving: emerging, the dig cooldown (with time left) and anger survive a save.

## Not tested

*   Anything drawn: the model, the animations, the glowing layers, the sonic boom particle, the Darkness lightmap,
    fog and icon, digging particles, and the heartbeat and other client sounds. Headless tests cannot see them.
*   The shield being disabled by a melee hit (needs a real blocking player).
*   Parrot imitation (the table is filled by reflection; the log says if it failed).
*   The shrieker summoning a warden end to end: stream S is not merged here; its exact procedure (by name, the flag,
    `onInitialSpawn`) is what the emerging test runs.
