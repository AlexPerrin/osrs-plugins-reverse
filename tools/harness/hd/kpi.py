"""KPIs from the event log (PRD 9b "Harness KPIs", problem 5): nothing typed, everything derived.

  compute(evs=None, since=None) -> dict:
    unattended_hours      wall time covered by ticks with no `note kind=user` event between them (the console
                          emits `note` events with kind user when the user speaks; absent = unattended)
    live_per_day          changes reaching live per day (stage events to=live), last 7 days
    lead_time_h           ready -> live, median and p90 (stage events per change)
    check_first_pass      share of changes whose first `check` event was PASS
    gate_first_pass       share of changes whose first gate verdict (stage to=gated vs hold failed from a gate) was PASS
    stall_minutes         minutes ready changes waited while a slot and lease were free (tick events carry
                          `stalled: [slugs]`)
    interventions_per_day `note kind=user` events per day
    denials_per_hour      `denial` events per hour, by source
    tokens_per_change     from `agent end` events: by role (median usd, minutes, turns)
    deadlocks             count, median detect and recover minutes (heal events)
    builds                gate seconds (batch events step=gating with seconds), cold/warm when known
  trend(name, days)       daily series for a tile

Definitions (decided for v3):
  unattended_hours: the hours between consecutive `tick` events at most 2*interval apart (the tick's `interval`
  field, seconds, default 60) with no `note kind=user` between them. live_per_day / interventions_per_day: a
  {day: n} dict over the last 7 days (today included) plus the 7-day mean. lead_time_h: per change, the first
  `stage to=ready` (or its first event) to the first `stage to=live`; median and p90 in hours. stall_minutes:
  sum over ticks of len(stalled) * interval / 60. denials_per_hour: denials over the hours the log covers (at
  least one), by `source`. Every value is None where there is no data.
"""
import collections
import datetime as dt
import statistics

from . import events
from .util import now, parse

DEFAULT_INTERVAL = 60
NOTE_KIND_FIELDS = ("note_kind", "by", "source")   # the event's `kind` is `note`; the note's kind is one of these


def note_kind(e):
    """The kind of a `note` event (user, upstream, ...): `events.emit("note", by="user", text=...)`."""
    if e.get("kind") != "note":
        return None
    for f in NOTE_KIND_FIELDS:
        if e.get(f):
            return e[f]
    return None


def _user(e):
    return note_kind(e) == "user"


def _day(ts):
    t = parse(ts)
    return t.strftime("%Y-%m-%d") if t else None


def _days(n, ref=None):
    ref = ref or now()
    return [(ref - dt.timedelta(days=i)).strftime("%Y-%m-%d") for i in range(n - 1, -1, -1)]


def _median(xs):
    xs = [x for x in xs if x is not None]
    return round(statistics.median(xs), 2) if xs else None


def _p90(xs):
    xs = sorted(x for x in xs if x is not None)
    if not xs:
        return None
    i = min(len(xs) - 1, int(round(0.9 * (len(xs) - 1))))
    return round(xs[i], 2)


def _per_day(evs, pred, days=7):
    by = collections.Counter(_day(e.get("ts")) for e in evs if pred(e))
    window = _days(days)
    out = {d: by.get(d, 0) for d in window}
    return {"by_day": out, "mean": round(sum(out.values()) / days, 2), "total": sum(by.values())}


def _unattended(evs):
    hours = 0.0
    last_tick, user_since = None, False
    for e in evs:
        k = e.get("kind")
        if k == "note" and _user(e):
            user_since = True
        elif k == "tick":
            t = parse(e.get("ts"))
            if last_tick is not None and t is not None and not user_since:
                gap = (t - last_tick[0]).total_seconds()
                if 0 <= gap <= 2 * last_tick[1]:
                    hours += gap / 3600
            if t is not None:
                last_tick = (t, float(e.get("interval") or DEFAULT_INTERVAL))
            user_since = False
    return round(hours, 2)


def _lead_times(evs):
    start, live = {}, {}
    first = {}
    for e in evs:
        c = e.get("change")
        if not c:
            continue
        first.setdefault(c, e.get("ts"))
        if e.get("kind") == "stage":
            if e.get("to") == "ready":
                start.setdefault(c, e.get("ts"))
            elif e.get("to") == "live":
                live.setdefault(c, e.get("ts"))
    out = []
    for c, t1 in live.items():
        t0 = parse(start.get(c) or first.get(c))
        t1 = parse(t1)
        if t0 and t1:
            out.append((t1 - t0).total_seconds() / 3600)
    return out


