"""Stream DD's assets, from 1.19.2: reinforced deepslate, the ancient city's templates, pools, processor lists and
loot table files. The deepslate copies are tools/deepdark/deepslate.py's.

    python -I tools/deepdark/generate.py

* Templates: every data/minecraft/structures/ancient_city/**.nbt of the 1.19.2 jar, unchanged, under
  structures/ancient_city/ (Backport Core serves minecraft:ancient_city/... from this mod's assets).
* Pools: 1.19.2's AncientCityStructurePieces / AncientCityStructurePools, entry for entry, in the JSON form 1.18+
  writes (worldgen/template_pool/ancient_city/...). 1.19.2 builds them in code; the jar has no files for them.
  ancient_city/walls/no_corners names intact_horizontal_wall_stairs_5, which 1.19.2 does not ship either: it stays,
  so the pool shuffles as 1.19.2's does, and loads as an empty template that joins nothing.
* Processor lists: named only; the city's pieces run 1.19's lists in code (deepdark/city/CityProcessors).
* Loot tables: empty files the code fills (deepdark/AncientCityLoot).
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import assets  # noqa: E402

SECTION = "deepdark"
PREFIX = "data/minecraft/structures/ancient_city/"

# ---- reinforced deepslate
assets.port_blockstate("reinforced_deepslate")
assets.port_item("reinforced_deepslate")

# ---- templates
count = 0
for info in assets.JAR.infolist():
    if info.filename.startswith(PREFIX) and info.filename.endswith(".nbt"):
        assets.write("structures/ancient_city/" + info.filename[len(PREFIX):], assets.jar_bytes(info.filename))
        count += 1

# ---- pools
START = "minecraft:ancient_city_start_degradation"
GENERIC = "minecraft:ancient_city_generic_degradation"
WALLS = "minecraft:ancient_city_walls_degradation"


def single(location, processors="minecraft:empty"):
    return {"element_type": "minecraft:single_pool_element", "location": "minecraft:ancient_city/" + location,
            "processors": processors, "projection": "rigid"}


def listed(*elements):
    return {"element_type": "minecraft:list_pool_element", "elements": list(elements), "projection": "rigid"}


EMPTY = {"element_type": "minecraft:empty_pool_element"}


def pool(name, entries):
    assets.write_json("worldgen/template_pool/ancient_city/%s.json" % name, {
        "name": "minecraft:ancient_city/" + name, "fallback": "minecraft:empty",
        "elements": [{"weight": w, "element": e} for e, w in entries]})


pool("city_center", [(single("city_center/city_center_%d" % i, START), 1) for i in (1, 2, 3)])
pool("structures", [
    (EMPTY, 7),
    (single("structures/barracks", GENERIC), 4),
    (single("structures/chamber_1", GENERIC), 4),
    (single("structures/chamber_2", GENERIC), 4),
    (single("structures/chamber_3", GENERIC), 4),
    (single("structures/sauna_1", GENERIC), 4),
    (single("structures/small_statue", GENERIC), 4),
    (single("structures/large_ruin_1", GENERIC), 1),
    (single("structures/tall_ruin_1", GENERIC), 1),
    (single("structures/tall_ruin_2", GENERIC), 1),
    (single("structures/tall_ruin_3", GENERIC), 2),
    (single("structures/tall_ruin_4", GENERIC), 2),
    (listed(single("structures/camp_1", GENERIC), single("structures/camp_2", GENERIC), single("structures/camp_3", GENERIC)), 1),
    (single("structures/medium_ruin_1", GENERIC), 1),
    (single("structures/medium_ruin_2", GENERIC), 1),
    (single("structures/small_ruin_1", GENERIC), 1),
    (single("structures/small_ruin_2", GENERIC), 1),
    (single("structures/large_pillar_1", GENERIC), 1),
    (single("structures/medium_pillar_1", GENERIC), 1),
    (listed(single("structures/ice_box_1")), 1),
])
pool("sculk", [({"element_type": "minecraft:feature_pool_element", "feature": "minecraft:sculk_patch_ancient_city",
                 "projection": "rigid"}, 6), (EMPTY, 1)])
pool("walls", [(single("walls/" + n, WALLS), w) for n, w in [
    ("intact_corner_wall_1", 1), ("intact_intersection_wall_1", 1), ("intact_lshape_wall_1", 1),
    ("intact_horizontal_wall_1", 1), ("intact_horizontal_wall_2", 1), ("intact_horizontal_wall_stairs_1", 1),
    ("intact_horizontal_wall_stairs_2", 1), ("intact_horizontal_wall_stairs_3", 1), ("intact_horizontal_wall_stairs_4", 4),
    ("intact_horizontal_wall_passage_1", 3), ("ruined_corner_wall_1", 1), ("ruined_corner_wall_2", 1),
    ("ruined_horizontal_wall_stairs_1", 2), ("ruined_horizontal_wall_stairs_2", 2), ("ruined_horizontal_wall_stairs_3", 3),
    ("ruined_horizontal_wall_stairs_4", 3)]])
pool("walls/no_corners", [(single("walls/" + n, WALLS), 1) for n in [
    "intact_horizontal_wall_1", "intact_horizontal_wall_2", "intact_horizontal_wall_stairs_1", "intact_horizontal_wall_stairs_2",
    "intact_horizontal_wall_stairs_3", "intact_horizontal_wall_stairs_4", "intact_horizontal_wall_stairs_5",
    "intact_horizontal_wall_bridge"]])
pool("city_center/walls", [(single("city_center/walls/" + n, GENERIC), 1) for n in [
    "bottom_1", "bottom_2", "bottom_left_corner", "bottom_right_corner_1", "bottom_right_corner_2", "left", "right", "top",
    "top_right_corner", "top_left_corner"]])
pool("city/entrance", [(single("city/entrance/" + n, GENERIC), 1) for n in [
    "entrance_connector", "entrance_path_1", "entrance_path_2", "entrance_path_3", "entrance_path_4", "entrance_path_5"]])

# ---- processor lists (run in code) and loot table files (filled in code)
for name in ("ancient_city_start_degradation", "ancient_city_generic_degradation", "ancient_city_walls_degradation"):
    assets.write_json("worldgen/processor_list/%s.json" % name, {"processors": []})
for name in ("ancient_city", "ancient_city_ice_box"):
    assets.write_json("loot_tables/chests/%s.json" % name, {"pools": []})

lines = sorted(assets.block_names(["reinforced_deepslate"]))
assets.merge_lang(SECTION, lines)
print("%s: %d templates, %d lang lines" % (SECTION, count, len(lines)))
