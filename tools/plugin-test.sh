#!/usr/bin/env bash
# Client/plugin-host test loop on BlueStacks (Windows adb from WSL):
#   tools/plugin-test.sh [N]   push re/host/plugins/*.jar (not *-desktop.jar), restart the game,
#                              tap Play Now, wait until logged in, print the first N host log lines
#   EXTRA="xpdrop statusbars"  also push re/host/plugins-extra/<name>.jar
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
for n in ${EXTRA:-}; do
    $ADB $S push "$(wslpath -w "$ROOT/re/host/plugins-extra/$n.jar")" $P/ >/dev/null
done
$ADB $S shell am force-stop $PKG
$ADB $S logcat -c
$ADB $S shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
timeout 90 bash -c "until $ADB $S logcat -d -s osrsrl | grep -qE 'plugin host (ready|failed)|jni: .*threw'; do sleep 2; done"
# Tap Play Now only after a screenshot shows the signed-in "Play Now / Tester" screen. Never tap
# anything else on the login screens: a signed-out app shows the sign-in chooser, where this tap
# would hit "Sign in with Google" (Google/Jagex account screens must never be touched). Sign in
# by hand with "Sign in with Email / Username" (LOCAL_AUTH builds) and rerun.
PY=${PY:-$HOME/tools/venv/bin/python}
SHOT=$(mktemp --suffix=.png)
screen=unknown
for _ in $(seq 1 20); do
    sleep 2
    $ADB $S exec-out screencap -p > "$SHOT" 2>/dev/null || continue
    screen=$("$PY" -I - "$SHOT" <<'PYEOF'
import sys
from PIL import Image
im = Image.open(sys.argv[1]).convert('RGB')
if im.size != (1920, 1080): print('unknown'); sys.exit()
def count(x0, y0, x1, y1, f):
    return sum(1 for x in range(x0, x1, 2) for y in range(y0, y1, 2) if f(*im.getpixel((x, y))))
yellow = count(880, 575, 1040, 615, lambda r, g, b: r > 200 and g > 180 and b < 90)        # "Tester"
google = count(680, 510, 730, 565, lambda r, g, b: r > 230 and g > 230 and b > 230)        # Google logo tile
play = count(870, 515, 1050, 555, lambda r, g, b: r > 230 and g > 230 and b > 230)         # "Play Now"
print('chooser' if google > 100 else 'playnow' if yellow > 40 and play > 250 else 'other')
PYEOF
)
    [[ $screen == playnow || $screen == chooser ]] && break
done
rm -f "$SHOT"
if [[ $screen != playnow ]]; then
    echo "plugin-test: not on the signed-in Play Now screen ($screen); not tapping. If signed out, use"
    echo "  'Sign in with Email / Username' only (never Google/Jagex screens), then rerun."
    exit 2
fi
$ADB $S shell input tap 957 563   # Play Now
timeout 60 bash -c "until $ADB $S logcat -d | grep -q 'GameStateChanged 30\|game state -> LOGGED_IN'; do sleep 2; done" || true
$ADB $S logcat -d | grep -E "osrsrl-host|rl\.|System.err|FATAL" | head -${1:-40}
