#!/bin/bash
# check.sh <OptiFine jar>: compiles and runs OptiFineCheck (patches P10/P11 against that jar). Build the mod first
# (./gradlew compileJava); the jar is only read.
cd "$(dirname "$0")/../.."
export MSYS_NO_PATHCONV=1
G=$(cygpath -m ~/.gradle/caches/modules-2/files-2.1)
ASM=$(ls $G/org.ow2.asm/asm-debug-all/5.2/*/asm-debug-all-5.2.jar | grep -v sources | grep -v javadoc)
LW=$(ls $G/net.minecraft/launchwrapper/1.12/*/launchwrapper-1.12.jar)
LOG=$(ls $G/org.apache.logging.log4j/log4j-api/2.8.1/*/log4j-api-2.8.1.jar | grep -v sources | grep -v javadoc)
LOGC=$(ls $G/org.apache.logging.log4j/log4j-core/2.8.1/*/log4j-core-2.8.1.jar | grep -v sources | grep -v javadoc)
OUT=build/tools/darkness
mkdir -p $OUT
CP="build/classes/java/main;$ASM;$LW;$LOG;$LOGC"
javac -nowarn -d $OUT -cp "$CP" tools/darkness/OptiFineCheck.java || exit 1
java -cp "$OUT;$CP" OptiFineCheck "$1"
