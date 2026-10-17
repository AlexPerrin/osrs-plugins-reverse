// Logs the game's outgoing connections and DNS lookups (logcat tag "osrsrl").
// Enabled with `adb shell setprop debug.osrsrl.nettrace 1`; useful when pointing the client at
// the test server in server/.
#include "net_trace.h"

#include <arpa/inet.h>
#include <netdb.h>
#include <sys/socket.h>
#include <sys/system_properties.h>

#include <cstdio>

#include "got_hook.h"
#include "log.h"

namespace {

using ConnectFn = int (*)(int, const sockaddr*, socklen_t);
using GetaddrinfoFn = int (*)(const char*, const char*, const addrinfo*, addrinfo**);
using SendFn = ssize_t (*)(int, const void*, size_t, int);
using RecvFn = ssize_t (*)(int, void*, size_t, int);
ConnectFn g_connect = nullptr;
SendFn g_send = nullptr;
RecvFn g_recv = nullptr;

// Logs up to 160 bytes as text, escaping control characters.
void log_bytes(const char* dir, int fd, const void* buf, ssize_t n) {
    if (n <= 0) {
        LOGI("net: %s fd=%d -> %zd", dir, fd, n);
        return;
    }
    char text[4 * 160 + 1];
    size_t o = 0;
    auto* p = static_cast<const unsigned char*>(buf);
    for (ssize_t i = 0; i < n && i < 160; i++) {
        unsigned char c = p[i];
        if (c >= 0x20 && c < 0x7f && c != '\\') text[o++] = static_cast<char>(c);
        else {
            int written = snprintf(text + o, sizeof text - o, "\\x%02x", c);
            if (written < 0 || static_cast<size_t>(written) >= sizeof text - o) break;
            o += static_cast<size_t>(written);
        }
    }
    text[o] = 0;
    LOGI("net: %s fd=%d n=%zd: %s", dir, fd, n, text);
}

ssize_t hook_send(int fd, const void* buf, size_t len, int flags) {
    ssize_t n = g_send(fd, buf, len, flags);
    log_bytes("send", fd, buf, n);
    return n;
}

ssize_t hook_recv(int fd, void* buf, size_t len, int flags) {
    ssize_t n = g_recv(fd, buf, len, flags);
    log_bytes("recv", fd, buf, n);
    return n;
}
GetaddrinfoFn g_getaddrinfo = nullptr;

int hook_connect(int fd, const sockaddr* addr, socklen_t len) {
    int rc = g_connect(fd, addr, len);
    char host[INET6_ADDRSTRLEN] = "?";
    int port = 0;
    if (addr->sa_family == AF_INET) {
        auto* in = reinterpret_cast<const sockaddr_in*>(addr);
        inet_ntop(AF_INET, &in->sin_addr, host, sizeof host);
        port = ntohs(in->sin_port);
    } else if (addr->sa_family == AF_INET6) {
        auto* in6 = reinterpret_cast<const sockaddr_in6*>(addr);
        inet_ntop(AF_INET6, &in6->sin6_addr, host, sizeof host);
        port = ntohs(in6->sin6_port);
    }
    LOGI("net: connect fd=%d %s:%d -> %d", fd, host, port, rc);
    return rc;
}

int hook_getaddrinfo(const char* node, const char* service, const addrinfo* hints, addrinfo** res) {
    int rc = g_getaddrinfo(node, service, hints, res);
    LOGI("net: getaddrinfo %s:%s -> %d", node ? node : "(null)", service ? service : "(null)", rc);
    return rc;
}

}  // namespace

void net_trace_init(const char* module) {
    char value[PROP_VALUE_MAX] = "";
    if (__system_property_get("debug.osrsrl.nettrace", value) <= 0 || value[0] != '1') return;
    g_connect = reinterpret_cast<ConnectFn>(got_hook(module, "connect", reinterpret_cast<void*>(hook_connect)));
    g_getaddrinfo = reinterpret_cast<GetaddrinfoFn>(
        got_hook(module, "getaddrinfo", reinterpret_cast<void*>(hook_getaddrinfo)));
    g_send = reinterpret_cast<SendFn>(got_hook(module, "send", reinterpret_cast<void*>(hook_send)));
    g_recv = reinterpret_cast<RecvFn>(got_hook(module, "recv", reinterpret_cast<void*>(hook_recv)));
    if (!g_send) g_send = send;
    if (!g_recv) g_recv = recv;
    if (!g_connect) g_connect = connect;
    if (!g_getaddrinfo) g_getaddrinfo = getaddrinfo;
    LOGI("net trace enabled");
}
