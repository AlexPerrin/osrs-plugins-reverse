#!/usr/bin/env bash
# QA gate helper for OpenRune-Server branches (openrune/upstream/QA.md). Builds, tests and runs a
# branch's own server from a fresh clone, beside (never instead of) the live osrs-openrune containers.
#
#   qa.sh clone  <slug> <commit> [merge-base]  fresh `git clone --shared` of re/upstream-pr/or at <commit>
#                                              into $QA_ROOT/<slug>-<commit7>; with merge-base (e.g. main),
#                                              also <slug>-<commit7>-run = <merge-base> + <commit> (merge)
#   qa.sh build  <dir>                         section 1: freshCache + mergePluginGamevals, buildCache,
#                                              generateRsa, spotlessCheck, assemble, test, integration
#                                              (separate runs), then :server:app:installDist. Logs: <dir>.logs/
#   qa.sh runbuild <dir>                       only what a server run needs (freshCache, mergePluginGamevals,
#                                              generateRsa, installDist), for the -run merge tree
#   qa.sh gradle <dir> <log> <args...>         one Gradle run with the QA limits
#   qa.sh start  <dir>                         run <dir>'s server: container qa-or-server, game 43596,
#                                              plus the jav_config sidecar qa-or-http on 8083 (world host
#                                              127.0.0.1); waits for "Server ready"; writes the desktop
#                                              proxy target (<dir>.logs/proxy-targets.yaml, game port 43596,
#                                              <dir>'s own .data/game.key modulus)
#   qa.sh stop                                 stop + remove qa-or-server and qa-or-http (nothing else)
#   qa.sh log [n]                              tail of the branch server log
#
# Limits (QA.md): docker --cpus=4 --memory=4g, Gradle --max-workers=2, -Xmx2g (cache tasks -Xmx2600m),
# clean --no-build-cache --no-configuration-cache, own Gradle home volume upstream-pr-gradle-qa, one
# build container at a time (the scratchpad gradle.lock, shared with the other agents' g.sh, plus a
# wait while any other container runs gradlew).
set -euo pipefail
REPO=/home/alex/osrs-plugins-reverse
SRC=$REPO/re/upstream-pr/or
QA_ROOT=${QA_ROOT:-/tmp/claude-1000/-home-alex-osrs-plugins-reverse/a0087aa1-b8d3-439d-add9-f4cbee8fc5e6/scratchpad/qa}
LOCK=${QA_LOCK:-/tmp/claude-1000/-home-alex-osrs-plugins-reverse/a0087aa1-b8d3-439d-add9-f4cbee8fc5e6/scratchpad/gradle.lock}
GAME_PORT=${QA_GAME_PORT:-43596}
HTTP_PORT=${QA_HTTP_PORT:-8083}
VOL=upstream-pr-gradle-qa

gradle() {  # gradle <dir> <log> <args...>
  local d=$1 log=$2 rc; shift 2
  mkdir -p "$(dirname "$log")"
  exec 9>"$LOCK"; flock 9
  while docker ps --format '{{.Command}}' | grep -q gradlew; do sleep 30; done
  docker run --rm --name "qa-gradle-$$" --cpus=4 --memory=4g -u 1000:1000 \
    -e GRADLE_USER_HOME=/gradle -e GRADLE_OPTS=-Xmx2g -e JAVA_TOOL_OPTIONS="${JTO:-}" \
    -v $VOL:/gradle -v "$d":"$d" -v "$SRC/.git":"$SRC/.git":ro -w "$d" \
    eclipse-temurin:21 ./gradlew --no-daemon --console=plain --no-build-cache --no-configuration-cache \
      --max-workers=${QA_WORKERS:-2} -Dorg.gradle.jvmargs=-Xmx2g "$@" > "$log" 2>&1 && rc=0 || rc=$?
  exec 9>&-
  echo "rc=$rc" >> "$log"; echo "$(basename "$log"): rc=$rc"; return $rc
}

own_vol() { docker run --rm -v $VOL:/v alpine:3.20 chown 1000:1000 /v; }

case "${1:-}" in
clone)
  slug=$2; c=$(git -C "$SRC" rev-parse "$3"); d=$QA_ROOT/$slug-${c:0:9}
  [ -e "$d" ] && { echo "exists: $d" >&2; exit 1; }
  git clone -q --shared --no-checkout "$SRC" "$d"; git -C "$d" checkout -q --detach "$c"
  cp "$d/game.example.yml" "$d/game.yml"
  echo "$d"
  if [ -n "${4:-}" ]; then
    b=$(git -C "$SRC" rev-parse "$4"); r=$d-run
    git clone -q --shared --no-checkout "$SRC" "$r"; git -C "$r" checkout -q --detach "$b"
    git -C "$r" -c user.name=qa -c user.email=qa@localhost merge -q --no-edit --no-ff "$c"
    cp "$r/game.example.yml" "$r/game.yml"
    echo "$r ($4 ${b:0:9} + ${c:0:9})"
  fi
  ;;
