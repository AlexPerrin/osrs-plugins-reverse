#!/usr/bin/env bash
# Off-device check of the host's load-time network gate (host/src/net/runelite/host/NetScan.java,
# R1 in docs/app-telemetry.md): a self-test on synthetic jars, then "load" or "REFUSE <reason>" per
# dexed plugin jar given (the jars pushed to the device, e.g. re/host/plugins/*.jar re/host/plugins-extra/*.jar).
# Vetted names load; the allow-list property (debug.osrsrl.netallow) is taken as empty here.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../../.." && pwd)
OUT=$ROOT/re/hub-build/netscan
mkdir -p "$OUT/src/net/runelite/host"
cat > "$OUT/src/net/runelite/host/Native.java" <<'J'
package net.runelite.host;
final class Native { static String property(String name) { return ""; } }
J
javac -d "$OUT/classes" "$OUT/src/net/runelite/host/Native.java" \
    "$ROOT/host/src/net/runelite/host/NetScan.java" "$ROOT/host/plugins/hub/NetScanCheck.java"
java -cp "$OUT/classes" net.runelite.host.NetScanCheck "$@"
