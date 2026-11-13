"""Assets for our copies of deepslate and its four sets (registered only without Caves & Cliffs), from 1.19.2.

Blockstates, models and textures as current Java's, with the owner's states: walls keep current Java's
none/low/tall sides; a slab is a half block and a double block. Also the four deepslate sound types.
Language lines and sound events go under the marker 'deepdark-deepslate'.

    python -I tools/deepdark/deepslate.py
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import assets  # noqa: E402

M = assets.MODID
SECTION = "deepdark-deepslate"
SETS = ["cobbled_deepslate", "polished_deepslate", "deepslate_brick", "deepslate_tile"]


def full(s):
    return s + "s" if s.endswith(("_brick", "_tile")) else s


names = ["deepslate", "chiseled_deepslate", "cracked_deepslate_bricks", "cracked_deepslate_tiles"]
extra = {}
for n in names:
    assets.port_blockstate(n)
    assets.port_item(n)

for s in SETS:
    for n in (full(s), s + "_stairs"):
        assets.port_blockstate(n)
        assets.port_item(n)
        names.append(n)
    wall = s + "_wall"
    assets.port_blockstate(wall)
    # The one state with no sides and no post never occurs (a lone wall has a post); draw it as a post too,
    # so every state has a model.
    path = os.path.join(assets.ASSETS, "blockstates", wall + ".json")
    data = json.load(open(path, encoding="utf-8"))
    post = [p for p in data["multipart"] if p.get("when") == {"up": "true"}][0]["apply"]
    data["multipart"].append({"when": {"up": "false", "north": "none", "east": "none", "south": "none", "west": "none"},
                              "apply": post})
    assets.write_json("blockstates/%s.json" % wall, data)
    assets.port_item(wall)
    names.append(wall)
    assets.port_slab(s + "_slab", s + "_double_slab")
    assets.port_item(s + "_slab")
    names.append(s + "_slab")
    extra[s + "_double_slab"] = "block.minecraft.%s_slab" % s

events = assets.block_sounds("deepslate", "polished_deepslate", "deepslate_bricks", "deepslate_tiles")

# sounds.json: merged as assets.merge_sounds does, with this generator's marker kept beside it in tools/deepdark/.
sounds_path = os.path.join(assets.ASSETS, "sounds.json")
sounds = json.load(open(sounds_path, encoding="utf-8")) if os.path.exists(sounds_path) else {}
marker = os.path.join(assets.ROOT, "tools", "deepdark", ".%s.sounds" % SECTION)
if os.path.exists(marker):
    for old in open(marker, encoding="utf-8").read().split():
        sounds.pop(old, None)
sounds.update(events)
with open(sounds_path, "w", encoding="utf-8", newline="\n") as f:
    f.write(json.dumps(sounds, indent=2) + "\n")
with open(marker, "w", encoding="utf-8", newline="\n") as f:
    f.write("\n".join(sorted(events)) + "\n")

lines = sorted(assets.block_names(names, extra)) + assets.subtitle_lines(events)
assets.merge_lang(SECTION, lines)
print("%s: %d sound events, %d lang lines" % (SECTION, len(events), len(lines)))
