#include "client_thread.h"

#include <sys/mman.h>
#include <unistd.h>

#include <atomic>
#include <cstdio>
#include <cstring>
#include <deque>
#include <mutex>

#include "game.h"
#include "log.h"

namespace client_thread {
namespace {

using DoCycleFn = void (*)(void*);
DoCycleFn g_orig = nullptr;
std::atomic<pid_t> g_tid{0};
std::atomic<uint64_t> g_cycles{0};

std::mutex g_listeners_mutex;
std::vector<Listener> g_listeners;

std::mutex g_tasks_mutex;
std::vector<std::function<void()>> g_tasks;

std::mutex g_log_mutex;
std::deque<std::string> g_log;
constexpr size_t kLogSize = 6;

// Game-thread-only state for deriving events.
int g_last_state = -1;
int g_last_tick = -1;
std::vector<int32_t> g_varp_shadow;

int g_next_message = -1;
std::mutex g_messages_mutex;
std::deque<game::ChatLine> g_messages;  // newest last, guarded by g_messages_mutex
constexpr size_t kMessageHistory = 256;

std::mutex g_npcs_mutex;
std::vector<game::Npc> g_npcs;  // guarded by g_npcs_mutex; written only on the game thread

const char* name(EventType t) {
    switch (t) {
        case EventType::ClientTick: return "ClientTick";
        case EventType::GameTick: return "GameTick";
        case EventType::GameStateChanged: return "GameStateChanged";
        case EventType::VarbitChanged: return "VarbitChanged";
        case EventType::NpcSpawned: return "NpcSpawned";
        case EventType::NpcDespawned: return "NpcDespawned";
        case EventType::ChatMessage: return "ChatMessage";
    }
    return "?";
}

void post(const Event& e) {
    if (e.type != EventType::ClientTick) {
        char line[96];
        snprintf(line, sizeof line, "%llu %s %d %d", (unsigned long long)g_cycles.load(), name(e.type), e.a, e.b);
        std::lock_guard lock(g_log_mutex);
        g_log.emplace_back(line);
        if (g_log.size() > kLogSize) g_log.pop_front();
    }
    std::vector<Listener> listeners;
    {
        std::lock_guard lock(g_listeners_mutex);
        listeners = g_listeners;
    }
    for (auto& l : listeners) l(e);
}

void diff_varps() {
    int n = game::varp_count();
    if (n <= 0 || n > 1 << 16) return;
    std::vector<int32_t> now(n);
    for (int i = 0; i < n; i++) now[i] = game::varp(i);
    if (g_varp_shadow.size() == now.size()) {
        if (memcmp(now.data(), g_varp_shadow.data(), n * sizeof(int32_t)) != 0)
            for (int i = 0; i < n; i++)
                if (now[i] != g_varp_shadow[i]) post({EventType::VarbitChanged, i, now[i]});
    }
    g_varp_shadow.swap(now);
}

void diff_npcs() {
    std::vector<game::Npc> now = game::read_npcs();
    std::vector<game::Npc> old;
    {
        std::lock_guard lock(g_npcs_mutex);
        old.swap(g_npcs);
        g_npcs = now;
    }
    auto same = [](const game::Npc& a, const game::Npc& b) { return a.index == b.index && a.address == b.address; };
    for (const auto& o : old) {
        bool kept = false;
        for (const auto& n : now) kept |= same(o, n);
        if (!kept) post({EventType::NpcDespawned, o.index, o.id});
    }
    for (const auto& n : now) {
        bool existed = false;
        for (const auto& o : old) existed |= same(o, n);
        if (!existed) post({EventType::NpcSpawned, n.index, n.id});
    }
}

void poll_messages() {
    int next = game::next_message_id();
    if (g_next_message < 0 || next < g_next_message) {  // first cycle: don't replay history
        g_next_message = next;
        return;
    }
    for (; g_next_message < next; g_next_message++) {
        game::ChatLine line;
        if (!game::read_message(g_next_message, line)) continue;
        {
            std::lock_guard lock(g_messages_mutex);
            g_messages.push_back(line);
            if (g_messages.size() > kMessageHistory) g_messages.pop_front();
        }
        post({EventType::ChatMessage, line.id, line.type});
    }
}

void after_cycle() {
    g_cycles++;
    post({EventType::ClientTick});

    int state = game::game_state();
    if (state != g_last_state) {
        post({EventType::GameStateChanged, state, g_last_state});
        g_last_state = state;
    }
    int tick = game::server_tick();
    if (tick != g_last_tick) {
        // The counter is reset on login; only an increase is a game tick.
        if (g_last_tick >= 0 && tick > g_last_tick) post({EventType::GameTick, tick});
        g_last_tick = tick;
    }
    diff_varps();
    diff_npcs();
    poll_messages();

    std::vector<std::function<void()>> tasks;
    {
        std::lock_guard lock(g_tasks_mutex);
        tasks.swap(g_tasks);
    }
    for (auto& t : tasks) t();
}

void hook_do_cycle(void* self) {
    g_tid.store(gettid(), std::memory_order_relaxed);
    g_orig(self);
    after_cycle();
}

}  // namespace

bool install() {
    uintptr_t vtable = game::client_vtable();
    if (!vtable) return false;
    auto** slot = reinterpret_cast<void**>(vtable + 5 * sizeof(void*));
    if (reinterpret_cast<uintptr_t>(*slot) != game::do_cycle()) {
        LOGE("client_thread: vtable slot 5 is %p, expected %p; not hooking", *slot,
             reinterpret_cast<void*>(game::do_cycle()));
        return false;
    }
    long page = sysconf(_SC_PAGESIZE);
    auto start = reinterpret_cast<void*>(reinterpret_cast<uintptr_t>(slot) & ~(page - 1));
    if (mprotect(start, page, PROT_READ | PROT_WRITE) != 0) {
        LOGE("client_thread: mprotect failed");
        return false;
    }
    g_orig = reinterpret_cast<DoCycleFn>(*slot);
    *slot = reinterpret_cast<void*>(hook_do_cycle);
    mprotect(start, page, PROT_READ);  // .data.rel.ro is read-only after relocation
    LOGI("client_thread: doCycle hooked");
    return true;
}

bool installed() { return g_orig != nullptr; }
bool on_client_thread() { return g_tid.load(std::memory_order_relaxed) == gettid(); }

void subscribe(Listener listener) {
    std::lock_guard lock(g_listeners_mutex);
    g_listeners.push_back(std::move(listener));
}

void invoke(std::function<void()> task) {
    if (on_client_thread()) {
        task();
        return;
    }
    std::lock_guard lock(g_tasks_mutex);
    g_tasks.push_back(std::move(task));
}

std::vector<std::string> recent_events() {
    std::lock_guard lock(g_log_mutex);
    return {g_log.begin(), g_log.end()};
}

uint64_t cycles() { return g_cycles.load(); }

bool message(int id, game::ChatLine& out) {
    std::lock_guard lock(g_messages_mutex);
    for (auto it = g_messages.rbegin(); it != g_messages.rend(); ++it) {
        if (it->id == id) {
            out = *it;
            return true;
        }
    }
    return false;
}

std::vector<game::Npc> npcs() {
    std::lock_guard lock(g_npcs_mutex);
    return g_npcs;
}

}  // namespace client_thread
