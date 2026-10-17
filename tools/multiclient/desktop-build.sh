#!/usr/bin/env bash
# Builds and stages the isolated desktop A/B client into C:\osrs-test (docs/multi-client.md).
#   - RSProx runtime classpath + the headless launcher (Docker, eclipse-temurin:11, re/rsprox);
#   - OpenRune-Developer-Tools (upstream build, re/devtools-build/devtools-unpatched.jar) into the
#     isolated RuneLite's sideloaded-plugins;
#   - proxy-targets.yaml (OpenRune Test) into the isolated ~/.rsprox;
#   - a first-run settings.properties that turns off the plugins that upload data.
# Nothing here reads or writes C:\Users\Alex\.runelite or the installed RuneLite. Stop the test client
# (desktop.sh stop) before restaging; a running client holds its jars open.
# The JDK (C:\osrs-test\jdk) is a Temurin 11 zip from Adoptium; see docs/multi-client.md.
set -euo pipefail
REPO="$(cd "$(dirname "$(readlink -f "$0")")/../.." && pwd)"
WIN=/mnt/c/osrs-test
HOME_W="$WIN/home"
# RSProx's launcher patches the client's .runelite to .rlcustom; the launcher itself keeps .runelite.
RL="$HOME_W/.rlcustom"
[ -x "$WIN/jdk/bin/java.exe" ] || { echo "missing $WIN/jdk (unzip a Temurin 11 Windows JDK there; a JRE lacks jdk.jartool, which the RSProx launcher needs)" >&2; exit 1; }

if [ "${SKIP_RSPROX_BUILD:-0}" != 1 ]; then
  docker run --rm -v "$REPO/re/rsprox:/src" -v "$REPO/tools/multiclient/headless:/headless:ro" \
    -v rsprox-gradle:/root/.gradle -w /src eclipse-temurin:11 bash -c '
      ./gradlew --no-daemon -q --init-script /headless/export-runtime.init.gradle.kts assemble osrsTestRuntime &&
      rm -rf build/osrs-test-launcher && mkdir -p build/osrs-test-launcher &&
      javac --release 11 -cp "build/osrs-test-runtime/*" -d build/osrs-test-launcher /headless/OsrsTestLauncher.java'
fi

mkdir -p "$WIN/rsprox/lib" "$WIN/rsprox/launcher" "$WIN/logs" "$HOME_W/.rsprox" "$RL/sideloaded-plugins"
rm -f "$WIN/rsprox/lib/"*.jar "$WIN/rsprox/launcher/"*.class
cp "$REPO/re/rsprox/build/osrs-test-runtime/"*.jar "$WIN/rsprox/lib/"
cp "$REPO/re/rsprox/build/osrs-test-launcher/"*.class "$WIN/rsprox/launcher/"
cp "$REPO/re/devtools-build/devtools-unpatched.jar" "$RL/sideloaded-plugins/OpenRune-Developer-Tools.jar"
cp "$REPO/tools/multiclient/proxy-targets.yaml" "$HOME_W/.rsprox/proxy-targets.yaml"

# RuneLite imports <runelite dir>/settings.properties into its first profile (ConfigManager.migrate), so
# this only takes effect before the first start. Later changes go through the profile itself.
if [ ! -d "$RL/profiles2" ]; then
  cat > "$RL/settings.properties" <<'EOF'
# Isolated desktop A/B profile (tools/multiclient/desktop-build.sh). No NetGuard on desktop, so
# plugins that upload data or could move the client to a real Jagex world are off.
runelite.discordplugin=false
runelite.loottrackerplugin=false
runelite.xptrackerplugin=false
runelite.xpupdaterplugin=false
runelite.crowdsourcingplugin=false
runelite.grandexchangeplugin=false
runelite.chatcommandsplugin=false
runelite.hiscoreplugin=false
runelite.worldhopperplugin=false
runelite.defaultworldplugin=false
runelite.twitchplugin=false
runelite.partyplugin2=false
runelite.openrunedevtoolsplugin=true
openrunedevtools.autoUpdate=false
EOF
fi
echo "staged $(ls "$WIN/rsprox/lib" | wc -l) RSProx jars, launcher, devtools, target into C:\\osrs-test"
