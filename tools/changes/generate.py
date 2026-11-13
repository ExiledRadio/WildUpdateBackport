"""Assets for stream C (other 1.19.x changes): 1.19's door models for 1.12.2's doors, and the stream's lang lines.

1.19 fixed MC-67308 (door tops flip when the door opens) with new models: each half has a left, right, left-open and
right-open model, and the blockstates pick them. Here every vanilla door gets 1.19's blockstate under our namespace,
pointing at 1.19's door templates (already ported by the mangrove stream) with 1.12.2's own door textures, so resource
packs and Backport Core's texture pack still apply. The client maps the vanilla doors to these files
(changes.client.ChangesClient) when the door-models switch is on.

    python -I tools/changes/generate.py
"""
import os
import sys
import zipfile

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import assets  # noqa: E402

M = assets.MODID

# 1.19 name -> 1.12.2 texture prefix (blocks/door_<x>_upper and _lower)
DOORS = {
    "oak_door": "wood",
    "spruce_door": "spruce",
    "birch_door": "birch",
    "jungle_door": "jungle",
    "acacia_door": "acacia",
    "dark_oak_door": "dark_oak",
    "iron_door": "iron",
}
PARTS = ["bottom_left", "bottom_left_open", "bottom_right", "bottom_right_open",
         "top_left", "top_left_open", "top_right", "top_right_open"]

old = zipfile.ZipFile(os.path.join(os.path.expanduser("~"), ".gradle", "caches", "minecraft", "net", "minecraft",
                                   "minecraft", "1.12.2", "minecraft-1.12.2.jar"))
old_names = set(old.namelist())

for door, tex in DOORS.items():
    for half in ("upper", "lower"):
        path = "assets/minecraft/textures/blocks/door_%s_%s.png" % (tex, half)
        if path not in old_names:
            raise SystemExit("1.12.2 has no " + path)
    for part in PARTS:
        assets.write_json("models/block/%s_%s.json" % (door, part), {
            "parent": "%s:block/door_%s" % (M, part),
            "textures": {"bottom": "blocks/door_%s_lower" % tex, "top": "blocks/door_%s_upper" % tex},
        })
    variants = {}
    for key, entry in assets.vanilla_blockstate(door)["variants"].items():
        model = entry["model"].split(":", 1)[-1]
        assert model.startswith("block/" + door + "_"), model
        variants[key] = dict(entry, model="%s:%s" % (M, model[len("block/"):]))
    assert len(variants) == 32, (door, len(variants))
    assets.write_json("blockstates/%s.json" % door, {"variants": variants})
    # The templates themselves come from the mangrove stream's port; make sure they are there.
    for part in PARTS:
        if not os.path.exists(os.path.join(assets.ASSETS, "models", "block", "door_%s.json" % part)):
            assets.port_model("block/door_%s" % part)

CONFIG = [
    ("minecarts", "Minecart Drops and Recipes",
     "Minecarts with chest, furnace, TNT and hopper drop themselves when broken and are crafted shapeless (1.19)."),
    ("explosionExperience", "Experience from Explosions",
     "Ores and spawners blown up by TNT a player lit drop experience (1.19)."),
    ("golemSpawnEggs", "Golem Spawn Eggs", "Iron golem and snow golem spawn eggs (1.19.3)."),
    ("creeperFireCharge", "Fire Charges Ignite Creepers", "Creepers can be ignited with a fire charge (1.19.3)."),
    ("endermanBlockDrops", "Enderman Block Drops",
     "An enderman's carried block drops as if mined with a Silk Touch tool (1.19.3)."),
    ("rabbitDrops", "Rabbit Drops", "Rabbits always drop one raw rabbit, cooked when on fire (1.19.3)."),
    ("jukeboxes", "Jukeboxes",
     "Playing jukeboxes show notes, give a redstone signal of 15, and take discs from hoppers and droppers (1.19.4)."),
    ("armorSwap", "Armor Swapping", "Using a chestplate, elytra or other armor swaps it with what is worn (1.19.4)."),
    ("foalStats", "Foal Stats", "Foals' health, speed and jump vary around their parents' average (1.19.4)."),
    ("potionColors", "Potion Colors", "The effect colors of potions and tipped arrows of 1.19.4."),
    ("potionGlint", "No Potion Glint", "Potions have no enchantment glint unless enchanted (1.19.4)."),
    ("doorModels", "Door Models", "The tops of doors no longer flip when they open (1.19's door models)."),
    ("shulkerBoxes", "Shulker Box Faces", "Opening shulker boxes pop the blocks attached to their open faces (1.19.3)."),
    ("netherSpawnLight", "Nether Spawn Light",
     "Endermen and skeletons spawn in the Nether at light level 7 and below (1.19.3)."),
    ("scaffoldingFuel", "Scaffolding Fuel", "Scaffolding burns in a furnace as long as bamboo (1.19)."),
]
lines = []
for key, label, tooltip in CONFIG:
    lines.append("config.%s.changes.%s=%s" % (M, key, label))
    lines.append("config.%s.changes.%s.tooltip=%s" % (M, key, tooltip))
assets.merge_lang("changes", lines)
print("changes: %d doors, %d lang lines" % (len(DOORS), len(lines)))
