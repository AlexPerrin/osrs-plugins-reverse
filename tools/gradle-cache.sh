#!/usr/bin/env bash
# Shared Gradle remote build cache for the build containers: Gradle's build cache node
# (gradle/build-cache-node, pinned) as one persistent local container. Safe for concurrent builds
# (it's an HTTP server; unlike a shared Gradle home volume, nothing locks across containers).
#   tools/gradle-cache.sh start     create the network, volume and node (idempotent)
#   tools/gradle-cache.sh status    container state, /ping, cache size
#   tools/gradle-cache.sh stop      stop + remove the node container (keeps the volume = the cache)
#   tools/gradle-cache.sh args      the docker run args a build container adds (network)
#   tools/gradle-cache.sh init      host path of the Gradle init script (mount it, pass -I)
# Build containers join the docker network `gradle-cache` and reach the node as
# http://gradle-cache-node:5071/cache/ (tools/gradle/remote-cache.init.gradle.kts). The node is
# published on 127.0.0.1:5071 only (ping/health from the host); anonymous read-write, UI disabled,
# no secrets: localhost and the private docker network only. Target size 20 GiB (LRU eviction).
set -euo pipefail
IMAGE=gradle/build-cache-node:21.2   # released 2025-06-06; config schema version 5
NAME=gradle-cache-node
NET=gradle-cache
VOL=gradle-build-cache
PORT=${GRADLE_CACHE_PORT:-5071}
HERE=$(cd "$(dirname "$0")" && pwd)

case "${1:-}" in
start)
  docker network inspect $NET >/dev/null 2>&1 || docker network create $NET >/dev/null
  if docker ps --format '{{.Names}}' | grep -qx $NAME; then echo "$NAME already running"; exit 0; fi
  docker rm -f $NAME >/dev/null 2>&1 || true
  docker volume inspect $VOL >/dev/null 2>&1 || docker volume create $VOL >/dev/null
  # The node rewrites conf/config.yaml (it must be writable), so it is seeded into the volume.
  docker run --rm -i -v $VOL:/data alpine:3.20 sh -c '
    mkdir -p /data/conf; cat > /data/conf/config.yaml; chown -R 1000:1000 /data' <<'YAML'
version: 5
cache:
  targetSize:
    type: fixed
    size: 20000
  freeSpaceBufferSize: 2000
  maxArtifactSize: 100
  accessControl:
    anonymousLevel: "readwrite"
uiAccess:
  type: "disabled"
YAML
  # About 1.5 GiB by default; one machine's builds need far less.
  docker run -d --name $NAME --restart unless-stopped --user 1000:1000 \
    --network $NET --memory=1g --cpus=2 \
    -e JAVA_OPTS="-Xms256m -Xmx512m -XX:MaxDirectMemorySize=256m" \
    -v $VOL:/data -p 127.0.0.1:$PORT:5071 \
    $IMAGE start --no-warn-anon-cache-write >/dev/null
  for _ in $(seq 60); do
    docker run --rm --network $NET alpine:3.20 wget -q -O- http://$NAME:5071/ping >/dev/null 2>&1 \
      && { echo "$NAME up ($IMAGE, http://$NAME:5071/cache/ on network $NET)"; exit 0; }
    sleep 2
  done
  docker logs --tail 40 $NAME; exit 1
  ;;
status)
  docker ps -a --filter name=^/$NAME$ --format '{{.Names}} {{.Image}} {{.Status}}'
  docker run --rm --network $NET alpine:3.20 sh -c "wget -q -O- http://$NAME:5071/ping && echo ' ping ok'" 2>&1 || true
  docker run --rm -v $VOL:/data:ro alpine:3.20 du -sh /data 2>/dev/null | sed 's/^/volume /'
  ;;
stop) docker rm -f $NAME ;;
args) echo "--network $NET" ;;
init) echo "$HERE/gradle/remote-cache.init.gradle.kts" ;;
*) sed -n 2,14p "$0"; exit 1 ;;
esac
