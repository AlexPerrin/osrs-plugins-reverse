#!/usr/bin/env bash
# tools/harness/image.sh <gated-clone> <batch>: the deploy image of a gated integration commit (BR-B1), from the
# gate's installDist output and data; the login key only ever comes from the live image.
#   1. preconditions: <clone>.logs/summary ends rc=0; the batch record harness/state/batches/<batch>.json names
#      the integration sha (= HEAD of the clone when it is a git checkout)
#   2. exit 3 if <clone>/.data/rsa.properties exists (the live key never enters a clone); stage
#      re/harness/image-<sha7>/: server/ = <clone>/server/app/build/install/app, data/ = <clone>/.data without
#      game.key (the gate's throwaway key) and postgres/; exit 3 if a key file survives the staging
#   3. the cache fixes (openrune/scripts add_varcs, fix_long_varps, fix_reftable, check_cache, python3 -I) on the
#      staged data/cache/LIVE, as openrune.sh build runs them on src/
#   4. docker build from tools/harness/Dockerfile.integration with LIVE_IMAGE = the live container's image id, so
#      game.key, rsa.properties, game.yml and entrypoint.sh are COPY --from the live image only
#   5. key hash: sha256sum of /app/.data/rsa.properties in both images, printed only as "key hash: match" or
#      "key hash: MISMATCH" (exit 4); the hash itself is never printed
#   6. private boot check: container hd-boot-<sha7>, no published port, tmpfs postgres, "Server ready in" within
#      HD_IMAGE_TIMEOUT s (default 1800); the container is removed on every exit path (trap)
#   7. record harness/state/images/<sha7>.json {integration, image, image_id, live_image_id, key_hash, boot,
#      seconds, ready_line, batch}
# Env: HD_STATE_DIR (harness/state), DOCKER or HD_DOCKER (the docker command: tests), HD_CACHE_SCRIPTS (the fix
# scripts' dir), HD_IMAGE_ROOT (the context dir's parent, re/harness), HD_IMAGE_TIMEOUT, HD_POLL_SECONDS,
# HD_LIVE_CONTAINER (osrs-openrune).
# Exit: 0 ok, 2 usage or precondition, 3 a key file is present, 4 key hash mismatch, 5 build or boot failed.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
STATE=${HD_STATE_DIR:-$ROOT/harness/state}
DOCKER=${DOCKER:-${HD_DOCKER:-docker}}
SCRIPTS=${HD_CACHE_SCRIPTS:-$ROOT/openrune/scripts}
CTX_ROOT=${HD_IMAGE_ROOT:-$ROOT/re/harness}
TIMEOUT=${HD_IMAGE_TIMEOUT:-1800}
POLL=${HD_POLL_SECONDS:-5}
LIVE=${HD_LIVE_CONTAINER:-osrs-openrune}
TEMPLATE=$(dirname "$0")/Dockerfile.integration

clone=${1:?usage: image.sh <gated-clone> <batch>}
batch=${2:?usage: image.sh <gated-clone> <batch>}
die() { echo "image: $*" >&2; exit "${2:-2}"; }

# 1. preconditions
[ -d "$clone" ] || die "no clone $clone"
summary=$clone.logs/summary
[ -f "$summary" ] || die "no gate summary $summary"
grep -q 'rc=0$' "$summary" || die "the gate of $clone did not pass ($(tail -n1 "$summary"))"
rec=$STATE/batches/$batch.json
[ -f "$rec" ] || die "no batch record $rec"
sha=$(python3 -I -c 'import json,sys; print(json.load(open(sys.argv[1])).get("integration") or "")' "$rec")
[ -n "$sha" ] || die "batch $batch has no integration sha"
if [ -e "$clone/.git" ]; then
    head=$(git -C "$clone" rev-parse HEAD)
    [ "$head" = "$sha" ] || die "clone HEAD $head is not the batch's integration $sha"
fi
sha7=${sha:0:7}
tag=osrs-openrune:integration-$sha7
ctx=$CTX_ROOT/image-$sha7
t0=$(date +%s)

