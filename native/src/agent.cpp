// Introspection agent: a read-only, line-based TCP server bound to 127.0.0.1
// inside the app, reached from the host with `adb forward tcp:27183 tcp:27183`.
// Off unless `setprop debug.osrsrl.agent 1` (adb shell only); every connection
// must first send `auth <token>`, where the random token is only written to
// logcat (unreadable by other apps).
// Frida can't instrument arm64 code under BlueStacks' translation layer, so
// this is how the host-side tooling (tools/agent.py) reads game memory.
//
// Commands (one per line, numbers in hex without 0x unless noted):
//   base <substr>             -> module load base
//   maps                      -> /proc/self/maps, terminated by "."
//   read <addr> <len>         -> hex bytes (len <= 16 MiB)
//   snap <slot> <addr> <len>  -> stores a copy (slots 0-7)
//   diff <slot> <maxhits>     -> "off old new" per changed u32, then "."; re-snaps
#include <arpa/inet.h>
#include <netinet/in.h>
#include <pthread.h>
#include <sys/socket.h>
#include <sys/system_properties.h>
#include <sys/uio.h>
#include <unistd.h>

#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <fstream>
#include <sstream>
#include <string>
#include <vector>

#include "agent.h"
#include "got_hook.h"
#include "log.h"

namespace {

constexpr uint16_t kPort = 27183;
char g_token[33];

struct Snapshot {
    uintptr_t addr = 0;
    std::vector<uint8_t> data;
};
Snapshot g_snaps[8];

// process_vm_readv on ourselves fails cleanly (EFAULT) on unmapped memory
// instead of crashing the game.
bool safe_read(uintptr_t addr, void* out, size_t len) {
    iovec local{out, len};
    iovec remote{reinterpret_cast<void*>(addr), len};
    return process_vm_readv(getpid(), &local, 1, &remote, 1, 0) == (ssize_t)len;
}

void send_all(int fd, const std::string& s) {
    size_t off = 0;
    while (off < s.size()) {
        ssize_t n = send(fd, s.data() + off, s.size() - off, MSG_NOSIGNAL);
        if (n <= 0) return;
        off += n;
    }
}

std::string to_hex(const uint8_t* p, size_t n) {
    static const char* digits = "0123456789abcdef";
    std::string out(n * 2, '0');
    for (size_t i = 0; i < n; i++) {
        out[i * 2] = digits[p[i] >> 4];
        out[i * 2 + 1] = digits[p[i] & 15];
    }
    return out;
}

std::string handle(const std::string& line) {
    std::istringstream in(line);
    std::string cmd;
    in >> cmd;
    if (cmd == "base") {
        std::string name;
        in >> name;
        char buf[32];
        snprintf(buf, sizeof buf, "%lx\n", (unsigned long)module_base(name.c_str()));
        return buf;
    }
    if (cmd == "maps") {
        std::ifstream f("/proc/self/maps");
        std::stringstream ss;
        ss << f.rdbuf() << ".\n";
        return ss.str();
    }
    if (cmd == "read") {
        uintptr_t addr = 0;
        size_t len = 0;
        in >> std::hex >> addr >> len;
        if (len == 0 || len > (16u << 20)) return "err len\n";
        std::vector<uint8_t> buf(len);
        if (!safe_read(addr, buf.data(), len)) return "err fault\n";
        return to_hex(buf.data(), len) + "\n";
    }
    if (cmd == "snap") {
        unsigned slot = 0;
        uintptr_t addr = 0;
        size_t len = 0;
        in >> std::hex >> slot >> addr >> len;
        if (slot >= 8 || len == 0 || len > (64u << 20)) return "err args\n";
        Snapshot& s = g_snaps[slot];
        s.addr = addr;
        s.data.resize(len);
        return safe_read(addr, s.data.data(), len) ? "ok\n" : "err fault\n";
    }
    if (cmd == "diff") {
        unsigned slot = 0, max_hits = 0;
        in >> std::hex >> slot >> max_hits;
        if (slot >= 8 || g_snaps[slot].data.empty()) return "err slot\n";
        Snapshot& s = g_snaps[slot];
        std::vector<uint8_t> now(s.data.size());
        if (!safe_read(s.addr, now.data(), now.size())) return "err fault\n";
        std::string out;
        unsigned hits = 0;
        for (size_t off = 0; off + 4 <= now.size() && hits < max_hits; off += 4) {
            uint32_t a, b;
            memcpy(&a, &s.data[off], 4);
            memcpy(&b, &now[off], 4);
            if (a == b) continue;
            char buf[64];
            snprintf(buf, sizeof buf, "%zx %x %x\n", off, a, b);
            out += buf;
            hits++;
        }
        s.data.swap(now);
        return out + ".\n";
    }
    return "err unknown\n";
}

void* server_thread(void*) {
    pthread_setname_np(pthread_self(), "osrsrl-agent");
    int srv = socket(AF_INET, SOCK_STREAM, 0);
    int one = 1;
    setsockopt(srv, SOL_SOCKET, SO_REUSEADDR, &one, sizeof one);
    sockaddr_in addr{};
    addr.sin_family = AF_INET;
    addr.sin_port = htons(kPort);
    addr.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
    if (bind(srv, reinterpret_cast<sockaddr*>(&addr), sizeof addr) != 0 || listen(srv, 1) != 0) {
        LOGE("agent: bind/listen on %d failed", kPort);
        return nullptr;
    }
    LOGI("agent listening on 127.0.0.1:%d", kPort);
    for (;;) {
        int fd = accept(srv, nullptr, nullptr);
        if (fd < 0) continue;
        std::string pending;
        bool authed = false;
        char buf[4096];
        ssize_t n;
        while ((n = recv(fd, buf, sizeof buf, 0)) > 0 && (authed || pending.size() < 64)) {
            pending.append(buf, n);
            size_t nl;
            while ((nl = pending.find('\n')) != std::string::npos) {
                std::string line = pending.substr(0, nl);
                pending.erase(0, nl + 1);
                if (!authed) {
                    authed = line == std::string("auth ") + g_token;
                    if (!authed) break;
                    send_all(fd, "ok\n");
                    continue;
                }
                send_all(fd, handle(line));
            }
        }
        close(fd);
    }
}

}  // namespace

void agent_start() {
    char value[PROP_VALUE_MAX] = {};
    __system_property_get("debug.osrsrl.agent", value);
    if (strcmp(value, "1") != 0) return;
    uint8_t raw[16];
    arc4random_buf(raw, sizeof raw);
    snprintf(g_token, sizeof g_token, "%s", to_hex(raw, sizeof raw).c_str());
    LOGI("agent token %s", g_token);
    pthread_t t;
    pthread_create(&t, nullptr, server_thread, nullptr);
    pthread_detach(t);
}
