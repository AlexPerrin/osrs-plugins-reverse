#!/usr/bin/env bash
# Builds OpenRune-Developer-Tools (https://github.com/OpenRune/OpenRune-Developer-Tools, BSD-2,
# the notice ships in the jar) for the Android host:
#   re/devtools-build/devtools.jar (classes, for host/build.sh's headless AWT inputs) and
#   re/host/plugins-extra/devtools.jar (dexed). Never part of the default plugin set.
# The clone lives in re/devtools (git-ignored); REV pins it. Compiled against our RuneLite 1.13.1
# jars with Lombok; versioned "-local" so its self-updater stays off.
# Enable on the device: push the jar, `adb shell setprop debug.osrsrl.devtools 1`, restart the game,
# then talk to it with tools/devtools-mcp.sh (docs/plugin-host.md "Developer tools (MCP)").
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../../.." && pwd)
REV=${REV:-8304e71}
SRC=$ROOT/re/devtools
OUT=$ROOT/re/devtools-build
[[ -d $SRC/.git ]] || git clone -q https://github.com/OpenRune/OpenRune-Developer-Tools "$SRC"
git -C "$SRC" cat-file -e "$REV^{commit}" 2>/dev/null || git -C "$SRC" fetch -q --unshallow origin || git -C "$SRC" fetch -q origin
git -C "$SRC" checkout -q "$REV"
mkdir -p "$OUT"
LOMBOK=$OUT/lombok-1.18.30.jar
[[ -f $LOMBOK ]] || curl -sfL -o "$LOMBOK" https://repo1.maven.org/maven2/org/projectlombok/lombok/1.18.30/lombok-1.18.30.jar
CP=$(ls "$ROOT"/re/runelite/*.jar | grep -v slf4j-simple | tr '\n' ':')$LOMBOK
rm -rf "$OUT/classes" "$OUT/jar" && mkdir -p "$OUT/classes" "$OUT/jar"
javac --release 11 -nowarn -proc:full -cp "$CP" -processorpath "$LOMBOK" -d "$OUT/classes" $(find "$SRC/src/main/java" -name '*.java')
cp -r "$OUT/classes/." "$SRC/src/main/resources/." "$OUT/jar/"
cp "$SRC/LICENSE" "$OUT/jar/LICENSE-OpenRune-Developer-Tools.txt"
printf 'Manifest-Version: 1.0\nImplementation-Title: OpenRune-Developer-Tools\nImplementation-Version: 0.1.0-local\n' > "$OUT/MANIFEST.MF"
rm -f "$OUT/devtools.jar"
jar cfm "$OUT/devtools.jar" "$OUT/MANIFEST.MF" -C "$OUT/jar" .
mkdir -p "$ROOT/re/host/plugins-extra"
"$ROOT/tools/dex-plugin.sh" "$OUT/devtools.jar" "$ROOT/re/host/plugins-extra/devtools.jar"
