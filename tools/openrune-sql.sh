#!/usr/bin/env bash
# Runs SQL against the live OpenRune server's embedded PostgreSQL (database "postgres", user
# postgres), through a throwaway psql container on the server container's network. The port is
# read from the server log ("jdbc:postgresql://localhost:<port>"); it changes each server start.
#   tools/openrune-sql.sh "select realm_id, login_message from realms"
#   tools/openrune-sql.sh -f file.sql
# Settings read at login (e.g. realms.login_message) take effect at the next server start.
set -euo pipefail
CONTAINER=${OPENRUNE_CONTAINER:-osrs-openrune}
port=$(docker logs "$CONTAINER" 2>&1 | grep -oE 'jdbc:postgresql://localhost:[0-9]+' | tail -1 | grep -oE '[0-9]+$')
[[ -n $port ]] || { echo "no postgres port in $CONTAINER's log"; exit 1; }
if [[ ${1:-} == -f ]]; then
    exec docker run --rm -i --network "container:$CONTAINER" postgres:16-alpine \
        psql -h 127.0.0.1 -p "$port" -U postgres -d postgres -v ON_ERROR_STOP=1 < "${2:?file}"
fi
exec docker run --rm --network "container:$CONTAINER" postgres:16-alpine \
    psql -h 127.0.0.1 -p "$port" -U postgres -d postgres -v ON_ERROR_STOP=1 -c "${1:?sql}"