# 2. staging, never a key file
[ -e "$clone/.data/rsa.properties" ] && die "$clone/.data/rsa.properties exists: the live key never enters a clone; refusing" 3
[ -d "$clone/server/app/build/install/app" ] || die "no installDist output in $clone (server/app/build/install/app)"
[ -d "$clone/.data" ] || die "no .data in $clone"
rm -rf "$ctx"
mkdir -p "$ctx"
cp -a "$clone/server/app/build/install/app" "$ctx/server"
mkdir -p "$ctx/data"
for entry in "$clone"/.data/* "$clone"/.data/.[!.]*; do
    [ -e "$entry" ] || continue
    case $(basename "$entry") in rsa.properties|game.key|postgres) continue ;; esac
    cp -a "$entry" "$ctx/data/"
done
for k in rsa.properties game.key; do
    [ -e "$ctx/data/$k" ] && { rm -rf "$ctx"; die "$k survived the staging; refusing" 3; }
done
cp "$TEMPLATE" "$ctx/Dockerfile"

# 3. the cache fixes on the staged copy
cache=$ctx/data/cache/LIVE
[ -d "$cache" ] || die "no cache at $cache"
for s in add_varcs fix_long_varps fix_reftable check_cache; do
    python3 -I "$SCRIPTS/$s.py" "$cache" || die "$s.py failed on $cache" 5
done

# 4. build with the live image's id pinned
live_id=$($DOCKER inspect "$LIVE" --format '{{.Image}}')
[ -n "$live_id" ] || die "no live container $LIVE"
echo "image: building $tag from $ctx (LIVE_IMAGE $live_id)"
$DOCKER build --build-arg LIVE_IMAGE="$live_id" -t "$tag" "$ctx" || die "docker build failed" 5
image_id=$($DOCKER inspect "$tag" --format '{{.Id}}')

# 5. key hash: compared, never printed
keyhash() { $DOCKER run --rm --entrypoint sha256sum "$1" /app/.data/rsa.properties | awk '{print $1}'; }
h_live=$(keyhash "$live_id")
h_new=$(keyhash "$tag")
if [ -n "$h_live" ] && [ "$h_live" = "$h_new" ]; then
    echo "key hash: match"
else
    echo "key hash: MISMATCH"
    exit 4
fi
unset h_live h_new

# 6. private boot check
name=hd-boot-$sha7
cleanup() { $DOCKER rm -f "$name" >/dev/null 2>&1 || true; }
trap cleanup EXIT
$DOCKER run -d --name "$name" --tmpfs /app/.data/postgres "$tag" >/dev/null || die "boot container did not start" 5
ready=""
waited=0
while [ "$waited" -lt "$TIMEOUT" ]; do
    ready=$($DOCKER logs "$name" 2>&1 | grep -m1 'Server ready in' || true)
    [ -n "$ready" ] && break
    sleep "$POLL"
    waited=$((waited + POLL))
done
[ -n "$ready" ] || die "boot check: no 'Server ready in' within ${TIMEOUT}s ($($DOCKER logs "$name" 2>&1 | tail -n 5 | tr '\n' ' '))" 5
echo "boot: ok ($ready)"

# 7. the record
seconds=$(( $(date +%s) - t0 ))
mkdir -p "$STATE/images"
python3 -I - "$STATE/images/$sha7.json" "$sha" "$tag" "$image_id" "$live_id" "$seconds" "$ready" "$batch" <<'EOF'
import json, sys
p, sha, tag, image_id, live_id, seconds, ready, batch = sys.argv[1:9]
rec = {"integration": sha, "image": tag, "image_id": image_id, "live_image_id": live_id, "key_hash": "match",
       "boot": "ok", "seconds": int(seconds), "ready_line": ready.strip(), "batch": batch}
with open(p, "w") as f:
    json.dump(rec, f, indent=1, sort_keys=True)
    f.write("\n")
EOF
echo "image: $tag ($image_id) recorded in $STATE/images/$sha7.json after ${seconds}s"
