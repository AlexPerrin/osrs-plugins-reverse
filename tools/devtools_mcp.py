#!/usr/bin/env python3
"""stdio <-> HTTP bridge for OpenRune-Developer-Tools' MCP server running in the Android plugin host.

The server binds the device's 127.0.0.1 only and needs a per-process token, which the host writes
to logcat ("httpserver token <hex> port <n>"). Each JSON-RPC line from stdin is POSTed to /mcp
through `adb shell -T nc 127.0.0.1 <port>` (a Windows `adb forward` isn't reachable from WSL), and
the response is written to stdout as one line. Notifications (202, no body) print nothing.

Enable on the device first: push the devtools plugin jar, `adb shell setprop debug.osrsrl.devtools 1`,
restart the game (docs/plugin-host.md). Env: ADB, ANDROID_SERIAL (as tools/agent.py).
  tools/devtools-mcp.sh                       # MCP stdio server (register in .mcp.json)
  tools/devtools_mcp.py --call tools/list     # one request, for testing
"""
import json
import os
import re
import subprocess
import sys

ADB = os.environ.get("ADB", "/mnt/c/Users/Alex/AppData/Local/Android/Sdk/platform-tools/adb.exe")
SERIAL = os.environ.get("ANDROID_SERIAL", "127.0.0.1:5555")
_auth = None


def log(msg):
    print(f"devtools-mcp: {msg}", file=sys.stderr, flush=True)


def auth(refresh=False):
    global _auth
    if _auth is None or refresh:
        out = subprocess.run([ADB, "-s", SERIAL, "logcat", "-d", "-s", "osrsrl-host"],
                             capture_output=True, text=True).stdout
        found = re.findall(r"httpserver token ([0-9a-f]{32}) port (\d+)", out)
        if not found:
            raise RuntimeError("no devtools server in logcat (push the plugin, setprop debug.osrsrl.devtools 1, "
                               "restart the game; logcat may have rotated: restart again)")
        _auth = found[-1]
    return _auth


def post(body: bytes, retry=True):
    token, port = auth()
    req = (f"POST /mcp HTTP/1.1\r\nHost: 127.0.0.1:{port}\r\nContent-Type: application/json\r\n"
           f"Accept: application/json, text/event-stream\r\nAuthorization: Bearer {token}\r\n"
           f"Content-Length: {len(body)}\r\nConnection: close\r\n\r\n").encode() + body
    p = subprocess.run([ADB, "-s", SERIAL, "shell", "-T", "nc", "127.0.0.1", port],
                       input=req, capture_output=True, timeout=180)
    # Windows adb's `shell -T` turns every LF of the output into CRLF; undo it (exact inverse).
    raw = p.stdout.replace(b"\r\n", b"\n")
    head, _, payload = raw.partition(b"\r\n\r\n")
    status = int(head.split(b" ", 2)[1]) if head.startswith(b"HTTP/") else 0
    if status == 401 and retry:
        auth(refresh=True)
        return post(body, retry=False)
    if status == 0:
        raise RuntimeError(f"no HTTP response (is the game running with the server on?) {p.stderr[:200]!r}")
    return status, payload


def handle(line):
    line = line.strip()
    if not line:
        return
    try:
        msg = json.loads(line)
    except ValueError:
        return
    try:
        status, payload = post(line.encode())
    except Exception as e:  # report to the client instead of dying
        if "id" in msg:
            print(json.dumps({"jsonrpc": "2.0", "id": msg["id"],
                              "error": {"code": -32000, "message": f"devtools bridge: {e}"}}), flush=True)
        log(str(e))
        return
    if payload.strip() and "id" in msg:
        text = payload.decode("utf-8", "replace").strip()
        print(json.dumps(json.loads(text), separators=(",", ":")) if text.startswith("{") else text, flush=True)


def main():
    if len(sys.argv) >= 3 and sys.argv[1] == "--call":
        params = json.loads(sys.argv[3]) if len(sys.argv) > 3 else {}
        handle(json.dumps({"jsonrpc": "2.0", "id": 1, "method": sys.argv[2], "params": params}))
        return
    for line in sys.stdin:
        handle(line)


if __name__ == "__main__":
    main()
