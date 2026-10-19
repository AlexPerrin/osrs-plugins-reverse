#!/usr/bin/env bash
# Runs inside a build container (qa.sh): the Gradle steps in order, each its own `./gradlew` run,
# its own log <L>/<name>.log ending in rc=<n>, and a line in <L>/summary (step, rc, seconds, tasks
# executed / from cache). Every step runs even after a failure (as `qa.sh build` always did), except
# after a failed cache step (1a-compile, 1-cache, 2-buildcache: everything later needs .data); the
# exit code is 1 if any failed.
#   gate-steps.sh <logs dir> <step>...    step = name=<gradle args, space-separated>
# Modes (QA_MODE):
#   gate  (default) the one gate container (5 GB): --no-daemon per step, --max-workers=4, Gradle
#         -Xmx1536m, Kotlin daemon -Xmx2g (it exits 1 s after each step's Gradle run); test and
#         integration with 1 worker (each test JVM -Xmx2048m); cache tools -Xmx2600m.
#   dev   exec'd into a dev's warm container (4 GB): one persistent Gradle daemon (-Xmx1536m, the
#         Kotlin compiler in-process, so it stays warm in that daemon), 2 workers, test JVMs capped
#         at 1536m; for cache steps the daemon is stopped and the step runs --no-daemon.
# Overrides: QA_WORKERS, QA_TEST_WORKERS (1), QA_TEST_HEAP (test JVM -Xmx), QA_GRADLE_XMX,
# QA_KOTLIN (daemon|in-process), QA_KOTLIN_XMX. The summary has each step's peak container memory
# (anon / anon + page cache).
# After 1-cache the downloaded OpenRS2 cache zips (~/.openrune/caches/<game>/<id>) are published to
# the shared store /dl.
set -uo pipefail
L=$1; shift
MODE=${QA_MODE:-gate}
INIT=/qa-init/remote-cache.init.gradle.kts
CACHES=$HOME/.openrune/caches
ABORT=/tmp/qa-abort-$$
rc_all=0
if [ "$MODE" = dev ]; then
  WORKERS=${QA_WORKERS:-2}; GXMX=${QA_GRADLE_XMX:-1536m}; KSTRAT=${QA_KOTLIN:-in-process}; KXMX=${QA_KOTLIN_XMX:-1g}
else
  WORKERS=${QA_WORKERS:-4}; GXMX=${QA_GRADLE_XMX:-1536m}; KSTRAT=${QA_KOTLIN:-daemon}; KXMX=${QA_KOTLIN_XMX:-2g}
fi
: > "$L/summary"
printf '%s container %s, Gradle home %s, build cache %s, workers %s, Gradle -Xmx%s, Kotlin %s (-Xmx%s)\n' \
  "$MODE" "$(hostname)" "$GRADLE_USER_HOME" "${GRADLE_REMOTE_CACHE_URL:-http://gradle-cache-node:5071/cache/}" \
  "$WORKERS" "${GXMX}" "$KSTRAT" "$KXMX" >> "$L/summary"

# Seed the per-container OpenRS2 download dir from the shared store (copies: the library deletes a
# dir it finds incomplete, and two gates must never write the same files).
if [ -d /dl/caches ]; then mkdir -p "$CACHES" && cp -an /dl/caches/. "$CACHES/" 2>/dev/null; fi

