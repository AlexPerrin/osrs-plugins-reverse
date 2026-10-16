#!/bin/sh
# Runs OpenRune from /app, the directory it resolves game.yml, .data/ and plugins/ against.
set -eu
cd /app
exec /app/server/bin/app "$@"
