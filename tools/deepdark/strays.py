"""Counts what the deep dark self-test's city census counts (liquid in the city's space, glow lichen on nothing), in a
1.18+ world saved by vanilla's own server, with the city's pieces the self-test wrote (eclipse/city-pieces-city.txt):
the same city laid out by vanilla, since the layouts are 1.19.2's piece for piece.

    python -I tools/deepdark/strays.py WORLD_FOLDER city-pieces-city.txt

The open space is a piece's box or where 1.19.2's beard_box (CityBeard) is below -squeeze(1); the sauna's own pool is
left out. Reads 1.18+ chunks only (block palettes with properties).
"""
import math
import os
import struct
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import anvil  # noqa: E402

ROCK = 0.5 - 1.0 / 24.0
KERNEL = [0.0] * (24 * 24 * 24)
for i in range(24):
    for j in range(24):
        for k in range(24):
            x, y, z = j - 12, (k - 12) + 0.5, i - 12
            KERNEL[i * 576 + j * 24 + k] = struct.unpack('f', struct.pack('f', math.exp(-(x * x + y * y + z * z) / 16.0)))[0]


def inv_sqrt(v):
    half = 0.5 * v
    i = struct.unpack('>q', struct.pack('>d', v))[0]
    i = 6910469410427058090 - (i >> 1)
    v = struct.unpack('>d', struct.pack('>q', i))[0]
    return v * (1.5 - half * v * v)


def contribution(x, y, z, to_ground):
    i, j, k = x + 12, y + 12, z + 12
    if not (0 <= i < 24 and 0 <= j < 24 and 0 <= k < 24):
        return 0.0
    d = to_ground + 0.5
    e = x * x + d * d + z * z
    f = -d * inv_sqrt(e / 2.0) / 2.0
    return f * KERNEL[k * 576 + i * 24 + j]


def beard(rigids, junctions, x, y, z):
    d = 0.0
    for r in rigids:
        dx = max(0, r[0] - x, x - r[3])
        dz = max(0, r[2] - z, z - r[5])
        dy = max(0, r[1] - y, y - r[4])
        d += contribution(dx, dy, dz, y - r[1]) * 0.8
    cx, cz = x >> 4 << 4, z >> 4 << 4
    for j in junctions:
        if cx - 12 < j[0] < cx + 27 and cz - 12 < j[2] < cz + 27:
            dy = y - j[1]
            d += contribution(x - j[0], dy, z - j[2], dy) * 0.4
    return d


def entry(p):
    if isinstance(p, str):
        return (p, {})
    if '' in p and 'Name' not in p:
        return (p[''], {})
    if 'id' in p:
        # 26.x: {id, properties}.
        return (p['id'], p.get('properties', {}))
    return (p['Name'], p.get('Properties', {}))


def chunk_states(world, cx, cz):
    """{section y: [(name, props)] * 4096} for a full 1.18+ chunk, or None."""
    c = world._region(cx >> 5, cz >> 5).chunk(cx, cz)
    if c is None or c.get('Status') not in ('full', 'minecraft:full'):
        return None
    out = {}
    for s in c['sections']:
        bs = s.get('block_states')
        if not bs:
            continue
        # 26.x writes a block with no properties as its bare name ({"": name} in a list of mixed tags).
        pal = [entry(p) for p in bs['palette']]
        if len(pal) == 1 or 'data' not in bs:
            out[s['Y']] = [pal[0]] * 4096
            continue
        bits = max(4, (len(pal) - 1).bit_length())
        per = 64 // bits
        mask = (1 << bits) - 1
        states = []
        for word in bs['data']:
            w = word & 0xFFFFFFFFFFFFFFFF
            for i in range(per):
                if len(states) == 4096:
                    break
                states.append(pal[(w >> (i * bits)) & mask])
        out[s['Y']] = states
    return out


NOT_SOLID = ('air', 'cave_air', 'void_air', 'water', 'lava', 'glow_lichen', 'sculk_vein', 'light', 'candle')


def main():
    folder, pieces_file = sys.argv[1], sys.argv[2]
    boxes, rigids, junctions = [], [], []
    for line in open(pieces_file):
        p = line.split()
        if p[0] == 'B':
            boxes.append(([int(v) for v in p[1:7]], p[7] == 'sauna'))
        elif p[0] == 'R':
            rigids.append([int(v) for v in p[1:7]])
        elif p[0] == 'J':
            junctions.append([int(v) for v in p[1:4]])
    x0 = min(b[0][0] for b in boxes) - 12
    y0 = min(b[0][1] for b in boxes) - 12
    z0 = min(b[0][2] for b in boxes) - 12
    x1 = max(b[0][3] for b in boxes) + 12
    y1 = max(b[0][4] for b in boxes) + 12
    z1 = max(b[0][5] for b in boxes) + 12
    world = anvil.World(folder)
    # 26.x keeps the Overworld's regions under dimensions/minecraft/overworld.
    modern_dir = os.path.join(folder, 'dimensions', 'minecraft', 'overworld')
    if not os.path.isdir(os.path.join(folder, 'region')) and os.path.isdir(os.path.join(modern_dir, 'region')):
        world.folder = modern_dir
    cache = {}

    def state(x, y, z):
        k = (x >> 4, z >> 4)
        if k not in cache:
            cache[k] = chunk_states(world, k[0], k[1])
        secs = cache[k]
        if secs is None:
            return None
        s = secs.get(y >> 4)
        if s is None:
            return ('minecraft:air', {})
        return s[((y & 15) << 8) | ((z & 15) << 4) | (x & 15)]

    counts = {}
    missing = 0
    lichen = floating = 0
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            for y in range(max(y0, -64), y1 + 1):
                st = state(x, y, z)
                if st is None:
                    missing += 1
                    continue
                name = st[0].split(':')[-1]
                liquid = name in ('water', 'lava')
                is_lichen = name == 'glow_lichen'
                if not liquid and not is_lichen:
                    continue
                where = None
                for b, sauna in boxes:
                    if b[0] <= x <= b[3] and b[1] <= y <= b[4] and b[2] <= z <= b[5]:
                        where = 'sauna' if sauna else (where or 'piece')
                        if sauna:
                            break
                if where is None and beard(rigids, junctions, x, y, z) < -ROCK:
                    where = 'beard'
                if where is None:
                    continue
                if liquid:
                    if where == 'sauna':
                        k = "the sauna's own " + name
                    else:
                        k = '%s %s in %s' % (name, 'source' if st[1].get('level', '0') == '0' else 'flowing', where)
                    counts[k] = counts.get(k, 0) + 1
                else:
                    lichen += 1
                    held = False
                    for face, (dx, dy, dz) in (('north', (0, 0, -1)), ('south', (0, 0, 1)), ('west', (-1, 0, 0)),
                                               ('east', (1, 0, 0)), ('up', (0, 1, 0)), ('down', (0, -1, 0))):
                        if st[1].get(face) == 'true':
                            n = state(x + dx, y + dy, z + dz)
                            if n is None or n[0].split(':')[-1] not in NOT_SOLID:
                                held = True
                    if not held:
                        floating += 1
    print('box', (x0, y0, z0), (x1, y1, z1), 'missing blocks', missing)
    print('liquid', sum(v for k, v in counts.items() if not k.startswith('the sauna')), counts)
    print('glow lichen', lichen, 'floating', floating)


if __name__ == '__main__':
    main()
