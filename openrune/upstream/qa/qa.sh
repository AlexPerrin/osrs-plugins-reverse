#!/usr/bin/env bash
# QA gate helper for OpenRune-Server branches (openrune/upstream/QA.md). Builds, tests and runs a
# branch's own server from a fresh clone, beside (never instead of) the live osrs-openrune containers.
#
#   qa.sh clone  <slug> <commit> [merge-base]  fresh `git clone --shared` of re/upstream-pr/or at <commit>
#                                              into $QA_ROOT/<slug>-<commit7>; with merge-base (e.g. main),
#                                              also <slug>-<commit7>-run = <merge-base> + <commit> (merge)
#   qa.sh gate   <dir>                         section 1 in ONE container and one build-queue ticket
#                                              (priority): clean + freshCache + mergePluginGamevals,
#                                              buildCache, generateRsa, :server:app:installDist (then
#                                              <dir>.logs/runnable), spotlessCheck, assemble, test,
#                                              integration; one log per step, <dir>.logs/summary (rc,
#                                              time, tasks from cache), <dir>.logs/tests (counts)
#   qa.sh build  <dir>                         = gate (split-build: the old uncached run, one container
#                                              per step, the fallback when the cache node is broken)
#   qa.sh gate-run <dir>                       only what a server run needs (freshCache, mergePluginGamevals,
#                                              generateRsa, installDist), one container, for the -run tree
#   qa.sh runbuild <dir>                       = gate-run (split-runbuild: the old one)
#   qa.sh dev <owner> <clone> [tasks...]       dev lane (evidence, never a gate): the build runs in the
#                                              owner's warm container qa-dev-<owner> (persistent Gradle
#                                              daemon, own Gradle home <owner>-dev-gradle, remote build
#                                              cache, no clean; buildq class dev). No tasks = the modules
#                                              changed since merge-base with DEV_BASE (upstream/main):
#                                              compile, then their :test (and :integration); logs in
#                                              <clone>.logs/dev/
#   qa.sh dev-stop <owner> / dev-status        stop the warm container (it also stops after 45 idle min)
#   qa.sh tests  <dir>                         test counts from <dir>'s test-results (per suite)
#   qa.sh gradle <dir> <log> <args...>         one Gradle run with the QA limits
#   qa.sh start  <dir>                         run <dir>'s server: container qa-or-server, game 43596,
#                                              plus the jav_config sidecar qa-or-http on 8083 (world host
#                                              127.0.0.1); waits for "Server ready"; writes the desktop
#                                              proxy target (<dir>.logs/proxy-targets.yaml, game port 43596,
#                                              <dir>'s own .data/game.key modulus)
#   qa.sh stop                                 stop + remove qa-or-server and qa-or-http (nothing else)
#   qa.sh log [n]                              tail of the branch server log
#
# Limits (QA.md): docker --cpus=4 --memory=4g, Gradle --max-workers=2 (test, integration: 1), -Xmx2g
# (cache tasks -Xmx2600m), --no-configuration-cache, through tools/buildq.sh (the agents' build
# queue). gate/runbuild/dev: --build-cache with the shared remote cache node only
# (tools/gradle-cache.sh; init script tools/gradle/remote-cache.init.gradle.kts; test tasks never
# cached), `clean` first (not dev), Gradle home upstream-pr-gradle-qa or -qa2 (whichever is free;
# Gradle's locks don't work across containers sharing one), OpenRS2 cache downloads shared through
# the volume openrune-dl-cache (keyed by OpenRS2 cache id; everything built from the branch is
# still built per gate). split-build/gradle: the old uncached runs (--no-build-cache), one per step.
# Replace this file with mv, never edit it in place: running qa.sh processes read it as they go.
set -euo pipefail
REPO=/home/alex/osrs-plugins-reverse
SRC=$REPO/re/upstream-pr/or
QA_ROOT=${QA_ROOT:-/tmp/claude-1000/-home-alex-osrs-plugins-reverse/a0087aa1-b8d3-439d-add9-f4cbee8fc5e6/scratchpad/qa}
# Gradle home volume locks, shared by every qa.sh caller whatever its QA_ROOT.
LOCKDIR=/tmp/claude-1000/-home-alex-osrs-plugins-reverse/a0087aa1-b8d3-439d-add9-f4cbee8fc5e6/scratchpad/qa
mkdir -p "$LOCKDIR"
GAME_PORT=${QA_GAME_PORT:-43596}
HTTP_PORT=${QA_HTTP_PORT:-8083}
VOL=upstream-pr-gradle-qa
DLVOL=${QA_DLVOL:-openrune-dl-cache}   # QA_DLVOL / GRADLE_REMOTE_CACHE_URL: an isolated store/node (measurements)

