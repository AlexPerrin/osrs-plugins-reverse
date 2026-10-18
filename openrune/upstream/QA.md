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

## Record (`qa/<slug>.md`)

The record holds:
- the branch and commit;
- the date;
- the result of each section above, as pass, fail or n/a, with a reason;
- the evidence;
- the verdict: **PASS**, or **FAIL** with what has to change.

A new commit on the branch voids the record and QA must be redone.
