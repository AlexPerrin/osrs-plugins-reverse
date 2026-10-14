#!/bin/sh
# Manages the vendored RSProt build (server/rsprot, gitignored): a pinned upstream commit plus
# server/rsprot.patch (the Android module and the API registrations).
#   scripts/rsprot.sh init     clone the pinned commit and apply rsprot.patch
#   scripts/rsprot.sh export   write the working copy's changes back to rsprot.patch
set -eu
cd "$(dirname "$0")/.."
REPO=https://github.com/blurite/rsprot.git
COMMIT=4ec57fd7a28e16e9ae207bd1f02b7f343bb77957

case "${1:-}" in
init)
    [ -d rsprot ] && { echo "rsprot/ already exists"; exit 1; }
    git clone -q "$REPO" rsprot
    git -C rsprot checkout -q "$COMMIT"
    git -C rsprot apply ../rsprot.patch
    echo "rsprot/ ready at $COMMIT + rsprot.patch"
    ;;
export)
    git -C rsprot add -A -N .
    git -C rsprot diff "$COMMIT" > rsprot.patch
    echo "wrote rsprot.patch ($(grep -c '^diff --git' rsprot.patch) files)"
    ;;
*)
    echo "usage: $0 init|export" >&2
    exit 2
    ;;
esac
