#!/bin/sh
# BlueStacks helpers for testing the server (Windows adb from WSL). Coordinates are 1920x1080.
#   tools/device.sh login        after a container restart: Try again, Play Now; waits for the server log
#   tools/device.sh cmd '::pos'  types a chat command (spaces ok) and submits it
#   tools/device.sh shot FILE    screenshot to FILE
set -eu
ADB=${ADB:-/mnt/c/Users/Alex/AppData/Local/Android/Sdk/platform-tools/adb.exe}
S="-s ${ANDROID_SERIAL:-127.0.0.1:5555}"
case "$1" in
login)
    since=$(date -u +%Y-%m-%dT%H:%M:%SZ)
    $ADB $S shell input tap 958 668; sleep 3     # "Try again" (or no-op on the Play Now screen)
    $ADB $S shell input tap 958 550              # "Play Now"
    for _ in $(seq 1 30); do
        docker logs --since "$since" "${GAME_CONTAINER:-osrs-openrune}" 2>&1 | grep -qE "logged in as|Token login .* mapped to local account" && { echo "logged in"; exit 0; }
        sleep 1
    done
    echo "login not seen in server log" >&2; exit 1 ;;
cmd)
    text=$(printf %s "$2" | sed 's/ /%s/g')
    $ADB $S shell input text "$text"; sleep 0.5
    # keyevents only register while the soft keyboard is shown; the keyboard button toggles it
    for _ in 1 2; do
        $ADB $S shell dumpsys input_method | grep -q "mInputShown=true" && break
        $ADB $S shell input tap 90 220; sleep 1.5
    done
    $ADB $S shell input keyevent 66 ;;
shot)
    $ADB $S exec-out screencap -p > "$2" ;;
*) echo "usage: $0 login|cmd TEXT|shot FILE" >&2; exit 2 ;;
esac
