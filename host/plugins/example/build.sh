#!/usr/bin/env bash
# Builds the example plugin: re/host/plugins/tickinfo-desktop.jar (plain classes) and
# re/host/plugins/tickinfo.jar (dexed for the Android host).
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../../.." && pwd)
RL=$ROOT/re/runelite
SRC=$ROOT/host/plugins/example/src
OUT=$ROOT/host/plugins/example/build
rm -rf "$OUT" && mkdir -p "$OUT"
CP=$(ls "$RL"/*.jar | tr '\n' ':')
javac --release 11 -nowarn -cp "$CP" -d "$OUT" $(find "$SRC" -name '*.java')
mkdir -p "$ROOT/re/host/plugins"
jar cf "$ROOT/re/host/plugins/tickinfo-desktop.jar" -C "$OUT" .
"$ROOT/tools/dex-plugin.sh" "$ROOT/re/host/plugins/tickinfo-desktop.jar" "$ROOT/re/host/plugins/tickinfo.jar"
