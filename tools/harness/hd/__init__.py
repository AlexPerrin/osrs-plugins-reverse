"""harnessd (hd): harness v3, built against docs/harness-v3-prd.md.

A change is the only unit of work; stages are observed, never set by hand; resources are leases; every state change
is an event; agents execute one stage with a budget; only two actions need the user. The package:

  util      clock (UTC in, US Eastern out), atomic files, slugs
  events    append-only events.jsonl (BR-H4)
  store     change / lease / run / batch / approval records on disk
  policy    harness/policy.yaml: tracks, roles, resources, limits (BR-P*)
  stages    the lifecycle and each stage's precondition per track (BR-C*, BR-S*)
  observe   external truth: git refs, fork CI, GitHub PRs, docker, processes (BR-H1)
  check     the hand-over check (the `checked` precondition)
  leases    the lease service (BR-R*)
  agents    headless stage executors (BR-G*)
  batches   trains and releases: queue, rebuild, gate, image, deploy (BR-B*)
  approvals the approval queue and its execution (BR-A*)
  heal      repairs (BR-H2, BR-H3)
  tick      observe -> advance -> dispatch -> heal -> log
  daemon    the loop and the dashboard server
  dashboard progress, status, approvals (section 9)
  kpi       the harness KPIs from the event log
  migrate   v2 task store -> v3 changes

Paths: ROOT is the main checkout (also from a worktree); HARNESS = ROOT/harness (changes/ and policy.yaml are
committed; state/ is git-ignored). HD_HARNESS_DIR overrides HARNESS (tests).
"""
import os
import subprocess

HERE = os.path.dirname(os.path.abspath(__file__))
_REPO = os.path.dirname(os.path.dirname(os.path.dirname(HERE)))


def _main_checkout(root):
    r = subprocess.run(["git", "-C", root, "rev-parse", "--path-format=absolute", "--git-common-dir"],
                       capture_output=True, text=True)
    common = r.stdout.strip()
    if r.returncode == 0 and common.endswith("/.git"):
        return common[:-len("/.git")]
    return root


ROOT = _main_checkout(_REPO)


def harness_dir():
    return os.environ.get("HD_HARNESS_DIR") or os.path.join(ROOT, "harness")


def changes_dir():
    return os.path.join(harness_dir(), "changes")


def state_dir():
    return os.environ.get("HD_STATE_DIR") or os.path.join(harness_dir(), "state")


def evidence_dir():
    return os.environ.get("HD_EVIDENCE_DIR") or os.path.join(ROOT, "re", "evidence")


def policy_path():
    return os.environ.get("HD_POLICY") or os.path.join(harness_dir(), "policy.yaml")