# The gate's steps (gate-steps.sh name=args). Test sources compile in 4-assemble, where the
# Kotlin daemon runs; it is gone (it exits after each Gradle run) when the test JVMs start. The cache tools' classpath is compiled in its own run
# first (one run doing both was OOM-killed in the 4 GB container). Tests read .data/game.key (integration), so the key
# comes before them; installDist early, so the branch server can start while the tests run.
GATE_STEPS=(
  "1a-compile=clean :or-cache:qaCacheToolsClasspath"
  "1-cache=:or-cache:freshCache :or-cache:mergePluginGamevals"
  "2-buildcache=:or-cache:buildCache"
  "2b-rsa=generateRsa"
  "7-install=:server:app:installDist -x test"
  "3-spotless=spotlessCheck"
  "4-assemble=assemble testClasses integrationClasses"
  "5-test=test --continue"
  "6-integration=integration --continue"
)
RUN_STEPS=(
  "1a-compile=clean :or-cache:qaCacheToolsClasspath"
  "1-cache=:or-cache:freshCache :or-cache:mergePluginGamevals"
  "7-install=generateRsa :server:app:installDist -x test"
)

# Branches with local/rsprot-* resolve our patched RSProt from <clone>/.m2-rsprot (settings.gradle.kts);
# it isn't in git, so a fresh clone gets a copy of the live tree's (openrune.sh rsprot builds it).
prep_clone() {
  local d=$1
  [ -f "$d/game.yml" ] || cp "$d/game.example.yml" "$d/game.yml"
  if grep -q 'm2-rsprot' "$d/settings.gradle.kts" 2>/dev/null && [ ! -d "$d/.m2-rsprot" ]; then
    cp -a "$REPO/openrune/src/.m2-rsprot" "$d/.m2-rsprot"
    grep -qx '/.m2-rsprot/' "$d/.git/info/exclude" 2>/dev/null || echo '/.m2-rsprot/' >> "$d/.git/info/exclude"
    echo "qa: copied .m2-rsprot into $d" >&2
  fi
}

