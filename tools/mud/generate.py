"""Assets for mud, packed mud, mud bricks and their stairs, slab and wall, from 1.19.2.

    python -I tools/mud/generate.py
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import assets  # noqa: E402

M = assets.MODID

for n in ("mud", "packed_mud", "mud_bricks", "mud_brick_stairs"):
    assets.port_blockstate(n)
assets.port_slab("mud_brick_slab", "mud_brick_double_slab")
assets.port_blockstate("mud_brick_wall", when_map=assets.wall_when)
# 1.12.2's wall can in principle be in a state with no post and no side (it never is: a lone wall gets
# a post). Draw that state as a post too, so every state has a model.
import json  # noqa: E402
path = os.path.join(assets.ASSETS, "blockstates", "mud_brick_wall.json")
data = json.load(open(path, encoding="utf-8"))
data["multipart"].append({"when": {"up": "false", "north": "false", "east": "false", "south": "false", "west": "false"},
                          "apply": {"model": "%s:mud_brick_wall_post" % M}})
assets.write_json("blockstates/mud_brick_wall.json", data)
for n in ("mud", "packed_mud", "mud_bricks", "mud_brick_stairs", "mud_brick_slab", "mud_brick_wall"):
    assets.port_item(n)

events = assets.block_sounds("mud", "packed_mud", "mud_bricks")
assets.merge_sounds("mud", events)
lines = assets.block_names(["mud", "packed_mud", "mud_bricks", "mud_brick_stairs", "mud_brick_slab", "mud_brick_wall"],
                           {"mud_brick_double_slab": "block.minecraft.mud_brick_slab"})
lines += assets.subtitle_lines(events)
assets.merge_lang("mud", lines)
print("mud: %d sound events, %d lang lines" % (len(events), len(lines)))
