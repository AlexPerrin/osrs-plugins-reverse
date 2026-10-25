#!/usr/bin/env bash
# End-of-slot restore check for the Android device (BlueStacks 127.0.0.1:5555), so the restore
# checklist is a check rather than prose (docs/workflow.md "State docs").
#   tools/device-restore-check.sh pre <dir>     at the start of a slot: saves settings.properties
#                                               (exec-out cat, byte-exact) and the files/ tree listing
#   tools/device-restore-check.sh check <dir>   at the end: prints one line per item, OK or OFF, and
#                                               exits 1 if anything is off
# Read-only on the device (it never removes or changes anything; fix what it reports by hand,
# test jars by name).
set -u
ADB=${ADB:-/mnt/c/Users/Alex/AppData/Local/Android/Sdk/platform-tools/adb.exe}
S="-s ${ANDROID_SERIAL:-127.0.0.1:5555}"
F=/sdcard/Android/data/com.jagex.oldscape.android/files
DEFAULT_PLUGINS="grounditems.jar opponentinfo.jar quest-helper.jar"
# Debug props that must be empty after a slot (devtools/soundlog may be 0 or empty).
EMPTY_PROPS="netallow probe set where say wdump keylog projlog camlog selflog tmodelog netlog netwrite agent"
ZERO_PROPS="devtools soundlog"
HOME_TILE="3219,3222"

mode=${1:-}; dir=${2:-}
[[ ( $mode == pre || $mode == check ) && -n $dir ]] || { echo "usage: $0 pre|check <dir>" >&2; exit 2; }
mkdir -p "$dir"

tree() {  # files/ tree, plugin data included; names only (sizes change with caches)
    $ADB $S shell "cd $F && find . -mindepth 1 -not -path './.runelite/cache/*' | sort" | tr -d '\r'
}

if [[ $mode == pre ]]; then
    $ADB $S exec-out cat $F/settings.properties > "$dir/settings.properties"
    tree > "$dir/tree.txt"
    echo "saved $(md5sum < "$dir/settings.properties" | cut -c1-32) settings.properties and $(wc -l < "$dir/tree.txt") paths to $dir"
    exit 0
fi

bad=0
ok()  { echo "OK   $*"; }
off() { echo "OFF  $*"; bad=1; }

# 1. Default plugin set, nothing else.
got=$($ADB $S shell ls $F/plugins | tr -d '\r' | sort | tr '\n' ' ' | sed 's/ $//')
want=$(tr ' ' '\n' <<<"$DEFAULT_PLUGINS" | sort | tr '\n' ' ' | sed 's/ $//')
[[ $got == "$want" ]] && ok "plugins: $got" || off "plugins: '$got' (want '$want'; remove extras by name)"

# 2. Debug props.
props_ok=1
for p in $EMPTY_PROPS; do
    v=$($ADB $S shell getprop debug.osrsrl.$p | tr -d '\r')
    [[ -z $v ]] || { off "debug.osrsrl.$p = '$v' (clear it)"; props_ok=0; }
done
for p in $ZERO_PROPS; do
    v=$($ADB $S shell getprop debug.osrsrl.$p | tr -d '\r')
    [[ -z $v || $v == 0 ]] || { off "debug.osrsrl.$p = '$v' (set 0)"; props_ok=0; }
done
[[ $props_ok == 1 ]] && ok "debug props cleared"

# 3. settings.properties byte-identical to the pre-slot copy.
$ADB $S exec-out cat $F/settings.properties > "$dir/settings.after"
if cmp -s "$dir/settings.properties" "$dir/settings.after"; then ok "settings.properties identical"
else off "settings.properties differs from $dir/settings.properties (push the backup back)"; diff "$dir/settings.properties" "$dir/settings.after" | head -10; fi

# 4. No new files (plugin data folders, sound caches, test jars).
tree > "$dir/tree.after"
new=$(comm -13 "$dir/tree.txt" "$dir/tree.after")
gone=$(comm -23 "$dir/tree.txt" "$dir/tree.after")
[[ -z $new ]] && ok "no new files under files/" || { off "new paths under files/ (remove by name):"; sed 's/^/       /' <<<"$new" | head -20; }
[[ -z $gone ]] || { off "paths missing since pre:"; sed 's/^/       /' <<<"$gone" | head -20; }

# 5. Logged in (last GameStateChanged in the host log).
st=$($ADB $S logcat -d -s osrsrl-host | grep -o 'GameStateChanged [A-Z_]*' | tail -1 | cut -d' ' -f2)
[[ $st == LOGGED_IN ]] && ok "game state LOGGED_IN" || off "game state '${st:-unknown}' (log in; the logcat may have been cleared)"

# 6. Sidecar: no LOCAL_AUTH user override left.
if docker exec osrs-openrune-http test -e /tmp/local-auth-user 2>/dev/null; then off "/tmp/local-auth-user exists in osrs-openrune-http"
else ok "/tmp/local-auth-user absent"; fi

echo "MANUAL player at $HOME_TILE (devtools get_client_state, or the overlay's 'player world' line); 1 coin pouch, 100,289 coins, GE lobster offer kept; inventory slot 15 empty (the gen 44 Fish barrel was destroyed in hub-fish-barrel-check)"
echo $bad > "$dir/result"   # 0 = all OK
exit $bad
