# QA: client/gen41-recheck
- Branch: `client/gen41-recheck`
- Commit: `e9993ad28ed8df86d8e40b03c36257838f4f5214`
## Verdict: **PASS**

Merge gate, client-qa, 2026-10-18 19:57-20:06. Change: `BufferedImage` keeps a live mutable Bitmap that
`CanvasGraphics.forImage` draws into (dirty on draw, synced back on every pixel read), so drawing shows without
`dispose()`; docs rows in plugin-host.md and client-state.md for gen 41.

## Build (at the commit, clean tree)
- `host/build.sh` rc 0 (host.jar 7989268 bytes, sha256 f98cf506...), `ninja -C re/build` rc 0 (libosrsrl.so sha256 0d6755ad...).
- `OPENRUNE=1 SERVER=10.0.2.2:8080 GAME_PORT=43594 LOCAL_AUTH=1 tools/repack.sh`: rc 0, **7 `telemetry:` lines**
  (Singular starter, consent sync POST, Firebase/Crashlytics manifest flags, 2 consent/sync URLs zeroed, group score host,
  runescape.com web domain), LOCAL_AUTH appauth smali patched, RSA modulus / jav_config / slr / game port patched.
- Installed with `adb install-multiple -r`; the installed base.apk and split sha256 (8903d3e9..., f1d8f3a1...) match re/out.

## Device regression (Android emulator, OpenRune, LOCAL_AUTH sign-in "Tester", slot `qa`)
Extra jars: devtools, poison, instancemap, banktags, hub-quick-prayer-preview, timersandbuffs, statusbars. All 11 plugins
started; no host exceptions in logcat.

| Check | Result |
|---|---|
| Quick Prayer Preview (the fixed case) | PASS: quick prayers set client-side (`set 'varbit 4102 5'`), a held finger on the prayer orb shows the tooltip with both icons (Thick Skin, Clarity of Thought). Matches the plugin-host.md row |
| Poison infobox | PASS: `poison.showInfoboxes=true`, `::varp 102 6` shows the green splat infobox with "2" and a 0:12 timer; `::varp 102 0` removes it. (The row in plugin-host.md still says "unverified"; it is now verified on device) |
| Instance Map | PASS: "Show Instance Map" from the world map orb's long-press strip draws the Lumbridge scene map with the player's white dot in the courtyard and the close button; closes on X |
| Bank Tags | PASS: `::openbank` shows the tag column (new-tab "+", scroll arrows, host-drawn custom sprites) |
| Overlays/infoboxes in general | PASS: Status Bars HP/prayer bars beside the inventory, the infobox above, the debug overlay; no blank or stale images |
| Frame time | PASS: 30 fps on the overlay through every check (bank open, instance map open, poison infobox) |

Not run: desktop A/B (the change restores desktop semantics in the host shim, and no plugin's drawing changed);
Network QA (no network change: only the two image/graphics files changed).

## Observations (not blocking)
- `W osrsrl-host: sprite override 1067: 26x26 doesn't match the game's 15x14` (5x, then capped) while poisoned:
  the Poison plugin's heart sprite override is refused by `HostSprites` because of the size mismatch; the green heart
  seen is the game's own. Unrelated to this change (HostSprites untouched), but the plugin-host.md Poison row
  ("writes into the game's cached sprite 1067") overstates it; worth a separate look.
- Code read: a `WritableRaster` from `getRaster()` that a plugin keeps and writes into *after* a later draw isn't
  reloaded into the live Bitmap (`liveStale` is only set when `getRaster()`/`setRGB`/`getPixels()` is called), and the
  next sync would overwrite those writes. Desktop shares one buffer, so this differs only for that interleaving; no
  plugin known to do it. Note it, not a fix requirement.

## Slot
`tools/device-restore-check.sh check /tmp/osrs-slot/qa`: all OK (default 3 plugins, props cleared, settings.properties
identical after pushing the backup back, no new files, LOGGED_IN, no local-auth override); player at 3219,3222.
Device freed 20:06. The installed build is this commit's.

Evidence: /tmp/claude-1000/-home-alex-osrs-plugins-reverse/a0087aa1-b8d3-439d-add9-f4cbee8fc5e6/scratchpad/qa41/
(qpp1.png, poison1.png, poison2.png, im2.png, bank1.png, r2.png, repack.log, host-build.log, ninja.log, logcat1.txt, sha.txt).
