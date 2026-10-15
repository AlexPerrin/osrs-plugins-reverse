#include "game.h"

#include <elf.h>
#include <link.h>

#include <algorithm>
#include <cstring>
#include <memory>

#include "got_hook.h"
#include "log.h"

namespace game {
namespace {

// Offsets are ELF virtual addresses (as in readelf/objdump; Ghidra shows them +0x100000)
// or struct field offsets. Found with tools/agent.py; see docs/offsets.md.
struct Build {
    const char* name;
    uint8_t build_id[20];
    uintptr_t client_ptr;   // global Client* (558 code refs)
    uintptr_t cycle;        // global int, written only by Client vtable slot 5 (0x411640)
    uint32_t f_game_state;  // Client::gameState
    uintptr_t client_vtable;  // Client vtable; slot 5 = doCycle (only reached through the vtable)
    uintptr_t do_cycle;       // expected value of that slot
    uintptr_t varps;          // {?, u32 count, int* values, ?}: client-side varps (setVarp 0x5d06f8)
    uintptr_t server_varps;   // same layout: last values sent by the server
    uint32_t f_server_tick;      // Client: count of SERVER_TICK_END packets (dispatcher case 55)
    uint32_t f_changed_varps;    // Client: int[32] ring of varp ids changed by VARP_SMALL/LARGE
    uint32_t f_changed_count;    // Client: total writes into that ring
    uint32_t f_dest_x, f_dest_y;    // Client: walk destination tile (-1 when none)
    uint32_t f_plane;               // Client: local player's plane
    uint32_t f_local_x, f_local_y;  // Client: local player's scene tile (world - scene base)
    uint32_t f_fine_x, f_fine_y;    // Client: local player's scene position, 128 units per tile
    uintptr_t get_varbit;           // int getVarbit(int id): looks up the varbit def, reads its varp bits
    uint32_t f_world_view;          // Client: WorldView* (NPC_INFO handlers pass Client[0x192a])
    uint32_t wv_plane;                             // WorldView: current plane (then size x, y = 104)
    uint32_t wv_base_x, wv_base_y;                 // WorldView: world tile of scene (0, 0)
    uint32_t wv_npc_buckets, wv_npc_bucket_count;  // WorldView: hash map index -> Npc*
    uint32_t wv_npc_indices, wv_npc_count;         // WorldView: int[] of active NPC indices
    uint32_t wv_player_buckets, wv_player_bucket_count;  // WorldView: hash map index -> Player*
    uint32_t f_local_player_index;                 // Client: local player's index in that map
    uint32_t player_name;                          // Player: pointer to a 24-byte string (name)
    uint32_t f_gpi;  // Client: player-info state; int count, then int[count] high-res player indices
    uint32_t npc_def, npc_fine_x, npc_fine_y;      // Npc: NpcType*, scene position (1/128 tile)
    uint32_t def_id, def_name;                     // NpcType: id, name (24-byte string)
    uint32_t f_camera;          // Client: camera x, height, y, pitch, yaw (14-bit angles: 16384 per turn)
    uint32_t f_interfaces;      // Client: InterfaceManager* (group table +0x6888 count, +0x6890 groups)
    uint32_t f_root_interface;  // Client: top-level interface id (IF_OPENTOP)
    uint32_t f_subs_buckets, f_subs_count;  // Client: open sub-interfaces, map id -> {id, group, ...}
    uint32_t w_hidden, w_text;  // Widget: bool hidden (IF_SETHIDE), text (IF_SETTEXT)
    // Widget geometry, from the script interpreter's CC_GETX/Y/WIDTH/HEIGHT/LAYER (0x4e5960):
    uint32_t w_x, w_y, w_width, w_height, w_parent, w_group;
    uint32_t f_skill_current, f_skill_real, f_skill_xp;  // Client: int[25] each (UPDATE_STAT, case 2)
    uintptr_t inventories;      // global map inv id -> {ids vector +0x08, quantities vector +0x20, next +0x38}
    uintptr_t next_message_id;  // global int: id the next chat message gets (0x3f0a04 increments it)
    uintptr_t messages_by_id;   // global hash map id -> Message* {?, buckets, u32 count}
    // Script VM (static object; int locals vector +0x08, object locals +0x38, int stack +0x50 with
    // size +0xff0, object stack of 0x28-byte variants +0xff8 with size +0xac38, running flag +0x04)
    // and runScriptEvent(vm, ScriptEvent*, maxOps).
    uintptr_t script_vm, run_script_event;
};

constexpr Build kBuilds[] = {
    {"241.4",
     {0xf0, 0xff, 0xf1, 0x19, 0x1f, 0x3f, 0x23, 0x5d, 0x20, 0x85,
      0x7c, 0x02, 0x70, 0x44, 0xb9, 0xc9, 0xf7, 0x09, 0xcd, 0x8c},
     0xaee748, 0xaee6e8, 0x2020, 0xa69fe0, 0x411640, 0x1127ca0, 0x1127c60, 0x3058, 0x413c5c, 0x413cdc,
     0xecdc, 0xece0, 0xcb78, 0xcb7c, 0xcb80, 0x8950c, 0x89514, 0x5d0040,
     0xc950, 0x18, 0x24, 0x28, 0x98, 0xa0, 0xd0, 0xd8, 0x68, 0x70, 0xcb0c, 0x718, 0xcb90, 0x730, 0x1fc, 0x200, 0x0, 0x8,
     0x89488, 0x413a98, 0x413a88, 0x413a60, 0x413a68, 0x78, 0x158, 0x5c, 0x60, 0x64, 0x68, 0x74, 0x6, 0x3210, 0x3274, 0x32d8, 0xaab690, 0xaab658, 0xaab628,
     0xaef1d0, 0x4df54c},
};

constexpr const char* kGameLib = "liblibs.hal.system.osclient.so";

const Build* g_build = nullptr;
uintptr_t g_base = 0;

struct NoteSearch {
    const uint8_t* id = nullptr;
    size_t id_len = 0;
};

int find_build_id(dl_phdr_info* info, size_t, void* data) {
    if (!info->dlpi_name || !strstr(info->dlpi_name, kGameLib)) return 0;
    auto* out = static_cast<NoteSearch*>(data);
    for (int i = 0; i < info->dlpi_phnum; i++) {
        const ElfW(Phdr)& ph = info->dlpi_phdr[i];
        if (ph.p_type != PT_NOTE) continue;
        auto p = reinterpret_cast<const uint8_t*>(info->dlpi_addr + ph.p_vaddr);
        const uint8_t* end = p + ph.p_memsz;
        while (p + sizeof(ElfW(Nhdr)) <= end) {
            auto* nh = reinterpret_cast<const ElfW(Nhdr)*>(p);
            const uint8_t* name = p + sizeof(*nh);
            const uint8_t* desc = name + ((nh->n_namesz + 3) & ~3u);
            if (nh->n_type == NT_GNU_BUILD_ID && nh->n_namesz == 4 && memcmp(name, "GNU", 4) == 0) {
                out->id = desc;
                out->id_len = nh->n_descsz;
                return 1;
            }
            p = desc + ((nh->n_descsz + 3) & ~3u);
        }
    }
    return 1;
}

template <typename T>
T read_at(uintptr_t addr) {
    T v;
    memcpy(&v, reinterpret_cast<const void*>(addr), sizeof v);
    return v;
}

uintptr_t client() {
    return g_build ? read_at<uintptr_t>(g_base + g_build->client_ptr) : 0;
}

}  // namespace

bool init() {
    g_base = module_base(kGameLib);
    NoteSearch note;
    dl_iterate_phdr(find_build_id, &note);
    if (!g_base || note.id_len != 20) {
        LOGE("game: library or build id not found");
        return false;
    }
    for (const Build& b : kBuilds) {
        if (memcmp(b.build_id, note.id, 20) == 0) {
            g_build = &b;
            LOGI("game: build %s recognised", b.name);
            return true;
        }
    }
    LOGE("game: unknown build id; game-state hooks disabled");
    return false;
}

bool available() { return g_build != nullptr; }
const char* build_name() { return g_build ? g_build->name : "unknown"; }

int cycle() { return g_build ? read_at<int32_t>(g_base + g_build->cycle) : -1; }

int game_state() {
    uintptr_t c = client();
    return c ? read_at<int32_t>(c + g_build->f_game_state) : -1;
}

uintptr_t client_vtable() { return g_build ? g_base + g_build->client_vtable : 0; }
uintptr_t do_cycle() { return g_build ? g_base + g_build->do_cycle : 0; }

namespace {
int field(uint32_t off, int fallback = -1) {
    uintptr_t c = client();
    return c ? read_at<int32_t>(c + off) : fallback;
}

int read_varp_table(uintptr_t table, int id) {
    if (!g_build || id < 0) return 0;
    uint32_t count = read_at<uint32_t>(g_base + table + 8);
    auto* values = read_at<const int32_t*>(g_base + table + 0x10);
    return values && static_cast<uint32_t>(id) < count ? values[id] : 0;
}
}  // namespace

int server_tick() { return g_build ? field(g_build->f_server_tick) : -1; }

int varp_count() { return g_build ? static_cast<int>(read_at<uint32_t>(g_base + g_build->varps + 8)) : 0; }
int varp(int id) { return g_build ? read_varp_table(g_build->varps, id) : 0; }
int server_varp(int id) { return g_build ? read_varp_table(g_build->server_varps, id) : 0; }

int varbit(int id) {
    if (!g_build || id < 0) return 0;
    auto fn = reinterpret_cast<int (*)(int)>(g_base + g_build->get_varbit);
    return fn(id);
}

uint32_t changed_varp_count() { return g_build ? static_cast<uint32_t>(field(g_build->f_changed_count, 0)) : 0; }
int changed_varp(uint32_t n) {
    return g_build ? field(g_build->f_changed_varps + (n & 31) * 4) : -1;
}

namespace {
// The game's strings are 24 bytes, folly-fbstring style: small strings are inline with
// 23 - length in the last byte; otherwise {char* data, size_t size, capacity | category}.
std::string read_string(uintptr_t addr) {
    uint8_t last = read_at<uint8_t>(addr + 23);
    if ((last & 0xc0) == 0) {
        size_t len = last <= 23 ? 23 - last : 0;
        return std::string(reinterpret_cast<const char*>(addr), len);
    }
    auto data = read_at<const char*>(addr);
    size_t len = read_at<size_t>(addr + 8);
    return data && len < 4096 ? std::string(data, len) : std::string();
}

uintptr_t npc_lookup(uintptr_t wv, int index) {
    auto buckets = read_at<uintptr_t>(wv + g_build->wv_npc_buckets);
    auto nb = read_at<uint64_t>(wv + g_build->wv_npc_bucket_count) & 0xffffffff;
    if (!buckets || !nb) return 0;
    for (auto node = read_at<uintptr_t>(buckets + (static_cast<uint64_t>(index) % nb) * 8); node;
         node = read_at<uintptr_t>(node + 24)) {
        if (read_at<int32_t>(node) == index) return read_at<uintptr_t>(node + 16);
    }
    return 0;
}
}  // namespace

std::vector<Npc> read_npcs() {
    std::vector<Npc> out;
    uintptr_t c = client();
    if (!c || game_state() != 30) return out;
    auto wv = read_at<uintptr_t>(c + g_build->f_world_view);
    if (!wv) return out;
    int count = read_at<int32_t>(wv + g_build->wv_npc_count);
    auto indices = read_at<const int32_t*>(wv + g_build->wv_npc_indices);
    if (count <= 0 || count > 65535 || !indices) return out;
    int base_x = read_at<int32_t>(wv + g_build->wv_base_x), base_y = read_at<int32_t>(wv + g_build->wv_base_y);
    for (int i = 0; i < count; i++) {
        uintptr_t npc = npc_lookup(wv, indices[i]);
        if (!npc) continue;
        auto def = read_at<uintptr_t>(npc + g_build->npc_def);
        Npc n;
        n.index = indices[i];
        n.address = npc;
        n.id = def ? read_at<int32_t>(def + g_build->def_id) : -1;
        if (def) n.name = read_string(def + g_build->def_name);
        n.fine_x = read_at<int32_t>(npc + g_build->npc_fine_x);
        n.fine_y = read_at<int32_t>(npc + g_build->npc_fine_y);
        n.world_x = base_x + (n.fine_x >> 7);
        n.world_y = base_y + (n.fine_y >> 7);
        out.push_back(std::move(n));
    }
    return out;
}

Point scene_base() {
    uintptr_t c = client();
    if (!c) return {-1, -1};
    auto wv = read_at<uintptr_t>(c + g_build->f_world_view);
    if (!wv) return {-1, -1};
    return {read_at<int32_t>(wv + g_build->wv_base_x), read_at<int32_t>(wv + g_build->wv_base_y)};
}

std::vector<PlayerInfo> read_players() {
    std::vector<PlayerInfo> out;
    uintptr_t c = client();
    if (!c || game_state() != 30) return out;
    auto wv = read_at<uintptr_t>(c + g_build->f_world_view);
    if (!wv) return out;
    auto buckets = read_at<uintptr_t>(wv + g_build->wv_player_buckets);
    auto count = read_at<uint64_t>(wv + g_build->wv_player_bucket_count) & 0xffffffff;
    if (!buckets || !count || count > 1 << 16) return out;
    uintptr_t sentinel = read_at<uintptr_t>(buckets + count * 8);
    int base_x = read_at<int32_t>(wv + g_build->wv_base_x), base_y = read_at<int32_t>(wv + g_build->wv_base_y);
    int local = read_at<int32_t>(c + g_build->f_local_player_index);
    // The map keeps players that have left view; the GPI index list is the live set.
    int active_count = read_at<int32_t>(c + g_build->f_gpi);
    if (active_count < 0 || active_count > 2048) return out;
    std::vector<int32_t> active(active_count);
    for (int i = 0; i < active_count; i++) active[i] = read_at<int32_t>(c + g_build->f_gpi + 4 + i * 4);
    std::sort(active.begin(), active.end());
    for (uint64_t b = 0; b < count; b++) {
        int guard = 0;
        for (auto node = read_at<uintptr_t>(buckets + b * 8); node && node != sentinel && guard++ < 2048;
             node = read_at<uintptr_t>(node + 0x18)) {
            auto p = read_at<uintptr_t>(node + 0x10);
            if (!p || !std::binary_search(active.begin(), active.end(), read_at<int32_t>(node))) continue;
            PlayerInfo info;
            info.index = read_at<int32_t>(node);
            info.address = p;
            info.local = info.index == local;
            auto name = read_at<uintptr_t>(p + g_build->player_name);
            if (name) info.name = read_string(name);
            info.fine_x = read_at<int32_t>(p + g_build->npc_fine_x);
            info.fine_y = read_at<int32_t>(p + g_build->npc_fine_y);
            info.world_x = base_x + (info.fine_x >> 7);
            info.world_y = base_y + (info.fine_y >> 7);
            out.push_back(std::move(info));
        }
    }
    return out;
}

Point player_world() {
    if (!g_build || !client()) return {-1, -1};
    Point base = scene_base(), tile = player_local_tile();
    return {base.x + tile.x, base.y + tile.y};
}

Camera camera() {
    Camera cam;
    uintptr_t c = client();
    if (!c) return cam;
    uintptr_t b = c + g_build->f_camera;
    cam.valid = true;
    cam.x = read_at<int32_t>(b);
    cam.z = read_at<int32_t>(b + 4);
    cam.y = read_at<int32_t>(b + 8);
    cam.pitch = read_at<int32_t>(b + 12) & 0x3fff;
    cam.yaw = read_at<int32_t>(b + 16) & 0x3fff;
    return cam;
}

int root_interface() { return g_build ? field(g_build->f_root_interface) : -1; }

// Mirrors the lookup in 0x5d1ad8 without loading missing groups (that has to happen on
// the game's own terms): groups are {?, u64 child count, slot*}, slots are {?, Widget*}.
uintptr_t widget(int component) {
    uintptr_t c = client();
    if (!c || component < 0) return 0;
    auto im = read_at<uintptr_t>(c + g_build->f_interfaces);
    if (!im) return 0;
    uint64_t group = static_cast<uint32_t>(component) >> 16, child = component & 0xffff;
    if (group >= read_at<uint64_t>(im + 0x6888)) return 0;
    uintptr_t g = read_at<uintptr_t>(im + 0x6890) + group * 0x18;
    if (child >= read_at<uint64_t>(g + 8)) return 0;
    auto slots = read_at<uintptr_t>(g + 0x10);
    return slots ? read_at<uintptr_t>(slots + child * 0x10 + 8) : 0;
}

WidgetGeometry widget_geometry(uintptr_t w) {
    WidgetGeometry g;
    if (!w) return g;
    g.x = read_at<int32_t>(w + g_build->w_x);
    g.y = read_at<int32_t>(w + g_build->w_y);
    g.width = read_at<int32_t>(w + g_build->w_width);
    g.height = read_at<int32_t>(w + g_build->w_height);
    g.parent = read_at<int32_t>(w + g_build->w_parent);
    g.group = read_at<uint16_t>(w + g_build->w_group);
    if (g.parent == -1) {
        // A group's root layer: its parent is the component the group is mounted in (as CC_GETLAYER does).
        for (const SubInterface& sub : open_sub_interfaces())
            if (sub.group == g.group) { g.parent = sub.component; break; }
    }
    return g;
}

int widget_child_count(int group) {
    uintptr_t c = client();
    if (!c || group < 0) return 0;
    auto im = read_at<uintptr_t>(c + g_build->f_interfaces);
    if (!im || static_cast<uint64_t>(group) >= read_at<uint64_t>(im + 0x6888)) return 0;
    return static_cast<int>(read_at<uint64_t>(read_at<uintptr_t>(im + 0x6890) + group * 0x18 + 8));
}

bool widget_hidden(uintptr_t w) { return w && read_at<uint8_t>(w + g_build->w_hidden) != 0; }
std::string widget_text(uintptr_t w) { return w ? read_string(w + g_build->w_text) : std::string(); }

std::vector<SubInterface> open_sub_interfaces() {
    std::vector<SubInterface> out;
    uintptr_t c = client();
    if (!c) return out;
    auto buckets = read_at<uintptr_t>(c + g_build->f_subs_buckets);
    auto count = read_at<uint64_t>(c + g_build->f_subs_count) & 0xffffffff;
    if (!buckets || !count || count > 1 << 20) return out;
    uintptr_t sentinel = read_at<uintptr_t>(buckets + count * 8);
    for (uint64_t b = 0; b < count; b++) {
        int guard = 0;
        for (auto node = read_at<uintptr_t>(buckets + b * 8); node && node != sentinel && guard++ < 1024;
             node = read_at<uintptr_t>(node + 0x10)) {
            out.push_back({read_at<int32_t>(node), read_at<int32_t>(node + 4)});
        }
    }
    return out;
}

Skills skills() {
    Skills out;
    uintptr_t c = client();
    if (!c) return out;
    for (int i = 0; i < kSkillCount; i++) {
        out.current[i] = read_at<int32_t>(c + g_build->f_skill_current + i * 4);
        out.real[i] = read_at<int32_t>(c + g_build->f_skill_real + i * 4);
        out.xp[i] = read_at<int32_t>(c + g_build->f_skill_xp + i * 4);
    }
    return out;
}

std::vector<Inventory> read_inventories() {
    std::vector<Inventory> out;
    if (!g_build) return out;
    uintptr_t map = g_base + g_build->inventories;
    auto buckets = read_at<uintptr_t>(map + 8);
    auto count = read_at<uint64_t>(map + 0x10) & 0xffffffff;
    if (!buckets || !count || count > 1 << 16) return out;
    uintptr_t sentinel = read_at<uintptr_t>(buckets + count * 8);
    for (uint64_t b = 0; b < count; b++) {
        int guard = 0;
        for (auto node = read_at<uintptr_t>(buckets + b * 8); node && node != sentinel && guard++ < 1024;
             node = read_at<uintptr_t>(node + 0x38)) {
            Inventory inv;
            inv.id = read_at<int32_t>(node);
            auto ids = read_at<const int32_t*>(node + 0x08), ids_end = read_at<const int32_t*>(node + 0x10);
            auto qty = read_at<const int32_t*>(node + 0x20), qty_end = read_at<const int32_t*>(node + 0x28);
            size_t n = ids && ids_end > ids ? static_cast<size_t>(ids_end - ids) : 0;
            size_t nq = qty && qty_end > qty ? static_cast<size_t>(qty_end - qty) : 0;
            if (n > 4096) continue;
            inv.ids.assign(ids, ids + n);
            inv.quantities.assign(n, 0);
            for (size_t i = 0; i < n && i < nq; i++) inv.quantities[i] = qty[i];
            out.push_back(std::move(inv));
        }
    }
    return out;
}

int next_message_id() { return g_build ? read_at<int32_t>(g_base + g_build->next_message_id) : -1; }

// Message (built by 0x3f0a04): +0x08 cycle, +0x40 type, +0x48 name, +0x70 sender, +0xa0 text.
bool read_message(int id, ChatLine& out) {
    if (!g_build) return false;
    uintptr_t map = g_base + g_build->messages_by_id;
    auto buckets = read_at<uintptr_t>(map + 8);
    auto nb = read_at<uint32_t>(map + 0x10);
    if (!buckets || !nb) return false;
    for (auto node = read_at<uintptr_t>(buckets + (static_cast<uint32_t>(id) % nb) * 8); node;
         node = read_at<uintptr_t>(node + 0x18)) {
        if (read_at<int32_t>(node) != id) continue;
        auto msg = read_at<uintptr_t>(node + 0x10);
        if (!msg) return false;
        out.id = id;
        out.cycle = read_at<int32_t>(msg + 0x08);
        out.type = read_at<int32_t>(msg + 0x40);
        out.name = read_string(msg + 0x48);
        out.sender = read_string(msg + 0x70);
        out.text = read_string(msg + 0xa0);
        return true;
    }
    return false;
}

int player_plane() { return g_build ? field(g_build->f_plane) : -1; }

int scene_plane() {
    uintptr_t c = client();
    if (!c) return -1;
    auto wv = read_at<uintptr_t>(c + g_build->f_world_view);
    return wv ? read_at<int32_t>(wv + g_build->wv_plane) : -1;
}

Point walk_destination() {
    if (!g_build || !client()) return {-1, -1};
    return {field(g_build->f_dest_x), field(g_build->f_dest_y)};
}
Point player_local_tile() {
    if (!g_build || !client()) return {-1, -1};
    return {field(g_build->f_local_x), field(g_build->f_local_y)};
}
Point player_fine() {
    if (!g_build || !client()) return {-1, -1};
    return {field(g_build->f_fine_x), field(g_build->f_fine_y)};
}

}  // namespace game

