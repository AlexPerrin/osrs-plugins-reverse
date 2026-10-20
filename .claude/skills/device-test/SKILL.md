---
name: device-test
description: "Test on the Android device (BlueStacks) against the OpenRune test server in a device slot, from taking the lock to handing the device back restored. Use for any check that needs the Android app."
---
# Device test (BlueStacks, adb 127.0.0.1:5555, app uid 10071)

1. **Slot.** Ask the orchestrator for a grant, then `tools/device-lock.sh take <holder> "<what>"`.
2. **Backup.** `tools/device-restore-check.sh pre /tmp/osrs-slot/<holder>` before touching anything.
3. **Set up.** Push test jars to the plugins dir; for the `osrs-devtools` MCP push `devtools.jar`,
   `adb shell setprop debug.osrsrl.devtools 1` and restart the app (docs/plugin-host.md).
4. **Sign in.** Screenshot first; tap only "Sign in with Email / Username" (LOCAL_AUTH). After a force-stop the
   server may still hold the session: wait 30-60 s, "Try again" (958,668) only on that screen.
5. **Test.** Drive it with `osrs-devtools` (load with ToolSearch) and read state from its data tools: menus
   (`get_npc_menu`, `get_widget_at`), widgets (`get_widget`, `dump_interface`), vars (`get_var`), chat
   (`get_chat_history`), NPCs and items (`list_*`). Raw `adb shell input` and screenshots are the fallback for what
   the tools can't reach (the native long-press menu: `input swipe x y x y 900`) and for drawing claims; confirm
   repeated chat lines by screenshot (`get_chat_history` under-reports repeats). `osrs-mcp` gives ids and game data.
6. **Restore.** Remove what you added BY NAME, each in a plain `adb -s 127.0.0.1:5555 shell rm <path>` call of its
   own (no wildcards, loops or `bash -c`); clear the debug props; log in at 3219,3222.
7. **Check.** `tools/device-restore-check.sh check /tmp/osrs-slot/<holder>` until every line is OK.
8. **Free.** `tools/device-lock.sh free <holder>`. The stop hook won't let you finish before 7 and 8.

Tester account: never `::empty` it, keep its GE offer (lobster), drop only items you spawned. More gotchas:
docs/client-state.md "Gotchas".

## Report
What was tested on which build, the result per item, and evidence paths (screenshots, logcat extracts).
