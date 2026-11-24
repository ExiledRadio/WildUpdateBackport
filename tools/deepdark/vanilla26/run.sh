#!/bin/bash
# Vanilla 26.3's own dedicated server, run from the client jar (its server classes and the launcher's libraries), to
# generate the chunks around one ancient city for tools/deepdark/strays.py.
#
#     bash tools/deepdark/vanilla26/run.sh <server dir> <seed> <chunkX0> <chunkZ0> <chunkX1> <chunkZ1>
#
# The chunk range is one forceload (at most 256 chunks), in block coordinates as forceload takes them. The world is
# <server dir>/world; regions land in world/dimensions/minecraft/overworld/region. Needs JDK 25.
set -u
here=$(cd "$(dirname "$0")" && pwd)
dir=$1
mkdir -p "$dir"
cd "$dir" || exit 1
echo "eula=true" > eula.txt
printf 'level-seed=%s\nserver-port=26557\nonline-mode=false\nspawn-protection=0\nmax-tick-time=-1\nview-distance=4\nsimulation-distance=4\nlevel-name=world\n' "$2" > server.properties
CP=$(python -I "$here/classpath.py")
rm -f server.log
(
  until grep -q "Done (" server.log 2>/dev/null; do sleep 2; done
  echo "forceload add $3 $4 $5 $6"
  sleep 180
  echo "save-all flush"
  sleep 30
  echo "stop"
) | "/c/Program Files/Java/jdk-25.0.3/bin/java" -Xmx6G -cp "$CP" net.minecraft.server.Main --nogui > server.log 2>&1
echo "EXIT $?" >> server.log
