#!/usr/bin/env python3
"""Progress report: writes docs/progress.md from the task store, upstream PR states, the client coverage metrics
(tools/progress.py --dry-run) and the integration pipeline phases. Read-only apart from the output file.

  tools/harness/progress_report.py [--out docs/progress.md] [--no-gh]

Completion % per track = (done + live) / tasks not in backlog; "in pipeline" adds shipped and gated.
"""
import argparse
import collections
import datetime
import glob
import json
import os
import re
import subprocess

import yaml

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
HARNESS = os.environ.get("HARNESS_DIR") or os.path.join(ROOT, "harness")
CLOSED = {"done", "live"}
PIPELINE = {"shipped", "gated", "qa"}


def tasks():
    out = []
    for f in sorted(glob.glob(os.path.join(HARNESS, "tasks", "*.md"))):
        text = open(f).read()
        m = re.match(r"---\n(.*?)\n---", text, re.S)
        if not m:
            continue
        meta = yaml.safe_load(m.group(1)) or {}
        meta["_log"] = text.split("## Log")[-1]
        out.append(meta)
    return out


def pct(a, b):
    return f"{100 * a / b:.0f}%" if b else "-"


def track_table(ts):
    rows = ["| Track | Done + live | In pipeline | Active | Ready | Blocked / waiting | Backlog | Completion (excl. backlog) |",
            "|---|---|---|---|---|---|---|---|"]
    for tr in ("client", "server", "orch"):
        c = collections.Counter(t.get("status") for t in ts if t.get("track") == tr)
        closed = sum(c[s] for s in CLOSED)
        pipe = sum(c[s] for s in PIPELINE)
        active = c["in-progress"] + c["specced"] + c["handover"]
        wait = c["blocked"] + c["waiting-user"] + c["failed"]
        scope = sum(c.values()) - c["backlog"]
        rows.append(f"| {tr} | {closed} | {pipe} | {active} | {c['ready']} | {wait} | {c['backlog']} | "
                    f"{pct(closed, scope)} ({pct(closed + pipe, scope)} with pipeline) |")
    return rows


def finished_on(ts, day):
    rows = []
    for t in ts:
        if t.get("status") in CLOSED | PIPELINE and re.search(rf"^- {day}T.*(set status=(done|live|shipped|gated)|"
                                                             r"shipped|migrate-live|-> live)", t["_log"], re.M):
            rows.append(f"- `{t['id']}` ({t.get('track')}, {t.get('status')}): {t.get('title', '')}")
    return sorted(rows)


def upstream(no_gh):
    if no_gh:
        return ["(skipped: --no-gh)"]
    try:
        raw = subprocess.run(["gh", "pr", "list", "-R", "OpenRune/OpenRune-Server", "--author", "AlexPerrin",
                              "--state", "all", "--limit", "100", "--json", "number,title,state,isDraft,mergedAt"],
                             capture_output=True, text=True, timeout=60).stdout
        prs = json.loads(raw or "[]")
    except Exception as e:  # report, don't fail
        return [f"(gh failed: {e})"]
    merged = [p for p in prs if p["state"] == "MERGED"]
    ready = [p for p in prs if p["state"] == "OPEN" and not p["isDraft"]]
    draft = [p for p in prs if p["state"] == "OPEN" and p["isDraft"]]
    closed = [p for p in prs if p["state"] == "CLOSED"]
    lines = [f"Upstream PRs (OpenRune/OpenRune-Server): {len(merged)} merged, {len(ready)} open for review, "
             f"{len(draft)} draft, {len(closed)} closed (superseded). Merged share of non-superseded: "
             f"{pct(len(merged), len(prs) - len(closed))}.", ""]
    for label, group in (("Open for review", ready), ("Draft", draft)):
        if group:
            lines.append(f"- {label}: " + ", ".join(f"#{p['number']} {p['title'][:55]}" for p in
                                                    sorted(group, key=lambda p: p["number"])))
    return lines


def client_metrics():
    try:
        out = subprocess.run(["python3", os.path.join(ROOT, "tools", "progress.py"), "--dry-run"],
                             capture_output=True, text=True, timeout=120, cwd=ROOT).stdout
    except Exception as e:
        return [f"(tools/progress.py failed: {e})"]
    rows = ["| Metric | Value |", "|---|---|"]
    for line in out.splitlines():
        m = re.match(r"^(\S.*?)\s{2,}(\d+/\d+)\s+([\d.]+%)", line)
        if m:
            rows.append(f"| {m.group(1)} | {m.group(2)} ({m.group(3)}) |")
    return rows


def phases(ts):
    ph = sorted((t for t in ts if t["id"].startswith("integration-phase-")), key=lambda t: t["id"])
    done = sum(1 for t in ph if t.get("status") in CLOSED)
    lines = [f"Integration pipeline (docs/integration-pipeline.md): {done} of {len(ph)} phases done ({pct(done, len(ph))})."]
    lines += [f"- {t['id']}: {t.get('status')}" for t in ph]
    return lines


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=os.path.join(ROOT, "docs", "progress.md"))
    ap.add_argument("--no-gh", action="store_true")
    a = ap.parse_args()
    ts = tasks()
    now = datetime.datetime.now(datetime.timezone.utc)
    day = now.strftime("%Y-%m-%d")
    body = [f"# Progress", "", f"Generated {now:%Y-%m-%d %H:%M} UTC by tools/harness/progress_report.py "
            "(task store, gh, tools/progress.py). Regenerate; don't edit by hand.", "",
            "## Tasks by track", ""] + track_table(ts) + [
            "", f"## Finished or shipped on {day} (UTC)", ""] + (finished_on(ts, day) or ["(none)"]) + [
            "", "## Upstream", ""] + upstream(a.no_gh) + [
            "", "## Client coverage (tools/progress.py)", ""] + client_metrics() + [
            "", "## Harness", ""] + phases(ts) + [""]
    with open(a.out, "w") as f:
        f.write("\n".join(body))
    print(f"wrote {os.path.relpath(a.out, ROOT)}")


if __name__ == "__main__":
    main()
