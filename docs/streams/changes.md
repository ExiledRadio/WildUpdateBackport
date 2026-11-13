# Stream C: other 1.19.x changes

Package `changes` (client: `changes.client`). Config `changes`: `enabled` and one switch per change. No packets
(50-54 reserved, none used). Patches P4 to P7 (`docs/PATCHES.md`). Branch `wu-changes`, dev-server port 25870.

## Changelog section

*   **Minecarts** with a chest, furnace, TNT or hopper drop themselves when broken, keeping their name, and are
    crafted shapeless (1.19).
*   **Ores and spawners** blown up by TNT a player lit (or another explosion a player caused) drop experience (1.19).
*   **Iron golem and snow golem spawn eggs** (1.19.3), in the Creative inventory where 1.19.3 puts them.
*   **Creepers** can be lit with a fire charge (1.19.3).
*   **Endermen** drop their carried block as if it were mined with a Silk Touch tool (1.19.3).
*   **Rabbits** always drop one raw rabbit, or a cooked one when killed by fire (1.19.3).
*   **Jukebox** (1.19.4): a note rises above a playing jukebox every second; it gives a redstone signal of 15
    while a disc plays, until the song ends; hoppers and droppers put discs in and hoppers take them out.
*   **Armor swapping** (1.19.4): using a chestplate, elytra or other armor while wearing one swaps them.
*   **Foals** (1.19.4): a foal's health, speed and jump vary around its parents' average instead of being pulled
    toward the middle.
*   **Potion colors** (1.19.4): Speed, Slowness, Strength, Harming, Leaping, Resistance, Fire Resistance, Water
    Breathing, Invisibility, Night Vision, Poison and Luck have 1.19.4's colors.
*   **Potions** no longer have an enchantment glint (1.19.4).
*   **Doors** (1.19): the top half no longer flips its texture when the door opens.
*   **Shulker boxes** (1.19.3): while open, only the face opposite the lid holds blocks; opening one pops blocks
    resting on its lid or sides.
*   **Nether spawning** (1.19.3): endermen, skeletons and wither skeletons spawn in the Nether at light level 7 and
    below.
