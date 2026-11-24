# The launcher libraries and client jar of 26.3, as a class path (Windows separators), for tools/deepdark/vanilla26/run.sh.
import json, os, sys
mc = os.path.expandvars(r'%APPDATA%\.minecraft')
d = json.load(open(os.path.join(mc, 'versions', '26.3', '26.3.json')))
paths = []
for lib in d['libraries']:
    ok = True
    for r in lib.get('rules', []):
        osn = r.get('os', {}).get('name')
        if r['action'] == 'allow' and osn and osn != 'windows':
            ok = False
        if r['action'] == 'disallow' and osn == 'windows':
            ok = False
    if not ok:
        continue
    a = lib.get('downloads', {}).get('artifact')
    if a:
        p = os.path.join(mc, 'libraries', a['path'].replace('/', os.sep))
        if os.path.exists(p):
            paths.append(p)
        else:
            sys.stderr.write('missing ' + p + '\n')
paths.append(os.path.join(mc, 'versions', '26.3', '26.3.jar'))
print(';'.join(paths))
