#!/bin/sh
# The superflat self-test (selftest/FlatSelfTest) in a fresh superflat world: points eclipse/server.properties at
# level-type FLAT with a preset, on a world of its own (eclipse/flatworld, made new each run), runs the flat test,
# then puts level-type, generator-settings and level-name back (the rest of the suite runs in a Default world).
#   sh tools/flat/selftest.sh [preset]
# default:             3;minecraft:bedrock,2*minecraft:dirt,minecraft:grass;1;village     (1.12's Classic Flat)
# decoration:          the same with decoration
# mangrove:            the default layers in the mangrove swamp (biome MANGROVE, below), no decoration
# mangrove-decoration: the same with decoration (mangroves expected)
# deepdark:            the default layers in the deep dark biome (DEEP_DARK), no decoration
# deepdark-decoration: the same with decoration (sculk expected)
# stone:               bedrock, 60 stone, dirt and grass in Extreme Hills, with lava lakes (air underground at the
#                      deep dark's heights of other worlds), no decoration
# stone-decoration:    the same with decoration (no sculk: superflat's deep dark is the deep dark biome only)
# Anything with a ';' is used as the preset itself.
# MANGROVE and DEEP_DARK are the two biomes' numeric ids in this dev setup (the test logs them); the mangrove swamp
# registers first, so they hold at every launch. A preset meant for one of them fails if the world has another biome.
set -e
MANGROVE=40
DEEP_DARK=41
layers='minecraft:bedrock,2*minecraft:dirt,minecraft:grass'
stone='minecraft:bedrock,60*minecraft:stone,minecraft:dirt,minecraft:grass'
root="$(cd "$(dirname "$0")/../.." && pwd)"
props="$root/eclipse/server.properties"
case "${1:-default}" in
    default) preset="3;$layers;1;village" ;;
    decoration) preset="3;$layers;1;village,decoration" ;;
    mangrove) preset="3;$layers;$MANGROVE;village" ;;
    mangrove-decoration) preset="3;$layers;$MANGROVE;village,decoration" ;;
    deepdark) preset="3;$layers;$DEEP_DARK;village" ;;
    deepdark-decoration) preset="3;$layers;$DEEP_DARK;village,decoration" ;;
    stone) preset="3;$stone;3;lava_lake" ;;
    stone-decoration) preset="3;$stone;3;lava_lake,decoration" ;;
    *\;*) preset="$1" ;;
    *) echo "unknown preset $1" >&2; exit 2 ;;
esac
old_level="$(sed -n 's/^level-name=//p' "$props")"
# A literal path: the flat world is always eclipse/flatworld.
(cd "$root/eclipse" && rm -rf flatworld)
sed -i "s/^level-type=.*/level-type=FLAT/; s|^generator-settings=.*|generator-settings=$preset|; s/^level-name=.*/level-name=flatworld/" "$props"
status=0
(cd "$root" && ./gradlew runServer -Pselftest=flat -PdeployDir= -PcoreDeployDir= -PwubDeployDir=) || status=$?
sed -i "s/^level-type=.*/level-type=DEFAULT/; s|^generator-settings=.*|generator-settings=|; s/^level-name=.*/level-name=${old_level:-selftest}/" "$props"
case "${1:-default}" in
    mangrove*) want=wildupdatebackport:mangrove_swamp ;;
    deepdark*) want=wildupdatebackport:deep_dark ;;
    *) want= ;;
esac
if [ -n "$want" ] && ! grep -q "biome $want " "$root/eclipse/logs/latest.log"; then
    echo "the world's biome is not $want: fix the ids above" >&2
    status=3
fi
if grep -q "SELFTEST DONE pass=[0-9]* fail=[1-9]" "$root/eclipse/logs/latest.log"; then
    status=1
fi
grep -h "SELFTEST DONE" "$root/eclipse/logs/latest.log" 2>/dev/null | tail -1 || true
exit $status
