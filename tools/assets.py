"""Shared helpers for the streams' asset generators: textures, sounds and names from modern Minecraft.

Sources:
  * textures, models and English names: the 1.19.2 client jar (G:/MCDownloads/wu-1.19.2/client.jar);
  * sounds and sounds.json: the 1.19 asset index; objects missing from the local store are downloaded
    once from Mojang's resource server into G:/MCDownloads/wu-1.19-objects.

Every stream's generator (tools/<stream>/generate.py) imports this module, copies what it needs into
src/main/resources/assets/wildupdatebackport/, and merges its sound events and language lines into the
shared sounds.json and en_us.lang between markers of its own, so generators never overwrite each other.
"""
import hashlib
import json
import os
import shutil
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODID = "wildupdatebackport"
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", MODID)
INSTALL = "G:/Sort this shit/Minecraft/Install"
DOWNLOADS = "G:/MCDownloads"
JAR = zipfile.ZipFile(DOWNLOADS + "/wu-1.19.2/client.jar")
INDEX = json.load(open(DOWNLOADS + "/wu-1.19.2/1.19.json", encoding="utf-8"))["objects"]
OBJECTS = DOWNLOADS + "/wu-1.19-objects"


def _object(path):
    """@return the bytes of an asset-index object such as 'minecraft/sounds.json' (downloaded once if needed)."""
    h = INDEX[path]["hash"]
    for base in ("%s/assets/objects" % INSTALL, OBJECTS):
        full = "%s/%s/%s" % (base, h[:2], h)
        if os.path.exists(full):
            with open(full, "rb") as f:
                return f.read()
    import urllib.request
    data = urllib.request.urlopen("https://resources.download.minecraft.net/%s/%s" % (h[:2], h)).read()
    if hashlib.sha1(data).hexdigest() != h:
        raise IOError("hash mismatch for " + path)
    full = "%s/%s/%s" % (OBJECTS, h[:2], h)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "wb") as f:
        f.write(data)
    return data


VANILLA_SOUNDS = json.loads(_object("minecraft/sounds.json").decode("utf-8"))
# English is the one language shipped inside the client jar rather than the asset index.
VANILLA_LANG = json.loads(JAR.read("assets/minecraft/lang/en_us.json").decode("utf-8"))


def write(rel, data):
    """Writes bytes or text to a path under our assets folder, creating directories."""
    full = os.path.join(ASSETS, *rel.split("/"))
    os.makedirs(os.path.dirname(full), exist_ok=True)
    mode = "wb" if isinstance(data, bytes) else "w"
    with open(full, mode, **({} if mode == "wb" else {"encoding": "utf-8", "newline": "\n"})) as f:
        f.write(data)


def write_json(rel, obj):
    write(rel, json.dumps(obj, indent=2) + "\n")


def jar_bytes(path):
    return JAR.read(path)


def jar_json(path):
    return json.loads(JAR.read(path).decode("utf-8"))


def texture(src, dst=None):
    """Copies textures/<src>.png (and its .mcmeta, for animated ones) from the 1.19.2 jar to ours.

    @param src like 'block/kelp'; @param dst our path, default the same
    @return our texture name, 'wildupdatebackport:<dst>'
    """
    dst = dst or src
    write("textures/%s.png" % dst, jar_bytes("assets/minecraft/textures/%s.png" % src))
    meta = "assets/minecraft/textures/%s.png.mcmeta" % src
    if meta in JAR.namelist():
        write("textures/%s.png.mcmeta" % dst, jar_bytes(meta))
    return "%s:%s" % (MODID, dst)


def vanilla_sound(event):
    """@return 1.19's sounds.json entry for an event such as 'block.wet_grass.break'."""
    return VANILLA_SOUNDS[event]


def copy_sound(event, our_event=None):
    """Copies an event's .ogg files into our sounds/ and returns our sounds.json entry for it.

    Files keep their vanilla paths under our namespace; event references stay as they are.
    """
    spec = json.loads(json.dumps(VANILLA_SOUNDS[event]))
    out = []
    for sound in spec["sounds"]:
        entry = dict(sound) if isinstance(sound, dict) else {"name": sound}
        if entry.get("type") == "event":
            out.append(entry)
            continue
        name = entry["name"]
        ns, _, path = name.rpartition(":")
        data = _object("minecraft/sounds/%s.ogg" % path)
        write("sounds/%s.ogg" % path, data)
        entry["name"] = "%s:%s" % (MODID, path)
        out.append(entry if len(entry) > 1 else entry["name"])
    spec["sounds"] = out
    if "subtitle" in spec and spec["subtitle"] not in OLD_LANG:
        # A subtitle 1.12.2 lacks gets our own key; subtitle_lines() gives its English line.
        spec["subtitle"] = spec["subtitle"].replace("subtitles.", "subtitles.%s." % MODID, 1)
    return spec


