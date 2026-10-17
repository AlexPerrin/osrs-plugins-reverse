#!/usr/bin/env python3
"""stdio <-> HTTP bridge for OpenRune-Developer-Tools in the isolated desktop RuneLite (Windows).

The plugin's MCP server binds Windows 127.0.0.1:7780 (upstream build, no token). WSL runs in NAT
mode, so Windows loopback isn't reachable from here; each JSON-RPC line from stdin is POSTed through
Windows' own curl.exe (WSL interop) and the response is written to stdout as one line.
Notifications (202, no body) print nothing. Nothing is exposed beyond Windows loopback.

Start the client first: tools/multiclient/desktop.sh start (docs/multi-client.md).
  tools/multiclient/desktop-devtools-mcp.sh                         # MCP stdio server
  tools/multiclient/desktop-devtools-mcp.sh --call tools/list       # one request, for testing
  tools/multiclient/desktop-devtools-mcp.sh --tool get_client_state '{}'   # one tools/call
Env: DESKTOP_DEVTOOLS_PORT (default 7780), CURL_EXE.
"""
import json
import os
import subprocess
import sys

CURL = os.environ.get("CURL_EXE", "/mnt/c/Windows/System32/curl.exe")
PORT = os.environ.get("DESKTOP_DEVTOOLS_PORT", "7780")
URL = f"http://127.0.0.1:{PORT}/mcp"


def log(msg):
    print(f"desktop-devtools-mcp: {msg}", file=sys.stderr, flush=True)


def post(body: bytes):
    p = subprocess.run([CURL, "-s", "-S", "--max-time", "180", "-o", "-", "-w", "\n%{http_code}",
                        "-H", "Content-Type: application/json",
                        "-H", "Accept: application/json, text/event-stream",
                        "--data-binary", "@-", URL],
                       input=body, capture_output=True, timeout=200)
    out = p.stdout.replace(b"\r\n", b"\n")
    payload, _, code = out.rpartition(b"\n")
    status = int(code) if code.strip().isdigit() else 0
    if status == 0:
        raise RuntimeError(f"no HTTP response from {URL} (is the desktop client running with "
                           f"OpenRune-DeveloperTools enabled?) {p.stderr[:200]!r}")
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
                              "error": {"code": -32000, "message": f"desktop devtools bridge: {e}"}}), flush=True)
        log(str(e))
        return
    if payload.strip() and "id" in msg:
        text = payload.decode("utf-8", "replace").strip()
        if text.startswith("data:"):  # SSE framing: keep the JSON of the last data line
            text = [l[5:].strip() for l in text.splitlines() if l.startswith("data:")][-1]
        print(json.dumps(json.loads(text), separators=(",", ":")) if text.startswith("{") else text, flush=True)


def main():
    if len(sys.argv) >= 3 and sys.argv[1] in ("--call", "--tool"):
        params = json.loads(sys.argv[3]) if len(sys.argv) > 3 else {}
        if sys.argv[1] == "--tool":
            method, params = "tools/call", {"name": sys.argv[2], "arguments": params}
        else:
            method = sys.argv[2]
        handle(json.dumps({"jsonrpc": "2.0", "id": 1, "method": method, "params": params}))
        return
    for line in sys.stdin:
        handle(line)


if __name__ == "__main__":
    main()
