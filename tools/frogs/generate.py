"""Assets for frogs, tadpoles, frogspawn, froglights and the bucket of tadpole.

    python -I tools/frogs/generate.py

Textures, models, names and the frog's keyframe animations from 1.19.2; sounds from current Java's
sounds.json and asset index (the plan's "now": 1.19.4 made froglights quieter), cached in
G:/MCDownloads/wu-frogs-index.
"""
import hashlib
import json
import os
import re
import sys
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import assets  # noqa: E402

M = assets.MODID
SRC = "G:/MCDownloads/wu-1.19.2-src/net/minecraft/client/animation/definitions/FrogAnimation.java"
NOW = "G:/MCDownloads/wu-frogs-index"
NOW_VERSION = "G:/MCDownloads/minecraft-26.3/26.3.json"

# --- blocks and items ------------------------------------------------------------------------------
LIGHTS = ["ochre_froglight", "verdant_froglight", "pearlescent_froglight"]
for n in LIGHTS:
    assets.port_blockstate(n)
    assets.port_item(n)
assets.port_blockstate("frogspawn")
assets.port_item("frogspawn")
assets.port_item("tadpole_bucket")
for v in ("temperate", "warm", "cold"):
    assets.texture("entity/frog/%s_frog" % v)
assets.texture("entity/tadpole/tadpole")

# --- animations: 1.19.2's FrogAnimation as data ----------------------------------------------------
text = open(SRC, encoding="utf-8").read()
NAMES = {"FROG_CROAK": "croak", "FROG_WALK": "walk", "FROG_JUMP": "jump", "FROG_TONGUE": "tongue",
         "FROG_SWIM": "swim", "FROG_IDLE_WATER": "idle_water"}
anims = {}
blocks = re.split(r"public static final AnimationDefinition (\w+) = ", text)[1:]
for name, body in zip(blocks[0::2], blocks[1::2]):
    length = float(re.search(r"withLength\(([\d.]+)F?\)", body).group(1))
    looping = ".looping()" in body
    channels = []
    for bone, target, keys in re.findall(r'addAnimation\(\s*"(\w+)",\s*new AnimationChannel\(\s*AnimationChannel\.Targets\.(\w+),(.*?)\)\s*\)\s*\)',
                                         body, re.S):
        frames = []
        for t, kind, a, b, c, interp in re.findall(
                r"new Keyframe\(([-\d.]+)F?, KeyframeAnimations\.(\w+)\(([-\d.]+)F?, ([-\d.]+)F?, ([-\d.]+)F?\), "
                r"AnimationChannel\.Interpolations\.(\w+)", keys):
            x, y, z = float(a), float(b), float(c)
            if kind == "posVec":
                vec = [x, -y, z]
            elif kind == "degreeVec":
                vec = [v * 3.14159265358979 / 180.0 for v in (x, y, z)]
            else:  # scaleVec
                vec = [x - 1.0, y - 1.0, z - 1.0]
            frames.append([float(t)] + [round(v, 6) for v in vec] + [interp.lower()])
        channels.append({"bone": bone, "target": target.lower(), "keyframes": frames})
    anims[NAMES[name]] = {"length": length, "looping": looping, "channels": channels}
assert set(anims) == set(NAMES.values()), anims.keys()
assets.write_json("animations/frog.json", anims)

# --- sounds: current Java ---------------------------------------------------------------------------
version = json.load(open(NOW_VERSION, encoding="utf-8"))
index_path = "%s/%s.json" % (NOW, version["assetIndex"]["id"])
if not os.path.exists(index_path):
    os.makedirs(NOW, exist_ok=True)
    with open(index_path, "wb") as f:
        f.write(urllib.request.urlopen(version["assetIndex"]["url"]).read())
NOW_INDEX = json.load(open(index_path, encoding="utf-8"))["objects"]


def now_object(path):
    h = NOW_INDEX[path]["hash"]
    full = "%s/objects/%s/%s" % (NOW, h[:2], h)
    if not os.path.exists(full):
        data = urllib.request.urlopen("https://resources.download.minecraft.net/%s/%s" % (h[:2], h)).read()
        if hashlib.sha1(data).hexdigest() != h:
            raise IOError("hash mismatch for " + path)
        os.makedirs(os.path.dirname(full), exist_ok=True)
        with open(full, "wb") as f:
            f.write(data)
    with open(full, "rb") as f:
        return f.read()


import zipfile  # noqa: E402
# Current Java names subtitles 1.19 did not have (the tadpole growing up): its English names too.
NOW_LANG = json.loads(zipfile.ZipFile("G:/MCDownloads/minecraft-26.3/client.jar").read("assets/minecraft/lang/en_us.json").decode("utf-8"))
NOW_SOUNDS = json.loads(now_object("minecraft/sounds.json").decode("utf-8"))


