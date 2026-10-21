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
"""
from . import store


def run(interval=60, once=False):
    raise NotImplementedError


def serve(port=7790):
    raise NotImplementedError


def start(interval=60, port=7790):
    raise NotImplementedError


def stop():
    raise NotImplementedError


def status():
    from .util import pid_alive
    d = store.read_json(__import__("os").path.join(__import__("hd").state_dir(), "daemon.json"), {}) if False else None
    return d


def digest(since=None):
    raise NotImplementedError
