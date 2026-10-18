#!/usr/bin/env bash
# Start/stop the isolated desktop A/B client (RuneLite through headless RSProx, to OpenRune) from WSL.
#   tools/multiclient/desktop.sh start     # headless RSProx -> RuneLite "OpenRune Test"
#   tools/multiclient/desktop.sh status    # our processes (executable under C:\osrs-test) + their sockets
#   tools/multiclient/desktop.sh log [n]   # tail of the RSProx/launcher log
#   tools/multiclient/desktop.sh login [user pass]  # Existing User login (default desktop/desktop;
#                                         if another account is online, it is logged out first)
#   tools/multiclient/desktop.sh relogin [user pass]  # refill the open classic form after a failure
#   tools/multiclient/desktop.sh logout    # in-game logout (stop does it first)
#   tools/multiclient/desktop.sh stop      # kills ONLY processes whose executable is under C:\osrs-test
# Isolation: JAVA_TOOL_OPTIONS=-Duser.home=C:\osrs-test\home is set only in the environment of the
# PowerShell that starts RSProx (never user- or machine-wide); RSProx, the RuneLite launcher and the
# client inherit it. Stage first with tools/multiclient/desktop-build.sh. See docs/multi-client.md.
set -euo pipefail
PS="powershell.exe -NoProfile -NonInteractive -Command"
JAVA='C:\osrs-test\jdk\bin\java.exe'
OURS="\$_.ExecutablePath -like 'C:\\osrs-test\\*'"

ours_ps() {  # PowerShell expression listing our processes
  echo "Get-CimInstance Win32_Process | Where-Object { $OURS }"
}

