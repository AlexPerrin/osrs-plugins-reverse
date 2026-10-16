#include "client_thread.h"

#include <sys/mman.h>
#include <unistd.h>

#include <atomic>
#include <cstdio>
#include <cstring>
#include <deque>
#include <algorithm>
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

std::vector<int> g_open_groups;  // root + open sub-interface groups, sorted

int g_next_message = -1;
std::mutex g_messages_mutex;
std::deque<game::ChatLine> g_messages;  // newest last, guarded by g_messages_mutex
constexpr size_t kMessageHistory = 256;

std::mutex g_inv_mutex;
std::vector<game::Inventory> g_inventories;  // guarded by g_inv_mutex

std::mutex g_players_mutex;
std::vector<game::PlayerInfo> g_players;  // guarded by g_players_mutex

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
        case EventType::WidgetLoaded: return "WidgetLoaded";
        case EventType::WidgetClosed: return "WidgetClosed";
        case EventType::PlayerSpawned: return "PlayerSpawned";
        case EventType::PlayerDespawned: return "PlayerDespawned";
        case EventType::ItemContainerChanged: return "ItemContainerChanged";
        case EventType::StatChanged: return "StatChanged";
        case EventType::MenuOpened: return "MenuOpened";
        case EventType::MenuOptionClicked: return "MenuOptionClicked";
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
    // OpenRune's cache defines far more varps than the stock one; a 1 << 16 bound silently skipped
    // the whole diff there (no VarbitChanged at all).
    static int logged = -1;
    if (n != logged) { LOGI("varps: %d", n); logged = n; }
    if (n <= 0 || n > 1 << 22) return;
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
    // A just-added Npc has no position until its first movement update (fine 0,0, which is never a
    // real position: actors stand on tile centres). RuneLite posts NpcSpawned after placement, and
    // QH's NpcStep rejects an NPC outside its roam range at spawn time, so hold it back until then.
    std::erase_if(now, [](const game::Npc& n) { return n.fine_x == 0 && n.fine_y == 0; });
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

void diff_interfaces() {
    std::vector<int> now;
    if (game::game_state() == 30) {
        int root = game::root_interface();
        if (root >= 0) now.push_back(root);
        for (const auto& sub : game::open_sub_interfaces()) now.push_back(sub.group);
        std::sort(now.begin(), now.end());
        now.erase(std::unique(now.begin(), now.end()), now.end());
    }
    for (int g : g_open_groups)
        if (!std::binary_search(now.begin(), now.end(), g)) post({EventType::WidgetClosed, g});
    for (int g : now)
        if (!std::binary_search(g_open_groups.begin(), g_open_groups.end(), g)) post({EventType::WidgetLoaded, g});
    g_open_groups.swap(now);
}

game::Skills g_skills;
bool g_skills_valid = false;

void diff_skills() {
    if (game::game_state() != 30) {
        g_skills_valid = false;
        return;
    }
    game::Skills now = game::skills();
    if (g_skills_valid) {
        for (int i = 0; i < game::kSkillCount; i++) {
            if (now.current[i] != g_skills.current[i] || now.real[i] != g_skills.real[i] || now.xp[i] != g_skills.xp[i])
                post({EventType::StatChanged, i});
        }
    }
    g_skills = now;
    g_skills_valid = true;
}

void diff_inventories() {
    std::vector<game::Inventory> now = game::read_inventories();
    std::vector<game::Inventory> old;
    {
        std::lock_guard lock(g_inv_mutex);
        old.swap(g_inventories);
        g_inventories = now;
    }
    for (const auto& n : now) {
        const game::Inventory* before = nullptr;
        for (const auto& o : old) if (o.id == n.id) before = &o;
        if (!before || before->ids != n.ids || before->quantities != n.quantities) post({EventType::ItemContainerChanged, n.id});
    }
}

