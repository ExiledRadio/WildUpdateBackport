# Stream F: frogs

Package `frog`. Config `frogs` (`enabled`). Entities `wildupdatebackport:frog` (id 5) and `tadpole` (id 6).
No packets (ids 30-34 unused: poses and variants travel as entity data). Port 25866. Branch `wu-frogs`.

## Changelog section

*   **Frogs**, in three variants: **temperate**, **warm** and **cold**, picked from the biome a frog is
    spawned or grows up in. They hop, swim, croak, make long jumps (onto lily pads when they can), and catch
    small slimes and small magma cubes with their tongues. Spawn in swamps in groups of 2 to 5.
*   A small magma cube eaten by a frog drops a **froglight**: **ochre** from a temperate frog,
    **pearlescent** from a warm one, **verdant** from a cold one. A small slime drops a slimeball.
*   Frogs breed with slimeballs. The mother then lays **frogspawn** on water next to land; it hatches into
    2 to 5 **tadpoles**.
*   **Tadpoles** grow into frogs after 20 minutes (slimeballs speed this up). They die on land. A water
    bucket catches one in a **bucket of tadpole**, which keeps its age.
*   **Froglights**: light level 15, placed along an axis like logs.
*   Advancements *Bukkit Bukkit*, *When the Squad Hops into Town* and *With Our Powers Combined!*.

## How it works

Numbers are 1.19.2's code (`Frog`, `FrogAi`, `Tadpole`, `TadpoleAi`, `FrogspawnBlock`, `ShootTongue`,
`LongJumpToPreferredBlock`, `TryLaySpawnOnWaterNearLand`, the loot tables and tags), checked against the
current wiki.

*   **Where frogs come from** is one class, `FrogSpawns`, so other terrain can hook in. Variant:
    1.19's `#spawns_cold_variant_frogs` and `#spawns_warm_variant_frogs` biome lists, matched by biome name in
    any namespace (1.12.2's names and 1.19's both, so a mod that registers 1.18's biomes gets the right
    frogs without code), then Forge's biome dictionary (`SNOWY`/`END` cold, `HOT`/`NETHER` warm) for
    modded biomes, else temperate. Spawns: creature, weight 10, 2-5, in every biome named `swamp`,
    `swampland`, `mutated_swampland` or `mangrove_swamp`, standing on grass, mud, mangrove roots or muddy
    mangrove roots, light above 8. **Hook:** `FrogSpawns.addSpawns(Biome)` adds 1.19's frog spawn to any
    biome (the mangrove swamp stream, or Caves & Cliffs' 1.18 terrain on its `cc118-all` branch, can call it
    for theirs if their names differ).
*   **Frog** (`EntityFrog`): 10 health, 10 attack, speed 1, 0.5 x 0.5, steps up a full block, breathes
    underwater, not pushed by water, 5 less fall damage, never a baby, water path cost 4, avoids trapdoors.
    1.19's brain is ported as an activity picked every tick (tongue > lay spawn > long jump > swim > idle,
    with 1.19's conditions) plus 1.12.2 tasks that run only in their activities (`FrogAi`); timers and
    sensors live in `FrogMemory` and the entity's own tick, never in `shouldExecute`.
    *   Movement: 1.19's `SmoothSwimmingMoveControl` (`SmoothSwimMoveHelper`: x0.1 on land, x0.02 in water,
        pitched swimming, a slight upward drift in water) and 1.19's frog `travel` (no gravity in water,
        x0.9 drag). 1.12.2 has no amphibious navigator, so a frog has two: a ground navigator that may swim
        on the surface, and a water navigator for its underwater random swims.
    *   Tongue: targets the nearest visible size-1 slime or magma cube within 10 (sensor every 20 ticks),
        not while breeding; walks to it at double speed, within 1.75 blocks tongues it (pulling it at 0.75),
        eats it 6 ticks later (10 damage, removed if dead), done 10 ticks after; up to 5 unreachable targets
        are skipped for 100 ticks.
    *   Long jump: every 100-140 ticks on land, not tempted or breeding; candidates within 4 across and 2
        up/down weighted by squared distance, half the time lily pads or big dripleaf first, landing spots
        that are solid (or a lily pad), path cost 0, not liquid, and not reachable by a path within 8
        blocks; arc angle 65/70/75/80 at most 1.5 a tick with a clear flight; 40 ticks of preparation;
        frictionless flight; landing slows it to a tenth and plays the step sound.
    *   Idle: stroll, walk to what it looks at, croak (60 ticks), rest 5-20 ticks (1.19's weights 1/1/3/2);
        in water: random swim (1.19's distance tiers) or stroll, then 30-60 ticks; heads for land within 8
        (in water) or 6; looks at players within 6 every 30-60 ticks; follows a slimeball within 10 at 1.25x
        (100-tick cooldown); panics at 2x for 40 ticks after being hurt.
    *   Breeding: slimeballs; two frogs in love walk together, 60-109 ticks within 3 blocks makes one
        pregnant (stats, the bred-animals trigger, 1-7 experience), both rest 6000 ticks, **no child**. A
        pregnant frog walks to land beside water (range 8, every 40 ticks at most) and, on the ground with
        open water beside it and air above, lays frogspawn there with the lay-spawn sound.
    *   Saved as 1.19: `variant: "minecraft:warm"`, plus `IsPregnant` and `LongJumpCooldown`.
    *   Drops: frogs drop nothing. Their kills: a magma cube drops only the froglight of the frog's variant,
        a small slime exactly one slimeball (1.19's loot tables, through `LivingDropsEvent`).
*   **Tadpole** (`EntityTadpole`): 6 health, 0.4 x 0.3; 1.12.2's water-mob air rules (300 air, 2 drowning
    damage a second out of water), `AbstractFish`'s flopping and swimming; swims at random at half speed,
    follows slimeballs, panics. `Age` counts to 24000, then it becomes a frog (variant from its biome, name
    and NoAI kept, never despawns, grow-up sound). A slimeball takes 10% of the remaining time off. A water
    bucket scoops it into a bucket of tadpole (1.19's bucket data: name, NoAI, Silent, NoGravity, Glowing,
    Invulnerable, Health, Age). Never despawns, no experience.
*   **Bucket of tadpole** (`ItemTadpoleBucket`): places water (evaporating in the Nether) and releases the
    tadpole; dispensers do the same; not a fluid container (so tanks cannot drain the tadpole away).
*   **Frogspawn** (`BlockFrogspawn`, `ItemFrogspawn`): placed on a water source with air above, like a lily
    pad; gone when that changes; hatches 3600-11999 ticks after it is placed into 2-5 tadpoles in the water
    below; broken by falling blocks; instant to break, drops nothing (creative only, as in 1.19).
*   **Froglights** (`BlockFroglight`): hardness and resistance 0.3, light 15, axis, any tool. Map colors
    sand, pink, and (verdant) 1.12.2's nearest to glow lichen's, silver.
*   **Sounds**: all 25 events from **current Java's** sounds.json and asset index (the plan's *now*: 1.19.4
    made froglights quieter). The tadpole's flop refers to the tropical fish's, which 1.12.2 lacks; its
    sounds are inlined.
*   **Client** (`frog/client`): 1.19's frog model (48 x 48) and its six keyframe animations (croak, walk,
    jump, tongue, swim, idle in water), played by a small keyframe runtime (`Part`, `Keyframes`,
    `AnimationClock`; Core request 7); the walk animation speeds up with movement as 1.19's. The tadpole's
    wagging tail. The animations are data, `animations/frog.json`, generated from 1.19.2's code.