def subtitle_lines(specs):
    """@return the en_us.lang lines for every subtitle of ours in these sounds.json entries."""
    lines = []
    for spec in specs.values():
        key = spec.get("subtitle")
        if key and key.startswith("subtitles.%s." % MODID):
            lines.append("%s=%s" % (key, VANILLA_LANG[key.replace("%s." % MODID, "", 1)]))
    return sorted(set(lines))


OLD_JAR = zipfile.ZipFile(os.path.join(os.path.expanduser("~"), ".gradle", "caches", "minecraft", "net", "minecraft",
                                       "minecraft", "1.12.2", "minecraft-1.12.2.jar"))
OLD_NAMES = set(OLD_JAR.namelist())
OLD_LANG = dict(l.split("=", 1) for l in OLD_JAR.read("assets/minecraft/lang/en_us.lang").decode("utf-8").splitlines()
                if "=" in l and not l.startswith("#"))

# 1.19 texture folders and their 1.12.2 names, for parents and textures that 1.12.2 already has.
_OLD_TEXTURE_DIRS = {"block/": "blocks/", "item/": "items/"}


def vanilla_or_ours(tpath):
    """A 1.19 texture path as our model should name it.

    A texture 1.12.2 has under its old folder and the same name (block/dirt -> blocks/dirt) stays vanilla's,
    so resource packs and Backport Core's modern-texture pack apply to it; anything else is copied to ours.
    """
    for new, old in _OLD_TEXTURE_DIRS.items():
        if tpath.startswith(new):
            old_path = old + tpath[len(new):]
            if ("assets/minecraft/textures/%s.png" % old_path) in OLD_NAMES:
                return old_path
    return texture(tpath)


def port_model(path, ours=None, textures_from_jar=True):
    """Ports models/<path>.json from the 1.19.2 jar into our namespace, ready for 1.12.2.

    * Textures named 'minecraft:block/x' are copied to ours and renamed 'wildupdatebackport:block/x'.
    * A parent 1.12.2 also has (block/cube_all, block/cross, item/generated, ...) is kept as vanilla's;
      any other vanilla parent is ported too, recursively.
    @return our model name, 'wildupdatebackport:<ours or path>'
    """
    ours = ours or path
    data = jar_json("assets/minecraft/models/%s.json" % path)
    parent = data.get("parent")
    if parent:
        pns, _, ppath = parent.rpartition(":")
        if (pns or "minecraft") == "minecraft":
            if ppath.startswith("builtin/") or ("assets/minecraft/models/%s.json" % ppath) in OLD_NAMES:
                data["parent"] = ppath
            else:
                data["parent"] = port_model(ppath)
    for key, value in list(data.get("textures", {}).items()):
        if value.startswith("#"):
            continue
        tns, _, tpath = value.rpartition(":")
        if (tns or "minecraft") == "minecraft" and textures_from_jar:
            data["textures"][key] = vanilla_or_ours(tpath)
    write_json("models/%s.json" % ours, data)
    return "%s:%s" % (MODID, ours)


def name(key):
    """@return 1.19's English name for a translation key such as 'block.minecraft.kelp'."""
    return VANILLA_LANG[key]


def merge_sounds(section, events):
    """Replaces this generator's events in sounds.json (keys it wrote before are dropped first)."""
    path = os.path.join(ASSETS, "sounds.json")
    data = json.load(open(path, encoding="utf-8")) if os.path.exists(path) else {}
    marker = os.path.join(ROOT, "tools", section, ".sounds")
    if os.path.exists(marker):
        for old in open(marker, encoding="utf-8").read().split():
            data.pop(old, None)
    data.update(events)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(json.dumps(data, indent=2) + "\n")
    with open(marker, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(sorted(events)) + "\n")


def merge_lang(section, lines):
    """Replaces the block between '# --- <section> ---' and the next '# ---' (or the end) in en_us.lang."""
    path = os.path.join(ASSETS, "lang", "en_us.lang")
    text = open(path, encoding="utf-8").read().rstrip("\n").split("\n")
    head = "# --- %s ---" % section
    out = []
    skipping = False
    for line in text:
        if line == head:
            skipping = True
            continue
        if skipping and line.startswith("# --- "):
            skipping = False
        if not skipping:
            out.append(line)
    while out and out[-1] == "":
        out.pop()
    out += ["", head] + lines
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(out) + "\n")


