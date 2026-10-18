#!/usr/bin/env bash
# Actions on our own fork (AlexPerrin/OpenRune-Server) only; it refuses anything else. Allowed
# without confirmation in .claude/settings.local.json. Anything upstream (OpenRune/*) goes through
# plain gh/git commands, which prompt.
#   tools/fork.sh push <branch> [--force-with-lease]
#   tools/fork.sh pr-create <branch> <title-file> <body-file>     (base main; prints the PR URL)
#   tools/fork.sh pr-edit <number> <body-file> [title-file]
#   tools/fork.sh pr-comment <number> <body-file>
#   tools/fork.sh sync                                            (fast-forward the fork's main to
#                                                                  upstream main; never forces)
set -euo pipefail
FORK=AlexPerrin/OpenRune-Server
CLONE=$(dirname "$0")/../re/upstream-pr/or
die() { echo "fork.sh: $*" >&2; exit 2; }
branch_ok() { [[ $1 =~ ^[A-Za-z0-9._/-]+$ && $1 != -* && $1 != main ]] || die "bad branch '$1'"; }
num_ok() { [[ $1 =~ ^[0-9]+$ ]] || die "bad PR number '$1'"; }
file_ok() { [[ -f $1 ]] || die "no file '$1'"; }

url=$(git -C "$CLONE" remote get-url fork)
[[ $url =~ [:/]$FORK(\.git)?$ ]] || die "remote 'fork' is $url, not $FORK"

cmd=${1:-}; shift || true
case $cmd in
push)
    branch_ok "${1:?branch}"
    extra=()
    [[ ${2:-} == --force-with-lease ]] && extra=(--force-with-lease)
    [[ -z ${2:-} || ${2:-} == --force-with-lease ]] || die "unknown option '$2'"
    exec git -C "$CLONE" push "${extra[@]}" fork "refs/heads/$1:refs/heads/$1" ;;
pr-create)
    branch_ok "${1:?branch}"; file_ok "${2:?title-file}"; file_ok "${3:?body-file}"
    exec gh pr create -R "$FORK" --base main --head "$1" --title "$(cat "$2")" --body-file "$3" ;;
pr-edit)
    num_ok "${1:?number}"; file_ok "${2:?body-file}"
    args=(-F "body=@$2")
    [[ -n ${3:-} ]] && { file_ok "$3"; args+=(-f "title=$(cat "$3")"); }
    exec gh api -X PATCH "repos/$FORK/pulls/$1" "${args[@]}" --jq .html_url ;;
pr-comment)
    num_ok "${1:?number}"; file_ok "${2:?body-file}"
    exec gh api -X POST "repos/$FORK/issues/$1/comments" -F "body=@$2" --jq .html_url ;;
sync)
    [[ -z ${1:-} ]] || die "sync takes no arguments"
    gh repo sync "$FORK" --source OpenRune/OpenRune-Server --branch main
    exec gh api "repos/$FORK/commits/main" --jq '.sha[0:9] + " " + (.commit.message | split("\n")[0])' ;;
*)
    die "usage: push|pr-create|pr-edit|pr-comment|sync (see header)" ;;
esac
