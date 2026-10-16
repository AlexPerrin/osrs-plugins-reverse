#!/usr/bin/env bash
# Writes proxy-targets.yaml (RSProx custom target for our OpenRune test server) with the login
# modulus the server serves right now. Usage: tools/multiclient/make_proxy_target.sh [http base]
set -euo pipefail
HTTP="${1:-http://127.0.0.1:8082}"
MOD="$(curl -fsS "$HTTP/rsa" | tr -d '[:space:]')"
# RuneLite 1.13.2 is the first (and, on 2026-10-16, current) rev-241 RuneLite; pinning its bootstrap
# keeps RSProx on rev 241 after Jagex moves on (static.runelite.net commit "Release 1.13.2").
RL_COMMIT=e617bef1a7f5c39fbe8768994d27d638941efd5d
cat > "$(dirname "$0")/proxy-targets.yaml" <<YAML
# RSProx custom target: our OpenRune test server (docs/multi-client.md).
# Copy to %USERPROFILE%\\.rsprox\\proxy-targets.yaml (or import it with the + button in RSProx).
config:
  - name: OpenRune Test
    jav_config_url: $HTTP/jav_config.ws
    modulus: $MOD
    revision: 241.1
    game_server_port: 43594
    runelite_bootstrap_commithash: $RL_COMMIT
    binary_folder: openrune-test
YAML
echo "wrote $(dirname "$0")/proxy-targets.yaml (modulus ${MOD:0:16}...)"
