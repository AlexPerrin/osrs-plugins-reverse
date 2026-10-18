---
name: ab-test
description: "Compare the same plugin or feature on desktop RuneLite and the Android app, or on a branch server and main. Use when behaviour or drawing must match between two clients or two servers."
---
# A/B test

Procedure: docs/multi-client.md "A/B recipe (desktop vs Android)". Desktop: the isolated RuneLite
(`tools/multiclient/desktop.sh start && desktop.sh login`, profile under C:\osrs-test only), driven with the
`osrs-devtools-desktop` MCP under `LOCK_NAME=desktop tools/device-lock.sh`. Android: the `device-test` skill,
driven with `osrs-devtools`.

1. Put both sides in the same state with the same commands (`::quest`, `::item`, `::tele`).
2. Install the plugin: the hub build on desktop (by config, never sideloaded), the host build on Android.
   Record both versions.
3. Step in lockstep; at each step capture `screenshot`, the plugin's widgets, the quest varps (`get_var`) and the
   highlight targets on both.
4. Server-vs-server: the same steps on desktop against the branch server (spare ports) and against main.

## Report
A table of steps with what each side showed, the differences, and which side matches OSRS. Evidence paths.
