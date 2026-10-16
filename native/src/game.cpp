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
    // shared_ptr<ObjType> getObjType(int id) (result via x8): definition cache lookup/load (OC_* ops).
    uintptr_t get_obj_type;
    uintptr_t get_npc_type;  // shared_ptr<NpcType> getNpcType(int id), same convention (NPC_INFO 0x469778)
    uintptr_t op_text;       // const string* opText(opVector*, int index): picks varp/varbit overrides (0x65c798)
    // Scene (WorldView +0x10): +0x944 planes, +0x948/+0x94c size x/y, +0x3d0 int heights
    // [planes][sx+1][sy+1]; tileSettings(scene, plane, x, y) (bit 2 = bridge).
    uintptr_t tile_settings;
    uintptr_t get_enum_type;  // shared_ptr<EnumType> getEnumType(int id) (ENUM ops 3400-3411)
    // Menus: Client field holding the MenuManager* (built by the Client ctor 0x40c9b4 with
    // 0x58dab8); the menu model's vtable, whose slot 1 = select(menu, path, x, y, flag) (0x515854); ELF vaddrs.
    uint32_t f_menu_manager;
    uintptr_t menu_vtable, menu_select;
};

constexpr Build kBuilds[] = {
    {"241.4",
     {0xf0, 0xff, 0xf1, 0x19, 0x1f, 0x3f, 0x23, 0x5d, 0x20, 0x85,
      0x7c, 0x02, 0x70, 0x44, 0xb9, 0xc9, 0xf7, 0x09, 0xcd, 0x8c},
     0xaee748, 0xaee6e8, 0x2020, 0xa69fe0, 0x411640, 0x1127ca0, 0x1127c60, 0x3058, 0x413c5c, 0x413cdc,
     0xecdc, 0xece0, 0xcb78, 0xcb7c, 0xcb80, 0x8950c, 0x89514, 0x5d0040,
     0xc950, 0x18, 0x24, 0x28, 0x98, 0xa0, 0xd0, 0xd8, 0x68, 0x70, 0xcb0c, 0x718, 0xcb90, 0x730, 0x1fc, 0x200, 0x0, 0x8,
     0x89488, 0x413a98, 0x413a88, 0x413a60, 0x413a68, 0x78, 0x158, 0x5c, 0x60, 0x64, 0x68, 0x74, 0x6, 0x3210, 0x3274, 0x32d8, 0xaab690, 0xaab658, 0xaab628,
     0xaef1d0, 0x4df54c, 0x63bfe8, 0x63633c, 0x65c798, 0x6de99c, 0x65cac8,
     0x4132b0, 0xa6d6c0, 0x515854},
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

namespace game {
namespace {
// shared_ptr control block as the game uses it: vtable, int uses (+8), int weak (+0xc).
void release_shared(uintptr_t ctrl) {
    if (!ctrl) return;
    auto vcall = [](uintptr_t obj, size_t slot) {
        auto vt = read_at<uintptr_t>(obj);
        reinterpret_cast<void (*)(uintptr_t)>(read_at<uintptr_t>(vt + slot))(obj);
    };
    if (__atomic_fetch_add(reinterpret_cast<int*>(ctrl + 8), -1, __ATOMIC_ACQ_REL) == 1) {
        vcall(ctrl, 0x10);
        if (__atomic_fetch_add(reinterpret_cast<int*>(ctrl + 0xc), -1, __ATOMIC_ACQ_REL) == 1) vcall(ctrl, 0x18);
    }
}
}  // namespace

bool item_def(int id, ItemDef& out) {
    if (!g_build || !g_build->get_obj_type || id < 0 || id > 0xfffff) return false;
    // Returns shared_ptr<ObjType> by value: a non-trivial type, so the result goes through x8
    // (AAPCS64 indirect result), not x0. A struct with a user-provided destructor gets the same ABI.
    struct SharedPtr {
        uintptr_t ctrl, obj;
        ~SharedPtr() {}
    };
    SharedPtr r = reinterpret_cast<SharedPtr (*)(int)>(g_base + g_build->get_obj_type)(id);
    uintptr_t sp[2] = {r.ctrl, r.obj};
    uintptr_t o = sp[1];
    if (!o) { release_shared(sp[0]); return false; }
    out.id = id;
    out.name = read_string(o + 0x08);
    out.price = read_at<int32_t>(o + 0xd8);
    out.stackable = read_at<int32_t>(o + 0xd4) == 1;
    out.members = read_at<uint8_t>(o + 0xe8) != 0;
    out.linked_note = read_at<int32_t>(o + 0x2b0);
    out.note_template = read_at<int32_t>(o + 0x2b4);
    out.placeholder = read_at<int32_t>(o + 0x314);
    out.placeholder_template = read_at<int32_t>(o + 0x318);
    out.weight = read_at<int32_t>(o + 0x2d0);
    out.category = read_at<int32_t>(o + 0x278);
    out.tradeable_guess = read_at<uint8_t>(o + 0x308) != 0;
    auto iops = read_at<uintptr_t>(o + 0x108);
    for (int i = 0; i < 5; i++) out.inventory_actions[i] = iops ? read_string(iops + i * 0x18) : std::string();
    release_shared(sp[0]);
    return true;
}

}  // namespace game

namespace game {

bool npc_def(int id, NpcDef& out) {
    if (!g_build || !g_build->get_npc_type || id < 0 || id > 0xfffff) return false;
    struct SharedPtr {
        uintptr_t ctrl, obj;
        ~SharedPtr() {}
    };
    SharedPtr r = reinterpret_cast<SharedPtr (*)(int)>(g_base + g_build->get_npc_type)(id);
    uintptr_t ctrl = r.ctrl, o = r.obj;
    if (!o) { release_shared(ctrl); return false; }
    out.id = read_at<int32_t>(o);
    out.name = read_string(o + 0x08);
    out.size = read_at<int32_t>(o + 0x20);
    out.combat_level = read_at<int32_t>(o + 0x11c);
    out.minimap_visible = read_at<uint8_t>(o + 0x118) != 0;
    out.interactable = read_at<uint8_t>(o + 0x158) != 0;
    auto op_text = reinterpret_cast<uintptr_t (*)(uintptr_t, int)>(g_base + g_build->op_text);
    for (int i = 0; i < 5; i++) {
        uintptr_t str = op_text(o + 0x100, i);
        out.actions[i] = str ? read_string(str) : std::string();
    }
    release_shared(ctrl);
    return true;
}

}  // namespace game

namespace game {

bool scene_heights(SceneHeights& out) {
    if (!g_build || !g_build->tile_settings) return false;
    uintptr_t c = client();
    if (!c) return false;
    uintptr_t wv = read_at<uintptr_t>(c + g_build->f_world_view);
    if (!wv) return false;
    uintptr_t scene = read_at<uintptr_t>(wv + 0x10);
    if (!scene) return false;
    int planes = read_at<int32_t>(scene + 0x944), sx = read_at<int32_t>(scene + 0x948), sy = read_at<int32_t>(scene + 0x94c);
    auto heights = read_at<const int32_t*>(scene + 0x3d0);
    if (planes < 1 || planes > 4 || sx < 1 || sx > 512 || sy < 1 || sy > 512 || !heights) return false;
    out.planes = planes;
    out.size_x = sx;
    out.size_y = sy;
    size_t n = static_cast<size_t>(planes) * (sx + 1) * (sy + 1);
    out.heights.assign(heights, heights + n);
    out.settings.assign(static_cast<size_t>(planes) * sx * sy, 0);
    auto settings = reinterpret_cast<uint32_t (*)(uintptr_t, int, int, int)>(g_base + g_build->tile_settings);
    for (int p = 0; p < planes; p++)
        for (int x = 0; x < sx; x++)
            for (int y = 0; y < sy; y++) out.settings[(static_cast<size_t>(p) * sx + x) * sy + y] = static_cast<uint8_t>(settings(scene, p, x, y));
    return true;
}

}  // namespace game

namespace game {

bool enum_def(int id, EnumDef& out) {
    if (!g_build || !g_build->get_enum_type || id < 0 || id > 0xfffff) return false;
    struct SharedPtr {
        uintptr_t ctrl, obj;
        ~SharedPtr() {}
    };
    SharedPtr r = reinterpret_cast<SharedPtr (*)(int)>(g_base + g_build->get_enum_type)(id);
    uintptr_t ctrl = r.ctrl, e = r.obj;
    if (!e) { release_shared(ctrl); return false; }
    // EnumType: +0 u16 key type, +2 u16 value type ('s' strings, 0xcf longs), +8 default string,
    // +0x20 default int, +0x30 count, +0x48 int keys, +0x60 int values, +0x90 strings (24 bytes).
    out.key_type = read_at<uint16_t>(e);
    out.value_type = read_at<uint16_t>(e + 2);
    out.default_string = read_string(e + 8);
    out.default_int = read_at<int32_t>(e + 0x20);
    int n = read_at<int32_t>(e + 0x30);
    if (n < 0 || n > 100000) n = 0;
    auto keys = read_at<const int32_t*>(e + 0x48);
    auto ints = read_at<const int32_t*>(e + 0x60);
    auto strs = read_at<uintptr_t>(e + 0x90);
    out.keys.clear();
    out.int_values.clear();
    out.string_values.clear();
    for (int i = 0; i < n; i++) {
        out.keys.push_back(keys ? keys[i] : 0);
        if (out.value_type == 's') out.string_values.push_back(strs ? read_string(strs + i * 0x18) : std::string());
        else out.int_values.push_back(ints ? ints[i] : 0);
    }
    release_shared(ctrl);
    return true;
}

}  // namespace game

namespace game {
namespace {
// MenuManager: +0x140 menu model (vtable menu_vtable), +0x7c0 menu shown (0 when closed).
// Menu model: +0x140 vector of 0x18-byte nodes {EntryList*, u64 index, children vector*};
// EntryList: +0x00 callbacks (0xa0 each: invoker at +0, storage at +0x10), +0x18 entries
// (0x150 each). Entry: +0x50 target, +0xb8 option (24-byte strings, each followed by a small
// arena), +0x120..+0x148 parameters, +0x14f kind byte.
constexpr uint32_t kMgrMenu = 0x140, kMgrShown = 0x7c0, kMenuNodes = 0x140, kListEntries = 0x18;
constexpr uint32_t kEntrySize = 0x150, kNodeSize = 0x18;

uintptr_t menu_manager() {
    uintptr_t c = client();
    return c ? read_at<uintptr_t>(c + g_build->f_menu_manager) : 0;
}

void read_entry(uintptr_t e, MenuEntry& out) {
    out.address = e;
    out.option = read_string(e + 0xb8);
    out.target = read_string(e + 0x50);
    memcpy(out.raw, reinterpret_cast<const void*>(e + 0x120), sizeof out.raw);
    out.kind = read_at<uint8_t>(e + 0x14f);
}

uintptr_t node_entry(uintptr_t node) {
    auto list = read_at<uintptr_t>(node);
    if (!list) return 0;
    auto entries = read_at<uintptr_t>(list + kListEntries);
    auto end = read_at<uintptr_t>(list + kListEntries + 8);
    uint64_t index = read_at<uint64_t>(node + 8);
    if (!entries || index >= (end - entries) / kEntrySize) return 0;
    return entries + index * kEntrySize;
}
}  // namespace

uintptr_t menu_object() {
    uintptr_t m = menu_manager();
    return m ? m + kMgrMenu : 0;
}

bool menu_open() {
    uintptr_t m = menu_manager();
    return m && read_at<uintptr_t>(m + kMgrShown) != 0;
}

std::vector<MenuEntry> read_menu() {
    std::vector<MenuEntry> out;
    uintptr_t menu = menu_object();
    if (!menu) return out;
    auto b = read_at<uintptr_t>(menu + kMenuNodes), e = read_at<uintptr_t>(menu + kMenuNodes + 8);
    if (!b || e < b || (e - b) / kNodeSize > 512) return out;
    for (uintptr_t n = b; n < e; n += kNodeSize) {
        uintptr_t entry = node_entry(n);
        if (!entry) continue;
        MenuEntry me;
        read_entry(entry, me);
        me.has_children = read_at<uintptr_t>(n + 0x10) != 0;
        out.push_back(std::move(me));
    }
    return out;
}

// Mirrors the walk in select (0x515854): path[0] indexes the top-level nodes, later elements
// index the children vector of the previous node.
bool menu_entry_for_path(uintptr_t menu, const uint64_t* path, size_t len, MenuEntry& out) {
    if (!menu || !len) return false;
    uintptr_t vec = menu + kMenuNodes, node = 0;
    for (size_t i = 0; i < len; i++) {
        if (!vec) return false;
        auto b = read_at<uintptr_t>(vec), e = read_at<uintptr_t>(vec + 8);
        if (path[i] >= (e - b) / kNodeSize) return false;
        node = b + path[i] * kNodeSize;
        vec = read_at<uintptr_t>(node + 0x10);
    }
    uintptr_t entry = node_entry(node);
    if (!entry) return false;
    read_entry(entry, out);
    out.has_children = vec != 0;
    return true;
}

uintptr_t menu_vtable() { return g_build ? g_base + g_build->menu_vtable : 0; }
uintptr_t menu_select() { return g_build ? g_base + g_build->menu_select : 0; }

namespace {
uintptr_t top_world_view() {
    uintptr_t c = client();
    return c && g_build ? read_at<uintptr_t>(c + g_build->f_world_view) : 0;
}
}  // namespace

// Scene (WorldView +0x10): +0x944 planes, +0x948/+0x94c size, +0x968 tile slots (0x10 each,
// Tile* at +8) indexed ((plane * sx) + x) * sy + y. Tile: +0x108 wall, +0x110 wall decoration,
// +0x118 ground decoration (object*, tag at +0x38), +0x34 game object count, +0x38 game object
// slots (0x10 each, object* at +8; tag +0x1f0, scene min x/max x/min y/max y +0x210..+0x21c).
std::vector<SceneObject> scene_objects() {
    std::vector<SceneObject> out;
    uintptr_t wv = top_world_view();
    uintptr_t scene = wv ? read_at<uintptr_t>(wv + 0x10) : 0;
    if (!scene) return out;
    int planes = read_at<int32_t>(scene + 0x944), sx = read_at<int32_t>(scene + 0x948), sy = read_at<int32_t>(scene + 0x94c);
    uintptr_t slots = read_at<uintptr_t>(scene + 0x968);
    if (planes < 1 || planes > 4 || sx < 1 || sx > 256 || sy < 1 || sy > 256 || !slots) return out;
    out.reserve(4096);
    for (int p = 0; p < planes; p++)
        for (int x = 0; x < sx; x++)
            for (int y = 0; y < sy; y++) {
                uintptr_t tile = read_at<uintptr_t>(slots + ((static_cast<size_t>(p) * sx + x) * sy + y) * 0x10 + 8);
                if (!tile) continue;
                static constexpr struct { uint32_t off; SceneLayer layer; } kSimple[] = {
                    {0x108, kWall}, {0x110, kWallDecoration}, {0x118, kGroundDecoration}};
                for (const auto& l : kSimple) {
                    uintptr_t o = read_at<uintptr_t>(tile + l.off);
                    if (!o) continue;
                    uint64_t tag = read_at<uint64_t>(o + 0x38);
                    out.push_back({l.layer, static_cast<uint8_t>(p), static_cast<uint8_t>(x), static_cast<uint8_t>(y), 1, 1,
                                   static_cast<int32_t>(tag >> 20 & 0xffff), tag});
                }
                int n = read_at<int32_t>(tile + 0x34);
                uintptr_t objs = read_at<uintptr_t>(tile + 0x38);
                if (n <= 0 || n > 16 || !objs) continue;
                for (int i = 0; i < n; i++) {
                    uintptr_t o = read_at<uintptr_t>(objs + static_cast<size_t>(i) * 0x10 + 8);
                    if (!o) continue;
                    int x0 = read_at<int32_t>(o + 0x210), x1 = read_at<int32_t>(o + 0x214);
                    int y0 = read_at<int32_t>(o + 0x218), y1 = read_at<int32_t>(o + 0x21c);
                    if (x0 != x || y0 != y) continue;  // listed on every tile it covers; report it once
                    uint64_t tag = read_at<uint64_t>(o + 0x1f0);
                    out.push_back({kGameObject, static_cast<uint8_t>(p), static_cast<uint8_t>(x), static_cast<uint8_t>(y),
                                   static_cast<uint8_t>(std::clamp(x1 - x0 + 1, 1, 255)),
                                   static_cast<uint8_t>(std::clamp(y1 - y0 + 1, 1, 255)),
                                   static_cast<int32_t>(tag >> 20 & 0xffff), tag});
                }
            }
    return out;
}

// Ground items: WorldView +0x140 → planes (0x18 each, +0x10 → x columns (0x18 each, +0x10 → y
// list heads (0x18 each: libc++ std::list {prev, next, size}))). Nodes {prev, next, Obj*, ctrl};
// Obj +0x40 id, +0x44 quantity (OBJ_ADD 0x45775c).
std::vector<GroundItem> ground_items() {
    std::vector<GroundItem> out;
    uintptr_t wv = top_world_view();
    uintptr_t scene = wv ? read_at<uintptr_t>(wv + 0x10) : 0;
    uintptr_t planes_arr = wv ? read_at<uintptr_t>(wv + 0x140) : 0;
    if (!scene || !planes_arr) return out;
    int planes = read_at<int32_t>(scene + 0x944), sx = read_at<int32_t>(scene + 0x948), sy = read_at<int32_t>(scene + 0x94c);
    if (planes < 1 || planes > 4 || sx < 1 || sx > 256 || sy < 1 || sy > 256) return out;
    for (int p = 0; p < planes; p++) {
        uintptr_t cols = read_at<uintptr_t>(planes_arr + static_cast<size_t>(p) * 0x18 + 0x10);
        if (!cols) continue;
        for (int x = 0; x < sx; x++) {
            uintptr_t heads = read_at<uintptr_t>(cols + static_cast<size_t>(x) * 0x18 + 0x10);
            if (!heads) continue;
            for (int y = 0; y < sy; y++) {
                uintptr_t head = heads + static_cast<size_t>(y) * 0x18;
                uint64_t n = read_at<uint64_t>(head + 0x10);
                if (!n || n > 256) continue;
                uintptr_t node = read_at<uintptr_t>(head + 8);
                for (uint64_t i = 0; i < n && node && node != head; i++) {
                    uintptr_t obj = read_at<uintptr_t>(node + 0x10);
                    if (obj)
                        out.push_back({static_cast<uint8_t>(p), static_cast<uint8_t>(x), static_cast<uint8_t>(y),
                                       read_at<int32_t>(obj + 0x40), read_at<int32_t>(obj + 0x44)});
                    node = read_at<uintptr_t>(node + 8);
                }
            }
        }
    }
    return out;
}

}  // namespace game
