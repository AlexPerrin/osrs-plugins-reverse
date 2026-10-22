"""harnessd: the loop and the server (PRD section 10).

  run(interval, once=False)   tick.tick() every `interval` seconds; errors in a step never stop the loop (an
                              `error` event); SIGTERM ends it after the current tick; `harness/state/daemon.json`
                              holds pid, started, last tick, interval, port
  serve(port)                 a thread with http.server on 127.0.0.1:<port>: GET / (dashboard.html), /api/view
                              (JSON), /events?kind=&change=&limit= (JSON), /text; POST /api/approve {id, note},
                              /api/deny {id, note} (approvals.approve / deny); nothing else writes
  start(interval, port)       detach (double fork or setsid) and write daemon.json; stop() sends SIGTERM to the
                              recorded pid; status() reads daemon.json and whether the pid is alive
  digest(since)               the one notification per tick with news (BR-A7): new approvals, decisions, failed
                              holds, deadlocks, deploys and rollbacks, as text; written to
                              harness/state/digest.md and returned (the notification channel is an open decision)

Files under harness/state/: daemon.json {pid, started, interval, port, last_tick, tick_seconds, ticks},
daemon.log (the detached child's stdio), digest.json {seen: n events consumed, last: ts}, digest.md.
"""
import http.server
import json
import os
import signal
import sys
import threading
import time
import urllib.parse

from . import events, state_dir, store
from .util import iso, local, now, pid_alive, read_json, write_json

PORT = None          # set by serve(); recorded in daemon.json
STOP = threading.Event()
STOP_WAIT_S = 15


def _path(name):
    return os.path.join(state_dir(), name)


def _daemon_json():
    return _path("daemon.json")


# --- the loop -------------------------------------------------------------------------------------------------------

def _install_signals():
    def handler(signum, frame):
        STOP.set()
    try:
        signal.signal(signal.SIGTERM, handler)
        signal.signal(signal.SIGINT, handler)
    except ValueError:   # not the main thread (tests)
        pass


def run(interval=60, once=False):
    """The loop. Returns the daemon record after the last tick."""
    STOP.clear()
    _install_signals()
    rec = {"pid": os.getpid(), "started": iso(), "interval": interval, "port": PORT, "last_tick": None,
           "tick_seconds": None, "ticks": 0}
    write_json(_daemon_json(), rec)
    while True:
        t0 = time.monotonic()
        try:
            from . import tick
            actions = tick.tick()
        except Exception as e:   # a step never stops the loop
            actions = None
            events.emit("error", step="tick", error=repr(e))
        try:
            digest()
        except Exception as e:
            events.emit("error", step="digest", error=repr(e))
        rec.update(last_tick=iso(), tick_seconds=round(time.monotonic() - t0, 2), ticks=rec["ticks"] + 1,
                   actions=len(actions) if isinstance(actions, list) else None)
        write_json(_daemon_json(), rec)
        if once or STOP.is_set():
            break
        if STOP.wait(max(0.0, interval - (time.monotonic() - t0))):
            break
    rec["stopped"] = iso()
    write_json(_daemon_json(), rec)
    return rec


# --- the server -----------------------------------------------------------------------------------------------------

