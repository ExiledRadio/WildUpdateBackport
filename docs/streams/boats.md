# Stream B: boats

Package `boat`. Config `boats` (`enabled`). Entities `wildupdatebackport:boat` (id 1) and `chest_boat`
(id 2). Branch `wu-boats`.

## Changelog section

*   **Mangrove boat**.
*   **Boats with chests** in every wood (oak, spruce, birch, jungle, acacia, dark oak, mangrove): a chest and a
    boat, shapeless. One seat. Sneak and right-click (or right-click when the seat is taken) to open the chest.
    Breaking one drops the boat with chest and everything inside; hoppers fill and empty them.
*   Boats with chests and the mangrove boat burn for 1200 ticks.

## How it works

*   **Woods** (`BoatWood`): the registry asked of Backport Core (request 4), kept here until Core has one.
    Vanilla's six woods and mangrove (when the mangrove stream is on). The entities save the wood as a string
    (`Wood: "minecraft:spruce"`), and an unknown wood (its mod removed) falls back to oak.
*   **Entities**: `EntityWoodBoat extends EntityBoat`, so paddling, floating, carrying and breaking are
    vanilla's code. Old woods also set vanilla's boat type, so vanilla's crash drops (3 planks, 2 sticks) are
    right; a new wood swaps the planks as they drop. `EntityWoodChestBoat` adds 1.19's chest: 27 slots, one
    seat with the rider 0.15 blocks forward, contents dropped when broken by damage, a crash or `/kill` (not by
    a creative player, as in 1.19), saved with the entity. It is an `IInventory` entity, which is how a 1.12.2
    hopper finds minecarts with chests, so hoppers work with it as in 1.19.
*   **Items** (`ItemWoodBoat`): vanilla's boat item placement rules (5-block reach, a little lower on water,
    refused when blocked), for our entities. Stacks to 1.
*   **Client**: vanilla's boat renderer with the wood's texture; the chest boat uses its own model with 1.19's
    128 x 128 texture layout and the chest's three parts (`client/ModelChestBoat`).
*   **Not done**: 1.19's retextured vanilla boats (wood-colored paddles) are vanilla's look and go to Core's
    texture packs (`CORE_REQUESTS.md` request 5); vanilla boats keep 1.12.2's burn time.

## Tested headlessly

`./gradlew runServer -Pselftest=scaffold,mangrove,mud,boats` (port 25864), then `python tools/check_assets.py`:
**`SELFTEST DONE pass=616 fail=0`**, **32 item models, 0 errors**. `vanilla_keys.py`: every item and entity
name is 1.19's.

*   All eight items registered, unstackable, fuel 1200.
*   Recipes: mangrove boat, a spruce boat with chest, a mangrove boat with chest (either order).
*   Saving: the entity name, the wood and the chest's contents survive writing and reading back; an old wood
    keeps vanilla's boat type and drops its own chest boat.
*   A real player: sneaking right-click opens a chest container, right-click rides; with the seat taken a
    second player opens the chest instead.
*   A hopper under a chest boat pulls an item from it.
*   A hard hit breaks it, dropping the boat with chest and its 5 diamonds; a mangrove boat's crash planks are
    mangrove planks.

## Not tested

*   Anything visual: both renderers, the chest boat model and its textures, the item models.
*   Placing a boat by right-clicking (needs a client-side look ray; the rules are vanilla's).
*   Paddling and riding physics (vanilla's).
*   The 18 language files (a later languages pass).
