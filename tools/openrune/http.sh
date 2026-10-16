#!/bin/sh
# OpenRune spike: jav_config.ws / slr.ws / LOCAL_AUTH sidecar for OpenRune, from our own test-server
# image (osrs-rsps-test:241) in a SEPARATE container (openrune-spike-http). Only its HTTP port is
# published (host 8081); its own game port stays unpublished. server/data is mounted read-only (cache).
# WORLD_ID=255 is OpenRune's world. The client reaches OpenRune's port 40255 because its game port
# is patched (tools/openrune/patch_port.py); the jav_config template is our own, unchanged.
set -eu
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
docker rm -f openrune-spike-http >/dev/null 2>&1 || true
exec docker run -d --name openrune-spike-http -p 8081:8080 \
    -v "$ROOT/server/data":/data:ro \
    -e WORLD_HOST=10.0.2.2 -e HTTP_PUBLIC_PORT=8081 -e WORLD_ID=255 -e SAVE_DIR=/tmp/saves \
    -e JAV_CONFIG_TEMPLATE=/app/protocol/jav_config.ws.template -e LOCAL_AUTH=true -e LOCAL_DISPLAY_NAME=Tester \
    -e PROOF_OF_WORK=false -e VALIDATE_CRCS=false osrs-rsps-test:241
