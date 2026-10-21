"""Clock, files and names. Times are stored in UTC ISO-8601 (`2026-10-20T23:50:00Z`) and shown to the user in
US Eastern (feedback-local-times)."""
import datetime as dt
import json
import os
import re
import tempfile
import zoneinfo

UTC = dt.timezone.utc
LOCAL = zoneinfo.ZoneInfo("America/New_York")
_FROZEN = None   # tests: freeze the clock


def now():
    return _FROZEN or dt.datetime.now(UTC).replace(microsecond=0)


def freeze(t):
    """Tests: set the clock (a datetime or ISO string); None unfreezes."""
    global _FROZEN
    _FROZEN = parse(t) if isinstance(t, str) else t


def iso(t=None):
    return (t or now()).astimezone(UTC).strftime("%Y-%m-%dT%H:%M:%SZ")


def parse(s):
    if not s:
        return None
    if isinstance(s, dt.datetime):
        return s if s.tzinfo else s.replace(tzinfo=UTC)
    s = str(s)
    if re.fullmatch(r"\d{4}-\d\d-\d\dT\d\d:\d\d:\d\dZ", s):
        return dt.datetime.strptime(s, "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=UTC)
    try:
        t = dt.datetime.fromisoformat(s.replace("Z", "+00:00"))
    except ValueError:
        return None
    return t if t.tzinfo else t.replace(tzinfo=UTC)


def local(t):
    """A UTC time as the user reads it: `7:50 PM EDT 10-20`."""
    t = parse(t) if not isinstance(t, dt.datetime) else t
    if t is None:
        return "-"
    lt = t.astimezone(LOCAL)
    return lt.strftime("%I:%M %p %Z %m-%d").lstrip("0")


def ago(t, ref=None):
    """`23 min`, `3.5 h`, `2 d` since t."""
    t = parse(t)
    if t is None:
        return "-"
    s = ((ref or now()) - t).total_seconds()
    if s < 0:
        s = 0
    if s < 3600:
        return f"{int(s // 60)} min"
    if s < 86400:
        return f"{s / 3600:.1f} h"
    return f"{s / 86400:.1f} d"


def minutes_since(t, ref=None):
    t = parse(t)
    return None if t is None else ((ref or now()) - t).total_seconds() / 60


def atomic_write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    fd, tmp = tempfile.mkstemp(dir=os.path.dirname(path), prefix=".tmp-")
    with os.fdopen(fd, "w") as f:
        f.write(text)
    os.replace(tmp, path)


def write_json(path, obj):
    atomic_write(path, json.dumps(obj, indent=1, sort_keys=True) + "\n")


def read_json(path, default=None):
    try:
        with open(path) as f:
            return json.load(f)
    except (OSError, ValueError):
        return default


SLUG = re.compile(r"[a-z0-9][a-z0-9.-]{1,60}")


def slug_ok(s):
    return bool(s) and bool(SLUG.fullmatch(s))


def slugify(text):
    return re.sub(r"[^a-z0-9]+", "-", text.lower()).strip("-")[:60]


def short(sha, n=9):
    return (sha or "")[:n]


def pid_alive(pid):
    if not pid:
        return False
    try:
        os.kill(int(pid), 0)
        return True
    except (OSError, ValueError):
        return False
