#!/bin/sh
# OpenRune spike: boots OpenRune (re/openrune) in Docker as uid 1000 (embedded Postgres refuses
# root). Game/JS5 port 43594 inside is published on host port ${PORT:-40255}: the Android client
# connects to 40000 + world id when jav_config param 15 (build type) is not LIVE (0), and
# OpenRune's world is 255. Never touches osrs-rsps-test (43594/8080).
set -eu
R="$(cd "$(dirname "$0")/../../re/openrune" && pwd)"
docker rm -f openrune-spike >/dev/null 2>&1 || true
exec docker run -d --name openrune-spike -u 1000:1000 -e HOME=/tmp -e GRADLE_USER_HOME=/gradle \
    -p "${PORT:-40255}:43594" -v openrune-gradle:/gradle -v "$R":/w ${LIVE_CACHE:+-v "$R/.spike/caches/$LIVE_CACHE":/w/.data/cache/LIVE:ro} -w /w eclipse-temurin:21 \
    ./gradlew --no-daemon --console=plain run