*   **Advancements** (husbandry): *Bukkit Bukkit* (`wildupdatebackport:filled_bucket`, 1.12.2 has no
    filled-bucket trigger), *When the Squad Hops into Town* (`wildupdatebackport:leashed_frog`, one per
    variant; 1.12.2 has no interact-with-entity trigger), *With Our Powers Combined!* (inventory, all three
    froglights; challenge). *Bukkit Bukkit*'s 1.19 parent, *Tactical Fishing*, is a 1.13 advancement, so it
    hangs off the husbandry root.

### Differences from the plan

*   The plan said frogspawn hatches 2-6 tadpoles and breaks when anything enters it. 1.19.2's code (and the
    current wiki) hatch 2-5 and break only for falling blocks; this follows vanilla.

## Tested headlessly

`./gradlew runServer -Pselftest=all` (port 25866): **`SELFTEST DONE pass=889 fail=0`** (frogs alone 245);
`python tools/check_assets.py`: **32 blocks, 292 states, 37 item models, 280 sound files, 0 errors**;
`tools/lang/vanilla_keys.py`: every block, item, entity, advancement and subtitle name is Mojang's except two
subtitles only current Java has (tadpole grows up, generic block fall).

*   Registration: four blocks with items, the bucket (stacks to 1), both entities and spawn eggs (1.19's
    colors), 25 sound events.
*   Froglights: hardness, resistance, light, sounds, axis placement, no tool.
*   Variants for 23 vanilla biomes; spawn entries in both swamps (10, 2-5) and none in plains, desert, snowy
    tundra; spawnable blocks; light; a spawned frog's variant from its biome.
*   Frog and tadpole stats; fall damage (8 blocks: none, 10 blocks: 2); what frogs eat (size 1 slimes and
    magma cubes only).
*   Frogspawn: placement rules, no collision, 1.5 px, instant, no drops, the item placing it on water and
    not on blocks, falling blocks break it and items do not, breaks when its water goes, 40 hatchings give
    2 to 5 tadpoles (kept, in the water), hatching is scheduled when placed.
*   Drops for each variant: froglight from a magma cube, one slimeball from a slime.
*   A live frog in a pen hunts down and eats a small magma cube with its tongue (the right froglight drops),
    and ignores a size-2 slime.
*   A live pair fed slimeballs: in love, one pregnant, 6000-tick cooldown, no baby, then the mother walks to
    the pond and lays frogspawn on it.
*   A live frog long-jumps out of a cell it cannot walk out of, lands, and waits again.
*   Idle frogs croak; frogs in water swim about without drowning.
*   Tadpoles: feeding (10% of 24000, of 14000), a slimeball within 10 tempts, growing up into a frog of the
    biome's variant that keeps its name and never despawns, suffocating on land while still aging.
*   Buckets: scooping (age and health kept, tadpole removed), emptying (water, the same tadpole, an empty
    bucket back), dispensers, not a fluid container.
*   Saving: variant (as 1.19's string), pregnancy, tadpole age.
*   Advancements load; a lead on each variant and scooping a tadpole earn theirs.

## Not tested

*   Anything visual: the frog and tadpole models, the six animations, textures, item and block models, the
    frogspawn's translucency.
*   Natural spawning in a generated swamp (the spawn entries and rules are tested, not world generation).
*   Long-jump target choice onto lily pads specifically (the jump itself is tested).
*   Sounds playing.
*   The 18 language files (a later languages pass).
*   With Caves & Cliffs installed: big dripleaf as a preferred jump target (by name).
