#!/usr/bin/env bash
# Network check for plugin jars: lists every class reference that can reach the network, so a
# plugin that bypasses the host's NetGuard (which only guards the shared OkHttp client) is caught
# before it is pinned or pushed.
#   host/plugins/hub/netcheck.sh re/hub-build/<name>.jar ...
# Per jar it prints one verdict line, then the references:
#   NET-RAW   java.net / javax.net I/O classes (URL.openConnection/openStream, URLConnection,
#             HttpURLConnection, Socket, ServerSocket, DatagramSocket, InetAddress, java.net.http.*,
#             javax.net.*): NOT guarded by NetGuard. Don't pin or push without the orchestrator's OK.
#   NET-OKHTTP okhttp3 only: goes through the injected client, so NetGuard applies (GET/HEAD only).
#             Still a network plugin: say so in docs/plugin-host.md, and it needs Network QA.
#   LINKS     LinkBrowser only (opens a browser on a tap; no traffic by itself).
#   clean     none of the above. java.net.URI/URLEncoder/URLDecoder are string helpers and don't count.
# Exit status: 2 if any jar is NET-RAW, else 0.
set -euo pipefail
raw_re='java/net/(URL|URLConnection|HttpURLConnection|JarURLConnection|Socket|ServerSocket|DatagramSocket|MulticastSocket|InetAddress|Inet4Address|Inet6Address|InetSocketAddress|Proxy|ProxySelector|CookieHandler|Authenticator|URLStreamHandler|SocketImpl|http/[A-Za-z$]+)\b|javax/net/'
status=0
for jar in "$@"; do
    tmp=$(mktemp -d)
    unzip -qo "$jar" '*.class' -d "$tmp" 2>/dev/null || true
    raw=""; ok=""; links=""
    # Class references live in each class's constant pool as UTF-8 names; strings(1) finds them.
    while IFS= read -r -d '' cls; do
        s=$(strings -n 6 "$cls")
        name=${cls#"$tmp"/}
        r=$(grep -oE "(java/net/[A-Za-z0-9/\$]+|javax/net/[A-Za-z0-9/\$]+)" <<<"$s" | grep -E "$raw_re" | sort -u || true)
        # java/net/URL by itself is often just a value type (resources, manifests); it only does I/O
        # through openConnection/openStream.
        if [[ -n $r ]] && ! grep -qvE '^java/net/URL$' <<<"$r" && ! grep -qE 'openConnection|openStream' <<<"$s"; then r=""; fi
        [[ -n $r ]] && raw+="$(sed "s|^|  raw: $name -> |" <<<"$r")"$'\n'
        grep -qE 'okhttp3/' <<<"$s" && ok+="  okhttp: $name"$'\n'
        grep -q 'net/runelite/client/util/LinkBrowser' <<<"$s" && links+="  links: $name"$'\n'
    done < <(find "$tmp" -name '*.class' -print0)
    rm -rf "$tmp"
    if [[ -n $raw ]]; then verdict=NET-RAW; status=2
    elif [[ -n $ok ]]; then verdict=NET-OKHTTP
    elif [[ -n $links ]]; then verdict=LINKS
    else verdict=clean; fi
    echo "$verdict $jar"
    printf "%s" "$raw"; printf "%s" "$ok" | head -3; printf "%s" "$links" | head -2
done
exit $status
