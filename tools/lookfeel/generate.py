#!/usr/bin/env python3
"""
generate.py - stream Lf's assets, from Mojang's 1.18.2, 1.19, 1.19.2 and 1.19.4 releases.

  python -I tools/lookfeel/generate.py <download dir>

<download dir> is a new, empty folder for Mojang's files (client jars, asset indexes, objects); the generated files
go under src/main/resources/assets/wildupdatebackport/. A client jar already on this machine (the Install folder, or
an earlier family download folder, read only) is used instead of a download. Writes:

  textures/gui/title/background/panorama_0..5.png       1.19's mangrove swamp panorama (22w15a), from the asset index
                                                        (the jars carry 1x1 placeholders); 1.19.4's is the same
  textures/gui/title/background/panorama_overlay.png    1.19.4's jar (unchanged since 1.16)
  textures/gui/title/minecraft.png, mojangstudios.png   1.19.4's jar (unchanged since 1.16)
  textures/gui/widgets.png, checkbox.png, accessibility.png   1.19.4's jar (unchanged since 1.15/1.16)
  texts/splashes_{added,removed}_1_19.txt               1.18.2 -> 1.19 splashes.txt
  texts/splashes_added_1_19_1.txt                       1.19 -> 1.19.2 ("Technoblade never dies!", 1.19.1-pre3)
  texts/splashes_added_1_19_3.txt                       1.19.2 -> 1.19.4 (the six of 22w42a and 1.19.3-pre1)
                                                        (a list that would be empty is not written)
  sounds/music/game/swamp/{aerie,firebugs,labyrinthine}.ogg   the three tracks 1.19 put on the title screen
  vanilla_text/<code>.lang                              vanilla's own text as 1.19.4 words it (below)
  lang/en_us.lang, block "lookfeel"                     1.19.4's English for the options this stream adds

Vanilla text: the union of the keys the earlier family backports reword (their vanilla_text/en_us.lang, each at its
version) plus the keys 1.19 to 1.19.4 reworded that 1.12.2 has, each with its 1.19.4 text in every language. A 1.12.2
key is matched to Mojang's by its English at the version that reworded it. A key whose Mojang string 1.19.4 no longer
has (F3 + F's help line, removed in 1.19) is left to the earlier backport. Keys whose 1.12.2 use differs from the modern
string are left out: command messages (1.13's commands take other arguments), and the creative tab names 1.19.3 gave
its new tabs ("Redstone Blocks", "Tools & Utilities"), which 1.12.2's tabs do not hold.
"""

import hashlib
import io
import json
import os
import sys
import urllib.request
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
ASSETS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'wildupdatebackport')
WS = os.path.dirname(ROOT)

sys.path.insert(0, os.path.dirname(HERE))

# The family's eighteen languages plus English (Nether Update's vanilla_text list).
CODES = ['en_us', 'de_de', 'es_ar', 'es_es', 'es_mx', 'es_uy', 'es_ve', 'fr_fr', 'it_it', 'ja_jp', 'ko_kr', 'nl_nl',
         'pl_pl', 'pt_br', 'ru_ru', 'tr_tr', 'uk_ua', 'zh_cn', 'zh_tw']


def first(*paths):
    for p in paths:
        if os.path.isfile(os.path.join(WS, p)):
            return p
    raise SystemExit('none of these exists: %s' % (paths,))


# Earlier backports' vanilla text, with the version each one's English is.
EARLIER = [
    ('UpdateAquaticBackport/src/main/resources/assets/updateaquaticbackport/vanilla_text/en_us.lang', '1.13.2'),
    (first('RLCraftVillagerTrading/src/main/resources/assets/villagerbackport/vanilla_text/en_us.lang',
           'RLCraftVillagerTrading-languages/src/main/resources/assets/villagerbackport/vanilla_text/en_us.lang'), '1.14.4'),
    ('BuzzyBeesBackport/src/main/resources/assets/buzzybeesbackport/vanilla_text/en_us.lang', '1.15.2'),
    ('NetherUpdateBackport/src/main/resources/assets/netherupdatebackport/vanilla_text/en_us.lang', '1.16.5'),
    (first('CavesAndCliffsBackport/src/main/resources/assets/cavesandcliffsbackport/vanilla_text/en_us.lang',
           'CavesAndCliffsBackport-lookfeel/src/main/resources/assets/cavesandcliffsbackport/vanilla_text/en_us.lang'), '1.18.2'),
]