def item_generated(name, texture_name):
    """Writes models/item/<name>.json as a flat item with one texture."""
    write_json("models/item/%s.json" % name, {"parent": "item/generated", "textures": {"layer0": texture_name}})


def item_from_block(name, model=None):
    """Writes models/item/<name>.json that shows the block model."""
    write_json("models/item/%s.json" % name, {"parent": "%s:block/%s" % (MODID, model or name)})


# --- Blockstates and item models ported from 1.19 (shared by the streams' generators) ----------------

def blockstate_model(name):
    """'minecraft:block/x' in a 1.19 blockstate -> ported model, named 'wildupdatebackport:x' as 1.12.2 wants."""
    path = name.split(":", 1)[-1]
    port_model(path)
    return "%s:%s" % (MODID, path[len("block/"):])


def vanilla_blockstate(name):
    return jar_json("assets/minecraft/blockstates/%s.json" % name)


def port_variants(variants):
    """Converts 1.19 variants: models ported, '' becomes 'normal'."""
    out = {}
    for key, entry in variants.items():
        entries = entry if isinstance(entry, list) else [entry]
        conv = [dict(e, model=blockstate_model(e["model"])) for e in entries]
        out["normal" if key == "" else key] = conv if isinstance(entry, list) else conv[0]
    return out


def port_blockstate(name, ours=None, when_map=None):
    """Ports blockstates/<name>.json from 1.19.

    @param when_map for multipart files, maps one 'when' dict to 1.12.2's (or None to drop the part)
    """
    data = vanilla_blockstate(name)
    if "multipart" in data:
        parts = []
        for part in data["multipart"]:
            part = dict(part)
            if "when" in part and when_map:
                part["when"] = when_map(part["when"])
                if part["when"] is None:
                    continue
            applies = part["apply"]
            if isinstance(applies, list):
                part["apply"] = [dict(a, model=blockstate_model(a["model"])) for a in applies]
            else:
                part["apply"] = dict(applies, model=blockstate_model(applies["model"]))
            parts.append(part)
        write_json("blockstates/%s.json" % (ours or name), {"multipart": parts})
    else:
        write_json("blockstates/%s.json" % (ours or name), {"variants": port_variants(data["variants"])})


def port_slab(name, double_name):
    """1.19's one slab block (type=bottom/top/double) as 1.12.2's half and double blocks."""
    v = vanilla_blockstate(name)["variants"]
    write_json("blockstates/%s.json" % name, {"variants": {
        "half=bottom": dict(v["type=bottom"], model=blockstate_model(v["type=bottom"]["model"])),
        "half=top": dict(v["type=top"], model=blockstate_model(v["type=top"]["model"])),
    }})
    write_json("blockstates/%s.json" % double_name, {"variants": {
        "normal": dict(v["type=double"], model=blockstate_model(v["type=double"]["model"])),
    }})


def wall_when(when):
    """1.19 walls name each side none/low/tall; 1.12.2's walls have a side or not (low)."""
    out = {}
    for k, v in when.items():
        if k == "up":
            out[k] = v
        elif v == "tall":
            return None
        else:
            out[k] = "true" if v == "low" else "false"
    return out


def port_item(name, ours=None):
    """models/item/<name>.json from 1.19's: a block parent is ported, flat items copy their textures."""
    data = jar_json("assets/minecraft/models/item/%s.json" % name)
    parent = data.get("parent", "").split(":")[-1]
    if parent.startswith("block/"):
        blockstate_model("minecraft:" + parent)
        data["parent"] = "%s:%s" % (MODID, parent)
    elif parent.startswith("item/") and parent not in ("item/generated", "item/handheld", "item/handheld_rod"):
        data["parent"] = port_model(parent)
    elif parent:
        data["parent"] = parent
    for key, value in list(data.get("textures", {}).items()):
        if not value.startswith("#"):
            data["textures"][key] = texture(value.split(":")[-1])
    write_json("models/item/%s.json" % (ours or name), data)


def block_sounds(*names):
    """Copies the five block.<name>.<break|step|place|hit|fall> events of each sound type."""
    events = {}
    for n in names:
        for kind in ("break", "step", "place", "hit", "fall"):
            e = "block.%s.%s" % (n, kind)
            events[e] = copy_sound(e)
    return events


def block_names(names, extra=None):
    """tile.<mod>.<name>.name lines with 1.19's English names; extra maps our name -> 1.19 key."""
    lines = ["tile.%s.%s.name=%s" % (MODID, n, name("block.minecraft." + n)) for n in names]
    for ours, key in (extra or {}).items():
        lines.append("tile.%s.%s.name=%s" % (MODID, ours, name(key)))
    return lines
