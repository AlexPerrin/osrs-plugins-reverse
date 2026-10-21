"""Approvals (BR-A1..A8): the user's yes or no on one outward action. Record `harness/state/approvals/<id>.json`:
  {id, kind: upstream|release|decision, change, batch, action: open-pr|update-pr|ready-pr|comment|tag-release,
   commands: [[argv]...], text: {title, body, comment}, record (the gate record name), diff: "base..head",
   caveats: [..], created, state: pending|approved|denied|done|stale|failed, note, result, decided, executed}
Kinds: `upstream` (open, update, mark ready, comment on an OpenRune PR; a push to a branch backing an open PR),
`release` (tag + GitHub release with the APK), `decision` (a question with options from an agent or the daemon).

  ensure(ch, obs)        for a gated server change: the one approval its state needs (open a PR when none; mark
                         ready when it is a draft at the commit; update = comment + push when the PR head is
                         behind the commit), created only when gated at that commit (BR-A2), with the exact
                         commands and the full text from the fork's docs branch pr-texts/<slug>.title and
                         pr-texts/<slug>.upstream.body (BR-A3). Returns the record or None. Marks an existing
                         pending approval `stale` when GitHub already shows the action done (BR-A5).
  ensure_release(batch)  the release approval for a gated release batch
  decision(ch, question, options, by)   a decision card; puts the change on a `decision` hold
  pending() / all()      records
  approve(aid, note) / deny(aid, note)   the dashboard's only writes; events `approval approved|denied`
  execute(obs)           BR-A4: runs every approved approval's commands once, records the result and the link,
                         marks `done` (or `failed` with the output); a denied one puts its change on a `decision`
                         hold with the note. Returns the records it touched. HD_GH overrides gh (tests).
  render(a)              the card as text (the dashboard, the digest)
Never edits an existing upstream PR description (BR-A8): updates are comments, unless the approval text says the
description is replaced and the user approved that text.
"""
from . import events, store

APPROVALS = "approvals"


def ensure(ch, obs):
    raise NotImplementedError


def ensure_release(batch):
    raise NotImplementedError


def decision(ch, question, options, by="harnessd"):
    raise NotImplementedError


def pending():
    return [a for a in store.rec_all(APPROVALS) if a.get("state") == "pending"]


def all_():
    return store.rec_all(APPROVALS)


def approve(aid, note=""):
    raise NotImplementedError


def deny(aid, note=""):
    raise NotImplementedError


def execute(obs):
    raise NotImplementedError


def render(a):
    raise NotImplementedError
