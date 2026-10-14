#!/bin/sh
# Fetches the OpenRS2 cache on first start, then runs the server.
set -eu
CACHE_DIR="${CACHE_DIR:-$DATA_DIR/cache}"
if [ ! -f "$CACHE_DIR/main_file_cache.dat2" ]; then
    : "${OPENRS2_CACHE_ID:?cache missing and OPENRS2_CACHE_ID not set}"
    echo "Downloading OpenRS2 cache $OPENRS2_CACHE_ID into $CACHE_DIR"
    tmp="$(mktemp -d)"
    curl -fSL --retry 3 -o "$tmp/disk.zip" "https://archive.openrs2.org/caches/runescape/$OPENRS2_CACHE_ID/disk.zip"
    unzip -q "$tmp/disk.zip" -d "$tmp"
    mkdir -p "$CACHE_DIR"
    mv "$tmp"/cache/* "$CACHE_DIR"/
    rm -rf "$tmp"
fi
exec /app/bin/rsps-test-server "$@"