# Dev lane: one warm container per server dev, qa-dev-<owner> (docker run -d, 4 GB, its own Gradle
# home <owner>-dev-gradle, the remote build cache), holding a persistent Gradle daemon with the
# Kotlin compiler in-process; `qa.sh dev` execs builds into it. It stops itself after
# DEV_IDLE_MIN (45) minutes without a build, or on `qa.sh dev-stop <owner>`, and is remade when
# the owner's clone changes.
dev_container_clone() { docker inspect -f '{{index .Config.Labels "qa.clone"}}' "qa-dev-$1" 2>/dev/null || true; }
dev_container_up() {
  local owner=$1 d=$2 name=qa-dev-$1 v=${DEV_VOL:-$1-dev-gradle} cur gm
  gm=$(git_mounts "$d" | tr '\n' ' ')
  # Remade when the clone or its git mounts (alternates) changed.
  cur=$(docker inspect -f '{{.State.Running}} {{index .Config.Labels "qa.clone"}}|{{index .Config.Labels "qa.gitmounts"}}' "$name" 2>/dev/null || true)
  [ "$cur" = "true $d|$gm" ] && return 0
  [ -n "$cur" ] && { echo "qa: remaking $name for $d (was: $cur)" >&2; docker rm -f "$name" >/dev/null 2>&1 || true; }
  "$REPO/tools/gradle-cache.sh" start >/dev/null || echo "qa: build cache node not reachable" >&2
  docker volume inspect $DLVOL >/dev/null 2>&1 || docker volume create $DLVOL >/dev/null
  docker run --rm -v $v:/v -v $DLVOL:/dl alpine:3.20 chown 1000:1000 /v /dl
  mkdir -p "$d.logs/dev"
  # shellcheck disable=SC2046
  docker run -d --rm --name "$name" --label qa.dev=1 --label qa.owner="$owner" --label qa.clone="$d" --label qa.gitmounts="$gm" \
    --cpus=4 --memory=${QA_DEV_MEM:-4g} -u 1000:1000 $("$REPO/tools/gradle-cache.sh" args) \
    -e GRADLE_USER_HOME=/gradle -e GRADLE_OPTS=-Xmx256m -e QA_CLONE="$d" -e DEV_IDLE_MIN="${DEV_IDLE_MIN:-45}" \
    -v $v:/gradle -v $DLVOL:/dl -v "$d":"$d" -v "$d.logs":"$d.logs" $(git_mounts "$d") \
    -v "$REPO/tools/gradle":/qa-init:ro -v "$REPO/openrune/upstream/qa":/qa-bin:ro \
    -w "$d" eclipse-temurin:21 bash -c '
      touch /gradle/.qa-dev-activity
      while sleep 60; do
        [ $(( $(date +%s) - $(stat -c %Y /gradle/.qa-dev-activity) )) -lt $(( ${DEV_IDLE_MIN:-45} * 60 )) ] && continue
        pgrep -f "qa-steps.sh" >/dev/null && continue
        ./gradlew --stop >/dev/null 2>&1; exit 0
      done' >/dev/null
  # /qa-steps.sh for docker exec: the directory mount keeps it current (a file mount would pin it).
  docker exec -u 0 "$name" ln -sf /qa-bin/gate-steps.sh /qa-steps.sh
  echo "qa: started $name (Gradle home $v, clone $d)" >&2
}

