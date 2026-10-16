#!/bin/sh
# OpenRune spike: serves a (hybrid) disk cache over JS5 on host port 40255 with our own server
# image, in container openrune-spike-js5. Usage: tools/openrune/js5.sh <dir under re/openrune/.spike/caches>
set -eu
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
docker rm -f openrune-spike-js5 >/dev/null 2>&1 || true
docker run -d --name openrune-spike-js5 -p 40255:43594 \
    -v "$ROOT/re/openrune/.spike/caches/$1":/cache:ro -e CACHE_DIR=/cache -e DATA_DIR=/tmp \
    -e SAVE_DIR=/tmp/saves -e WORLD_HOST=10.0.2.2 osrs-rsps-test:241 >/dev/null
for _ in $(seq 1 60); do
    docker logs openrune-spike-js5 2>&1 | grep -q "listening on :43594" && { echo "js5 up: $1"; exit 0; }
    docker ps -q -f name=openrune-spike-js5 | grep -q . || { docker logs openrune-spike-js5 2>&1 | tail -5; exit 1; }
    sleep 1
done
echo "timeout"; exit 1
