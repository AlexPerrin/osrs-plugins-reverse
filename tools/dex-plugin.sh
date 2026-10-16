#!/usr/bin/env bash
# Converts a RuneLite plugin jar (e.g. from the Plugin Hub) for the Android host:
#   tools/dex-plugin.sh plugin.jar out.jar
# The output holds classes*.dex plus plugin-classes.txt (classes annotated with
# @PluginDescriptor). Push it to /sdcard/Android/data/com.jagex.oldscape.android/files/plugins/.
#
# Core RuneLite plugins are already inside host.jar (it embeds the client jar), so they need no
# dex; this writes a jar holding only plugin-classes.txt for one plugin package:
#   tools/dex-plugin.sh --core grounditems re/host/plugins/grounditems.jar
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
RL=$ROOT/re/runelite

if [[ ${1:-} == --core ]]; then
    PKG=$2
    OUT=$(realpath -m "$3")
    WORK=$(mktemp -d)
    trap 'rm -rf "$WORK"' EXIT
    (cd "$WORK" && unzip -q "$RL/client-1.13.1.jar" "net/runelite/client/plugins/$PKG/*.class")
    (cd "$WORK" && grep -l 'Lnet/runelite/client/plugins/PluginDescriptor;' net/runelite/client/plugins/"$PKG"/*.class || true) \
        | sed -e 's|\.class$||' -e 's|/|.|g' > "$WORK/plugin-classes.txt"
    [[ -s $WORK/plugin-classes.txt ]] || { echo "no plugin class in net.runelite.client.plugins.$PKG"; exit 1; }
    echo "plugin classes: $(tr '\n' ' ' < "$WORK/plugin-classes.txt")"
    rm -f "$OUT"
    (cd "$WORK" && zip -q "$OUT" plugin-classes.txt)
    ls -la "$OUT"
    exit 0
fi
SDK=${ANDROID_SDK:-/mnt/c/Users/Alex/AppData/Local/Android/Sdk}
ANDROID_JAR=$SDK/platforms/android-37.0/android.jar
IN=$(realpath "$1")
OUT=$(realpath -m "$2")
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

CLASSPATH_ARGS=()
for j in "$RL"/runelite-api-1.13.1.jar "$RL"/client-1.13.1.jar "$RL"/guice-4.1.0-no_aop.jar "$RL"/guava-23.2-jre.jar \
         "$RL"/javax.inject-1.jar "$RL"/slf4j-api-1.7.25.jar "$RL"/commons-text-1.2.jar "$RL"/commons-lang3-3.7.jar "$RL"/gson-2.8.5.jar; do
    CLASSPATH_ARGS+=(--classpath "$j")
done
java -cp "$SDK/build-tools/36.0.0/lib/d8.jar" com.android.tools.r8.D8 --release --min-api 26 \
    --lib "$ANDROID_JAR" "${CLASSPATH_ARGS[@]}" --output "$WORK/dex.zip" "$IN" 2>&1 \
    | grep -v -E "^Warning|Missing class|referenced from" | head -20 || true

mkdir "$WORK/classes"
(cd "$WORK/classes" && unzip -q "$IN" '*.class')
(cd "$WORK/classes" && grep -rl --include='*.class' 'Lnet/runelite/client/plugins/PluginDescriptor;' . || true) \
    | sed -e 's|^\./||' -e 's|\.class$||' -e 's|/|.|g' > "$WORK/plugin-classes.txt"
echo "plugin classes: $(tr '\n' ' ' < "$WORK/plugin-classes.txt")"
cp "$WORK/dex.zip" "$OUT"
(cd "$WORK" && zip -q "$OUT" plugin-classes.txt)
# Resources (json, images, ...) stay loadable through the plugin's class loader.
mkdir "$WORK/res"
(cd "$WORK/res" && unzip -q "$IN" -x '*.class' 'META-INF/*' 2>/dev/null || true)
if [[ -n $(ls -A "$WORK/res") ]]; then (cd "$WORK/res" && zip -qr "$OUT" .); fi
ls -la "$OUT"