# git dirs a clone needs read-only in the container: its common git dir and its alternates.
git_mounts() {
  local d=$1 g
  g=$(git -C "$d" rev-parse --path-format=absolute --git-common-dir)
  case $g in "$d"/*) ;; *) echo "-v $g:$g:ro" ;; esac
  alt_mounts "$g/objects" 0
}
# The alternates chain (git clone --shared, also of a --shared clone), absolute or relative to the
# objects dir, each mounted read-only: jgit (spotless's ratchet) reads objects through them.
alt_mounts() {
  local o=$1 depth=$2 a
  [ "$depth" -lt 5 ] && [ -f "$o/info/alternates" ] || return 0
  while read -r a; do
    [ -n "$a" ] || continue
    case $a in /*) ;; *) a=$(cd "$o/$a" 2>/dev/null && pwd) || continue ;; esac
    echo "-v $a:$a:ro"
    alt_mounts "$a" $((depth + 1))
  done < "$o/info/alternates"
}

# Test counts per suite from test-results XML (tests, failures, errors, skipped).
test_counts() {  # test_counts <dir> [only results newer than this file]
  python3 - "$1" "${2:-}" <<'PY'
import glob, os, sys, xml.etree.ElementTree as ET
root = sys.argv[1]; tot = {}
since = os.path.getmtime(sys.argv[2]) if sys.argv[2] and os.path.exists(sys.argv[2]) else 0
for f in glob.glob(os.path.join(root, "**/build/test-results/*/TEST-*.xml"), recursive=True):
    if os.path.getmtime(f) < since: continue
    suite = f.split(os.sep)[-2]
    try: e = ET.parse(f).getroot()
    except ET.ParseError: tot.setdefault(suite, [0]*5)[4] += 1; continue
    t = tot.setdefault(suite, [0]*5)
    for i, k in enumerate(("tests", "failures", "errors", "skipped")): t[i] += int(e.get(k, 0))
for s, (n, fl, er, sk, bad) in sorted(tot.items()):
    print(f"{s}: {n} tests, {fl} failures, {er} errors, {sk} skipped" + (f", {bad} unreadable" if bad else ""))
PY
}

gradle() {  # gradle <dir> <log> <args...>
  local d=$1 log=$2 rc; shift 2
  mkdir -p "$(dirname "$log")"
  # The agents' FIFO build queue (tools/buildq.sh) decides when the container runs; `_run` then
  # picks a free Gradle home volume (two QA containers can run at once, and Gradle's cross-process
  # locks don't work across containers sharing one volume).
  "$REPO/tools/buildq.sh" qa -- "$0" _run "$d" ${QA_WORKERS:-2} "$@" > "$log" 2>&1 && rc=0 || rc=$?
  echo "rc=$rc" >> "$log"; echo "$(basename "$log"): rc=$rc"; return $rc
}

own_vol() { :; }  # _run chowns the volume it picks

case "${1:-}" in
_run)  # _run <dir> <workers> <gradle args...>: one Gradle container on a free QA volume
  shift; d=$1; w=$2; shift 2
  for v in $VOL ${VOL}2; do
    exec 8>"$LOCKDIR/.vol-$v.lock"; flock -n 8 && break; exec 8>&-; v=
  done
  [ -n "$v" ] || { echo "qa: no free Gradle volume" >&2; exit 1; }
  docker run --rm -v $v:/v alpine:3.20 chown 1000:1000 /v
  echo "qa: Gradle home volume $v"
  exec docker run --rm --name "qa-gradle-$$" --cpus=4 --memory=4g -u 1000:1000 \
    -e GRADLE_USER_HOME=/gradle -e GRADLE_OPTS=-Xmx2g -e JAVA_TOOL_OPTIONS="${JTO:-}" \
    -v $v:/gradle -v "$d":"$d" -v "$SRC/.git":"$SRC/.git":ro -w "$d" \
    eclipse-temurin:21 ./gradlew --no-daemon --console=plain --no-build-cache --no-configuration-cache \
      --max-workers=$w -Dorg.gradle.jvmargs=-Xmx2g -Pkotlin.compiler.execution.strategy=in-process "$@"
  ;;
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
_gate)  # _gate <qa|dev:<volume>> <dir> <steps...>: one container running gate-steps.sh
  shift; mode=$1; d=$2; shift 2; L=${QA_LOGS:-$d.logs}; mkdir -p "$L"
  if [ "$mode" = qa ]; then
    for v in $VOL ${VOL}2; do
      exec 8>"$LOCKDIR/.vol-$v.lock"; flock -n 8 && break; exec 8>&-; v=
    done
    [ -n "$v" ] || { echo "qa: no free Gradle volume" >&2; exit 1; }
  else
    v=${mode#dev:}
    exec 8>"$LOCKDIR/.vol-$v.lock"; flock 8   # one container per volume; a second dev run waits
  fi
  "$REPO/tools/gradle-cache.sh" start >/dev/null || echo "qa: build cache node not reachable; building without it" >&2
  docker volume inspect $DLVOL >/dev/null 2>&1 || docker volume create $DLVOL >/dev/null
  docker run --rm -v $v:/v -v $DLVOL:/dl alpine:3.20 chown 1000:1000 /v /dl
  echo "qa: Gradle home volume $v"
  # The container must not outlive this process: a TERM/INT/HUP here stops it (docker run runs in
  # the background so the trap fires at once), and a heartbeat file that this process alone keeps
  # fresh lets gate-steps.sh stop itself if this process is SIGKILLed. If the caller (the buildq
  # ticket) dies, this process stops too, so the container never runs without its queue slot.
  cname="qa-gate-$$"; caller=$PPID
  touch "$L/.heartbeat"
  ( while kill -0 $$ 2>/dev/null; do
      kill -0 "$caller" 2>/dev/null || { kill -TERM $$; break; }
      touch "$L/.heartbeat"; sleep 20
    done ) &
  trap 'docker stop -t 15 "$cname" >/dev/null 2>&1 || true' EXIT
  trap 'exit 143' TERM INT HUP
  # shellcheck disable=SC2046
  docker run --rm --name "$cname" --cpus=4 --memory=${QA_GATE_MEM:-5g} -u 1000:1000 \
    $("$REPO/tools/gradle-cache.sh" args) \
    -e GRADLE_USER_HOME=/gradle -e GRADLE_OPTS=-Xmx256m -e QA_MODE=gate \
    ${QA_WORKERS:+-e QA_WORKERS=$QA_WORKERS} ${QA_KOTLIN:+-e QA_KOTLIN=$QA_KOTLIN} \
    ${QA_GRADLE_XMX:+-e QA_GRADLE_XMX=$QA_GRADLE_XMX} ${QA_KOTLIN_XMX:+-e QA_KOTLIN_XMX=$QA_KOTLIN_XMX} \
    ${QA_TEST_WORKERS:+-e QA_TEST_WORKERS=$QA_TEST_WORKERS} ${QA_TEST_HEAP:+-e QA_TEST_HEAP=$QA_TEST_HEAP} \
    ${GRADLE_REMOTE_CACHE_URL:+-e GRADLE_REMOTE_CACHE_URL=$GRADLE_REMOTE_CACHE_URL} \
    -v $v:/gradle -v $DLVOL:/dl -v "$d":"$d" -v "$L":"$L" $(git_mounts "$d") \
    -v "$REPO/tools/gradle":/qa-init:ro -v "$REPO/openrune/upstream/qa/gate-steps.sh":/qa-steps.sh:ro \
    -w "$d" eclipse-temurin:21 bash /qa-steps.sh "$L" "$@" &
  wait $! && rc=0 || rc=$?
  exit $rc
  ;;
gate|build)
  d=$(cd "$2" && pwd); L=$d.logs; mkdir -p "$L"
  git -C "$d" rev-parse HEAD > "$L/commit"
  prep_clone "$d"
  rm -f "$L/runnable"
  t0=$(date +%s)
  # GATE_VOL=<volume>: a Gradle home of your own instead of the QA pair (e.g. a dev's own gate).
  mode=qa; [ -n "${GATE_VOL:-}" ] && mode=dev:$GATE_VOL
  BUILDQ_CLASS=${BUILDQ_CLASS:-gate} BUILDQ_PRIO=${BUILDQ_PRIO:-1} "$REPO/tools/buildq.sh" "${GATE_OWNER:-qa}" -- "$0" _gate "$mode" "$d" "${GATE_STEPS[@]}" \
    > "$L/gate.log" 2>&1 && rc=0 || rc=$?
  git -C "$d" status --short > "$L/status-after"
  test_counts "$d" > "$L/tests"
  { echo "total $(( $(date +%s) - t0 ))s wall (queue wait included), rc=$rc"; } >> "$L/summary"
  cat "$L/summary" "$L/tests"
  exit $rc
  ;;
split-build)
  own_vol; d=$2; L=$d.logs; mkdir -p "$L"; rc=0
  git -C "$d" rev-parse HEAD > "$L/commit"
  JTO=-Xmx2600m gradle "$d" "$L/1-cache.log" clean :or-cache:freshCache :or-cache:mergePluginGamevals || rc=1
  JTO=-Xmx2600m gradle "$d" "$L/2-buildcache.log" :or-cache:buildCache || rc=1
  # Tests read .data/game.key (integration), so the key comes before them.
  gradle "$d" "$L/2b-rsa.log" generateRsa || rc=1
  # installDist early, so the branch server can run while the tests wait in the queue.
  gradle "$d" "$L/7-install.log" :server:app:installDist -x test || rc=1
  touch "$L/runnable"
  gradle "$d" "$L/3-spotless.log" spotlessCheck || rc=1
  gradle "$d" "$L/4-assemble.log" assemble || rc=1
  # One test JVM at a time: each has -Xmx2048m (test-conventions), and two plus the Gradle daemon
  # overrun the 4 GB container (exit 137).
  QA_WORKERS=1 gradle "$d" "$L/5-test.log" test --continue || rc=1
  QA_WORKERS=1 gradle "$d" "$L/6-integration.log" integration --continue || rc=1
  git -C "$d" status --short > "$L/status-after"
  exit $rc
  ;;
gate-run|runbuild)
  d=$(cd "$2" && pwd); L=$d.logs; mkdir -p "$L"
  git -C "$d" rev-parse HEAD > "$L/commit"
  prep_clone "$d"
  BUILDQ_PRIO=${BUILDQ_PRIO:-1} "$REPO/tools/buildq.sh" qa -- "$0" _gate qa "$d" "${RUN_STEPS[@]}" \
    > "$L/runbuild.log" 2>&1 && rc=0 || rc=$?
  cat "$L/summary"; exit $rc
  ;;
split-runbuild)
  own_vol; d=$2; L=$d.logs; mkdir -p "$L"
  JTO=-Xmx2600m gradle "$d" "$L/1-cache.log" clean :or-cache:freshCache :or-cache:mergePluginGamevals
  gradle "$d" "$L/7-install.log" generateRsa :server:app:installDist -x test
  ;;
dev)
  owner=$2; d=$(cd "$3" && pwd); shift 3; L=$d.logs/dev; mkdir -p "$L"
  [[ $owner =~ ^[A-Za-z0-9._-]+$ ]] || { echo "dev: bad owner '$owner'" >&2; exit 2; }
  prep_clone "$d"
  steps=()
  # A clone without a built cache gets one first (tests load .data/cache/SERVER). The cache tools'
  # classpath compiles in its own run: compile + cache tool in one run overran the container.
  [ -d "$d/.data/cache/SERVER" ] || steps+=("1a-compile=:or-cache:qaCacheToolsClasspath" "1-cache=:or-cache:freshCache :or-cache:mergePluginGamevals")
  [ -f "$d/.data/game.key" ] || steps+=("2b-rsa=generateRsa")
  if [ $# -gt 0 ]; then
    steps+=("dev-test=$* --continue")   # one worker: the tasks may run test JVMs
  else
    base=$(git -C "$d" merge-base HEAD "${DEV_BASE:-upstream/main}") || { echo "dev: no merge-base with ${DEV_BASE:-upstream/main}; pass tasks" >&2; exit 2; }
    mapfile -t files < <( { git -C "$d" diff --name-only "$base"; git -C "$d" ls-files --others --exclude-standard; } | sort -u)
    declare -A seen=(); ctasks=(); ttasks=(); cache=
    for f in "${files[@]}"; do
      case $f in */pack/*|.data/raw-cache/*|or-cache/*|*gamevals.toml) cache=1 ;; esac
      p=$(dirname "$f")
      while [ "$p" != . ] && [ ! -f "$d/$p/build.gradle.kts" ]; do p=$(dirname "$p"); done
      [ "$p" = . ] && continue
      case $p in build-logic*) continue ;; esac
      proj=":${p//\//:}"
      [ "$(basename "$p")" = pack ] && proj="${proj%:pack}:$(basename "$(dirname "$p")")-pack"
      [ -n "${seen[$proj]:-}" ] && continue; seen[$proj]=1
      ctasks+=("$proj:testClasses"); ttasks+=("$proj:test")
      if grep -q 'integration-test-suite' "$d/$p/build.gradle.kts"; then
        ctasks+=("$proj:integrationClasses"); ttasks+=("$proj:integration")
      fi
    done
    # Data-only changes (packs, raw-cache, gamevals) still rebuild the cache.
    if [ -n "$cache" ]; then
      [[ " ${steps[*]} " == *" 1a-compile="* ]] || steps+=("1a-compile=:or-cache:qaCacheToolsClasspath")
      steps+=("2-buildcache=:or-cache:buildCache")
    fi
    if [ ${#ctasks[@]} -gt 0 ]; then
      steps+=("dev-compile=${ctasks[*]} --continue" "dev-test=${ttasks[*]} --continue")
    elif [ -z "$cache" ]; then
      echo "dev: no changed modules or cache data since ${base:0:9}; pass tasks" >&2; exit 2
    fi
  fi
  printf 'dev steps:\n'; printf '  %s\n' "${steps[@]}"
  touch "$L/.started"
  # A warm container needs little more memory; a new one needs its full limit.
  need=4608; [ "$(dev_container_clone "$owner")" = "$d" ] && need=1024   # (remade on a mount change: rare)
  BUILDQ_CLASS=dev BUILDQ_MEM_MB=$need "$REPO/tools/buildq.sh" "$owner" -- "$0" _dev "$owner" "$d" "${steps[@]}" \
    > "$L/dev.log" 2>&1 && rc=0 || rc=$?
  cat "$L/summary"; test_counts "$d" "$L/.started"; exit $rc
  ;;
_dev)  # _dev <owner> <clone> <steps...>: run the steps in the owner's warm container (made if needed)
  owner=$2; d=$3; shift 3; L=$d.logs/dev; mkdir -p "$L"; name=qa-dev-$owner
  dev_container_up "$owner" "$d"
  caller=$PPID; touch "$L/.heartbeat"
  ( while kill -0 $$ 2>/dev/null; do
      kill -0 "$caller" 2>/dev/null || { kill -TERM $$; break; }
      touch "$L/.heartbeat"; sleep 20
    done ) &
  # A killed run stops its Gradle client (the warm daemon stays); the watchdog in gate-steps.sh
  # covers a SIGKILLed host side.
  trap 'docker exec "$name" pkill -TERM -f "qa-steps.sh $L" >/dev/null 2>&1; docker exec "$name" pkill -TERM -f GradleWrapperMain >/dev/null 2>&1 || true' TERM INT HUP
  docker exec "$name" touch /gradle/.qa-dev-activity
  docker exec -u 1000:1000 -w "$d" -e QA_MODE=dev -e QA_TEST_HEAP=${QA_TEST_HEAP:-1536m} \
    ${QA_WORKERS:+-e QA_WORKERS=$QA_WORKERS} ${QA_KOTLIN:+-e QA_KOTLIN=$QA_KOTLIN} \
    "$name" bash /qa-steps.sh "$L" "$@" &
  wait $! && rc=0 || rc=$?
  docker exec "$name" touch /gradle/.qa-dev-activity >/dev/null 2>&1 || true
  exit $rc
  ;;
dev-stop)  # dev-stop <owner>: stop the owner's warm container (Gradle daemon first)
  name=qa-dev-$2
  docker exec "$name" sh -c 'cd "$QA_CLONE" && ./gradlew --stop' >/dev/null 2>&1 || true
  docker rm -f "$name" 2>/dev/null | sed 's/^/stopped /'
  ;;
dev-status)
  docker ps --filter label=qa.dev=1 --format '{{.Names}}\t{{.Status}}\t{{.Label "qa.clone"}}'
  docker stats --no-stream --format '{{.Name}} {{.MemUsage}}' $(docker ps -q --filter label=qa.dev=1) 2>/dev/null
  ;;
tests) test_counts "$(cd "$2" && pwd)" ;;
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
*) sed -n 2,45p "$0"; exit 1 ;;
esac