gradle) own_vol; shift; d=$1; log=$2; shift 2; gradle "$d" "$log" "$@" ;;
build)
  own_vol; d=$2; L=$d.logs; mkdir -p "$L"; rc=0
  git -C "$d" rev-parse HEAD > "$L/commit"
  JTO=-Xmx2600m gradle "$d" "$L/1-cache.log" clean :or-cache:freshCache :or-cache:mergePluginGamevals || rc=1
  JTO=-Xmx2600m gradle "$d" "$L/2-buildcache.log" :or-cache:buildCache || rc=1
  # Tests read .data/game.key (integration), so the key comes before them.
  gradle "$d" "$L/2b-rsa.log" generateRsa || rc=1
  gradle "$d" "$L/3-spotless.log" spotlessCheck || rc=1
  gradle "$d" "$L/4-assemble.log" assemble || rc=1
  # One test JVM at a time: each has -Xmx2048m (test-conventions), and two plus the Gradle daemon
  # overrun the 4 GB container (exit 137).
  QA_WORKERS=1 gradle "$d" "$L/5-test.log" test --continue || rc=1
  QA_WORKERS=1 gradle "$d" "$L/6-integration.log" integration --continue || rc=1
  gradle "$d" "$L/7-install.log" :server:app:installDist -x test || rc=1
  git -C "$d" status --short > "$L/status-after"
  exit $rc
  ;;
runbuild)
  own_vol; d=$2; L=$d.logs; mkdir -p "$L"
  JTO=-Xmx2600m gradle "$d" "$L/1-cache.log" clean :or-cache:freshCache :or-cache:mergePluginGamevals
  gradle "$d" "$L/7-install.log" generateRsa :server:app:installDist -x test
  ;;
start)
  d=$2; L=$d.logs; mkdir -p "$L"
  [ -x "$d/server/app/build/install/app/bin/app" ] || { echo "no installDist in $d" >&2; exit 1; }
  docker rm -f qa-or-server qa-or-http >/dev/null 2>&1 || true
  docker run -d --name qa-or-server --cpus=4 --memory=4g -u 1000:1000 -e HOME="$d" \
    -e JAVA_OPTS=-Xmx2g -p $GAME_PORT:43594 -v "$d":"$d" -w "$d" \
    eclipse-temurin:21-jre "$d/server/app/build/install/app/bin/app" >/dev/null
  # jav_config.ws / slr.ws for the desktop client: the same read-only sidecar image as
  # osrs-openrune-http-desktop, on its own port. RSProx forwards the game connection to
  # 127.0.0.1:<game_server_port> from the proxy target, so the sidecar's own /rsa is not used.
  docker run -d --name qa-or-http -p $HTTP_PORT:8080 -v $REPO/server/data:/data:ro \
    -e WORLD_HOST=127.0.0.1 -e HTTP_PUBLIC_PORT=$HTTP_PORT -e WORLD_ID=255 -e SAVE_DIR=/tmp/saves \
    -e JAV_CONFIG_TEMPLATE=/app/protocol/jav_config.ws.template -e LOCAL_AUTH=false \
    -e PROOF_OF_WORK=false -e VALIDATE_CRCS=false osrs-rsps-test:241 >/dev/null
  for _ in $(seq 150); do
    docker logs qa-or-server 2>&1 | grep -q "Server ready in" && break
    docker ps --format '{{.Names}}' | grep -qx qa-or-server || { docker logs --tail 40 qa-or-server; exit 1; }
    sleep 2
  done
  docker logs qa-or-server 2>&1 | grep -o "Server ready in [0-9.]*s" || { echo "not ready" >&2; exit 1; }
  mod=$(openssl rsa -in "$d/.data/game.key" -noout -modulus | cut -d= -f2 | tr A-F a-f)
  cat > "$L/proxy-targets.yaml" <<YAML
# QA branch server ($(git -C "$d" rev-parse --short=9 HEAD)), written by openrune/upstream/qa/qa.sh start.
config:
  - name: OpenRune Test
    jav_config_url: http://127.0.0.1:$HTTP_PORT/jav_config.ws
    modulus: $mod
    revision: 241.1
    game_server_port: $GAME_PORT
    runelite_bootstrap_commithash: e617bef1a7f5c39fbe8768994d27d638941efd5d
    binary_folder: openrune-qa
YAML
  echo "proxy target: $L/proxy-targets.yaml"
  echo "desktop: DESKTOP_TARGET_YAML=$L/proxy-targets.yaml tools/multiclient/desktop.sh start"
  ;;
stop) docker rm -f qa-or-server qa-or-http 2>&1 | sed 's/^/removed /' ;;
log) docker logs --tail "${2:-60}" qa-or-server 2>&1 ;;
*) sed -n 2,26p "$0"; exit 1 ;;
esac