namespace game {
namespace {
// A script value as the VM stores it: 32 bytes of payload, then a type tag (1 int, 2 string,
// 4 object, 5 long). Strings use the 24-byte layout read_string() describes.
struct Variant {
    uint8_t payload[32];
    uint32_t type;
    uint32_t pad;
};
static_assert(sizeof(Variant) == 0x28, "variant size");

void write_string(uint8_t* dst, const std::string& s, std::vector<std::unique_ptr<char[]>>& keep) {
    memset(dst, 0, 24);
    if (s.size() <= 22) {
        memcpy(dst, s.data(), s.size());
        dst[23] = static_cast<uint8_t>(23 - s.size());
        return;
    }
    // The VM copies argument strings, so the buffer only has to live for the call.
    keep.emplace_back(new char[s.size() + 1]);
    char* buf = keep.back().get();
    memcpy(buf, s.data(), s.size());
    buf[s.size()] = 0;
    uint64_t size = s.size(), cap = s.size() | 0x8000000000000000ull;
    memcpy(dst, &buf, 8);
    memcpy(dst + 8, &size, 8);
    memcpy(dst + 16, &cap, 8);
}
}  // namespace

bool run_script(const std::vector<ScriptArg>& args, ScriptResult& out, std::string& error) {
    if (!g_build || !g_build->run_script_event) { error = "unsupported build"; return false; }
    if (args.empty() || args[0].is_string) { error = "first argument must be the script id"; return false; }
    uintptr_t vm = g_base + g_build->script_vm;
    if (read_at<uint8_t>(vm + 4)) { error = "script VM busy"; return false; }

    std::vector<std::unique_ptr<char[]>> keep;
    std::vector<Variant> vars(args.size());
    for (size_t i = 0; i < args.size(); i++) {
        memset(&vars[i], 0, sizeof(Variant));
        if (args[i].is_string) {
            write_string(vars[i].payload, args[i].s, keep);
            vars[i].type = 2;
        } else {
            memcpy(vars[i].payload, &args[i].i, 4);
            vars[i].type = 1;
        }
    }
    // ScriptEvent, as runScriptEvent (0x4df54c) reads it: +0x08 argc, +0x10 args, +0x40 source
    // widget, +0x48.. mouse/op values for the magic int args, +0x60 drag target, +0x70 opbase
    // string, +0x88 copied to vm+0xe0cc, +0x8c trigger type (0 = look the script up by id).
    alignas(16) uint8_t ev[0x100] = {};
    int32_t argc = static_cast<int32_t>(args.size());
    Variant* argv = vars.data();
    memcpy(ev + 0x08, &argc, 4);
    memcpy(ev + 0x10, &argv, 8);
    ev[0x70 + 23] = 23;  // empty inline string
    auto fn = reinterpret_cast<void (*)(uintptr_t, void*, int)>(g_base + g_build->run_script_event);
    fn(vm, ev, 500000);

    int n = read_at<int32_t>(vm + 0xff0);
    if (n < 0 || n > 1000) n = 0;
    out.ints.resize(n);
    if (n) memcpy(out.ints.data(), reinterpret_cast<const void*>(vm + 0x50), n * 4u);
    int m = read_at<int32_t>(vm + 0xac38);
    if (m < 0 || m > 1000) m = 0;
    out.objects.clear();
    out.object_is_string.clear();
    for (int i = 0; i < m; i++) {
        uintptr_t v = vm + 0xff8 + static_cast<uintptr_t>(i) * 0x28;
        bool str = read_at<uint32_t>(v + 0x20) == 2;
        out.object_is_string.push_back(str);
        out.objects.push_back(str ? read_string(v) : std::string());
    }
    return true;
}

}  // namespace game