def _first_pass(evs, kind_pred, pass_pred):
    first = {}
    for e in evs:
        c = e.get("change")
        if c and kind_pred(e) and c not in first:
            first[c] = pass_pred(e)
    n = len(first)
    p = sum(1 for v in first.values() if v)
    return {"rate": round(p / n, 3) if n else None, "pass": p, "n": n}


def _is_gate_verdict(e):
    return (e.get("kind") == "stage" and e.get("to") == "gated") or (
        e.get("kind") == "hold" and e.get("hold") == "failed" and e.get("source") == "gate")


def _by_role(evs):
    rows = collections.defaultdict(lambda: {"usd": [], "minutes": [], "turns": []})
    for e in evs:
        if e.get("kind") == "agent" and e.get("action") == "end":
            r = rows[e.get("role") or "?"]
            for f in ("usd", "minutes", "turns"):
                if e.get(f) is not None:
                    r[f].append(float(e[f]))
    return {role: {"usd": _median(r["usd"]), "minutes": _median(r["minutes"]), "turns": _median(r["turns"]),
                   "n": max(len(r["usd"]), len(r["minutes"]), len(r["turns"]))} for role, r in rows.items()}


def _denials(evs):
    den = [e for e in evs if e.get("kind") == "denial"]
    ts = [parse(e.get("ts")) for e in evs if parse(e.get("ts"))]
    hours = max((max(ts) - min(ts)).total_seconds() / 3600, 1.0) if ts else 1.0
    by = collections.Counter(e.get("source") or "?" for e in den)
    return {"by_source": {s: round(n / hours, 3) for s, n in by.items()}, "total": round(len(den) / hours, 3),
            "n": len(den), "hours": round(hours, 2)}


def compute(evs=None, since=None):
    if evs is None:
        evs = events.read(since=since)
    lead = _lead_times(evs)
    heals = [e for e in evs if e.get("kind") == "heal" and e.get("action") == "deadlock"]
    ticks = [e for e in evs if e.get("kind") == "tick"]
    gates = [float(e["seconds"]) for e in evs
             if e.get("kind") == "batch" and e.get("step") == "gating" and e.get("seconds") is not None]
    stall = sum(len(e.get("stalled") or []) * float(e.get("interval") or DEFAULT_INTERVAL) / 60 for e in ticks)
    return {
        "unattended_hours": _unattended(evs),
        "live_per_day": _per_day(evs, lambda e: e.get("kind") == "stage" and e.get("to") == "live"),
        "lead_time_h": {"median": _median(lead), "p90": _p90(lead), "n": len(lead)},
        "check_first_pass": _first_pass(evs, lambda e: e.get("kind") == "check", lambda e: e.get("result") == "PASS"),
        "gate_first_pass": _first_pass(evs, _is_gate_verdict, lambda e: e.get("kind") == "stage"),
        "stall_minutes": round(stall, 2),
        "interventions_per_day": _per_day(evs, _user),
        "denials_per_hour": _denials(evs),
        "tokens_per_change": _by_role(evs),
        "deadlocks": {"count": len(heals),
                      "detect_min": _median([e.get("detect_min") for e in heals]),
                      "recover_min": _median([e.get("recover_min") for e in heals])},
        "builds": {"median": _median(gates), "last": gates[-1] if gates else None, "n": len(gates)},
        "ticks": len(ticks),
        "events": len(evs),
    }


def trend(name, days=14, evs=None):
    """[(day, value)] for the last `days` days: live (changes reaching live), interventions (user notes),
    denials, usd (agent spend)."""
    if evs is None:
        evs = events.read(since=(now() - dt.timedelta(days=days + 1)))
    if name == "live":
        pred, val = (lambda e: e.get("kind") == "stage" and e.get("to") == "live"), (lambda e: 1)
    elif name == "interventions":
        pred, val = _user, (lambda e: 1)
    elif name == "denials":
        pred, val = (lambda e: e.get("kind") == "denial"), (lambda e: 1)
    elif name == "usd":
        pred, val = (lambda e: e.get("kind") == "agent" and e.get("action") == "end"), (lambda e: float(e.get("usd") or 0))
    else:
        raise ValueError(name)
    by = collections.defaultdict(float)
    for e in evs:
        if pred(e):
            by[_day(e.get("ts"))] += val(e)
    return [(d, round(by.get(d, 0), 2)) for d in _days(days)]