# 1.19 to 1.19.4's rewordings of text 1.12.2 has (and the earlier backports leave as it was): 1.12.2 key -> Mojang key.
V119 = [
    ('selectWorld.mapFeatures.info', 'selectWorld.mapFeatures.info'),       # "Villages, Shipwrecks, etc."
    ('options.realmsNotifications', 'options.realmsNotifications'),         # "Realms News & Invites"
    ('structure_block.mode_info.load', 'structure_block.mode_info.load'),
    ('structure_block.mode_info.data', 'structure_block.mode_info.data'),
    ('death.attack.hotFloor.player', 'death.attack.hotFloor.player'),
    ('death.attack.dragonBreath', 'death.attack.dragonBreath'),
    ('subtitles.entity.parrot.imitate.elder_guardian', 'subtitles.entity.parrot.imitate.elder_guardian'),
    ('advancements.adventure.shoot_arrow.description', 'advancements.adventure.shoot_arrow.description'),
    ('advancements.adventure.sleep_in_bed.description', 'advancements.adventure.sleep_in_bed.description'),
    ('advancements.end.dragon_breath.description', 'advancements.end.dragon_breath.description'),
    ('advancements.end.elytra.description', 'advancements.end.elytra.description'),
    ('advancements.nether.brew_potion.description', 'advancements.nether.brew_potion.description'),
    ('advancements.nether.create_full_beacon.description', 'advancements.nether.create_full_beacon.description'),
    ('advancements.story.deflect_arrow.description', 'advancements.story.deflect_arrow.description'),
    ('advancements.story.form_obsidian.description', 'advancements.story.form_obsidian.description'),
    ('advancements.story.iron_tools.description', 'advancements.story.iron_tools.description'),
    ('advancements.story.lava_bucket.description', 'advancements.story.lava_bucket.description'),
    ('advancements.story.mine_stone.description', 'advancements.story.mine_stone.description'),
    ('advancements.story.smelt_iron.description', 'advancements.story.smelt_iron.description'),
    ('advancements.story.upgrade_tools.description', 'advancements.story.upgrade_tools.description'),
]

# The options this stream adds, by 1.19.4's own keys (the mod's lang file; the family tool translates them later).
OPTION_KEYS = ['options.darknessEffectScale', 'options.darknessEffectScale.tooltip',
               'options.damageTiltStrength', 'options.damageTiltStrength.tooltip',
               'options.glintSpeed', 'options.glintSpeed.tooltip',
               'options.glintStrength', 'options.glintStrength.tooltip']

MENU_TRACKS = ['aerie', 'firebugs', 'labyrinthine']

VERSIONS = ['1.13.2', '1.14.4', '1.15.2', '1.16.5', '1.18.2', '1.19', '1.19.2', '1.19.4']

# Client jars already on this machine (read only).
LOCAL = [
    'G:/Sort this shit/Minecraft/Install/versions/{v}/{v}.jar',
    'G:/MCDownloads/client-{v}/client.jar',
    'G:/BackportDownloads/cc-lookfeel-1794581692/{v}/client.jar',
    'G:/MCDownloads/wu-lookfeel-1.19.4/client-{v}.jar',
    'G:/MCDownloads/minecraft-{v}/client.jar',
]


def get(url):
    req = urllib.request.Request(url, headers={'User-Agent': 'backport-tools'})
    with urllib.request.urlopen(req) as r:
        return r.read()


def obj(h):
    data = get('https://resources.download.minecraft.net/%s/%s' % (h[:2], h))
    assert hashlib.sha1(data).hexdigest() == h, h
    return data


class Version:
    def __init__(self, dl, vid, manifest):
        self.id = vid
        self.dir = os.path.join(dl, vid)
        os.makedirs(self.dir, exist_ok=True)
        meta = json.loads(get([v['url'] for v in manifest['versions'] if v['id'] == vid][0]))
        want = meta['downloads']['client']['sha1']
        jar = None
        for pattern in LOCAL:
            p = pattern.format(v=vid)
            if os.path.isfile(p) and hashlib.sha1(io.open(p, 'rb').read()).hexdigest() == want:
                jar = p
                break
        if jar is None:
            jar = os.path.join(self.dir, 'client.jar')
            if not os.path.exists(jar):
                io.open(jar, 'wb').write(get(meta['downloads']['client']['url']))
        self.jar = zipfile.ZipFile(jar)
        self.index = json.loads(get(meta['assetIndex']['url']))['objects']

    def asset(self, name):
        """An asset-index object (cached in the download folder)."""
        p = os.path.join(self.dir, 'objects', name)
        if not os.path.exists(p):
            os.makedirs(os.path.dirname(p), exist_ok=True)
            io.open(p, 'wb').write(obj(self.index[name]['hash']))
        return io.open(p, 'rb').read()

    def lang(self, code):
        if code == 'en_us':
            return json.loads(self.jar.read('assets/minecraft/lang/en_us.json').decode('utf-8'))
        name = 'minecraft/lang/%s.json' % code
        return json.loads(self.asset(name).decode('utf-8')) if name in self.index else {}

    def splashes(self):
        text = self.jar.read('assets/minecraft/texts/splashes.txt').decode('utf-8')
        return [l for l in text.replace('\r', '').split('\n') if l.strip()]


