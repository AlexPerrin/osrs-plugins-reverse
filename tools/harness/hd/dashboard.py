"""The dashboard (PRD section 9): one page, three areas (progress, status, approvals), readable on a phone, built
from the store and the event log, refreshed within a tick; the only writes are approve and deny.

  view(obs=None) -> dict   the view model (JSON-serialisable):
    progress: {client: {api, plugins_core, plugins_user, plugins_hub, packets}, server: {parity_live, parity_main},
               upstream: {merged, open, drafts, in_review}, rate: {live_per_day, metric_delta_week}, trends}
              client numbers from the last `metric` events (tools/progress.py --dry-run on each client live);
              parity from `metric name=parity-*` events when the parity tool exists
    changes:  rows: slug, track, stage, hold, reason, waiting (stages.derive's words), age_in_stage, owner,
              cost, prio, branch, commit; sorted holds first, then prio, then age
    batches, live, resources (leases + buildq status + MemAvailable), agents, stalls, kpis, approvals, daemon
              (pid, last tick, tick seconds, interval), generated (UTC) and generated_local
  html(view) -> str        the page: inline CSS, no external assets, light and dark, phone width, auto-refresh
                           from /api/view every 30 s, Approve / Deny (with a note) buttons posting to
                           /api/approve and /api/deny, a pending-count badge in the title, times in US Eastern,
                           every number linking to /events?kind=...&change=...
  text(view) -> str        the same as terminal text (hd status)
"""
from . import approvals, batches, events, kpi, leases, stages, store


def view(obs=None):
    raise NotImplementedError


def html(v):
    raise NotImplementedError


def text(v):
    raise NotImplementedError
