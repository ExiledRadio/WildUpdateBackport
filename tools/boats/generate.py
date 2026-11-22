"""Assets for the mangrove boat and boats with chests, from 1.19.2.

    python -I tools/boats/generate.py
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import assets  # noqa: E402

M = assets.MODID
WOODS = ["oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove"]

assets.port_item("mangrove_boat")
assets.texture("entity/boat/mangrove")
lines = ["item.%s.mangrove_boat.name=%s" % (M, assets.name("item.minecraft.mangrove_boat"))]
for w in WOODS:
    assets.port_item("%s_chest_boat" % w)
    assets.texture("entity/chest_boat/%s" % w)
    lines.append("item.%s.%s_chest_boat.name=%s" % (M, w, assets.name("item.minecraft.%s_chest_boat" % w)))
# The boat entities are Backport Core's (its boat/), named there.
assets.merge_lang("boats", lines)
print("boats: %d lang lines" % len(lines))
