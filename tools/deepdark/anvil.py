"""Minimal readers for region files: NBT, 1.12.2 chunks (numeric ids, with the world's FML id map) and 1.18+ chunks
(palettes). Read-only; used by census.py to compare a 1.12 world's ancient city with vanilla 1.19.2's."""
import gzip
import io
import os
import struct
import zlib


def _read_nbt(buf, tag):
    if tag == 1:
        return struct.unpack('>b', buf.read(1))[0]
    if tag == 2:
        return struct.unpack('>h', buf.read(2))[0]
    if tag == 3:
        return struct.unpack('>i', buf.read(4))[0]
    if tag == 4:
        return struct.unpack('>q', buf.read(8))[0]
    if tag == 5:
        return struct.unpack('>f', buf.read(4))[0]
    if tag == 6:
        return struct.unpack('>d', buf.read(8))[0]
    if tag == 7:
        n = struct.unpack('>i', buf.read(4))[0]
        return buf.read(n)
    if tag == 8:
        n = struct.unpack('>H', buf.read(2))[0]
        return buf.read(n).decode('utf-8', 'replace')
    if tag == 9:
        t = buf.read(1)[0]
        n = struct.unpack('>i', buf.read(4))[0]
        return [_read_nbt(buf, t) for _ in range(n)]
    if tag == 10:
        out = {}
        while True:
            t = buf.read(1)[0]
            if t == 0:
                return out
            n = struct.unpack('>H', buf.read(2))[0]
            name = buf.read(n).decode('utf-8', 'replace')
            out[name] = _read_nbt(buf, t)
    if tag == 11:
        n = struct.unpack('>i', buf.read(4))[0]
        return struct.unpack('>%di' % n, buf.read(4 * n))
    if tag == 12:
        n = struct.unpack('>i', buf.read(4))[0]
        return struct.unpack('>%dq' % n, buf.read(8 * n))
    raise ValueError('tag %d' % tag)


def nbt(data):
    buf = io.BytesIO(data)
    t = buf.read(1)[0]
    n = struct.unpack('>H', buf.read(2))[0]
    buf.read(n)
    return _read_nbt(buf, t)


def nbt_file(path):
    with open(path, 'rb') as f:
        raw = f.read()
    try:
        raw = gzip.decompress(raw)
    except OSError:
        pass
    return nbt(raw)


class Region:
    def __init__(self, path):
        self.path = path
        self.data = open(path, 'rb').read() if os.path.exists(path) else b''

    def chunk(self, cx, cz):
        if not self.data:
            return None
        i = 4 * ((cx & 31) + (cz & 31) * 32)
        loc = struct.unpack('>I', self.data[i:i + 4])[0]
        off, count = loc >> 8, loc & 0xFF
        if off == 0:
            return None
        at = off * 4096
        length = struct.unpack('>I', self.data[at:at + 4])[0]
        kind = self.data[at + 4]
        body = self.data[at + 5:at + 4 + length]
        if kind == 2:
            body = zlib.decompress(body)
        elif kind == 1:
            body = gzip.decompress(body)
        return nbt(body)


class World:
    """Block names by position, for 1.12.2 (FML ids) or 1.18+ (palettes) worlds."""

    def __init__(self, folder):
        self.folder = folder
        self.regions = {}
        self.cache = {}
        level = nbt_file(os.path.join(folder, 'level.dat'))
        self.ids = None
        fml = level.get('FML')
        if fml:
            for reg in fml.get('Registries', {}).items():
                if reg[0] == 'minecraft:blocks':
                    self.ids = {e['V']: e['K'] for e in reg[1]['ids']}

    def _region(self, rx, rz):
        k = (rx, rz)
        if k not in self.regions:
            self.regions[k] = Region(os.path.join(self.folder, 'region', 'r.%d.%d.mca' % (rx, rz)))
        return self.regions[k]

    def sections(self, cx, cz):
        """{section y: 4096 block names (index y*256 + z*16 + x)}, or None if the chunk is missing."""
        k = (cx, cz)
        if k in self.cache:
            return self.cache[k]
        c = self._region(cx >> 5, cz >> 5).chunk(cx, cz)
        out = None
        if c is not None:
            out = self._modern(c) if 'sections' in c else self._legacy(c)
        if len(self.cache) > 4096:
            self.cache.clear()
        self.cache[k] = out
        return out

    def _modern(self, c):
        if c.get('Status') not in ('full', 'minecraft:full'):
            return None
        out = {}
        for s in c['sections']:
            bs = s.get('block_states')
            if not bs:
                continue
            pal = [p['Name'] for p in bs['palette']]
            if len(pal) == 1 or 'data' not in bs:
                out[s['Y']] = [pal[0]] * 4096
                continue
            bits = max(4, (len(pal) - 1).bit_length())
            per = 64 // bits
            mask = (1 << bits) - 1
            names = []
            for word in bs['data']:
                w = word & 0xFFFFFFFFFFFFFFFF
                for i in range(per):
                    if len(names) == 4096:
                        break
                    names.append(pal[(w >> (i * bits)) & mask])
            out[s['Y']] = names
        return out

    def _legacy(self, c):
        lvl = c['Level']
        if not lvl.get('TerrainPopulated'):
            return None
        out = {}
        # Sections below 0 and above 255 (a world 384 high) are kept in a list of their own, Y signed.
        for s in lvl.get('Sections', []) + lvl.get('cavesandcliffsbackport:Sections', []):
            blocks = s['Blocks']
            add = s.get('Add')
            names = []
            for i in range(4096):
                b = blocks[i]
                if add:
                    b |= ((add[i >> 1] >> ((i & 1) * 4)) & 15) << 8
                names.append(self.ids.get(b, str(b)) if self.ids else str(b))
            out[s['Y']] = names
        return out

    def block(self, x, y, z):
        secs = self.sections(x >> 4, z >> 4)
        if secs is None:
            return None
        s = secs.get(y >> 4)
        if s is None:
            return 'minecraft:air'
        return s[((y & 15) << 8) | ((z & 15) << 4) | (x & 15)]
