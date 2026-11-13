"""Compares an ancient city's surroundings across worlds (1.12.2 with 1.18 heights, or vanilla 1.19.2): block counts in
an area, and a picture per world: the floor seen from above (the first block under open space, below y -12) and
two vertical slices through the start.

    python -I tools/deepdark/census.py OUT.png X0 Z0 X1 Z1 SX SZ LABEL=WORLD [LABEL=WORLD ...]

Colors: sculk teal, the city's blocks tan, rock grey (darker lower down), open space black, missing chunks red.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import anvil  # noqa: E402
from PIL import Image, ImageDraw  # noqa: E402

NATURAL = ('deepslate', 'stone', 'tuff', 'granite', 'diorite', 'andesite', 'gravel', 'dirt', 'clay', 'calcite',
           'bedrock', 'cobblestone', 'dripstone_block', 'moss_block', 'smooth_basalt', 'amethyst_block', 'budding_amethyst')
OPEN = ('air', 'water', 'lava', 'glow_lichen', 'cave_vines', 'pointed_dripstone', 'moss_carpet', 'spore_blossom',
        'flowing_water', 'flowing_lava', 'tallgrass', 'hanging_roots')


def kind(name):
    if name is None:
        return 'missing'
    n = name.split(':')[-1]
    if 'sculk' in n:
        return 'sculk'
    if n in OPEN or n.endswith('_air') or 'lichen' in n or 'vines' in n:
        return 'open'
    if n in NATURAL or n.endswith('_ore') or n.endswith('deepslate') and 'brick' not in n and 'tile' not in n:
        return 'rock'
    return 'city'


def color(k, y):
    if k == 'sculk':
        return (20, 140, 150)
    if k == 'city':
        return (190, 160, 110)
    if k == 'open':
        return (0, 0, 0)
    if k == 'missing':
        return (160, 0, 0)
    g = int(70 + 120 * max(0, min(1, (y + 64) / 64.0)))
    return (g, g, g)


def main():
    out = sys.argv[1]
    x0, z0, x1, z1, sx, sz = (int(a) for a in sys.argv[2:8])
    worlds = [a.split('=', 1) for a in sys.argv[8:]]
    lo, hi = -64, 0
    panels = []
    for label, folder in worlds:
        w = anvil.World(folder)
        counts = {}
        floor_sculk = floor_total = 0
        top = Image.new('RGB', (x1 - x0 + 1, z1 - z0 + 1))
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                was_open = False
                floor = None
                for y in range(hi - 1, lo - 1, -1):
                    k = kind(w.block(x, y, z))
                    counts[k] = counts.get(k, 0) + 1
                    if floor is None and y < -12:
                        if k == 'missing':
                            floor = ('missing', y)
                        elif k == 'open':
                            was_open = True
                        elif was_open:
                            floor = (k, y)
                if floor is None:
                    floor = ('rock', lo)
                if floor[0] != 'missing':
                    floor_total += 1
                    floor_sculk += floor[0] == 'sculk'
                top.putpixel((x - x0, z - z0), color(*floor))
        slices = []
        for axis in ('x', 'z'):
            span = range(x0, x1 + 1) if axis == 'x' else range(z0, z1 + 1)
            img = Image.new('RGB', (len(span), hi - lo))
            for i, a in enumerate(span):
                for y in range(lo, hi):
                    b = w.block(a, y, sz) if axis == 'x' else w.block(sx, y, a)
                    img.putpixel((i, hi - 1 - y), color(kind(b), y))
            slices.append(img)
        total = sum(v for k, v in counts.items() if k != 'missing')
        line = '%s: %d blocks; open %.1f%%, sculk %d (%.2f%%), city %d; floors under open space %d, sculk on %.1f%%' % (
            label, total, 100.0 * counts.get('open', 0) / max(1, total), counts.get('sculk', 0),
            100.0 * counts.get('sculk', 0) / max(1, total), counts.get('city', 0), floor_total,
            100.0 * floor_sculk / max(1, floor_total))
        if counts.get('missing'):
            line += '; %d missing' % counts['missing']
        print(line)
        panels.append((label, top, slices))
    scale = 2
    width = max(p[1].width for p in panels) * scale
    height = sum((p[1].height + 2 * 64 + 40) * scale for p in panels)
    sheet = Image.new('RGB', (width + 20, height), (255, 255, 255))
    d = ImageDraw.Draw(sheet)
    y = 0
    for label, top, slices in panels:
        d.text((4, y + 2), label + ': floor from above (below y -12)', fill=(0, 0, 0))
        y += 14
        sheet.paste(top.resize((top.width * scale, top.height * scale), Image.NEAREST), (0, y))
        y += top.height * scale + 4
        for s, name in zip(slices, ('slice along x at z %d' % sz, 'slice along z at x %d' % sx)):
            d.text((4, y), label + ': ' + name + ', y -64 to 0', fill=(0, 0, 0))
            y += 12
            sheet.paste(s.resize((s.width * scale, s.height * scale), Image.NEAREST), (0, y))
            y += s.height * scale + 4
    sheet = sheet.crop((0, 0, sheet.width, y))
    sheet.save(out)
    print('wrote ' + out)


if __name__ == '__main__':
    main()
