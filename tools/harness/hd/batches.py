"""Batches (BR-B1..B6): trains (server) and releases (client). Record `harness/state/batches/<id>.json`:
  {id, kind: train|release, includes: [slugs], step, steps: [{step, started, ended, rc, detail}], integration (sha),
   tools (sha), gate_dir, image (tag), image_id, rollback, pid, log, created, exception}
Train steps: queued -> pinning -> rebuilding -> gating -> imaging -> deploying -> live | exception | failed.
Release steps: queued -> gating -> gated -> published (the user's approval) | failed.

  enqueue(ch)                   a checked server change joins the queue (`harness/state/queue.json`; a newer
                                commit of the same branch replaces its entry); idempotent
  advance(obs)                  one step of every open batch, never blocking: starts a train when the oldest
                                entry is past policy `batches.train_debounce_minutes` (or `--now`) and the
                                `integration` lease is free; the rebuild (integrate.py's helpers: pins, the tools
                                commit, build-integration.sh, the lease push) runs synchronously (seconds);
                                gate (`qa.sh clone` + `qa.sh gate` through buildq), image (tools/harness/image.sh)
                                and deploy (tools/harness/deploy.sh) run as background processes recorded on the
                                batch (pid, log) and are polled on later ticks. Returns a list of actions taken.
  deploy_ready(obs)             the newest gated image waiting for a deploy, or None
  deploy(batch, obs)            BR-B3: needs every instance leased by the daemon (the tick drains first); runs
                                deploy.sh in the background; on "Server ready in" the tick marks the included
                                changes live, writes live.json (integration, image, image_id, rollback, since,
                                upstream_main) and emits `deploy`; on timeout deploy.sh rolled back: `exception`
  on_exception(batch, text)     records the stop output; the tick spawns an `integrator` with it (BR-B4)
  resume(batch_id)              after the integrator recorded a resolution: re-run from the failed step
  repin_due(obs)                BR-B5: upstream main moved past live.json's upstream_main (or an open change
                                merged upstream): the sha, else None
  repin(obs)                    fast-forward the fork's main (tools/fork.sh sync), drop merged branches from
                                integration.txt (a merged change -> stage merged on the next derive), queue a train
  release_due(idx)              BR-B6: N live client changes since the last release, or a `release` request file
  new_release(slugs)            a release batch: gates master at HEAD with client-qa
  status()                      open batches with their step and timings (the dashboard)
Scripts: tools/harness/image.sh <gated-clone> <batch> (stages server/ + data/ without key files, cache fixes,
template tools/harness/Dockerfile.integration with COPY --from the live image for the key and game.yml, key-hash
compare printing only match/MISMATCH, private boot check, record harness/state/images/<sha7>.json);
tools/harness/deploy.sh <batch> (image record ok, all instances held by harnessd, rollback tag, retag :241,
`docker compose --env-file switch.env up -d --no-deps openrune` in openrune/, wait for "Server ready in",
auto-rollback on timeout; never --build). HD_DOCKER/HD_QA_SH/HD_BUILDQ override the commands (tests).
"""
from . import events, leases, policy, store

BATCHES = "batches"


def enqueue(ch):
    raise NotImplementedError


def advance(obs):
    raise NotImplementedError


def deploy_ready(obs):
    raise NotImplementedError


def deploy(batch, obs):
    raise NotImplementedError


def on_exception(batch, text):
    raise NotImplementedError


def resume(batch_id):
    raise NotImplementedError


def repin_due(obs):
    raise NotImplementedError


def repin(obs):
    raise NotImplementedError


def release_due(idx):
    raise NotImplementedError


def new_release(slugs):
    raise NotImplementedError


def status():
    return [b for b in store.rec_all(BATCHES) if b.get("step") not in ("live", "published", "failed")]
