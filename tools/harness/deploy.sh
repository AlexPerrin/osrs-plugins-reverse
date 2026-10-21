#!/usr/bin/env bash
# tools/harness/deploy.sh <batch>: deploy a train's image to the live test server (BR-B3), the daemon's step only.
#   1. harness/state/batches/<batch>.json names the integration sha; harness/state/images/<sha7>.json must say
#      key_hash match and boot ok (image.sh)
#   2. refuses (exit 75) unless every instance in tools/instances.tsv and `live-server` are leased by harnessd
#      (harness/state/leases/<resource>.json holder): the tick drains and takes them first (BR-R4)
#   3. rollback tag first: osrs-openrune:241 -> osrs-openrune:rollback-<live sha7> (live.json's integration, else
#      the live image id); then the new image -> osrs-openrune:241
#   4. `docker compose --env-file switch.env up -d --no-deps openrune` in openrune/, never --build
#   5. waits for "Server ready in" in the live container's log since the start, up to HD_DEPLOY_TIMEOUT s (300);
#      on timeout the rollback tag goes back to :241 and compose up runs again: result rollback, exit 1
#   6. record harness/state/deploys/<batch>.json {result: ok|rollback, image, rollback_tag, seconds, ready_line}
# Never prints or copies a key file. Env: HD_STATE_DIR, DOCKER or HD_DOCKER (tests), HD_OPENRUNE_DIR (openrune/),
# HD_INSTANCES (tools/instances.tsv), HD_DEPLOY_TIMEOUT, HD_POLL_SECONDS, HD_LIVE_CONTAINER (osrs-openrune),
# HD_LIVE_TAG (osrs-openrune:241).
# Exit: 0 deployed, 1 rolled back, 2 precondition, 75 the leases are not held.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
STATE=${HD_STATE_DIR:-$ROOT/harness/state}
DOCKER=${DOCKER:-${HD_DOCKER:-docker}}
OPENRUNE=${HD_OPENRUNE_DIR:-$ROOT/openrune}
INSTANCES=${HD_INSTANCES:-$ROOT/tools/instances.tsv}
TIMEOUT=${HD_DEPLOY_TIMEOUT:-300}
POLL=${HD_POLL_SECONDS:-5}
LIVE=${HD_LIVE_CONTAINER:-osrs-openrune}
LIVE_TAG=${HD_LIVE_TAG:-osrs-openrune:241}

batch=${1:?usage: deploy.sh <batch>}
die() { echo "deploy: $*" >&2; exit "${2:-2}"; }
jget() { python3 -I -c 'import json,sys; print(json.load(open(sys.argv[1])).get(sys.argv[2]) or "")' "$1" "$2"; }

# 1. the image record
rec=$STATE/batches/$batch.json
[ -f "$rec" ] || die "no batch record $rec"
sha=$(jget "$rec" integration)
[ -n "$sha" ] || die "batch $batch has no integration sha"
sha7=${sha:0:7}
img=$STATE/images/$sha7.json
[ -f "$img" ] || die "no image record $img (run image.sh first)"
[ "$(jget "$img" key_hash)" = match ] || die "image record $img: key hash is not a match"
[ "$(jget "$img" boot)" = ok ] || die "image record $img: boot check not ok"
tag=$(jget "$img" image)
[ -n "$tag" ] || die "image record $img names no image"

# 2. the leases: every instance and the live server, held by harnessd
missing=$(python3 -I - "$STATE/leases" "$INSTANCES" <<'EOF'
import json, os, sys
leases, instances = sys.argv[1:3]
names = []
try:
    with open(instances) as f:
        for line in f:
            if line.strip() and not line.startswith("#"):
                names.append(line.split("\t")[0].strip())
except OSError:
    pass
names.append("live-server")
missing = []
for n in names:
    try:
        with open(os.path.join(leases, n + ".json")) as f:
            holder = json.load(f).get("holder")
    except (OSError, ValueError):
        holder = None
    if holder != "harnessd":
        missing.append(n)
print(" ".join(missing))
EOF
)
[ -z "$missing" ] || die "not leased by harnessd: $missing (the tick drains and takes them first)" 75

# 3. rollback tag first
live_sha=$(jget "$STATE/live.json" integration 2>/dev/null || true)
if [ -n "$live_sha" ]; then
    rb=osrs-openrune:rollback-${live_sha:0:7}
else
    live_id=$($DOCKER inspect "$LIVE" --format '{{.Image}}' 2>/dev/null || true)
    rb=osrs-openrune:rollback-$(echo "${live_id#sha256:}" | cut -c1-12)
fi
t0=$(date +%s)
start=$(date -u +%Y-%m-%dT%H:%M:%SZ)
mkdir -p "$STATE/deploys"
record() {
    python3 -I - "$STATE/deploys/$batch.json" "$1" "$tag" "$rb" "$(( $(date +%s) - t0 ))" "$2" "$sha" <<'EOF'
import json, sys
p, result, image, rb, seconds, line, sha = sys.argv[1:8]
with open(p, "w") as f:
    json.dump({"result": result, "image": image, "rollback_tag": rb, "seconds": int(seconds),
               "ready_line": line.strip(), "integration": sha}, f, indent=1, sort_keys=True)
    f.write("\n")
EOF
}
echo "deploy: $tag -> $LIVE_TAG (rollback $rb)"
$DOCKER tag "$LIVE_TAG" "$rb" || die "rollback tag failed"
$DOCKER tag "$tag" "$LIVE_TAG" || die "retag failed"

# 4. compose up, never --build
compose_up() { (cd "$OPENRUNE" && $DOCKER compose --env-file switch.env up -d --no-deps openrune); }
compose_up || { $DOCKER tag "$rb" "$LIVE_TAG"; record rollback "compose up failed"; die "compose up failed; $rb retagged as $LIVE_TAG" 1; }

# 5. wait for the server
ready=""
waited=0
while [ "$waited" -lt "$TIMEOUT" ]; do
    ready=$($DOCKER logs --since "$start" "$LIVE" 2>&1 | grep -m1 'Server ready in' || true)
    [ -n "$ready" ] && break
    sleep "$POLL"
    waited=$((waited + POLL))
done
if [ -z "$ready" ]; then
    echo "deploy: no 'Server ready in' within ${TIMEOUT}s; rolling back to $rb" >&2
    $DOCKER logs --since "$start" "$LIVE" 2>&1 | tail -n 20 >&2 || true
    $DOCKER tag "$rb" "$LIVE_TAG"
    compose_up || true
    record rollback "timeout after ${TIMEOUT}s"
    exit 1
fi

# 6. the record
record ok "$ready"
echo "deploy: ok ($ready) after $(( $(date +%s) - t0 ))s; rollback $rb"
