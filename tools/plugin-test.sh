#!/usr/bin/env bash
# Client/plugin-host test loop on BlueStacks (Windows adb from WSL):
#   tools/plugin-test.sh [N]   push re/host/plugins/*.jar (not *-desktop.jar), restart the game,
#                              tap Play Now, wait until logged in, print the first N host log lines
# Assumes a LOCAL_AUTH build is installed and signed in (docs/client-handoff.md).
set -u
ROOT=$(cd "$(dirname "$0")/.." && pwd)
ADB=${ADB:-/mnt/c/Users/Alex/AppData/Local/Android/Sdk/platform-tools/adb.exe}
S="-s ${ANDROID_SERIAL:-127.0.0.1:5555}"
PKG=com.jagex.oldscape.android
P=/sdcard/Android/data/$PKG/files/plugins

$ADB connect 127.0.0.1:5555 >/dev/null
$ADB $S logcat -G 16M
$ADB $S shell mkdir -p $P
$ADB $S shell "rm -f $P/*.jar"
for j in "$ROOT"/re/host/plugins/*.jar; do
    [[ $j == *-desktop.jar ]] || $ADB $S push "$(wslpath -w "$j")" $P/ >/dev/null
done
$ADB $S shell am force-stop $PKG
$ADB $S logcat -c
$ADB $S shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
timeout 90 bash -c "until $ADB $S logcat -d -s osrsrl | grep -qE 'plugin host (ready|failed)|jni: .*threw'; do sleep 2; done"
sleep 3
$ADB $S shell input tap 957 563   # Play Now
timeout 60 bash -c "until $ADB $S logcat -d | grep -q 'GameStateChanged 30\|game state -> LOGGED_IN'; do sleep 2; done" || true
$ADB $S logcat -d | grep -E "osrsrl-host|rl\.|System.err|FATAL" | head -${1:-40}
