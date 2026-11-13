#!/bin/sh
# Builds and runs AncientCityOracle119 against vanilla 1.19.2 (Mojang-named client jar + libraries, JDK 17), writing
# tools/deepdark/oracle119.txt, which the deep dark self-test reads.
#   sh tools/deepdark/oracle.sh
set -e
here="$(cygpath -m "$(cd "$(dirname "$0")" && pwd)")"
mc="${MC1192:-G:/MCDownloads/minecraft-1.19.2}"
jdk="${JDK17:-C:/Program Files/Java/jdk-17}"
out="$here/../../build/deepdark-oracle"
mkdir -p "$out"
cp="$mc/client-named.jar;$mc/libs/*"
"$jdk/bin/javac" -nowarn -cp "$cp" -d "$out" "$here"/oracle/net/minecraft/world/level/levelgen/structure/AncientCityOracle119.java
# Run from the build folder: Minecraft writes logs/ into the working directory.
cd "$out"
"$jdk/bin/java" -Xmx2G -cp "$out;$cp" net.minecraft.world.level.levelgen.structure.AncientCityOracle119 \
    "$here/oracle119.txt" 8 3 42 12345 -7346534567 8675309 852203380978249405
