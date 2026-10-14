#include "game.h"

#include <elf.h>
#include <link.h>

#include <cstring>

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
    uint32_t f_local_x, f_local_y;  // Client: local player's scene tile (world - scene base)
    uint32_t f_fine_x, f_fine_y;    // Client: local player's scene position, 128 units per tile
    uintptr_t get_varbit;           // int getVarbit(int id): looks up the varbit def, reads its varp bits
    uint32_t f_world_view;          // Client: WorldView* (NPC_INFO handlers pass Client[0x192a])
    uint32_t wv_base_x, wv_base_y;                 // WorldView: world tile of scene (0, 0)
    uint32_t wv_npc_buckets, wv_npc_bucket_count;  // WorldView: hash map index -> Npc*
    uint32_t wv_npc_indices, wv_npc_count;         // WorldView: int[] of active NPC indices
    uint32_t npc_def, npc_fine_x, npc_fine_y;      // Npc: NpcType*, scene position (1/128 tile)
    uint32_t def_id, def_name;                     // NpcType: id, name (24-byte string)
};

constexpr Build kBuilds[] = {
    {"241.4",
     {0xf0, 0xff, 0xf1, 0x19, 0x1f, 0x3f, 0x23, 0x5d, 0x20, 0x85,
      0x7c, 0x02, 0x70, 0x44, 0xb9, 0xc9, 0xf7, 0x09, 0xcd, 0x8c},
     0xaee748, 0xaee6e8, 0x2020, 0xa69fe0, 0x411640, 0x1127ca0, 0x1127c60, 0x3058, 0x413c5c, 0x413cdc,
     0xecdc, 0xece0, 0xcb7c, 0xcb80, 0x8950c, 0x89514, 0x5d0040,
     0xc950, 0x24, 0x28, 0x98, 0xa0, 0xd0, 0xd8, 0x730, 0x1fc, 0x200, 0x0, 0x8},
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

Point player_world() {
    if (!g_build || !client()) return {-1, -1};
    Point base = scene_base(), tile = player_local_tile();
    return {base.x + tile.x, base.y + tile.y};
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