void diff_players() {
    std::vector<game::PlayerInfo> now = game::read_players();
    std::erase_if(now, [](const game::PlayerInfo& p) { return p.fine_x == 0 && p.fine_y == 0; });  // see diff_npcs
    std::vector<game::PlayerInfo> old;
    {
        std::lock_guard lock(g_players_mutex);
        old.swap(g_players);
        g_players = now;
    }
    auto same = [](const game::PlayerInfo& a, const game::PlayerInfo& b) { return a.index == b.index && a.address == b.address; };
    for (const auto& o : old) {
        bool kept = false;
        for (const auto& n : now) kept |= same(o, n);
        if (!kept) post({EventType::PlayerDespawned, o.index});
    }
    for (const auto& n : now) {
        bool existed = false;
        for (const auto& o : old) existed |= same(o, n);
        if (!existed) post({EventType::PlayerSpawned, n.index});
    }
}

bool g_menu_was_open = false;

void diff_menu() {
    bool open = game::menu_open();
    if (open && !g_menu_was_open) {
        auto entries = game::read_menu();
        post({EventType::MenuOpened, static_cast<int>(entries.size())});
    }
    g_menu_was_open = open;
}

// Menu model vtable slot 1: select(menu, const std::vector<uint64_t>* path, x, y, flag).
using MenuSelectFn = uint64_t (*)(void*, const uint64_t* const*, int, int, bool);
MenuSelectFn g_orig_select = nullptr;
game::MenuEntry g_clicked;
bool g_click_consumed = false;

uint64_t hook_menu_select(void* menu, const uint64_t* const* path, int x, int y, bool flag) {
    game::MenuEntry entry;
    if (path && path[0] && path[1] > path[0] &&
        game::menu_entry_for_path(reinterpret_cast<uintptr_t>(menu), path[0], path[1] - path[0], entry)) {
        g_clicked = entry;
        g_click_consumed = false;
        LOGI("menu: select '%s' '%s' kind %d path[0] %llu len %zu x %d y %d flag %d open %d on %s thread",
             entry.option.c_str(), entry.target.c_str(), entry.kind, (unsigned long long)path[0][0],
             (size_t)(path[1] - path[0]), x, y, flag, game::menu_open(), on_client_thread() ? "client" : "other");
        post({EventType::MenuOptionClicked, static_cast<int>(path[0][0])});
        if (g_click_consumed) {
            g_click_consumed = false;
            return 1;
        }
    }
    return g_orig_select(menu, path, x, y, flag);
}

bool patch_slot(uintptr_t slot_addr, void* value, void** old) {
    auto** slot = reinterpret_cast<void**>(slot_addr);
    long page = sysconf(_SC_PAGESIZE);
    auto start = reinterpret_cast<void*>(slot_addr & ~(page - 1));
    if (mprotect(start, page, PROT_READ | PROT_WRITE) != 0) return false;
    if (old) *old = *slot;
    *slot = value;
    mprotect(start, page, PROT_READ);
    return true;
}

void install_menu_hook() {
    uintptr_t vt = game::menu_vtable();
    if (!vt) return;
    uintptr_t slot = vt + sizeof(void*);
    if (*reinterpret_cast<uintptr_t*>(slot) != game::menu_select()) {
        LOGE("menu: vtable slot 1 is %p, expected %p; not hooking", *reinterpret_cast<void**>(slot),
             reinterpret_cast<void*>(game::menu_select()));
        return;
    }
    void* old = nullptr;
    if (patch_slot(slot, reinterpret_cast<void*>(hook_menu_select), &old)) {
        g_orig_select = reinterpret_cast<MenuSelectFn>(old);
        LOGI("menu: select hooked");
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
    diff_players();
    diff_inventories();
    diff_skills();
    poll_messages();
    diff_interfaces();
    diff_menu();

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
    install_menu_hook();
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

std::vector<game::Inventory> inventories() {
    std::lock_guard lock(g_inv_mutex);
    return g_inventories;
}

std::vector<game::PlayerInfo> players() {
    std::lock_guard lock(g_players_mutex);
    return g_players;
}

const game::MenuEntry& clicked_menu_entry() { return g_clicked; }
void consume_menu_click() { g_click_consumed = true; }

std::vector<game::Npc> npcs() {
    std::lock_guard lock(g_npcs_mutex);
    return g_npcs;
}

}  // namespace client_thread
