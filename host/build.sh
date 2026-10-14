#!/usr/bin/env bash
# Builds the Android plugin host: re/host/host.jar (classes*.dex), embedded into libosrsrl.so.
# Inputs (re/runelite/): runelite-api and client 1.13.1 from repo.runelite.net, and the
# client's runtime dependencies from Maven Central (guice no_aop, guava, slf4j, ...).
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
RL=$ROOT/re/runelite
OUT=$ROOT/re/host
SDK=${ANDROID_SDK:-/mnt/c/Users/Alex/AppData/Local/Android/Sdk}
ANDROID_JAR=$SDK/platforms/android-37.0/android.jar
D8="java -cp $SDK/build-tools/36.0.0/lib/d8.jar com.android.tools.r8.D8"

API=$RL/runelite-api-1.13.1.jar
DEPS=($RL/guice-4.1.0-no_aop.jar $RL/guava-23.2-jre.jar $RL/javax.inject-1.jar $RL/aopalliance-1.0.jar
      $RL/slf4j-api-1.7.25.jar $RL/jsr305-3.0.2.jar $RL/commons-text-1.2.jar $RL/commons-lang3-3.7.jar)
CP=$(IFS=:; echo "$API:$RL/client-1.13.1.jar:${DEPS[*]}")

rm -rf "$OUT/gen" "$OUT/classes" "$OUT/client-classes" "$OUT/awt-classes" "$OUT/awt-shim.jar"
mkdir -p "$OUT/gen/net/runelite/host" "$OUT/classes" "$OUT/client-classes"

# 1. Throwing implementations of the API interfaces; host classes override what works.
for iface in Client Player NPC WorldView MessageNode widgets.Widget; do
    name=${iface##*.}
    java -cp "$API" "$ROOT/host/gen/GenStub.java" net.runelite.api.$iface net.runelite.host ${name}Stub \
        > "$OUT/gen/net/runelite/host/${name}Stub.java"
done

# 2. Compile the host, the generated stubs and the java.awt shim (host/awt) together against
#    android.jar: -source 8 with a boot class path has no module system, so the shim's
#    java.awt classes are accepted and everything is checked against what ART provides.
rm -rf "$OUT/compile-stubs" && mkdir -p "$OUT/compile-stubs"
javac -nowarn -source 8 -target 8 -bootclasspath "$ANDROID_JAR" -d "$OUT/compile-stubs" \
    $(find "$ROOT/host/compile-stubs" -name '*.java') 2>&1 | grep -v -E "^warning|^[0-9]+ warnings?$" || true
javac -nowarn -Xlint:none -source 8 -target 8 -bootclasspath "$ANDROID_JAR:$OUT/compile-stubs" -cp "$CP" -d "$OUT/classes" \
    $(find "$ROOT/host/src" "$OUT/gen" "$ROOT/host/awt" -name '*.java') 2>&1 \
    | grep -v -E "^warning|^Note|^[0-9]+ warnings?$" || true
[[ -f $OUT/classes/net/runelite/host/Host.class ]] || { echo "host compile failed"; exit 1; }

# 3. The few runelite-client classes plugins extend or annotate with; the rest of the
#    client (Swing UI, managers) is replaced by the host.
(cd "$OUT/client-classes" && unzip -q "$RL/client-1.13.1.jar" \
    'net/runelite/client/plugins/Plugin.class' 'net/runelite/client/plugins/PluginDescriptor.class' \
    'net/runelite/client/plugins/PluginDependency.class' 'net/runelite/client/plugins/PluginDependencies.class' \
    'net/runelite/client/eventbus/Subscribe.class' 'net/runelite/client/util/*.class' \
    'net/runelite/client/config/*.class' 'net/runelite/client/events/*.class' \
    'net/runelite/client/ui/overlay/Overlay.class' 'net/runelite/client/ui/overlay/Overlay$*.class' \
    'net/runelite/client/ui/overlay/OverlayLayer.class' 'net/runelite/client/ui/overlay/OverlayPosition.class' \
    'net/runelite/client/ui/overlay/OverlayPriority.class' 'net/runelite/client/ui/overlay/OverlayMenuEntry.class' \
    'net/runelite/client/ui/overlay/OverlayOrigin*.class' 'net/runelite/client/ui/overlay/OverlayPanel.class' \
    'net/runelite/client/ui/overlay/OverlayUtil.class' 'net/runelite/client/ui/overlay/RenderableEntity.class' \
    'net/runelite/client/ui/overlay/components/*' 'net/runelite/client/ui/overlay/infobox/InfoBox*.class' \
    'net/runelite/client/ui/runescape*.ttf')
# Classes the host replaces with same-name sources (d8 rejects duplicates).
rm -f "$OUT"/client-classes/net/runelite/client/config/ConfigManager*.class \
      "$OUT"/client-classes/net/runelite/client/config/ConfigInvocationHandler*.class
(cd "$OUT/client-classes" && jar cf ../client-subset.jar .)
# Resources (fonts) go into host.jar beside the dex files after dexing.
mkdir -p "$OUT/resources/net/runelite/client/ui"
cp "$OUT"/client-classes/net/runelite/client/ui/runescape*.ttf "$OUT/resources/net/runelite/client/ui/"
(cd "$OUT/classes" && jar cf ../host-classes.jar .)

# 4. Dex everything into one jar. Missing desktop-only classes (java.awt, Swing) only warn;
#    code touching them fails when it runs, not when it loads.
$D8 --release --min-api 26 --lib "$ANDROID_JAR" --output "$OUT/host.jar" \
    "$OUT/host-classes.jar" "$OUT/client-subset.jar" "$API" "${DEPS[@]}" 2>&1 \
    | grep -v -E "^Warning|Missing class|referenced from" | head -20 || true
(cd "$OUT/resources" && zip -qr "$OUT/host.jar" .)

# 5. java.awt members the API or the reused client classes call but the shim lacks
#    (these would be NoSuchMethodErrors at runtime). Also run it on plugin jars.
python3 "$ROOT/host/tools/check_awt.py" "$OUT/classes" "$OUT/client-subset.jar" "$API" | sed 's/^/awt shim missing: /' 
ls -la "$OUT/host.jar"
