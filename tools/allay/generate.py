"""Assets for the allay, from 1.19.2: texture, sounds, names, advancements, and the outpost cage.

    python -I tools/allay/generate.py

The cage (1.19's pillager_outpost/feature_cage_with_allays) is converted into the template format that
Village & Pillage's own template loader reads, and written where that loader looks for it
(assets/villagerbackport/structures/...). Its two allays are not in the file: the allay stream spawns them.
"""
import gzip
import io
import json
import os
import struct
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import assets  # noqa: E402

M = assets.MODID
SRC = assets.DOWNLOADS + "/wu-1.19.2-src"

# --- texture -------------------------------------------------------------------------------------
assets.texture("entity/allay/allay")

# --- sounds --------------------------------------------------------------------------------------
EVENTS = ["entity.allay.ambient_with_item", "entity.allay.ambient_without_item", "entity.allay.death",
          "entity.allay.hurt", "entity.allay.item_given", "entity.allay.item_taken", "entity.allay.item_thrown",
          # Caves & Cliffs' sound; ours is registered only without it
          "block.amethyst_block.chime"]
events = {e: assets.copy_sound(e) for e in EVENTS}
assets.merge_sounds("allay", events)

# --- advancements --------------------------------------------------------------------------------
ADV = {
    "allay_deliver_item_to_player": {
        "parent": "minecraft:husbandry/root",
        "icon": "minecraft:cookie",
        "criteria": {"allay_deliver_item_to_player": {
            "trigger": "%s:thrown_item_picked_up_by_player" % M,
            "conditions": {"entity": "%s:allay" % M}}},
    },
    # Current Java: a normal (task) advancement, no longer a challenge (1.19.3).
    "allay_deliver_cake_to_note_block": {
        "parent": "%s:husbandry/allay_deliver_item_to_player" % M,
        "icon": "minecraft:noteblock",
        "criteria": {"allay_deliver_cake_to_note_block": {
            "trigger": "%s:allay_drop_item_on_block" % M,
            "conditions": {"item": {"item": "minecraft:cake"}, "block": "minecraft:noteblock"}}},
    },
}
lang = ["entity.%s.allay.name=%s" % (M, assets.name("entity.minecraft.allay"))]
for name, a in ADV.items():
    key = "advancements.%s.husbandry.%s" % (M, name)
    assets.write_json("advancements/husbandry/%s.json" % name, {
        "parent": a["parent"],
        "display": {
            "icon": {"item": a["icon"]},
            "title": {"translate": key + ".title"},
            "description": {"translate": key + ".description"},
            "frame": "task",
            "show_toast": True,
            "announce_to_chat": True,
            "hidden": True,
        },
        "criteria": a["criteria"],
        "requirements": [list(a["criteria"])],
    })
    lang.append("%s.title=%s" % (key, assets.name("advancements.husbandry.%s.title" % name)))
    lang.append("%s.description=%s" % (key, assets.name("advancements.husbandry.%s.description" % name)))
lang += assets.subtitle_lines(events)
assets.merge_lang("allay", lang)


# --- the outpost cage, in Village & Pillage's template format ------------------------------------
def read_nbt(path):
    f = io.BytesIO(gzip.open(path).read())

    def rd(t):
        if t == 1: return struct.unpack(">b", f.read(1))[0]
        if t == 2: return struct.unpack(">h", f.read(2))[0]
        if t == 3: return struct.unpack(">i", f.read(4))[0]
        if t == 4: return struct.unpack(">q", f.read(8))[0]
        if t == 5: return struct.unpack(">f", f.read(4))[0]
        if t == 6: return struct.unpack(">d", f.read(8))[0]
        if t == 7: return f.read(struct.unpack(">i", f.read(4))[0])
        if t == 8: return f.read(struct.unpack(">H", f.read(2))[0]).decode("utf-8")
        if t == 9:
            et = f.read(1)[0]
            return [rd(et) for _ in range(struct.unpack(">i", f.read(4))[0])]
        if t == 10:
            d = {}
            while True:
                tt = f.read(1)[0]
                if tt == 0:
                    return d
                n = f.read(struct.unpack(">H", f.read(2))[0]).decode("utf-8")
                d[n] = rd(tt)
        if t == 11:
            n = struct.unpack(">i", f.read(4))[0]
            return list(struct.unpack(">%di" % n, f.read(4 * n)))
        raise ValueError(t)

    t = f.read(1)[0]
    f.read(struct.unpack(">H", f.read(2))[0])
    return rd(t)


