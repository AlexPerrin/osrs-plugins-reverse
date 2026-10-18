# QA gate for OpenRune-Server PRs

No PR is opened, on our fork or upstream, and no draft is marked ready, until its branch passes this
gate. The result is recorded in `qa/<slug>.md`, and the orchestrator checks that record before it
runs `tools/fork.sh pr-create` or `gh pr create`.

QA is done by the QA agent, not by the agent that wrote the branch.

## 1. Build and tests (exact branch commit)

- Use a fresh `git clone --shared` of `re/upstream-pr/or` at the branch commit, with its own Gradle
  home volume.
- Run `clean`, `--no-build-cache --no-configuration-cache`, then `:or-cache:buildCache`,
  `spotlessCheck`, `assemble`, `test`, `integration` (as separate runs).
- The server boots to "Server ready" with no exceptions in the log.
- New behaviour has tests. Each test fails without the change, or the record says why it can't.

## 2. Accuracy

- `accuracy/<slug>.md` exists for the current commit.
- Every gameplay-visible divergence is fixed.
- Every remaining divergence, guess or gap is listed in the PR's Caveats.
- Every rule names its source (wiki page/section, cache, Mod post).

## 3. In-game verification

- Run the feature in a real client and record the evidence: chat lines, varp/varbit values,
  inventory, and screenshots only where layout matters.
  - Preferred: the branch's own server (`upstream main + branch`), on a spare port, with the isolated
    desktop RuneLite. The client is driven through the osrs-devtools MCP, under the desktop lock.
  - Otherwise: our live server, but only when the feature's files are byte-identical to the branch.
    Record the diff check in the QA record.
- Cover each player-facing claim in the PR text, including its main failure paths (wrong item,
  full inventory, requirement missing).
- Where a RuneLite plugin reads the feature, also check that the plugin reacts (e.g. Time Tracking,
  Did I Compost, Coal Bag).

## 4. PR text review

- Every claim in `.body` and `.upstream.body` is true of the commit: ids, files, tests, results and
  "matches OSRS" statements.
- CONTRIBUTING format: `feat:`/`fix:` subject, release note, checklist, data sources, attribution
  line.
- Stacked branches say so and name the PR they follow.

## Procedure (QA agent)

The helper is `openrune/upstream/qa/qa.sh`. Clones live in the QA agent's scratchpad (`QA_ROOT`), one per
branch commit, with logs beside them in `<clone>.logs/`.

1. **Clone.** `qa.sh clone <slug> <commit> main` makes two fresh `git clone --shared` trees of
   `re/upstream-pr/or`: `<slug>-<commit9>` at the branch commit (sections 1 and 2) and `<slug>-<commit9>-run`
   = upstream `main` + the branch, merged (section 3). A merge conflict there is a FAIL in itself.
2. **Build and tests.** `qa.sh build <clone>` runs each step as its own Gradle container (`--cpus=4
   --memory=4g`, `--max-workers=2`, `-Xmx2g`, cache tasks `-Xmx2600m`, `--no-build-cache
   --no-configuration-cache`, Gradle home volume `upstream-pr-gradle-qa`). It takes the scratchpad
   `gradle.lock` (shared with the other agents' builds) and waits while any other container runs
   `gradlew`. Steps: `clean :or-cache:freshCache :or-cache:mergePluginGamevals` (a fresh cache download:
   a new clone has none, and freshCache ends with a forced BUILD), `:or-cache:buildCache`,
   `spotlessCheck`, `assemble`, `test`, `integration`, then `generateRsa :server:app:installDist`.
   `qa.sh runbuild <clone>-run` builds only what a server run needs.
3. **Branch server.** `qa.sh start <clone>-run` runs the installDist in `eclipse-temurin:21-jre` as
   `qa-or-server` (game port **43596**, its own embedded PostgreSQL under the clone's `.data/postgres`,
   so every run starts with no accounts) and the jav_config sidecar `qa-or-http` (**8083**, the
   `osrs-rsps-test:241` image as for `osrs-openrune-http-desktop`, world host 127.0.0.1). It waits for
   "Server ready in" and writes `<clone>-run.logs/proxy-targets.yaml`: the "OpenRune Test" target with
   jav_config `http://127.0.0.1:8083`, `game_server_port: 43596` and the modulus of the clone's own
   `.data/game.key` (RSProx re-encrypts the login with it; the sidecar's `/rsa` is not used).
   `osrs-openrune`, `osrs-openrune-http` and `osrs-openrune-http-desktop` are never touched.
4. **Desktop client** (under the desktop lock: `LOCK_NAME=desktop tools/device-lock.sh take qa`, after
   the orchestrator grants it):
   `tools/multiclient/desktop.sh stop` (if it runs), then
   `DESKTOP_TARGET_YAML=<clone>-run.logs/proxy-targets.yaml tools/multiclient/desktop.sh start`,
   `desktop.sh login` (desktop/desktop, created on first login) or `desktop.sh login <fresh> <fresh>`.
   Drive it with the desktop devtools bridge, `tools/multiclient/desktop-devtools-mcp.sh --tool <name>
   '<json>'` (data tools first: `get_var`, `get_inventory`, `get_chat_history`, `get_dialogue`;
   screenshots only for layout). **Not** the session's `osrs-devtools` MCP server: `.mcp.json` points it
   at `tools/devtools-mcp.sh`, which is the Android device (adb), not the desktop client.
   Before the GUI, `desktop_login.py --host 127.0.0.1 --port 43596 --http <url serving the modulus as /rsa>`
   checks the desktop login headlessly (no lock needed). Upstream accepts our desktop client as is
   (checked 2026-10-18: login OK, staff 2, toplevel 161).
   - **Members:** upstream accounts are non-members (`account_characters.members` defaults false, no
     command sets it), so members items show "(Members)" and hide their ops. Until there is an agreed
     way to make the QA account a member, send the hidden op with `invoke_menu_action` and say so in
     the record. Inventory ops (interface 149) map to `CC_OP` identifiers op1 = 2, op2 = 3, op3 = 4,
     op4 = 6, op5 = 7; item-on-item is `WIDGET_TARGET_ON_WIDGET` (param0 = target slot, param1 =
     9764864, itemId = target) after `item_action {"option":"Use"}`.
   - The devtools chat history never showed examine text in these runs, not even a plain inventory
     Examine, so examine can't be checked through it.
5. **Clean up.** `desktop.sh stop` (it also restores the default target,
   `tools/multiclient/proxy-targets.yaml`, when `C:\osrs-test\target-override` marks an override), then `qa.sh stop` (removes
   only `qa-or-server` and `qa-or-http`) and free the lock (`LOCK_NAME=desktop tools/device-lock.sh free qa`).
6. **Record.** `qa/<slug>.md` with the commit, the date, each section, the evidence and the verdict.

## Record (`qa/<slug>.md`)

The record holds:
- the branch and commit;
- the date;
- the result of each section above, as pass, fail or n/a, with a reason;
- the evidence;
- the verdict: **PASS**, or **FAIL** with what has to change.

A new commit on the branch voids the record and QA must be redone.
