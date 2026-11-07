"""Assets for the mangrove wood set: blockstates, models, textures, sounds and names from 1.19.2.

    python -I tools/mangrove/generate.py

1.19's blockstate files are mostly usable as they are: the doors, trapdoors, fence gates, stairs, fence,
leaves, propagule and roots name the same properties 1.12.2's classes have. What differs:
  * a state with no properties is "" in 1.19 and "normal" in 1.12.2;
  * 1.12.2's logs have a fourth axis, none (all bark), which 1.19 has no file entry for;
  * 1.19's slab is one block with type=bottom/top/double, 1.12.2's is a half and a double block;
  * 1.19's button names face + facing, 1.12.2's one facing (up = on the floor, down = on the ceiling);
  * signs are drawn by a tile renderer; their block model is only a particle texture.
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import assets  # noqa: E402

M = assets.MODID


def our_model(name):
    """'minecraft:block/x' -> ('block/x', 'wildupdatebackport:x' as a blockstate names it)."""
    path = name.split(":", 1)[-1]
    assets.port_model(path)
    return "%s:%s" % (M, path[len("block/"):])


def convert(variants, rename=None):
    out = {}
    for key, entry in variants.items():
        entries = entry if isinstance(entry, list) else [entry]
        conv = []
        for e in entries:
            e = dict(e)
            e["model"] = our_model(e["model"])
            conv.append(e)
        key = "normal" if key == "" else key
        if rename:
            key = rename(key)
            if key is None:
                continue
        out[key] = conv if isinstance(entry, list) else conv[0]
    return out


def vanilla_blockstate(name):
    return assets.jar_json("assets/minecraft/blockstates/%s.json" % name)


def plain(name, ours=None, extra=None):
    data = vanilla_blockstate(name)
    if "multipart" in data:
        parts = []
        for part in data["multipart"]:
            part = dict(part)
            applies = part["apply"]
            if isinstance(applies, list):
                part["apply"] = [dict(a, model=our_model(a["model"])) for a in applies]
            else:
                part["apply"] = dict(applies, model=our_model(applies["model"]))
            parts.append(part)
        assets.write_json("blockstates/%s.json" % (ours or name), {"multipart": parts})
        return
    variants = convert(data["variants"])
    if extra:
        variants.update(extra(variants))
    assets.write_json("blockstates/%s.json" % (ours or name), {"variants": variants})


def log_with_none(name, none_model):
    """1.12.2's axis=none shows bark (or stripped bark) on all six faces."""
    plain(name, extra=lambda v: {"axis=none": {"model": our_model("minecraft:block/" + none_model)}})


def slab():
    v = vanilla_blockstate("mangrove_slab")["variants"]
    assets.write_json("blockstates/mangrove_slab.json", {"variants": {
        "half=bottom": {"model": our_model(v["type=bottom"]["model"])},
        "half=top": {"model": our_model(v["type=top"]["model"])},
    }})
    assets.write_json("blockstates/mangrove_double_slab.json", {"variants": {
        "normal": {"model": our_model(v["type=double"]["model"])},
    }})


def button():
    """1.12.2's facing: up = floor, down = ceiling, a side = on the wall facing that way."""
    v = vanilla_blockstate("mangrove_button")["variants"]
    out = {}
    for powered in ("false", "true"):
        out["facing=up,powered=%s" % powered] = v["face=floor,facing=north,powered=%s" % powered]
        out["facing=down,powered=%s" % powered] = v["face=ceiling,facing=north,powered=%s" % powered]
        for side in ("north", "south", "east", "west"):
            out["facing=%s,powered=%s" % (side, powered)] = v["face=wall,facing=%s,powered=%s" % (side, powered)]
    assets.write_json("blockstates/mangrove_button.json", {"variants": convert(out)})


def sign(name):
    plain(name)


def item(name):
    """models/item/<name>.json from 1.19's, its block parent or textures ported too."""
    data = assets.jar_json("assets/minecraft/models/item/%s.json" % name)
    parent = data.get("parent", "")
    if parent.split(":")[-1].startswith("block/"):
        our_model(parent)
        data["parent"] = "%s:%s" % (M, parent.split(":")[-1])
    for key, value in list(data.get("textures", {}).items()):
        if not value.startswith("#"):
            data["textures"][key] = assets.texture(value.split(":")[-1])
    if parent.split(":")[-1] in ("item/generated", "item/handheld"):
        data["parent"] = parent.split(":")[-1]
    assets.write_json("models/item/%s.json" % name, data)


def main():
    log_with_none("mangrove_log", "mangrove_wood")
    log_with_none("stripped_mangrove_log", "stripped_mangrove_wood")
    log_with_none("mangrove_wood", "mangrove_wood")
    log_with_none("stripped_mangrove_wood", "stripped_mangrove_wood")
    for name in ("mangrove_planks", "mangrove_stairs", "mangrove_fence", "mangrove_fence_gate", "mangrove_door",
                 "mangrove_trapdoor", "mangrove_pressure_plate", "mangrove_leaves", "mangrove_propagule",
                 "potted_mangrove_propagule", "mangrove_roots", "muddy_mangrove_roots", "mangrove_sign",
                 "mangrove_wall_sign"):
        plain(name)
    slab()
    button()
    assets.texture("entity/signs/mangrove")

    items = ["mangrove_log", "mangrove_wood", "stripped_mangrove_log", "stripped_mangrove_wood", "mangrove_planks",
             "mangrove_stairs", "mangrove_slab", "mangrove_fence", "mangrove_fence_gate", "mangrove_door",
             "mangrove_trapdoor", "mangrove_button", "mangrove_pressure_plate", "mangrove_sign", "mangrove_leaves",
             "mangrove_propagule", "mangrove_roots", "muddy_mangrove_roots"]
    for name in items:
        item(name)

    events = {}
    for what in ("mangrove_roots", "muddy_mangrove_roots"):
        for kind in ("break", "step", "place", "hit", "fall"):
            e = "block.%s.%s" % (what, kind)
            events[e] = assets.copy_sound(e)
    events["item.axe.strip"] = assets.copy_sound("item.axe.strip")
    assets.merge_sounds("mangrove", events)

    lines = []
    blocks = ["mangrove_log", "mangrove_wood", "stripped_mangrove_log", "stripped_mangrove_wood", "mangrove_planks",
              "mangrove_stairs", "mangrove_slab", "mangrove_fence", "mangrove_fence_gate", "mangrove_door",
              "mangrove_trapdoor", "mangrove_button", "mangrove_pressure_plate", "mangrove_sign", "mangrove_wall_sign",
              "mangrove_leaves", "mangrove_propagule", "potted_mangrove_propagule", "mangrove_roots",
              "muddy_mangrove_roots"]
    for name in blocks:
        lines.append("tile.%s.%s.name=%s" % (M, name, assets.name("block.minecraft." + name)))
    lines.append("tile.%s.mangrove_double_slab.name=%s" % (M, assets.name("block.minecraft.mangrove_slab")))
    lines.append("item.%s.mangrove_door.name=%s" % (M, assets.name("block.minecraft.mangrove_door")))
    lines.append("item.%s.mangrove_sign.name=%s" % (M, assets.name("block.minecraft.mangrove_sign")))
    lines += assets.subtitle_lines(events)
    assets.merge_lang("mangrove", lines)
    print("mangrove: %d sound events, %d lang lines" % (len(events), len(lines)))


if __name__ == "__main__":
    main()
