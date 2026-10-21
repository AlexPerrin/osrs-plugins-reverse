"""One driver tick (problem 3f, PRD section 8), in this order:

  1 observe + reconcile   every change's stage re-derived from git, CI, GitHub, docker and the leases; a record
                          that disagrees is repaired and logged as `drift`; a branch that moved past its
                          hand-over re-enters at ready (BR-C2); deps set or clear `blocked` holds (BR-C4)
  2 reap                  ended agent runs; a change at dev with a hand-over gets the check: PASS -> checked,
                          FAIL -> ready with the failing items (BR-S5)
  3 ship / train          checked client and harness changes merge into master; checked server changes join the
                          queue; trains advance (rebuild, gate, image) one step, never blocking
  4 deploy                a gated image deploys once every instance is drained and leased by the daemon (BR-B3,
                          BR-R4); included changes become live, dependents unblock
  5 re-pin                upstream main moved: fast-forward, drop merged branches, train (BR-B5)
  6 gates                 live server changes need the upstream gate; the release rule makes a release batch
  7 approvals             gated changes get their one approval; approved ones are executed (BR-A2..A5)
  8 dispatch              ready changes x free slots x free leases, by priority (BR-C5); one agent per stage
  9 heal                  idle leases, lingering agents, dead containers, stuck batches, deadlocks (BR-H2, H3)
  10 log                  the `tick` event with its actions and the stalled changes

Every step runs in its own try/except: a failure is an `error` event, the others still run. `dry_run` observes,
reconciles and reports what it would do without spawning, shipping, training or deploying."""
import time
import traceback

from . import agents, approvals, batches, check, events, heal, leases, policy, ship, stages, store
from .util import iso, minutes_since


class Ctx:
    def __init__(self, obs, dry_run):
        self.obs, self.dry_run = obs, dry_run
        self.actions, self.stalled = [], []
        self.idx = store.index()

    def act(self, text):
        self.actions.append(text)

    def refresh(self, slug=None):
        if slug:
            self.idx[slug] = store.load(slug)
        else:
            self.idx = store.index()


def _step(ctx, name, fn):
    try:
        fn(ctx)
    except NotImplementedError:
        ctx.act(f"{name}: not implemented yet")
    except Exception as e:   # one failing step never stops the tick
        events.emit("error", step=name, error=repr(e), trace=traceback.format_exc()[-1500:])
        ctx.act(f"{name}: error {e!r}")


# --- 1 observe + reconcile ----------------------------------------------------------------------------------------

def reconcile(ctx):
    waiting = {}
    for slug, ch in list(ctx.idx.items()):
        if ch.stage == stages.terminal(ch) and ch.stage != "live":
            continue
        stage, why, moved = stages.derive(ch, ctx.obs, ctx.idx)
        waiting[slug] = why
        if moved and ch.reached("checked") and not ctx.dry_run:
            # BR-C2: a new commit on the branch; the handed-over commit stays recorded as what is live/gated
            if ch.stage == "live" or ch.reached("live"):
                ch["live_commit"] = ch["commit"]
            events.emit("stage", change=slug, **{"from": ch.stage, "to": "ready"}, commit=moved,
                        why="new commit on the branch: hand over again (BR-C2)")
            store.set_stage(ch, "ready", f"branch moved to {moved[:9]} past {str(ch['commit'])[:9]}: hand over again (BR-C2)")
            ch["commit"] = None
            ch["owner"] = None
            store.save(ch)
            ctx.refresh(slug)
            ctx.act(f"{slug}: branch moved to {moved[:9]}, re-enters at ready")
            continue
        if stage != ch.stage:
            direction = "drift" if store.STAGES.index(stage) < store.STAGES.index(ch.stage) else "stage"
            if ctx.dry_run:
                ctx.act(f"{slug}: {ch.stage} -> {stage} ({why or 'observed'}) [dry run]")
                continue
            before = ch.stage
            if stage == "live":
                ch["live_commit"] = ch["commit"]
            elif stage == "gated":
                ch["gated_commit"] = ch["commit"]
            elif stage == "published":
                ch["published_commit"] = ch["commit"]
            store.set_stage(ch, stage, "observed" if direction == "stage" else f"drift: {why}")
            if stage == "gated" and ch.hold == "failed":
                store.clear_hold(ch, "gate PASS observed")
            store.save(ch)
            events.emit(direction, change=slug, **{"from": before, "to": stage}, commit=ch.get("commit"), why=why)
            ctx.refresh(slug)
            ctx.act(f"{slug}: {before} -> {stage}")
    store.rec_save("view", "waiting", {"waiting": waiting})
    # deps -> blocked holds (BR-C4)
    for slug, ch in list(ctx.idx.items()):
        if ch.stage not in ("backlog", "ready", "dev") or ctx.dry_run:
            continue
        ok, open_ = stages.deps_met(ch, ctx.idx)
        if not ok and ch.hold is None:
            store.set_hold(ch, "blocked", "waiting for " + ", ".join(open_))
            store.save(ch)
            events.emit("hold", change=slug, hold="blocked", reason=", ".join(open_))
            ctx.refresh(slug)
        elif ok and ch.hold == "blocked":
            store.clear_hold(ch, "every dep is live")
            store.save(ch)
            events.emit("hold", change=slug, hold="none", reason="deps live")
            ctx.refresh(slug)
            ctx.act(f"{slug}: unblocked")


