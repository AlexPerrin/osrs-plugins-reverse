#!/bin/sh
# Recreates the `integration` branch: `main` plus every branch in integration.txt, merged in that
# order. integration.txt, the pins (integration.lock) and the recorded conflict resolutions
# (scripts/integration-rr-cache/, git rerere) are read from the local/integration-tools branch, not
# from the working tree.
#
# Pins: integration.lock holds `<branch> <sha>` lines, the commit of each branch that was handed
# over and checked. A pinned branch is merged at its pin, not at its tip, so a later push to the
# branch isn't taken until it is handed over again. A branch without a pin (or whose pin is not in
# the repository) is merged at its tip with a warning; local/integration-tools itself is never
# pinned (it holds the pins).
#
#   scripts/build-integration.sh [remote]   remote whose branches are merged (default: origin;
#                                           "." merges local branches)
#
# A conflict without a recorded resolution stops the build with a non-zero exit and lists the
# files. To record one: resolve it in the stopped merge, `git add` the files, `git commit`, then
# copy the new directories from .git/rr-cache/ into scripts/integration-rr-cache/ on
# local/integration-tools and commit them there.
set -eu
remote=${1:-origin}
if [ "$remote" = "." ]; then prefix=""; else prefix="$remote/"; git fetch -q "$remote"; fi
cd "$(git rev-parse --show-toplevel)"
if [ -n "$(git status --porcelain --untracked-files=no)" ]; then
    echo "build-integration: the working tree has changes; commit or stash them first" >&2
    exit 1
fi
tools="${prefix}local/integration-tools"
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
git show "$tools:integration.txt" > "$tmp/list"
git show "$tools:integration.lock" > "$tmp/pins" 2>/dev/null || : > "$tmp/pins"
gitdir=$(git rev-parse --git-common-dir)   # rerere reads the common dir, also in a worktree
mkdir -p "$gitdir/rr-cache"
if git cat-file -e "$tools:scripts/integration-rr-cache" 2>/dev/null; then
    git archive "$tools" scripts/integration-rr-cache | tar -x -C "$tmp"
    cp -R "$tmp/scripts/integration-rr-cache/." "$gitdir/rr-cache/"
fi
git checkout -q -B integration "${prefix}main"
grep -v '^[[:space:]]*\(#\|$\)' "$tmp/list" > "$tmp/branches"
while read -r branch; do
    pin=$(awk -v b="$branch" '$1 == b { print $2; exit }' "$tmp/pins")
    tip=$(git rev-parse -q --verify "$prefix$branch^{commit}" || true)
    if [ -n "$pin" ] && git cat-file -e "$pin^{commit}" 2>/dev/null; then
        rev=$pin
        at=" at pin $(git rev-parse --short "$pin")"
        if [ -n "$tip" ] && [ "$(git rev-parse "$pin")" != "$tip" ]; then
            at="$at (tip $(git rev-parse --short "$tip") not taken)"
        fi
    else
        rev=$prefix$branch
        at=""
        if [ -n "$pin" ]; then
            echo "build-integration: warning: pin $pin of $branch is not in the repository; merging the tip" >&2
        elif [ "$branch" != local/integration-tools ]; then
            echo "build-integration: warning: $branch has no pin in integration.lock; merging the tip" >&2
        fi
    fi
    if git -c rerere.enabled=true -c rerere.autoupdate=true \
        merge -q --no-ff --no-edit -m "Merge $branch into integration" "$rev" >/dev/null 2>&1; then
        echo "merged $branch$at"
        continue
    fi
    if ! git rev-parse -q --verify MERGE_HEAD >/dev/null; then
        echo "build-integration: merging $branch failed before any conflict (untracked files in" >&2
        echo "the way, or a missing branch?); run the merge by hand to see why." >&2
        exit 1
    fi
    unresolved=$(git diff --name-only --diff-filter=U)
    if [ -n "$unresolved" ]; then
        echo "build-integration: UNRECORDED CONFLICT merging $branch in:" >&2
        echo "$unresolved" | sed 's/^/  /' >&2
        echo "Resolve, git add, git commit, then store .git/rr-cache as described in this script." >&2
        exit 1
    fi
    git commit -q --no-edit
    echo "merged $branch$at (recorded resolution)"
done < "$tmp/branches"
echo "integration = $(git rev-parse --short HEAD)"