*   **Scaffolding** burns in a furnace as long as bamboo (1.19; Village & Pillage's scaffolding).

## How it works

Every number is Mojang's: 1.19.2's code (`G:\MCDownloads\wu-1.19.2-src`) for 1.19's changes, 1.19.4's client jar
(`G:\MCDownloads\wu-lookfeel-1.19.4`, read as bytecode) for 1.19.4's values, 1.20.1's named jar (decompiled with
Vineflower) for the 1.19.3/1.19.4 code that 1.20.1 still has unchanged.

*   **Minecarts** (patch P4, `ChangesHooks.cartItem/cartBlockItem/cartBlock`): 1.19's `AbstractMinecart.destroy`
    drops `getDropItem()`. In 1.12.2, `EntityMinecart.killMinecart` drops a minecart and each block cart's own
    `killMinecart` then drops its block. P4 turns the minecart into the cart's item and the block into air (an
    empty stack drops nothing), for the four exact vanilla classes only; other mods' subclasses keep their drops.
    The chest and hopper carts' contents still drop. Recipes (`ItemChanges.recipes`): the four 1.12.2 recipes are
    replaced by shapeless ones under the same names (so the recipe book's unlock advancements still find them);
    Forge logs its "dangerous alternative prefix" warning for each, which is expected for an intended override.
*   **Experience from explosions** (`ItemChanges.explosion`, `ExplosionEvent.Detonate`, lowest priority): 1.19's
    `Explosion.finalizeExplosion` calls each block's `spawnAfterBreak` with `dropExperience` = "`getSourceMob()` is a
    player". Here, before the blocks go, when the explosion destroys blocks, `doTileDrops` is on and the source mob
    (primed TNT's igniter, a living exploder, a fireball's shooter: 1.19's `getSourceMob`) is a player, each
    affected block that can drop from explosions gives `getExpDrop(state, world, pos, 0)` as orbs at its center.
    1.12.2's ore and spawner amounts are 1.19's (coal 0-2, diamond/emerald 3-7, lapis/quartz 2-5, redstone 1-5,
    spawner 15-43). Modded blocks with experience drops give theirs too, as 1.19's do.
*   **Golem spawn eggs** (`ChangesContent`): `EntityEntry.setEgg` for `villager_golem` (14405058 / 7643954) and
    `snowman` (14283506 / 8496292), 1.19.4's `Items` colors, skipped if another mod already gave them eggs; Backport
    Core's `CreativePlacement.placeSpawnEgg` puts them where 1.19.3's `iron_golem_spawn_egg` and
    `snow_golem_spawn_egg` sit. The egg also registers the golems' kill statistics, as every 1.12.2 egg does.
*   **Creepers** (`MobChanges.interact`): 1.19.3's `Creeper.mobInteract` for the new member of `creeper_igniters`:
    the fire charge sound (pitch 0.8-1.2), ignite, one charge used (not in creative: 1.12.2 gives creative players
    their item back only after the event this runs in), arm swing. Flint and steel stays 1.12.2's own path.
*   **Endermen** (`MobChanges.enderman`, `LivingDropsEvent`): 1.12.2's own drop (the block's item and metadata) is
    replaced by what the block gives a Silk Touch tool: `getSilkTouchDrop` when `canSilkHarvest`, else `getDrops`.
    For every block a vanilla enderman picks up this is the same item as before; it differs for blocks other mods
    make holdable. If the vanilla drop is not in the list (another mod changed it), nothing is touched.
*   **Rabbits** (`ItemChanges.loot`, `LootTableLoadEvent`): the meat pool's entry is rebuilt with `set_count` 1
    (was 0-1), keeping `furnace_smelt` on fire and `looting_enchant` 0-1, as 1.19.3's table.
*   **Jukeboxes** (`Jukeboxes`, patch P5): 1.12.2's jukebox tile does not tick, so every jukebox tile is remembered
    when Forge gathers its capabilities and ticked from the server world tick, as 1.19.4's
    `JukeboxBlockEntity.tick`: `IsPlaying`, `RecordStartTick`, `TickCount` (1.19.4's fields, in the tile's Forge
    data under `wildupdatebackport:jukebox`, saved with it). A disc seen for the first time starts playing (whoever
    put it in); playing stops `length + 20` ticks later (1.19.4's `SONG_END_PADDING`); every 20 ticks a note
    particle at the block's bottom center + 1.2, color `nextInt(4) / 24`. Lengths are 1.19.4's `RecordItem`
    seconds by disc name (`record_*` or `music_disc_*` in any namespace, so other family mods' discs count),
    plus 1.20/1.21's discs from their `jukebox_song` files; an unknown disc plays until taken out. Neighbors are
    notified when playing starts and stops. P5 makes the jukebox a signal source (15 while playing). Forge then
    reads a signal source as a non-conducting, non-solid block, so P5 also passes on the strong power reaching
    it (1.19.4's jukebox stays a conductor) and keeps its sides solid. Hoppers and droppers: an `IItemHandler`
    capability, one slot, discs only, inserting only into an empty jukebox (1.19.4's `canPlaceItem`, then
    `setItem`'s start), extracting stops the music without popping the disc (1.19.4's `removeItem`). As in 1.19.4,
    a playing jukebox powers (and so locks) a hopper right under it.
*   **Armor swapping** (`ItemChanges.swap`, `RightClickItem`): 1.19.4's `Equipable.swapWithEquipmentSlot` for
    `ItemArmor` and `ItemElytra` whose class keeps the vanilla right-click: when the slot is taken, not by a
    Curse of Binding piece and not by an equal stack, the two swap, the use statistic counts, and 1.12.2's
    `setItemStackToSlot` plays the equip sound. With the slot empty, 1.12.2's own equip runs (the same result).
*   **Foals** (`MobChanges.foal`, `BabyEntitySpawnEvent`): 1.19.4's `AbstractHorse.createOffspringAttribute` for
    max health (15-30), jump strength (0.4-1.0) and movement speed (0.1125-0.3375), the ranges as 1.19.4 computes
    them (`generateMaxHealth`/`JumpStrength`/`Speed`, cast through float), from the first parent's random.
    Horses, donkeys, mules and llamas.
*   **Potion colors** (`ChangesContent.potionColors`): 1.12.2's `Potion.liquidColor` (final, set by reflection) for
    the twelve effects 1.19.4's `MobEffects` changed; Turtle Master is not in 1.12.2. Wither and Slow Falling
    changed later (1.20) and are left alone.
*   **Potion glint** (patch P6): `ItemPotion.hasEffect` answers "is it enchanted", as 1.19.4's plain `Item.isFoil`.
*   **Door models** (`changes.client.ChangesClient`, `tools/changes/generate.py`): MC-67308 was fixed in 1.19 by
    new door models. On the client, a state mapper points each of 1.12.2's seven doors at our blockstate file of
    1.19's name (`oak_door`, ..., `iron_door`) with 1.19's 32 variants, whose models are 1.19's door templates
    (ported by the mangrove stream) with 1.12.2's own door textures, so resource packs and Backport Core's
    texture pack still apply. Item models are untouched. A model change, not a patch.
*   **Shulker boxes** (patch P7, `ShulkerBoxes`): P7 gives the box 1.19.3's support faces (closed: every face;
    otherwise only the face opposite the lid). `ShulkerBoxes` remembers every shulker box tile and, on each server
    world tick, notifies its neighbors whenever its animation status changed, as 1.19.3's `doNeighborUpdates`
    (start of opening and closing, fully open, fully closed). 1.12.2's own exceptions stay: torches, buttons and
    ladders never attach to a shulker box at all; blocks that ask only the face shape (redstone dust, and
    modded blocks that do the same) pop as in 1.19.3.
*   **Nether spawning** (`MobChanges.netherSpawn`, `LivingSpawnEvent.CheckSpawn`, low priority, only when no one
    else decided): for natural spawns of `EntityEnderman`, `EntitySkeleton` and `EntityWitherSkeleton` in the
    Nether, 1.12.2's `isValidLightLevel` (`light <= rand(8)`) is replaced by the Nether's 1.19.3 rule
    (`monster_spawn_light_level` 7, block light limit 15: light 7 or below, always); the rest of
    `getCanSpawnHere` is checked as vanilla does (peaceful, the block below, path weight, collisions).
*   **Scaffolding fuel** (`ItemChanges.fuel`): 50 ticks for `villagerbackport:scaffolding` (1.19's furnace table,
    `add(Blocks.SCAFFOLDING, 50)`); nothing without Village & Pillage.

No world generation, so nothing for Caves & Cliffs' 1.18-terrain hooks.

## Tested (`./gradlew runServer -Pselftest=changes`, 467 checks; full suite `-Pselftest=all` 2051, 0 failures)

*   Every switch on by default; every config key has an English name and tooltip.
*   P4 to P7 on the dev jar's MCP-named classes and Forge's SRG-named jar: hook calls counted, ASM's stack verifier on
    every method, P5's new methods SRG-named in the SRG jar; on this dedicated server P4 patched five classes, P5 and
    P7 one each, P6 none (client-only method).
*   Each block cart and the plain cart killed: one cart item, named, no separate minecart or block; the chest cart's
    contents still drop. The four recipes are shapeless and match in any arrangement.
*   Diamond ore (3-7) and a spawner (15-43) blown up by player-lit TNT drop experience; ore under TNT nobody lit,
    and stone, drop none.
*   Golem eggs: colors, kill statistics, spawning an iron golem and a snow golem.
*   A real player's fire charge on a creeper: success, ignited, one charge used.
*   Enderman: clay, melon, a flower and coarse dirt drop as silk-touched; a killed enderman holding clay drops the
    clay block, no clay balls.
*   100 rabbit loot rolls: exactly 100 raw rabbit; a burning rabbit: one cooked rabbit.
*   Jukebox: signal source with solid sides; a stick refused, one of two discs taken, plays, signal 15, a second
    disc refused; 11 stops after 71 s + 20 ticks and the signal goes; a hopper above inserts, a hopper below is
    locked while the disc plays and takes it out once the song is over; a lever's power passes through; lengths.
*   Armor: chestplate over chestplate swaps; elytra over chestplate swaps; a Curse of Binding helmet stays on.
*   Foals: the 1.19.4 formula's range and reflection; 200 foals of two 30-health horses average above 29 (1.12.2's
    formula: about 27.5).
*   Potion colors (and Wither unchanged), the potion glint hook.
*   Shulker boxes: closed faces solid; opening: sides unsolid, base solid; dust on the lid pops, dust on a
    downward box's base stays; open sides unsolid; closed again, solid.
*   Nether: a dark spot and a spot at light 7 (glowstone 8 away) allow an enderman, also through the event; light 8
    denies it and a skeleton.
*   Scaffolding recognized by name, 50 ticks. The seven doors' blockstates (32 variants) and models are in the jar.
*   `tools/check_assets.py`: 0 errors.

The full suite also exposed a flake in the allay test (present on the untouched base commit: 1 pass in 3 runs):
`World.updateEntityWithOptionalForce` skips an entity unless every chunk within 32 blocks is loaded, and the allay
test's area neighbors were sometimes unloaded. The allay test now loads them first (3 of 3 passes afterwards).

## Not tested

*   Anything drawn: the note particles, the door models in game, the potion colors and lack of glint on screen,
    the golem eggs' place in the Creative inventory. The door blockstates are checked for completeness, not looked at.
*   Dropper insertion (Forge's dropper hook uses the same capability as the hoppers tested).
*   Natural spawning in a real Nether over time (the spawn check is tested directly and through the event).
*   With Village & Pillage installed (scaffolding is tested by name only).
