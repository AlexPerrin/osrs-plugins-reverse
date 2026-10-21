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
"""
from . import events


def compute(evs=None, since=None):
    raise NotImplementedError


def trend(name, days=14, evs=None):
    raise NotImplementedError
