#!/usr/bin/env bash
# MCP stdio server for OpenRune-Developer-Tools in the isolated desktop RuneLite on Windows (see
# tools/multiclient/desktop_devtools_mcp.py). Register: {"command": "<repo>/tools/multiclient/desktop-devtools-mcp.sh"}.
exec python3 -I "$(dirname "$(readlink -f "$0")")/desktop_devtools_mcp.py" "$@"
