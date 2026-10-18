#!/usr/bin/env bash
# QA only: makes one account's characters members on the throwaway QA branch server (qa-or-server,
# started by qa.sh), so members content (rune pouch, cannon, plank sack...) can be checked in game.
# Upstream has no command for this and new accounts default to account_characters.members = false.
# It never touches the live server (osrs-openrune). Approved by the user on 2026-10-18.
#   openrune/upstream/qa/qa-members.sh <account_name>
set -euo pipefail
CONTAINER=qa-or-server
name=${1:?account name}
[[ $name =~ ^[a-z0-9_]{1,12}$ ]] || { echo "qa-members: bad account name '$name'" >&2; exit 2; }
docker inspect "$CONTAINER" >/dev/null 2>&1 || { echo "qa-members: $CONTAINER is not running" >&2; exit 1; }
port=$(docker logs "$CONTAINER" 2>&1 | grep -oE 'jdbc:postgresql://localhost:[0-9]+' | tail -1 | grep -oE '[0-9]+$')
[[ -n $port ]] || { echo "qa-members: no postgres port in $CONTAINER's log" >&2; exit 1; }
exec docker run --rm --network "container:$CONTAINER" postgres:16-alpine \
    psql -h 127.0.0.1 -p "$port" -U postgres -d postgres -v ON_ERROR_STOP=1 -c \
    "update account_characters set members = true
       where account_id = (select id from accounts where lower(account_name) = '$name')
     returning id, display_name, members"