class Handler(http.server.BaseHTTPRequestHandler):
    server_version = "harnessd/3"

    def log_message(self, fmt, *args):   # quiet; the daemon log is for errors
        pass

    def _send(self, code, body, ctype="application/json; charset=utf-8"):
        data = body.encode("utf-8") if isinstance(body, str) else body
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(data)

    def _json(self, code, obj):
        self._send(code, json.dumps(obj, sort_keys=True, default=str))

    def do_GET(self):
        from . import dashboard
        u = urllib.parse.urlsplit(self.path)
        q = urllib.parse.parse_qs(u.query)
        try:
            if u.path == "/":
                self._send(200, dashboard.html(dashboard.view()), "text/html; charset=utf-8")
            elif u.path == "/api/view":
                self._json(200, dashboard.view())
            elif u.path == "/text":
                self._send(200, dashboard.text(dashboard.view()), "text/plain; charset=utf-8")
            elif u.path == "/events":
                kind = q.get("kind", [""])[0]
                limit = q.get("limit", [""])[0]
                self._json(200, events.read(kinds=[kind] if kind else None, change=q.get("change", [""])[0] or None,
                                            since=q.get("since", [""])[0] or None,
                                            limit=int(limit) if limit.isdigit() else 500))
            else:
                self._json(404, {"ok": False, "error": "not found"})
        except Exception as e:
            self._json(500, {"ok": False, "error": repr(e)})

    do_HEAD = do_GET

    def do_POST(self):
        u = urllib.parse.urlsplit(self.path)
        what = {"/api/approve": "approve", "/api/deny": "deny"}.get(u.path)
        if not what:
            self._json(404, {"ok": False, "error": "not found"})
            return
        try:
            n = int(self.headers.get("Content-Length") or 0)
            body = json.loads(self.rfile.read(n) or b"{}") if n else {}
        except ValueError:
            self._json(400, {"ok": False, "error": "bad json"})
            return
        aid, note = body.get("id"), body.get("note") or ""
        if not aid:
            self._json(400, {"ok": False, "error": "id missing"})
            return
        try:
            from . import approvals
            fn = getattr(approvals, what)
            rec = fn(aid, note)
        except NotImplementedError:
            self._json(501, {"ok": False, "error": f"approvals.{what} not implemented"})
            return
        except ImportError as e:
            self._json(501, {"ok": False, "error": repr(e)})
            return
        except Exception as e:
            self._json(500, {"ok": False, "error": repr(e)})
            return
        state = rec.get("state") if isinstance(rec, dict) else (store.rec_load("approvals", aid) or {}).get("state")
        self._json(200, {"ok": True, "id": aid, "state": state})


def serve(port=7790):
    """Start the dashboard server on 127.0.0.1:port in a daemon thread; returns the server (shutdown() stops it;
    server_address gives the bound port, useful with port 0)."""
    global PORT
    srv = http.server.ThreadingHTTPServer(("127.0.0.1", port), Handler)
    srv.daemon_threads = True
    PORT = srv.server_address[1]
    th = threading.Thread(target=srv.serve_forever, kwargs={"poll_interval": 0.5}, daemon=True, name="harnessd-http")
    th.start()
    srv.thread = th
    return srv


# --- process control ------------------------------------------------------------------------------------------------

def _detach(log_name, banner, main):
    """Double-fork a process (reparented to init) running main(); returns its pid. stdio goes to the log."""
    os.makedirs(state_dir(), exist_ok=True)
    r, w = os.pipe()
    pid = os.fork()
    if pid:   # the parent: read the grandchild's pid, reap the intermediate child
        os.close(w)
        data = b""
        while True:
            chunk = os.read(r, 32)
            if not chunk:
                break
            data += chunk
        os.close(r)
        os.waitpid(pid, 0)
        if not data.strip().isdigit():
            raise RuntimeError(f"failed to start (see harness/state/{log_name})")
        return int(data)
    try:   # the intermediate child: new session, fork again, report the pid, exit
        os.close(r)
        os.setsid()
        pid2 = os.fork()
        if pid2:
            os.write(w, str(pid2).encode())
            os.close(w)
            os._exit(0)
        os.close(w)
        log = os.open(_path(log_name), os.O_WRONLY | os.O_CREAT | os.O_APPEND, 0o644)
        null = os.open(os.devnull, os.O_RDONLY)
        os.dup2(null, 0)
        os.dup2(log, 1)
        os.dup2(log, 2)
        os.close(null)
        os.close(log)
        sys.stdout.reconfigure(line_buffering=True)
        print(f"{banner} {os.getpid()} starting {iso()}", flush=True)
        main()
        print(f"{banner} {os.getpid()} stopped {iso()}", flush=True)
    except BaseException as e:
        try:
            print(f"{banner} {os.getpid()} died: {e!r}", flush=True)
        finally:
            os._exit(1)
    os._exit(0)


def start(interval=60, port=None):
    """The tick loop as a detached process (harness/state/daemon.json, daemon.log); returns its pid. It serves no
    page: the dashboard is its own process (start_server), so the loop restarts without closing the port."""
    st = status()
    if st.get("alive"):
        raise RuntimeError(f"harnessd already running (pid {st.get('pid')})")
    global PORT
    PORT = port or PORT
    return _detach("daemon.log", "harnessd", lambda: run(interval))


def _server_json():
    return _path("server.json")