def now_sound(event):
    """Current Java's entry for an event, files copied under our namespace; event references resolved."""
    spec = json.loads(json.dumps(NOW_SOUNDS[event]))
    out = []
    for sound in spec["sounds"]:
        entry = dict(sound) if isinstance(sound, dict) else {"name": sound}
        if entry.get("type") == "event":
            # 1.12.2 may not have the referenced event (entity.tropical_fish.flop): take its sounds.
            out.extend(now_sound(entry["name"].split(":")[-1])["sounds"])
            continue
        path = entry["name"].split(":")[-1]
        assets.write("sounds/%s.ogg" % path, now_object("minecraft/sounds/%s.ogg" % path))
        entry["name"] = "%s:%s" % (M, path)
        out.append(entry if len(entry) > 1 else entry["name"])
    spec["sounds"] = out
    if "subtitle" in spec and spec["subtitle"] not in assets.OLD_LANG:
        spec["subtitle"] = spec["subtitle"].replace("subtitles.", "subtitles.%s." % M, 1)
    return spec


EVENTS = ["entity.frog.%s" % e for e in ("ambient", "death", "eat", "hurt", "lay_spawn", "long_jump", "step", "tongue")]
EVENTS += ["entity.tadpole.%s" % e for e in ("death", "flop", "grow_up", "hurt")]
EVENTS += ["item.bucket.empty_tadpole", "item.bucket.fill_tadpole"]
EVENTS += ["block.frogspawn.%s" % e for e in ("break", "fall", "hatch", "hit", "place", "step")]
EVENTS += ["block.froglight.%s" % e for e in ("break", "fall", "hit", "place", "step")]
events = {e: now_sound(e) for e in EVENTS}
assets.merge_sounds("frogs", events)

# --- advancements -------------------------------------------------------------------------------------
lang = []


def advancement(name, parent, icon, frame, criteria, requirements):
    key = "advancements.%s.husbandry.%s" % (M, name)
    assets.write_json("advancements/husbandry/%s.json" % name, {
        "parent": parent,
        "display": {"icon": {"item": icon}, "title": {"translate": key + ".title"},
                    "description": {"translate": key + ".description"}, "frame": frame,
                    "show_toast": True, "announce_to_chat": True, "hidden": False},
        "criteria": criteria,
        "requirements": requirements,
    })
    lang.append("%s.title=%s" % (key, assets.name("advancements.husbandry.%s.title" % name)))
    lang.append("%s.description=%s" % (key, assets.name("advancements.husbandry.%s.description" % name)))


# 1.19's parent, tactical_fishing, is a 1.13 advancement 1.12.2 lacks: the husbandry root instead.
advancement("tadpole_in_a_bucket", "minecraft:husbandry/root", "%s:tadpole_bucket" % M, "task",
            {"tadpole_bucket": {"trigger": "%s:filled_bucket" % M,
                                "conditions": {"item": {"item": "%s:tadpole_bucket" % M}}}},
            [["tadpole_bucket"]])
advancement("leash_all_frog_variants", "%s:husbandry/tadpole_in_a_bucket" % M, "minecraft:lead", "task",
            {v: {"trigger": "%s:leashed_frog" % M, "conditions": {"variant": "minecraft:%s" % v}}
             for v in ("temperate", "warm", "cold")},
            [["temperate"], ["warm"], ["cold"]])
advancement("froglights", "%s:husbandry/leash_all_frog_variants" % M, "%s:verdant_froglight" % M, "challenge",
            {"froglights": {"trigger": "minecraft:inventory_changed",
                            "conditions": {"items": [{"item": "%s:%s" % (M, n)} for n in
                                                     ("ochre_froglight", "pearlescent_froglight", "verdant_froglight")]}}},
            [["froglights"]])

# --- names ----------------------------------------------------------------------------------------
lang += assets.block_names(LIGHTS + ["frogspawn"])
lang.append("item.%s.tadpole_bucket.name=%s" % (M, assets.name("item.minecraft.tadpole_bucket")))
lang.append("entity.%s.frog.name=%s" % (M, assets.name("entity.minecraft.frog")))
lang.append("entity.%s.tadpole.name=%s" % (M, assets.name("entity.minecraft.tadpole")))
for e in events.values():
    key = e.get("subtitle")
    if key and key.startswith("subtitles.%s." % M):
        vanilla = key.replace("%s." % M, "", 1)
        lang.append("%s=%s" % (key, assets.VANILLA_LANG.get(vanilla) or NOW_LANG[vanilla]))
assets.merge_lang("frogs", sorted(set(lang), key=lang.index))
print("frogs: %d animations, %d sound events, %d lang lines" % (len(anims), len(events), len(lang)))
