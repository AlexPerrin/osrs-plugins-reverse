#!/usr/bin/env bash
# Builds Plugin Hub plugins from source for the Android host:
#   host/plugins/hub/build.sh <hub-name>...      e.g. visual-metronome shortest-path
# For each name, the pinned repository/commit come from host/plugins/hub/plugins.txt (copied from
# runelite/plugin-hub's plugins/<name>; refresh with --pin <name>). The source is cloned into the
# git-ignored re/hub-src/<name>, compiled with Lombok against our RuneLite 1.13.1 jars, and packed
# with its resources, LICENSE and a manifest (re/hub-build/<name>.jar), then converted with
# tools/dex-plugin.sh into re/host/plugins-extra/hub-<name>.jar. Never part of the default plugin set.
# Extra Maven dependencies a plugin declares in build.gradle are not fetched: the build stops and
# lists them, so they can be reviewed and added to host.jar (or the plugin skipped).
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../../.." && pwd)
PINS=$ROOT/host/plugins/hub/plugins.txt
OUT=$ROOT/re/hub-build
mkdir -p "$OUT" "$ROOT/re/hub-src" "$ROOT/re/host/plugins-extra"
LOMBOK=$OUT/lombok-1.18.30.jar
[[ -f $LOMBOK ]] || curl -sfL -o "$LOMBOK" https://repo1.maven.org/maven2/org/projectlombok/lombok/1.18.30/lombok-1.18.30.jar

if [[ ${1:-} == --pin ]]; then
    shift
    for name in "$@"; do
        d=$(curl -sf "https://raw.githubusercontent.com/runelite/plugin-hub/master/plugins/$name")
        repo=$(sed -n 's/^repository=//p' <<<"$d"); commit=$(sed -n 's/^commit=//p' <<<"$d")
        [[ -n $repo && -n $commit ]] || { echo "$name: no descriptor"; exit 1; }
        grep -v "^$name " "$PINS" > "$PINS.tmp" 2>/dev/null || true
        echo "$name $repo $commit" >> "$PINS.tmp"; sort -o "$PINS" "$PINS.tmp"; rm -f "$PINS.tmp"
        echo "pinned $name $commit"
    done
    exit 0
fi

CP=$(ls "$ROOT"/re/runelite/*.jar | grep -v slf4j-simple | tr '\n' ':')$LOMBOK
for name in "$@"; do
    read -r _ repo commit < <(grep "^$name " "$PINS") || { echo "$name: not pinned (use --pin)"; exit 1; }
    src=$ROOT/re/hub-src/$name
    [[ -d $src/.git ]] || git clone -q "$repo" "$src"
    git -C "$src" cat-file -e "$commit^{commit}" 2>/dev/null || git -C "$src" fetch -q origin
    git -C "$src" checkout -q -f "$commit"
    # Dependencies beyond what the hub's gradle template provides.
    deps=$(grep -hE "^\s*(implementation|compile|api|runtimeOnly)\b" "$src"/build.gradle* 2>/dev/null \
        | grep -vE "runelite|lombok|junit|mockito|hamcrest|guice-testlib|testImplementation" || true)
    if [[ -n $deps && -z ${ALLOW_DEPS:-} ]]; then
        echo "$name: extra dependencies (review; ALLOW_DEPS=1 to build anyway):"; echo "$deps"; exit 1
    fi
    # ALLOW_DEPS=1: 'group:artifact:version' dependencies are fetched from Maven Central into
    # re/hub-deps, put on the compile classpath and unpacked into the plugin jar (as the hub's
    # shadow jar does). Their licences must allow it; check each one when adding a plugin.
    # Transitive runtime dependencies (Gradle resolves them, we don't) are listed per plugin in
    # host/plugins/hub/deps.txt: "<name> group:artifact:version...".
    extra=$(sed -n "s/^$name //p" "$ROOT/host/plugins/hub/deps.txt" 2>/dev/null || true)
    depjars=""
    for gav in $(grep -ohE "['\"][a-zA-Z0-9_.-]+:[a-zA-Z0-9_.-]+:[0-9][a-zA-Z0-9_.-]*['\"]" <<<"$deps" | tr -d "'\"") $extra; do
        IFS=: read -r g a v <<<"$gav"
        dj=$ROOT/re/hub-deps/$a-$v.jar
        mkdir -p "$ROOT/re/hub-deps"
        [[ -s $dj ]] || curl -sfL -o "$dj" "https://repo1.maven.org/maven2/${g//.//}/$a/$v/$a-$v.jar" || { echo "$name: fetching $gav failed"; exit 1; }
        depjars="$depjars:$dj"
    done
    lic=$(ls "$src" | grep -iE '^(license|licence|copying)' | head -1 || true)
    [[ -n $lic ]] || echo "$name: WARNING no LICENSE file in the repository"
    w=$OUT/$name; rm -rf "$w" && mkdir -p "$w/classes" "$w/jar"
    javac --release 11 -nowarn -proc:full -encoding UTF-8 -cp "$CP$depjars" -processorpath "$LOMBOK" -d "$w/classes" \
        $(find "$src/src/main/java" -name '*.java') 2>&1 | grep -vE "^Note:" || true
    [[ -n $(find "$w/classes" -name '*.class' | head -1) ]] || { echo "$name: compile failed"; exit 1; }
    cp -r "$w/classes/." "$w/jar/"
    for dj in ${depjars//:/ }; do (cd "$w/jar" && unzip -qo "$dj" -x 'META-INF/MANIFEST.MF' 'META-INF/*.SF' 'META-INF/*.RSA' 'META-INF/*.DSA' 'module-info.class'); done
    [[ -d $src/src/main/resources ]] && cp -r "$src/src/main/resources/." "$w/jar/"
    [[ -n $lic ]] && cp "$src/$lic" "$w/jar/LICENSE-$name.txt"
    # Resources a gradle task generates: version.txt = "version=<project version>" (Bank Tag Layouts).
    if grep -q 'version.txt").text' "$src"/build.gradle* 2>/dev/null; then
        v=$(sed -nE "s/^version *= *['\"]([^'\"]+)['\"].*/\1/p" "$src"/build.gradle* | head -1)
        printf 'version=%s' "$v" > "$w/jar/version.txt"
    fi
    printf 'Manifest-Version: 1.0\nImplementation-Title: %s\nImplementation-Version: %s\n' "$name" "${commit:0:10}" > "$w/MANIFEST.MF"
    rm -f "$OUT/$name.jar"
    jar cfm "$OUT/$name.jar" "$w/MANIFEST.MF" -C "$w/jar" .
    "$ROOT/tools/dex-plugin.sh" "$OUT/$name.jar" "$ROOT/re/host/plugins-extra/hub-$name.jar"
done