def write(rel, data):
    p = os.path.join(ASSETS, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    io.open(p, 'wb').write(data)


def write_lines(rel, lines):
    write(rel, ('\n'.join(lines) + '\n').encode('utf-8'))


def to_lang(value):
    return value.replace('\r', '').replace('\n', '\\n')


def read_lang(path):
    out = []
    for line in io.open(path, encoding='utf-8'):
        line = line.rstrip('\r\n')
        if line and not line.startswith('#') and '=' in line:
            out.append(tuple(line.split('=', 1)))
    return out


def main():
    dl = sys.argv[1]
    manifest = json.loads(get('https://piston-meta.mojang.com/mc/game/version_manifest_v2.json'))
    v = {vid: Version(dl, vid, manifest) for vid in VERSIONS}
    v18, v19, v192, v194 = v['1.18.2'], v['1.19'], v['1.19.2'], v['1.19.4']

    # Panorama: 1.19's, from the asset index; 1.19.4 still ships the same six faces.
    for i in range(6):
        name = 'minecraft/textures/gui/title/background/panorama_%d.png' % i
        face = v19.asset(name)
        if face != v194.asset(name):
            raise SystemExit('%s differs between 1.19 and 1.19.4' % name)
        write('textures/gui/title/background/panorama_%d.png' % i, face)
    for src in ['textures/gui/title/background/panorama_overlay.png', 'textures/gui/title/minecraft.png',
                'textures/gui/title/mojangstudios.png', 'textures/gui/widgets.png', 'textures/gui/checkbox.png',
                'textures/gui/accessibility.png']:
        write(src, v194.jar.read('assets/minecraft/' + src))

    # Splashes.
    for a, b, tag in [(v18, v19, '1_19'), (v19, v192, '1_19_1'), (v192, v194, '1_19_3')]:
        sa, sb = a.splashes(), b.splashes()
        for kind, lines in (('added', [s for s in sb if s not in sa]), ('removed', [s for s in sa if s not in sb])):
            rel = 'texts/splashes_%s_%s.txt' % (kind, tag)
            if lines:
                write_lines(rel, lines)
            elif os.path.exists(os.path.join(ASSETS, rel)):
                os.remove(os.path.join(ASSETS, rel))

    # Menu music: the three tracks 1.19 added to music.menu (the game's swamp music).
    for t in MENU_TRACKS:
        write('sounds/music/game/swamp/%s.ogg' % t, v194.asset('minecraft/sounds/music/game/swamp/%s.ogg' % t))

    # Vanilla text: 1.12.2 key -> Mojang key.
    mapping = []
    seen = set()
    for rel, ver in EARLIER:
        en = v[ver].lang('en_us')
        by_text = {}
        for k in sorted(en):
            by_text.setdefault(to_lang(en[k]), []).append(k)
        for key, text in read_lang(os.path.join(WS, rel)):
            if key in seen:
                continue
            if key in en and to_lang(en[key]) == text:
                mk = key
            else:
                same = by_text.get(text)
                if not same:
                    raise SystemExit('%s=%s: no key with that text in %s' % (key, text, ver))
                mk = key if key in same else same[0]
            seen.add(key)
            mapping.append((key, mk))
    for key, mk in V119:
        if key not in seen:
            seen.add(key)
            mapping.append((key, mk))
    en19 = v194.lang('en_us')
    gone = [(key, mk) for key, mk in mapping if mk not in en19]
    mapping = [(key, mk) for key, mk in mapping if mk in en19]
    for key, mk in gone:
        print('generate: %s (%s) is gone in 1.19.4; left to the earlier backport' % (key, mk))
    fallbacks = 0
    for code in CODES:
        moj = v194.lang(code)
        lines = []
        for key, mk in mapping:
            if mk in moj:
                lines.append('%s=%s' % (key, to_lang(moj[mk])))
            else:
                fallbacks += code != 'en_us'
                lines.append('%s=%s' % (key, to_lang(en19[mk])))
        write_lines('vanilla_text/%s.lang' % code, lines)
    print('generate: %d vanilla-text keys in %d languages (%d English fallbacks)' % (len(mapping), len(CODES), fallbacks))

    # The options' English, from 1.19.4.
    import assets  # noqa: E402  (tools/assets.py: the mod's lang file)
    assets.merge_lang('lookfeel', ['%s=%s' % (k, to_lang(en19[k])) for k in OPTION_KEYS])
    print('generate: %d option keys in en_us.lang' % len(OPTION_KEYS))


if __name__ == '__main__':
    main()