# --- 2 reap --------------------------------------------------------------------------------------------------------

def run_check(ctx, ch):
    passed, items = check.run(ch, ctx.obs)
    text = check.render(ch, items)
    events.emit("check", change=ch["slug"], result="PASS" if passed else "FAIL",
                items=[t for ok, t in items if not ok])
    if passed:
        store.clear_hold(ch, "handed over")
        store.set_stage(ch, "checked", "hand-over check PASS")
        ch["owner"] = None
        ch.pop("check", None)
        events.emit("stage", change=ch["slug"], **{"from": "dev", "to": "checked"}, commit=ch.get("commit"))
    else:
        fails = [t for ok, t in items if not ok]
        ch["check"] = fails
        store.set_stage(ch, "ready", f"hand-over check FAIL ({len(fails)}): the next dev fixes exactly those items")
        ch["owner"] = None
        events.emit("stage", change=ch["slug"], **{"from": "dev", "to": "ready"}, why="check FAIL", items=fails)
    store.save(ch)
    ctx.refresh(ch["slug"])
    ctx.act(f"{ch['slug']}: check {'PASS' if passed else 'FAIL'}")
    store.rec_save("checks", ch["slug"], {"text": text, "passed": passed})
    return passed


def reap(ctx):
    ended = agents.poll()
    for r in ended:
        ctx.act(f"run {r.get('name')} ended ({r.get('exit')})")
    ctx.refresh()
    for slug, ch in list(ctx.idx.items()):
        if ch.stage != "dev" or not ch.get("commit"):
            continue
        if ctx.dry_run:
            ctx.act(f"{slug}: would run the hand-over check [dry run]")
            continue
        run_check(ctx, ch)


# --- 3 ship / train ----------------------------------------------------------------------------------------------------

def ship_and_train(ctx):
    for slug, ch in list(ctx.idx.items()):
        if ch.stage != "checked":
            continue
        if ch.get("track") == "server":
            if ctx.dry_run:
                ctx.act(f"{slug}: would join the train queue [dry run]")
                continue
            if batches.enqueue(ch):
                ctx.act(f"{slug}: queued for the next train")
        else:
            if ctx.dry_run:
                ctx.act(f"{slug}: would merge into master [dry run]")
                continue
            ok, msg = ship.merge_to_master(ch)
            store.add_log(ch, f"ship: {msg}")
            if not ok:
                store.set_hold(ch, "exception", f"ship: {msg}")
                events.emit("hold", change=slug, hold="exception", reason=msg)
            store.save(ch)
            ctx.refresh(slug)
            ctx.act(f"{slug}: {msg}")
    if ctx.dry_run:
        return
    for a in batches.advance(ctx.obs) or []:
        ctx.act(a)
    ctx.refresh()


# --- 4 deploy -----------------------------------------------------------------------------------------------------------

def deploy_resources():
    return list(policy.instances()) + ["live-server"]


def deploy(ctx):
    b = batches.deploy_ready(ctx.obs)
    if not b:
        for r in deploy_resources():
            if leases.status(r).get("drain") and not leases.status(r).get("holder"):
                leases.undrain(r)
        return
    if ctx.dry_run:
        ctx.act(f"deploy of {b.get('id')} pending [dry run]")
        return
    res = deploy_resources()
    cap = policy.get().get("leases", {}).get("drain_cap_minutes", 30)
    for r in res:
        st = leases.status(r)
        if st.get("holder") and st["holder"] != leases.DAEMON:
            leases.drain(r)
            d = st.get("drain") or leases.status(r).get("drain") or {}
            m = minutes_since(d.get("since")) or 0
            if m > cap:
                leases.reclaim(r, f"drain cap {cap} min passed for the deploy of {b['id']} (held {m:.0f} min)")
                ctx.act(f"{r}: reclaimed for the deploy")
    if not leases.holders_free([r for r in res if leases.status(r).get("holder") != leases.DAEMON]):
        held = [f"{r} by {leases.status(r)['holder']}" for r in res if leases.status(r).get("holder") not in (None, leases.DAEMON)]
        ctx.act(f"deploy of {b['id']} waits for: {', '.join(held)}")
        return
    for r in res:
        if not leases.holds(r, leases.DAEMON):
            ok, msg = leases.take(r, leases.DAEMON, f"deploy {b['id']}")
            if not ok:
                ctx.act(f"deploy of {b['id']}: {msg}")
                return
    ok, msg = batches.deploy(b, ctx.obs)
    ctx.act(f"deploy {b['id']}: {msg}")


# --- 5 re-pin -------------------------------------------------------------------------------------------------------------