def start_server(port=7790):
    """The dashboard server as a detached process (harness/state/server.json, server.log); returns its pid."""
    st = server_status()
    if st.get("alive"):
        raise RuntimeError(f"dashboard server already running (pid {st.get('pid')}, port {st.get('port')})")

    def main():
        srv = serve(port)
        write_json(_server_json(), {"pid": os.getpid(), "port": srv.server_address[1], "started": iso()})
        stop_ev = threading.Event()
        for sig in (signal.SIGTERM, signal.SIGINT):
            signal.signal(sig, lambda *_: stop_ev.set())
        stop_ev.wait()
        srv.shutdown()
        write_json(_server_json(), {"pid": None, "port": port, "stopped": iso()})
    return _detach("server.log", "harnessd-http", main)


def stop_server():
    d = read_json(_server_json(), {}) or {}
    pid = d.get("pid")
    if not pid_alive(pid):
        return {"pid": pid, "stopped": True, "text": "dashboard server not running"}
    os.kill(int(pid), signal.SIGTERM)
    for _ in range(STOP_WAIT_S * 10):
        if not pid_alive(pid):
            return {"pid": pid, "stopped": True, "text": f"dashboard server {pid} stopped"}
        time.sleep(0.1)
    return {"pid": pid, "stopped": False, "text": f"dashboard server {pid} still running"}


def server_status():
    d = read_json(_server_json(), {}) or {}
    d["alive"] = pid_alive(d.get("pid"))
    return d


def stop():
    """SIGTERM the recorded pid and wait up to 15 s; returns {pid, stopped, text}."""
    d = read_json(_daemon_json(), {}) or {}
    pid = d.get("pid")
    if not pid_alive(pid):
        return {"pid": pid, "stopped": True, "text": "harnessd not running" if not pid else f"pid {pid} already gone"}
    os.kill(int(pid), signal.SIGTERM)
    for _ in range(STOP_WAIT_S * 10):
        if not pid_alive(pid):
            return {"pid": pid, "stopped": True, "text": f"harnessd {pid} stopped"}
        time.sleep(0.1)
    return {"pid": pid, "stopped": False, "text": f"harnessd {pid} still alive after {STOP_WAIT_S} s"}


def status():
    d = dict(read_json(_daemon_json(), {}) or {})
    d["alive"] = pid_alive(d.get("pid"))
    d["last_tick_local"] = local(d["last_tick"]) if d.get("last_tick") else None
    return d


# --- the digest (BR-A7) ---------------------------------------------------------------------------------------------

def _news(e):
    """One digest line for an event with news, else None."""
    k = e.get("kind")
    if k == "approval":
        act = e.get("action") or e.get("state")
        if act == "created":
            akind = e.get("approval_kind") or ""
            what = "decision needed" if akind == "decision" else "new approval"
            return f"{what}: {e.get('approval')} ({' '.join(x for x in (akind, e.get('change') or e.get('batch')) if x)})"
        if act in ("approved", "denied", "done", "failed", "stale"):
            return f"approval {e.get('approval')} {act}" + (f": {e['note']}" if e.get("note") else "")
    elif k == "hold" and e.get("hold") == "failed":
        return f"{e.get('change')} failed: {e.get('reason') or ''}".rstrip()
    elif k == "heal" and e.get("action") == "deadlock":
        return f"deadlock resolved: {e.get('detail') or e.get('resource') or ''} (detect {e.get('detect_min')} min, recover {e.get('recover_min')} min)"
    elif k == "deploy":
        return f"deploy {e.get('result') or ''}: integration {str(e.get('integration') or '')[:9]} image {e.get('image') or ''}"
    elif k == "batch" and e.get("step") in ("exception", "rolled-back", "failed"):
        return f"batch {e.get('batch')} {e.get('step')}: {e.get('detail') or ''}".rstrip()
    return None


def digest(since=None):
    """The news since the last digest (or `since`), appended to digest.md; "" when there is none."""
    state = read_json(_path("digest.json"), {}) or {}
    if since:
        evs = events.read(since=since)
        seen = None
    else:
        allev = events.read()
        seen = state.get("seen") or 0
        if seen > len(allev):   # the log was replaced
            seen = 0
        evs = allev[seen:]
        seen = len(allev)
    lines = [f"- {local(e.get('ts'))}  {t}" for e in evs for t in [_news(e)] if t]
    if seen is not None:
        write_json(_path("digest.json"), {"seen": seen, "last": iso()})
    if not lines:
        return ""
    text = f"## {local(now())}\n" + "\n".join(lines) + "\n"
    p = _path("digest.md")
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, "a") as f:
        f.write(text + "\n")
    events.emit("digest", lines=len(lines))
    return text
