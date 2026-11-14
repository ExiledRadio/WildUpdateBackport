"""Assets for stream Gt: the goat copied from Caves & Cliffs Backport, for games without it.

    python -I tools/goats/generate.py [path to CavesAndCliffsBackport]

Everything comes from the owner's own assets, so the copy looks, sounds and reads exactly as the owner's goat:
its 17 sound events (renamed into our namespace, same files, volumes and pitches), its .ogg files, its texture,
its English lines (goat, subtitles, the boat advancement) and its settings' lines (config.<ours>.goats.*).
"""
import json
import os
import shutil
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import assets  # noqa: E402

M = assets.MODID
CC = "cavesandcliffsbackport"
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SRC = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(ROOT), "CavesAndCliffsBackport")
CC_ASSETS = os.path.join(SRC, "src", "main", "resources", "assets", CC)


def ours(text):
    return text.replace(CC + ":", M + ":").replace("." + CC + ".", "." + M + ".")


# Sound events and files.
cc_sounds = json.load(open(os.path.join(CC_ASSETS, "sounds.json"), encoding="utf-8"))
events = {}
for key, spec in cc_sounds.items():
    if key.startswith("entity.goat."):
        events[key] = json.loads(ours(json.dumps(spec)))
        for s in spec["sounds"]:
            name = s["name"] if isinstance(s, dict) else s
            rel = name.split(":", 1)[1] + ".ogg"
            dst = os.path.join(assets.ASSETS, "sounds", rel)
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            shutil.copyfile(os.path.join(CC_ASSETS, "sounds", rel), dst)
assets.merge_sounds("goats", events)

# Texture.
dst = os.path.join(assets.ASSETS, "textures", "entity", "goat")
os.makedirs(dst, exist_ok=True)
shutil.copyfile(os.path.join(CC_ASSETS, "textures", "entity", "goat", "goat.png"), os.path.join(dst, "goat.png"))

# English: the owner's goat section and its goat settings, renamed.
lines = []
for line in open(os.path.join(CC_ASSETS, "lang", "en_us.lang"), encoding="utf-8").read().split("\n"):
    key = line.split("=", 1)[0]
    if ".goat." in key or key.endswith("ride_a_boat_with_a_goat.title") or key.endswith("ride_a_boat_with_a_goat.description") \
            or key.startswith("config.%s.goats" % CC):
        lines.append(ours(line))
# Our settings say when they apply.
lines = [l.replace("Goats and screaming goats.", "Goats and screaming goats, when Caves & Cliffs (which owns them) is not installed.")
         if l.startswith("config.%s.goats.tooltip=" % M) else l for l in lines]
lines = [("config.%s.goats.enabled.tooltip=Goats, when Caves & Cliffs is not installed (with it, its goats are used). "
          "Off, nothing is registered." % M) if l.startswith("config.%s.goats.enabled.tooltip=" % M) else l for l in lines]
assets.merge_lang("goats", sorted(set(lines)))
print("goats: %d sound events, %d lang lines" % (len(events), len(lines)))
