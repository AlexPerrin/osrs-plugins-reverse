#!/bin/sh
# OpenRune spike: publishes our patched RSProt (re/openrune/.spike/rsprot = pinned upstream +
# server/rsprot.patch, version bumped to 1.0.0-ALPHA-20261001.99) to re/openrune/.spike/m2.
# Runs in Docker only (gradle 8.14, as RSProt needs); cache in volume openrune-spike-gradle8.
set -eu
S="$(cd "$(dirname "$0")/../../re/openrune/.spike" && pwd)"
T=""
for m in buffer protocol crypto compression haproxy; do T="$T :$m:publishToMavenLocal"; done
for m in android api common desktop internal model shared; do T="$T :protocol:osrs-241:osrs-241-$m:publishToMavenLocal"; done
docker run --rm --name openrune-spike-rsprot -e GRADLE_USER_HOME=/cache -v openrune-spike-gradle8:/cache \
    -v "$S/rsprot":/src -v "$S/m2":/m2 -w /src gradle:8.14.3-jdk21 \
    sh -c "gradle --no-daemon --console=plain -Dmaven.repo.local=/m2 -x test $T; s=\$?; chown -R 1000:1000 /src /m2; exit \$s"
