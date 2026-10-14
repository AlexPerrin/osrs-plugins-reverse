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
      $RL/slf4j-api-1.7.25.jar $RL/jsr305-3.0.2.jar)
CP=$(IFS=:; echo "$API:$RL/client-1.13.1.jar:${DEPS[*]}:$ANDROID_JAR")

rm -rf "$OUT/gen" "$OUT/classes" "$OUT/client-classes" "$OUT/awt-classes"
mkdir -p "$OUT/gen/net/runelite/host" "$OUT/classes" "$OUT/client-classes" "$OUT/awt-classes"

# 1. Throwing implementations of the API interfaces; host classes override what works.
for iface in Client Player NPC WorldView; do
    java -cp "$API" "$ROOT/host/gen/GenStub.java" net.runelite.api.$iface net.runelite.host ${iface}Stub \
        > "$OUT/gen/net/runelite/host/${iface}Stub.java"
done

# 2. Compile the host.
javac -nowarn -Xlint:none --release 11 -cp "$CP" -d "$OUT/classes" \
    $(find "$ROOT/host/src" "$OUT/gen" -name '*.java')

# 2b. The java.awt shim (host/awt). --release 8 has no module system, so javac accepts
#     sources in java.awt; the API was compiled against real AWT and links by name.
javac -nowarn -Xlint:none --release 8 -d "$OUT/awt-classes" $(find "$ROOT/host/awt" -name '*.java') 2>&1 \
    | grep -v -E "^warning|^Note|^[0-9]+ warning" || true
(cd "$OUT/awt-classes" && jar cf ../awt-shim.jar .)

# 3. The few runelite-client classes plugins extend or annotate with; the rest of the
#    client (Swing UI, managers) is replaced by the host.
(cd "$OUT/client-classes" && unzip -q "$RL/client-1.13.1.jar" \
    'net/runelite/client/plugins/Plugin.class' 'net/runelite/client/plugins/PluginDescriptor.class' \
    'net/runelite/client/plugins/PluginDependency.class' 'net/runelite/client/plugins/PluginDependencies.class' \
    'net/runelite/client/eventbus/Subscribe.class' 'net/runelite/client/util/Filepath*.class' \
    'net/runelite/client/config/Config.class' 'net/runelite/client/config/ConfigGroup.class' \
    'net/runelite/client/config/ConfigItem.class' 'net/runelite/client/config/ConfigSection.class' \
    'net/runelite/client/config/Range.class' 'net/runelite/client/config/Units.class')
(cd "$OUT/client-classes" && jar cf ../client-subset.jar .)
(cd "$OUT/classes" && jar cf ../host-classes.jar .)

# 4. Dex everything into one jar. Missing desktop-only classes (java.awt, Swing) only warn;
#    code touching them fails when it runs, not when it loads.
$D8 --release --min-api 26 --lib "$ANDROID_JAR" --output "$OUT/host.jar" \
    "$OUT/host-classes.jar" "$OUT/awt-shim.jar" "$OUT/client-subset.jar" "$API" "${DEPS[@]}" 2>&1 \
    | grep -v -E "^Warning|Missing class|referenced from" | head -20 || true
ls -la "$OUT/host.jar"