publish_downloads() {
  [ -d /dl ] && [ -d "$CACHES" ] || return 0
  local dir rel zip
  for dir in "$CACHES"/*/*/; do
    [ -d "$dir" ] || continue
    dir=${dir%/}; rel=${dir#"$CACHES"/}
    [ -e "/dl/caches/$rel" ] && continue
    # A complete download only (xteas.json is optional from revision 237); `jar tf` reads the zip's
    # central directory, so a cut-off download fails it.
    zip=$(ls "$dir"/*-disk.zip 2>/dev/null | head -1)
    [ -n "$zip" ] && jar tf "$zip" >/dev/null 2>&1 || { echo "not published (no complete zip): $rel" >> "$L/summary"; continue; }
    (
      flock 7
      [ -e "/dl/caches/$rel" ] && exit 0
      mkdir -p "/dl/caches/$(dirname "$rel")"
      tmp="/dl/caches/$(dirname "$rel")/.tmp-$(basename "$rel")-$$"
      cp -a "$dir" "$tmp" && mv "$tmp" "/dl/caches/$rel" && echo "published download $rel" >> "$L/summary"
    ) 7>/dl/.lock
  done
}

# Watchdog: qa.sh touches <L>/.heartbeat every 20 s while it (and its build-queue ticket) lives.
# If it goes stale, the host side is gone: stop this run's Gradle and skip the remaining steps, so
# nothing runs on without its queue slot.
( while sleep 30; do
    [ $(( $(date +%s) - $(stat -c %Y "$L/.heartbeat" 2>/dev/null || echo 0) )) -lt 120 ] && continue
    echo "watchdog: host side gone (stale $L/.heartbeat), stopping" >> "$L/summary"
    touch "$ABORT"
    pkill -TERM -f GradleWrapperMain; [ "$MODE" = gate ] && pkill -TERM java
    exit 0
  done ) &
WD=$!
trap 'kill $WD 2>/dev/null; rm -f "$ABORT"' EXIT

for step in "$@"; do
  [ -e "$ABORT" ] && { echo "aborted before ${step%%=*}" >> "$L/summary"; rc_all=1; break; }
  name=${step%%=*}; args=${step#*=}
  if [ -n "${cache_failed:-}" ]; then echo "$name: skipped (cache step failed)" >> "$L/summary"; continue; fi
  jto=; w=$WORKERS; daemon=--no-daemon; gx=$GXMX
  case $name in 1-cache*|2-buildcache*) jto=-Xmx2600m ;; esac
  case $name in *test*|*integration*) w=${QA_TEST_WORKERS:-1} ;; esac
  if [ "$MODE" = dev ]; then
    case $name in
      1-cache*|2-buildcache*)   # the cache tool needs 2.6 GB: no warm daemon beside it
        ./gradlew --stop >/dev/null 2>&1; gx=1g ;;
      *) daemon="--daemon" ;;
    esac
  fi
  log=$L/$name.log
  t0=$(date +%s)
  # Peak container memory during the step, sampled every 2 s from cgroup v2: anon (the JVMs; what
  # an OOM kill is about) and current (anon + page cache, which the kernel reclaims at the limit).
  ( pa=0; pc=0; while :; do
      a=$(awk '$1=="anon"{print $2}' /sys/fs/cgroup/memory.stat 2>/dev/null); c=$(cat /sys/fs/cgroup/memory.current 2>/dev/null)
      [ "${a:-0}" -gt $pa ] && pa=$a; [ "${c:-0}" -gt $pc ] && pc=$c
      echo "$((pa / 1048576))/$((pc / 1048576))" > "/tmp/qa-peak-$$"; sleep 2; done ) &
  PS=$!
  # shellcheck disable=SC2086
  JAVA_TOOL_OPTIONS=$jto ./gradlew $daemon --console=plain --build-cache --no-configuration-cache \
    -I "$INIT" --max-workers=$w -Dorg.gradle.jvmargs="-Xmx$gx -Dfile.encoding=UTF-8" \
    -Pkotlin.compiler.execution.strategy=$KSTRAT -Pkotlin.daemon.jvmargs="-Xmx$KXMX" \
    ${QA_TEST_HEAP:+-Pqa.testMaxHeap=$QA_TEST_HEAP} $args > "$log" 2>&1
  rc=$?
  t1=$(date +%s)
  kill $PS 2>/dev/null; wait $PS 2>/dev/null; peak=$(cat "/tmp/qa-peak-$$" 2>/dev/null || echo ?)
  echo "rc=$rc" >> "$log"
  [ $rc -eq 0 ] || rc_all=1
  tasks=$(grep -E '^[0-9]+ actionable tasks?:' "$log" | tail -1)
  printf '%-14s rc=%d %4ds  peak anon/all %9s MB  %s\n' "$name" $rc $((t1 - t0)) "$peak" "${tasks:-}" >> "$L/summary"
  echo "$name: rc=$rc ($((t1 - t0))s)"
  case $name in 1-cache*) publish_downloads ;; esac
  case $name in 1a-compile*|1-cache*|2-buildcache*) [ $rc -eq 0 ] || cache_failed=1 ;; esac
  case $name in *install*) [ $rc -eq 0 ] && touch "$L/runnable" ;; esac
done
exit $rc_all
