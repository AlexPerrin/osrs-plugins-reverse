---
name: network-qa
description: "Run Network QA on a client build: DNS lookups, app sockets, expected hosts only, and the telemetry patch log. Use for every client release and every build that can change network behaviour."
---
# Network QA

Procedure, expected hosts and must-not-appear list: docs/app-telemetry.md "Network QA". Run it inside a
`device-test` slot.

When it's required: changes to `tools/repack.sh`, `patch_telemetry.py` or `patch_server.py`; a new APK or game
version; the sidecar or jav_config; host networking code; a hub plugin that makes network calls; every release.

1. `adb logcat -c`, then sample ESTABLISHED app-uid sockets from `/proc/net/tcp` and `tcp6` every 0.5 s.
2. Run the sign-out, LOCAL_AUTH sign-in, play, `::drop` relog, app restart and play sequence.
3. DNS: `adb logcat -d | grep -i "asked for"`.
4. Compare against the expected hosts; anything else, or anything on the must-not-appear list: stop and report
   at once.
5. Check the build's `repack.sh` log has the `telemetry:` lines.

## Report
The build, the time window, the DNS list and the distinct socket addresses, added under a dated heading in
docs/app-telemetry.md, plus PASS or FAIL.
