#!/usr/bin/env bash
# Builds the API probe (tools/api-probe.sh): re/host/plugins-extra/api-probe.jar, dexed for the
# Android host, with api-methods.txt (the denominator: every abstract method of every
# net.runelite.api interface in the shipped runelite-api jar). Never part of the default plugin set.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../../.." && pwd)
RL=$ROOT/re/runelite
API=$RL/runelite-api-1.13.1.jar
SRC=$ROOT/host/plugins/api-probe
OUT=$SRC/build
rm -rf "$OUT" && mkdir -p "$OUT/classes"
CP=$(ls "$RL"/*.jar | grep -v slf4j-simple | tr '\n' ':')
javac --release 11 -nowarn -cp "$CP" -d "$OUT/classes" $(find "$SRC/src" -name '*.java')
{
    echo "# $(basename "$API") sha256 $(sha256sum "$API" | cut -c1-16)"
    java -cp "$API" "$SRC/ApiList.java" "$API"
} > "$OUT/classes/api-methods.txt"
jar cf "$OUT/api-probe-classes.jar" -C "$OUT/classes" .
mkdir -p "$ROOT/re/host/plugins-extra"
"$ROOT/tools/dex-plugin.sh" "$OUT/api-probe-classes.jar" "$ROOT/re/host/plugins-extra/api-probe.jar"