case "${1:-}" in
start)
  if [ -n "$($PS "$(ours_ps) | Select-Object -ExpandProperty ProcessId" | tr -d '\r')" ]; then
    echo "already running:"; exec "$0" status
  fi
  # Ports our client binds (RSProx HTTP 43600+, proxy 43701+, devtools 7780) must be free.
  busy="$(netstat.exe -ano | tr -d '\r' | awk '$4=="LISTENING"{print $2}' | grep -E ':(7780|4360[0-9]|4370[0-9]|4371[0-9])$' || true)"
  if [ -n "$busy" ]; then echo "ports in use, not starting: $busy" >&2; exit 1; fi
  ts=$(date +%Y%m%d-%H%M%S)
  rm -f /mnt/c/osrs-test/rsprox.pid
  # The interop pipe stays open while the Windows child lives, so background the PowerShell.
  nohup $PS "\$env:JAVA_TOOL_OPTIONS='-Duser.home=C:\\osrs-test\\home -Dosrstest.marker=osrs-test-desktop';
    Remove-Item Env:JX_* -ErrorAction SilentlyContinue;
    \$p = Start-Process -FilePath '$JAVA' -WorkingDirectory 'C:\\osrs-test' -WindowStyle Hidden -PassThru \`
      -ArgumentList '-Dosrstest.marker=osrs-test-desktop','-cp','C:\\osrs-test\\rsprox\\launcher;C:\\osrs-test\\rsprox\\lib\\*','OsrsTestLauncher' \`
      -RedirectStandardOutput 'C:\\osrs-test\\logs\\rsprox-$ts.out' -RedirectStandardError 'C:\\osrs-test\\logs\\rsprox-$ts.err';
    \$p.Id | Out-File -Encoding ascii C:\\osrs-test\\rsprox.pid; 'started RSProx pid ' + \$p.Id" >/dev/null 2>&1 </dev/null &
  disown
  for _ in $(seq 30); do [ -s /mnt/c/osrs-test/rsprox.pid ] && break; sleep 1; done
  echo "started RSProx pid $(tr -d '\r\n' < /mnt/c/osrs-test/rsprox.pid 2>/dev/null || echo '?')"
  echo "log: /mnt/c/osrs-test/logs/rsprox-$ts.{out,err}"
  ;;
status)
  $PS "\$ids = @($(ours_ps) | ForEach-Object { \$_.ProcessId });
    $(ours_ps) | Select-Object ProcessId,ParentProcessId,@{n='Cmd';e={\$_.CommandLine.Substring(0,[Math]::Min(160,\$_.CommandLine.Length))}} | Format-Table -AutoSize -Wrap | Out-String -Width 250;
    if (\$ids.Count) { Get-NetTCPConnection -OwningProcess \$ids -ErrorAction SilentlyContinue | Where-Object { \$_.State -ne 'Bound' -and -not (\$_.RemoteAddress -eq '127.0.0.1' -and \$_.LocalPort -ge 49152 -and \$_.RemotePort -ge 49152) } | Select-Object OwningProcess,State,LocalAddress,LocalPort,RemoteAddress,RemotePort | Sort-Object OwningProcess,State | Format-Table -AutoSize | Out-String -Width 200 } else { 'not running' }"
  ;;
log)
  f=$(ls -t /mnt/c/osrs-test/logs/rsprox-*.out 2>/dev/null | head -1); [ -n "$f" ] || { echo "no log"; exit 1; }
  tail -n "${2:-40}" "$f"; e="${f%.out}.err"; [ -s "$e" ] && { echo "--- stderr"; tail -n "${2:-40}" "$e"; } || true
  ;;
login|relogin)
  # Fixed-mode canvas (765x503) clicks through the devtools bridge. login starts on the "Welcome"
  # screen (Existing User); relogin refills the classic form that stays open after a failure.
  # Retries for ~2 min: after a logout or a killed session, OpenRune refuses the account for a while
  # (CONNECT_FAIL 50 / "already logged in" 5), and some failures drop back to the Welcome screen.
  M="$(dirname "$(readlink -f "$0")")/desktop-devtools-mcp.sh"
  state() { "$M" --tool get_client_state '{}' | grep -o 'gameState[^,]*' | grep -o '[A-Z_]\{4,\}'; }
  clear() { for _ in $(seq 24); do "$M" --tool press_key '{"key":"BACKSPACE"}' >/dev/null; done; }
  existing() {
    "$M" --tool click '{"x":460,"y":290}' >/dev/null; sleep 1   # "Existing User"
    # The Jagex-account upsell follows the first "Existing User" of each client run only. Its "Login"
    # (never "Download Launcher") sits where the form's "Cancel" is, so click it once per run.
    local flag="/mnt/c/osrs-test/upsell-seen-$(tr -d '\r\n' < /mnt/c/osrs-test/rsprox.pid 2>/dev/null)"
    if [ ! -e "$flag" ]; then "$M" --tool click '{"x":462,"y":321}' >/dev/null; sleep 1; touch "$flag"; fi
  }
  fill() {
    "$M" --tool click '{"x":360,"y":248}' >/dev/null; sleep 0.5; clear   # "Login:" field
    "$M" --tool type_chat "{\"text\":\"$user\",\"send\":false}" >/dev/null
    "$M" --tool click '{"x":360,"y":263}' >/dev/null; sleep 0.5; clear   # "Password:" field
    "$M" --tool type_chat "{\"text\":\"$pass\",\"send\":true}" >/dev/null
    for _ in $(seq 12); do [ "$(state)" = LOGGED_IN ] && return 0; sleep 1; done; return 1
  }
  user="${2:-desktop}"; pass="${3:-desktop}"
  # The account last logged in by this script, kept for reference; the check uses the local player.
  acct=/mnt/c/osrs-test/desktop-account
  me() { "$M" --tool list_players '{}' | python3 -c 'import sys,json
t=json.loads(json.load(sys.stdin)["result"]["content"][0]["text"])
print(next((p["name"] for p in t["players"] if p.get("isLocal")), ""))' 2>/dev/null || true; }
  s="$(state)"
  if [ "$s" = LOGGED_IN ]; then
    cur="$(me)"
    [ "$cur" = "$user" ] && { echo "already logged in as $user"; exit 0; }
    echo "logged in as '${cur:-unknown}', not $user: logging out first" >&2
    "$0" logout >/dev/null; rm -f "$acct"; sleep 5; s="$(state)"
  fi
  [ "$s" = LOGIN_SCREEN ] || { echo "not on the login screen: $s" >&2; exit 1; }
  [ "$1" = login ] && existing
  for try in 1 2 3 4 5 6; do
    fill && break
    sleep 8; existing; fill && break   # back on Welcome after some failures
    echo "login attempt $try refused; retrying" >&2; sleep 10
  done
  [ "$(state)" = LOGGED_IN ] && printf '%s\n' "$user" > "$acct"
  "$M" --tool get_client_state '{}'
  ;;
logout)  # fixed mode: Logout tab, then "Click here to logout" (avoids the server's duplicate-login hold)
  M="$(dirname "$(readlink -f "$0")")/desktop-devtools-mcp.sh"
  "$M" --tool get_client_state '{}' 2>/dev/null | grep -q LOGGED_IN || { echo "not logged in"; exit 0; }
  "$M" --tool press_key '{"key":"ESCAPE"}' >/dev/null
  "$M" --tool click '{"x":642,"y":483}' >/dev/null; sleep 1   # Logout tab
  "$M" --tool click '{"x":641,"y":423}' >/dev/null; sleep 3   # Click here to logout
  rm -f /mnt/c/osrs-test/desktop-account
  "$M" --tool get_client_state '{}' | grep -o 'gameState[^,]*'
  ;;
stop)
  "$0" logout 2>/dev/null || true
  $PS "$(ours_ps) | ForEach-Object { 'stopping ' + \$_.ProcessId; Stop-Process -Id \$_.ProcessId -Force -ErrorAction SilentlyContinue }"
  rm -f /mnt/c/osrs-test/rsprox.pid
  ;;
*) sed -n 2,12p "$0"; exit 1 ;;
esac
