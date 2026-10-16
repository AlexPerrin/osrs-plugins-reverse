#!/bin/sh
# OpenRune spike: runs OpenRune's Gradle wrapper in Docker (eclipse-temurin:21) as uid 1000, never on
# the host. Usage: tools/openrune/gradle.sh <gradle args...>   (cache in volume openrune-gradle)
set -eu
R="$(cd "$(dirname "$0")/../../re/openrune" && pwd)"
docker run --rm --name openrune-spike-gradle -u 1000:1000 -e HOME=/tmp -e GRADLE_USER_HOME=/gradle \
    -v openrune-gradle:/gradle -v "$R":/w -w /w eclipse-temurin:21 ./gradlew --no-daemon --console=plain "$@"
