#!/bin/sh
# Manages our OpenRune-Server build (openrune/src, git-ignored): a pinned upstream commit plus
# openrune/openrune.patch (Android support, token login, mobile gameframe, test commands, ...).
# Everything that compiles or runs OpenRune does so in Docker, never on the host.
#
#   scripts/openrune.sh init      clone the pinned commit into src/ and apply openrune.patch
#   scripts/openrune.sh export    write src/'s working-copy changes back to openrune.patch
#   scripts/openrune.sh rsprot    publish our RSProt (pinned upstream + server/rsprot.patch) to
#                                 src/.m2-rsprot, the Maven repo src/settings.gradle.kts points at
#   scripts/openrune.sh gradle <args...>
#                                 run OpenRune's Gradle wrapper in Docker (uid 1000)
#   scripts/openrune.sh build     rsprot (if missing) + cache (first time: download + build; later:
#                                 incremental rebuild) + RSA keys + installDist
#   scripts/openrune.sh modulus   print the login RSA modulus (hex) for tools/repack.sh
#   scripts/openrune.sh shared-key
#                                 use our own server's login key (server/data/rsa.properties)
set -eu
cd "$(dirname "$0")/.."
OR=$(pwd)
ROOT=$(cd .. && pwd)

REPO=https://github.com/OpenRune/OpenRune-Server.git
COMMIT=9a8b338a89cb87345e002bf83228e775a746ce69

RSPROT_REPO=https://github.com/blurite/rsprot.git
RSPROT_COMMIT=4ec57fd7a28e16e9ae207bd1f02b7f343bb77957 # same pin as server/scripts/rsprot.sh
RSPROT_VERSION=1.0.0-ALPHA-20261001.99                  # OpenRune's 20261001 + our patch

# Paths inside src/ that are ours but never part of the patch (build outputs, local Maven repo).
EXCLUDES=".m2-rsprot/"

# Named volumes start out root-owned; the builds run as uid 1000.
own() { docker run --rm -v "$1":/v alpine:3.20 chown 1000:1000 /v; }

gradle() {
    own openrune-gradle
    docker run --rm --name openrune-gradle-$$ -u 1000:1000 -e HOME=/gradle/home \
        ${OPENRUNE_SKIP_CS2_PACKS+-e OPENRUNE_SKIP_CS2_PACKS="$OPENRUNE_SKIP_CS2_PACKS"} \
        -e GRADLE_USER_HOME=/gradle -v openrune-gradle:/gradle -v "$OR/src":/w -w /w \
        eclipse-temurin:21 ./gradlew --no-daemon --console=plain "$@"
}

case "${1:-}" in
init)
    [ -d src ] && { echo "src/ already exists"; exit 1; }
    git clone -q "$REPO" src
    git -C src checkout -q "$COMMIT"
    git -C src apply --whitespace=nowarn ../openrune.patch
    for e in $EXCLUDES; do echo "/$e" >> src/.git/info/exclude; done
    cp src/game.example.yml src/game.yml
    echo "src/ ready at $COMMIT + openrune.patch"
    ;;
export)
    git -C src add -A -N .
    git -C src diff "$COMMIT" > openrune.patch
    echo "wrote openrune.patch ($(grep -c '^diff --git' openrune.patch) files)"
    ;;
rsprot)
    # A clean clone of RSProt at the pin, re-patched every time so it follows server/rsprot.patch.
    if [ ! -d rsprot ]; then
        git clone -q "$RSPROT_REPO" rsprot
    fi
    git -C rsprot checkout -q -f "$RSPROT_COMMIT"
    git -C rsprot clean -q -fdx -e build -e .gradle
    git -C rsprot apply "$ROOT/server/rsprot.patch"
    sed -i "s/version = \"1.0.0-ALPHA-20261001\"/version = \"$RSPROT_VERSION\"/" rsprot/build.gradle.kts
    sed -i 's/-Xmx8G/-Xmx4G/' rsprot/gradle.properties
    mkdir -p src/.m2-rsprot
    T=""
    for m in buffer protocol crypto compression haproxy; do T="$T :$m:publishToMavenLocal"; done
    for m in android api common desktop internal model shared; do
        T="$T :protocol:osrs-241:osrs-241-$m:publishToMavenLocal"
    done
    own openrune-gradle8
    docker run --rm --name openrune-rsprot-$$ -u 1000:1000 -e HOME=/cache/home -e GRADLE_USER_HOME=/cache \
        -v openrune-gradle8:/cache -v "$OR/rsprot":/src -v "$OR/src/.m2-rsprot":/m2 -w /src \
        gradle:8.14.3-jdk21 gradle --no-daemon --console=plain -Dmaven.repo.local=/m2 -x test $T
    echo "published RSProt $RSPROT_VERSION to src/.m2-rsprot"
    ;;
gradle)
    shift
    gradle "$@"
    ;;
build)
    [ -d src/.m2-rsprot/net/rsprot/osrs-241-android/$RSPROT_VERSION ] || "$0" rsprot
    # Separate runs: the cache build generates api/generated sources the server compile needs.
    if [ -f src/.data/cache/LIVE/main_file_cache.dat2 ]; then
        gradle :or-cache:buildCache
    else
        gradle --no-configuration-cache install   # upstream task is not config-cache safe
    fi
    # The packer leaves new clientscript groups with zero sizes in the reference table (crashes
    # the Android client at cache sync), and the quest scripts use varcs rev 241 does not define.
    # Its compiler also reads/writes long varps with the int opcodes 1/2 (Jagex: 64/65).
    python3 -I scripts/add_varcs.py
    python3 -I scripts/fix_long_varps.py
    python3 -I scripts/fix_reftable.py
    gradle generateRsa
    gradle :server:app:installDist -x test
    python3 -I scripts/check_cache.py
    ;;
modulus)
    # The login key: our server's (shared-key) when present, else OpenRune's own game.key.
    if [ -f src/.data/rsa.properties ]; then
        grep '^modulus=' src/.data/rsa.properties | cut -d= -f2
    else
        openssl rsa -in src/.data/game.key -noout -modulus | cut -d= -f2 | tr A-F a-f
    fi
    ;;
shared-key)
    # Use our own server's login key (server/data/rsa.properties), so app builds made for it log in
    # here too. Takes effect with the next image build; undo by deleting src/.data/rsa.properties.
    cp ../server/data/rsa.properties src/.data/rsa.properties
    echo "src/.data/rsa.properties: modulus $(grep '^modulus=' src/.data/rsa.properties | cut -d= -f2 | cut -c1-16)..."
    ;;
*)
    echo "usage: $0 init|export|rsprot|gradle <args>|build|modulus|shared-key" >&2
    exit 2
    ;;
esac
