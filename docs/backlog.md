# Backlog

The backlog is the task store: `harness/tasks/<id>.md`, one file per deliverable (docs/harness.md).

```
tools/harness/task.py status            # per track: in flight, ready, blocked, waiting on the user
tools/harness/task.py list --status backlog   # the roadmap (content/* without a definition of done yet)
tools/harness/task.py list --track orch --status waiting-user   # questions for the user
tools/harness/task.py new --track server --title "..." --prio P2 --dod "..."   # a new row
```

The tables that used to live here were migrated on 2026-10-19 (88 tasks; `git show dbb3ba6:docs/backlog.md` for
the last table version). Server roadmap rows are `content-*` tasks in `backlog`; the upstream queue is the `orch`
tasks in `waiting-user`.
