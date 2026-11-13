"""Assets for stream I: echo shard, recovery compass, music disc 5, disc fragment, goat horn, Swift Sneak; from 1.19.2.

    python -I tools/items/generate.py
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import assets  # noqa: E402

M = assets.MODID


def port_with_overrides(name):
    """An item model whose overrides name other vanilla item models: those are ported too, under our namespace."""
    assets.port_item(name)
    path = os.path.join(assets.ASSETS, "models", "item", name + ".json")
    out = json.load(open(path, encoding="utf-8"))
    overrides = []
    for o in assets.jar_json("assets/minecraft/models/item/%s.json" % name).get("overrides", []):
        target = o["model"].split(":")[-1]
        if target != "item/" + name:
            assets.port_item(target[len("item/"):])
        overrides.append({"predicate": o["predicate"], "model": "%s:%s" % (M, target)})
    out["overrides"] = overrides
    assets.write_json("models/item/%s.json" % name, out)


for n in ("echo_shard", "music_disc_5", "disc_fragment_5"):
    assets.port_item(n)
port_with_overrides("recovery_compass")
port_with_overrides("goat_horn")

events = {}
for i in range(8):
    events["item.goat_horn.sound.%d" % i] = assets.copy_sound("item.goat_horn.sound.%d" % i)
events["entity.goat.horn_break"] = assets.copy_sound("entity.goat.horn_break")
events["music_disc.5"] = assets.copy_sound("music_disc.5")
assets.merge_sounds("items", events)

lines = []
for n in ("echo_shard", "recovery_compass", "music_disc_5", "disc_fragment_5", "goat_horn"):
    lines.append("item.%s.%s.name=%s" % (M, n, assets.name("item.minecraft." + n)))
# 1.12.2's records name their description line item.record.<name>.desc.
lines.append("item.record.5.desc=%s" % assets.name("item.minecraft.music_disc_5.desc"))
lines.append("item.%s.disc_fragment_5.desc=%s" % (M, assets.name("item.minecraft.disc_fragment_5.desc")))
for i in ("ponder", "sing", "seek", "feel", "admire", "call", "yearn", "dream"):
    lines.append("instrument.%s.%s_goat_horn=%s" % (M, i, assets.name("instrument.minecraft.%s_goat_horn" % i)))
lines.append("enchantment.%s.swift_sneak=%s" % (M, assets.name("enchantment.minecraft.swift_sneak")))
lines += assets.subtitle_lines(events)
assets.merge_lang("items", lines)
print("items: %d sound events, %d lang lines" % (len(events), len(lines)))
