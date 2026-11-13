"""Assets for sculk, sculk veins, catalysts, sensors and shriekers, from 1.19.2.

    python -I tools/sculk/generate.py

Blockstates, models, item models, block and particle textures, sounds (with the warden's four replies to a
shrieker), English names and subtitles, and the advancements It Spreads and Sneak 100.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import assets  # noqa: E402

M = assets.MODID
BLOCKS = ["sculk", "sculk_vein", "sculk_catalyst", "sculk_sensor", "sculk_shrieker"]

for n in BLOCKS:
    assets.port_blockstate(n)
    assets.port_item(n)

# Particle sprites (stitched into the block atlas by the client).
for i in range(7):
    assets.texture("particle/sculk_charge_%d" % i)
for i in range(4):
    assets.texture("particle/sculk_charge_pop_%d" % i)
for i in range(11):
    assets.texture("particle/sculk_soul_%d" % i)
assets.texture("particle/shriek")

events = assets.block_sounds(*BLOCKS)
for e in ("block.sculk.charge", "block.sculk.spread", "block.sculk_catalyst.bloom", "block.sculk_sensor.clicking",
          "block.sculk_sensor.clicking_stop", "block.sculk_shrieker.shriek", "entity.warden.nearby_close",
          "entity.warden.nearby_closer", "entity.warden.nearby_closest", "entity.warden.listening_angry"):
    events[e] = assets.copy_sound(e)
assets.merge_sounds("sculk", events)


def advancement(name, parent, icon, frame, trigger):
    assets.write_json("advancements/adventure/%s.json" % name, {
        "parent": parent,
        "display": {
            "icon": {"item": "%s:%s" % (M, icon)},
            "title": {"translate": "advancements.%s.%s.title" % (M, name)},
            "description": {"translate": "advancements.%s.%s.description" % (M, name)},
            "frame": frame,
            "show_toast": True,
            "announce_to_chat": True,
            "hidden": False,
        },
        "criteria": {name: {"trigger": "%s:%s" % (M, trigger)}},
        "requirements": [[name]],
    })


advancement("kill_mob_near_sculk_catalyst", "minecraft:adventure/kill_a_mob", "sculk_catalyst", "challenge",
            "kill_mob_near_sculk_catalyst")
advancement("avoid_vibration", "minecraft:adventure/root", "sculk_sensor", "task", "avoid_vibration")

lines = assets.block_names(BLOCKS)
for adv in ("kill_mob_near_sculk_catalyst", "avoid_vibration"):
    for part in ("title", "description"):
        lines.append("advancements.%s.%s.%s=%s" % (M, adv, part, assets.name("advancements.adventure.%s.%s" % (adv, part))))
lines += assets.subtitle_lines(events)
assets.merge_lang("sculk", lines)
print("sculk: %d sound events, %d lang lines" % (len(events), len(lines)))