def repin(ctx):
    sha = batches.repin_due(ctx.obs)
    if not sha:
        return
    if ctx.dry_run:
        ctx.act(f"re-pin due: upstream main at {sha[:9]} [dry run]")
        return
    if leases.status("integration").get("holder"):
        ctx.act(f"re-pin to {sha[:9]} waits for the running train")
        return
    ctx.act(batches.repin(ctx.obs) or f"re-pin to {sha[:9]}")
    ctx.refresh()


# --- 6 gates and releases ---------------------------------------------------------------------------------------------------

def gates(ctx):
    if ctx.dry_run:
        return
    due = batches.release_due(ctx.idx)
    if due:
        b = batches.new_release(due)
        ctx.act(f"release batch {b.get('id')} for {len(due)} live client changes")


# --- 7 approvals ---------------------------------------------------------------------------------------------------------------

def approvals_step(ctx):
    for slug, ch in list(ctx.idx.items()):
        if ch.stage == "gated" and not ctx.dry_run:
            a = approvals.ensure(ch, ctx.obs)
            if a:
                ctx.act(f"approval {a.get('id')} ({a.get('action')}) for {slug}")
    for b in batches.status():
        if b.get("kind") == "release" and b.get("step") == "gated" and not ctx.dry_run:
            a = approvals.ensure_release(b)
            if a:
                ctx.act(f"release approval {a.get('id')}")
    if ctx.dry_run:
        return
    for a in approvals.execute(ctx.obs) or []:
        ctx.act(f"approval {a.get('id')}: {a.get('state')}")
    ctx.refresh()


# --- 8 dispatch -----------------------------------------------------------------------------------------------------------------

def slots_free(role, running):
    pol = policy.role(role)
    track = pol["track"]
    used = sum(1 for r in running if r.get("role") == role)
    if pol["stage"] == "dev":
        return policy.track(track).get("dev_slots", 1) - used
    return 1 - used   # one QA per track, one integrator


def candidates(ctx):
    out = []
    for ch in ctx.idx.values():
        ex = stages.next_executor(ch)
        if not ex or ch.get("owner"):
            continue
        if ex == "dev" and not ch.has_dod():
            continue
        role = policy.role_for(ch["track"], ex)
        if not role:
            continue
        out.append((policy.prio_order(ch.get("prio")), ch.get("created") or "", ch, ex, role))
    out.sort(key=lambda t: (t[0], t[1]))
    return [(ch, ex, role) for _, _, ch, ex, role in out]


def dispatch(ctx):
    running = agents.running()
    for b in batches.status():
        if b.get("step") == "exception" and not any(r.get("role") == "integrator" and r.get("batch") == b["id"] for r in running):
            if ctx.dry_run:
                ctx.act(f"would spawn an integrator for {b['id']} [dry run]")
            else:
                r = agents.spawn(b, "integrator", "train", [])
                if r:
                    running.append(r)
                    ctx.act(f"integrator {r.get('name')} for {b['id']}")
    for ch, ex, role in candidates(ctx):
        if slots_free(role, running) <= 0:
            ctx.stalled.append(ch["slug"])
            continue
        pol = policy.track(ch["track"])
        need = pol.get("instances" if ex == "dev" else "qa_instances", []) or []
        busy = [r for r in need if leases.status(r).get("holder") or leases.status(r).get("drain")]
        if busy:
            ctx.stalled.append(ch["slug"])
            continue
        if ctx.dry_run:
            ctx.act(f"would dispatch {role} for {ch['slug']} ({ex}) [dry run]")
            continue
        r = agents.spawn(ch, role, ex, need)
        running.append(r)
        ctx.refresh(ch["slug"])
        ctx.act(f"{role} {r.get('name')} -> {ch['slug']} ({ex})")


# --- 9 heal --------------------------------------------------------------------------------------------------------------------------

def heal_step(ctx):
    if ctx.dry_run:
        return
    for a in heal.run(ctx.obs, ctx.idx) or []:
        ctx.act(f"heal: {a}")
    ctx.refresh()


# --- the tick ----------------------------------------------------------------------------------------------------------------------------

def tick(obs=None, dry_run=False, interval=60):
    if obs is None:
        from .observe import Observer
        obs = Observer()
    obs.reset()
    t0 = time.time()
    ctx = Ctx(obs, dry_run)
    for name, fn in (("reconcile", reconcile), ("reap", reap), ("ship", ship_and_train), ("deploy", deploy),
                     ("repin", repin), ("gates", gates), ("approvals", approvals_step), ("dispatch", dispatch),
                     ("heal", heal_step)):
        _step(ctx, name, fn)
    secs = round(time.time() - t0, 1)
    n = (store.rec_load("view", "tick") or {}).get("n", 0) + 1
    store.rec_save("view", "tick", {"n": n, "seconds": secs, "at": iso(), "actions": ctx.actions, "dry_run": dry_run})
    if not dry_run:
        events.emit("tick", n=n, seconds=secs, actions=ctx.actions, stalled=sorted(set(ctx.stalled)), interval=interval)
    return ctx.actions