class Ints(list):
    """An int array tag."""


def write_nbt(path, root):
    out = io.BytesIO()

    def name(n):
        b = n.encode("utf-8")
        out.write(struct.pack(">H", len(b)) + b)

    def tag_type(v):
        if isinstance(v, Ints): return 11
        if isinstance(v, bool): return 1
        if isinstance(v, int): return 3
        if isinstance(v, float): return 6
        if isinstance(v, str): return 8
        if isinstance(v, list): return 9
        if isinstance(v, dict): return 10
        raise TypeError(v)

    def payload(v):
        t = tag_type(v)
        if t == 11:
            out.write(struct.pack(">i", len(v)) + struct.pack(">%di" % len(v), *v))
        elif t == 1:
            out.write(struct.pack(">b", int(v)))
        elif t == 3:
            out.write(struct.pack(">i", v))
        elif t == 6:
            out.write(struct.pack(">d", v))
        elif t == 8:
            b = v.encode("utf-8")
            out.write(struct.pack(">H", len(b)) + b)
        elif t == 9:
            et = tag_type(v[0]) if v else 10
            out.write(bytes([et]) + struct.pack(">i", len(v)))
            for e in v:
                payload(e)
        else:
            for k, e in v.items():
                out.write(bytes([tag_type(e)]))
                name(k)
                payload(e)
            out.write(b"\x00")

    out.write(b"\x0a")
    name("")
    payload(root)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(gzip.compress(out.getvalue(), mtime=0))


def map_state(name, props):
    """The six blocks of the cage, as 1.12.2 names them (the shapes 1.12.2 works out from neighbors)."""
    if name == "minecraft:dark_oak_log":
        return ["minecraft:log2"], {"variant": "dark_oak", "axis": props.get("axis", "y")}
    if name == "minecraft:dark_oak_fence":
        return ["minecraft:dark_oak_fence"], {}
    if name == "minecraft:dark_oak_slab":
        return ["minecraft:wooden_slab"], {"variant": "dark_oak", "half": "top" if props.get("type") == "top" else "bottom"}
    if name == "minecraft:dark_oak_stairs":
        return ["minecraft:dark_oak_stairs"], {"facing": props["facing"], "half": props["half"]}
    if name == "minecraft:air":
        return ["minecraft:air"], {}
    raise KeyError(name)


cage = read_nbt(SRC + "/data/minecraft/structures/pillager_outpost/feature_cage_with_allays.nbt")
palette, index, blocks, jigsaws = [], {}, [], []


def slot(name, props):
    key = (name, tuple(sorted(props.items())))
    if key not in index:
        names, p = map_state(name, props)
        index[key] = len(palette)
        palette.append({"Names": names, "Props": dict(p), "Src": name, "SrcProps": dict(props)})
    return index[key]


for b in cage["blocks"]:
    st = cage["palette"][b["state"]]
    name, props = st["Name"], st.get("Properties", {})
    pos = Ints(b["pos"])
    if name == "minecraft:jigsaw":
        te = b["nbt"]
        i = slot(te.get("final_state", "minecraft:air"), {})
        jigsaws.append({"pos": pos, "facing": props["orientation"].split("_")[0] if "orientation" in props else props["facing"],
                        "pool": te["pool"].replace("minecraft:", ""), "attach": te["name"], "state": i})
        blocks.append({"pos": pos, "state": i})
        continue
    if name == "minecraft:structure_block":
        continue
    blocks.append({"pos": pos, "state": slot(name, props)})

write_nbt(os.path.join(assets.ROOT, "src", "main", "resources", "assets", "villagerbackport", "structures",
                       "pillager_outpost", "feature_cage_with_allays.nbt"),
          {"size": Ints(cage["size"]), "palette": palette, "blocks": blocks, "jigsaws": jigsaws, "entities": []})

print("allay: %d sounds, %d lang lines, cage %d blocks %d jigsaws" % (len(events), len(lang), len(blocks), len(jigsaws)))
