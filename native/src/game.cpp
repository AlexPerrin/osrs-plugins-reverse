#include "game.h"

#include <elf.h>
#include <link.h>

#include <mutex>
#include <unordered_map>
#include <sys/mman.h>
#include <sys/system_properties.h>
#include <unistd.h>
#include <cstdlib>
#include <algorithm>
#include <cstring>
#include <memory>

#include "got_hook.h"
#include "inline_hook.h"
#include "log.h"

namespace game {
void install_message_hook();
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
    // const shared_ptr<LocType>* getLocType(int id): cache lookup/load (pointer to the cached
    // {ctrl, LocType*}; the world menu builder 0x533a1c uses it for loc entries).
    uintptr_t get_loc_type;
    uintptr_t add_chat_message;  // addChatMessage(client, type, const string* name, const string* text) (MESSAGE_GAME)
    // Client-side setters, from the script VM (0x4dd08c): setVarbit(id, value) (op 27), and on the
    // varc store (Client +0x417b68) setVarcInt(store, id, int) (op 43) / setVarcString(store, id,
    // const variant*) (op 49; 0x28-byte variant, index at +0x20, 2 = string).
    uintptr_t set_varbit, set_varc_int, set_varc_string;
    uintptr_t get_varbit_type;  // shared_ptr<VarbitType> getVarbitType(int id) (x8): {int varp, lsb, msb}
    // Menu close, as the close branch of 0x599f68: clear the model (MenuManager+0x140) and the
    // displayed menu (+0x540), then shown (+0x7c0) = 0.
    uintptr_t menu_clear_model, menu_clear_display;
    // Builds the displayed menu (+0x540) from the model (+0x140): (display, model, MenuManager
    // +0x7c8 layout), as the show branch of 0x599f68.
    uintptr_t menu_build_display;
    // The long-press menu build: (MenuManager*, x, y) in UI canvas units (0x54ae24's touch handler).
    uintptr_t menu_build_at;
    // Scene picking for menus: set the pick point (screen px, mode; 0x72b758 as the touch handler
    // 0x518840), the "picked" flag the menu build checks, and the pick mode (2 pending, 3 done).
    uintptr_t pick_set, pick_ready, pick_mode;
    uintptr_t mod_icons;  // global shared_ptr obj of the font's <img=N> table (setModIcons 0x431a84)
    uintptr_t message_ctor;  // Message(this, type, name*, sender*, text*): every chat line, id = ++next id
    // Client: int[32] ring of skill ids changed by UPDATE_STAT (case 2 of 0x541dd4: ring[count++ & 31] = skill);
    // widgets' onStatTransmit listeners run for the skills in it (RuneLite's changedSkills).
    uint32_t f_changed_skills, f_changed_skill_count;
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
     0x4132b0, 0xa6d6c0, 0x515854, 0x62e5ec, 0x43a9c0,
     0x5d00fc, 0x3f804c, 0x3f8470, 0x64b450,
     0x514f48, 0x507398, 0x50621c, 0x58fa84,
     0x72b758, 0x1d2f52d, 0x1d7ddf0,
     0x18e2130, 0x3f0a04,
     0x413d64, 0x413de4},
};

constexpr const char* kGameLib = "liblibs.hal.system.osclient.so";

const Build* g_build = nullptr;
uintptr_t g_base = 0;
thread_local bool g_own_model_call = false;  // our own getModel calls (drawlog ignores them)

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
            install_message_hook();
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

// UPDATE_RUNWEIGHT (dispatcher case 0x1a) stores s16 at Client+0x33a4; UPDATE_RUNENERGY (0x66)
// stores 0..10000 at Client+0x33a0.
int world() { uintptr_t c = client(); return c ? read_at<int32_t>(c + 0x3174) : 0; }
bool viewport_zoom(int out[5]) {
    uintptr_t c = client();
    if (!c) return false;
    out[0] = read_at<int32_t>(c + 0x46d4);
    for (int i = 0; i < 4; i++) out[1 + i] = read_at<int32_t>(c + 0x46e0 + 4 * i);
    return out[0] > 0 && out[0] < 100000 && out[3] > 0 && out[4] > 0;
}
int world_flags() { uintptr_t c = client(); return c ? read_at<int32_t>(c + 0x2f64) : 0; }

// Grand Exchange offers: slot i is the object *(Client + 0x4185c0 + i*8), read by the CS2
// stockmarket_* ops (handler 0x4ed838, ops 3903-3913): +0x00 u8 state (0 empty, 1 adding, 2 stable,
// 5 finished: isofferempty/adding/stable/finished), +0x01 u8 type (0 buy, 1 sell; getoffertype),
// +0x04 int item, +0x08 s64 price, +0x10 int total quantity, +0x14 int quantity done, +0x18 s64 gold
// spent. out = 8 x {state, type, item, price, total, done, spent}; state -1 = no object.
bool ge_offers(int64_t out[8 * 7]) {
    uintptr_t c = client();
    if (!c) return false;
    for (int i = 0; i < 8; i++) {
        int64_t* o = out + 7 * i;
        uintptr_t off = read_at<uintptr_t>(c + 0x4185c0 + 8u * static_cast<uintptr_t>(i));
        if (!off) { o[0] = -1; for (int k = 1; k < 7; k++) o[k] = 0; continue; }
        o[0] = read_at<uint8_t>(off);
        o[1] = read_at<uint8_t>(off + 1);
        o[2] = read_at<int32_t>(off + 4);
        o[3] = read_at<int64_t>(off + 8);
        o[4] = read_at<int32_t>(off + 0x10);
        o[5] = read_at<int32_t>(off + 0x14);
        o[6] = read_at<int64_t>(off + 0x18);
    }
    return true;
}

uintptr_t ge_slot_raw(int slot, uint8_t out[0x28]) {
    uintptr_t c = client();
    if (!c || slot < 0 || slot > 7) return 0;
    uintptr_t off = read_at<uintptr_t>(c + 0x4185c0 + 8u * static_cast<uintptr_t>(slot));
    if (!off) return 0;
    for (int i = 0; i < 0x28; i++) out[i] = read_at<uint8_t>(off + i);
    return off;
}

// Widget drag, from the drag block of the cycle function 0x41d0a4 (Ghidra FUN_0051d0a4): Client
// +0x413ad8 dragged Widget* (+0x413ad0 its reference count holder), +0x413ae8 the drag parent,
// +0x413af0 press point, +0x413b00 dragged-on Widget* (+0x413af8), +0x413b20 u8 dragging (set
// once the widget's dead time +0x324 and dead zone +0x320 are exceeded), +0x413b24 int cycles held.
// Touch state Client+0x40e988: 3 held, 4 released (the release runs onDragComplete +0x500 and the
// swap 0x528b50(…, dragged, dragged-on)).
namespace {
constexpr uintptr_t kDragged = 0x413ad8, kDraggedOn = 0x413b00, kDragging = 0x413b20, kDragCycles = 0x413b24,
                    kTouchState = 0x40e988;
}
bool drag_state(int out[7]) {
    uintptr_t c = client();
    if (!c) return false;
    uintptr_t d = read_at<uintptr_t>(c + kDragged);
    if (!d) return false;
    out[0] = out[1] = out[2] = out[3] = -1;
    widget_ref(d, &out[0], &out[1]);
    uintptr_t on = read_at<uintptr_t>(c + kDraggedOn);
    if (on) widget_ref(on, &out[2], &out[3]);
    out[4] = read_at<uint8_t>(c + kDragging);
    out[5] = read_at<int32_t>(c + kDragCycles);
    out[6] = read_at<int32_t>(c + kTouchState);
    return true;
}
void drag_clear_target() {
    uintptr_t c = client();
    // Only the pointer: the game checks it before the swap and replaces both words (releasing the
    // reference holder) when the next target is set.
    if (c) *reinterpret_cast<volatile uintptr_t*>(c + kDraggedOn) = 0;
}
int touch_state() { uintptr_t c = client(); return c ? read_at<int32_t>(c + kTouchState) : 0; }

namespace { std::string read_string(uintptr_t addr); }

// Friends chat: *(Client+0x4608) (CS2 ops 3611-3627, handler 0x4ebdb0): +0x0c member count,
// +0x20 member pointers; member +0x08 name, +0x68 world, +0x88 rank; +0xa8 channel name, +0xd8
// owner, s8 +0xf0 kick rank, +0xf4 own rank.
// Clan channels (ops 3850-3861, handler 0x4ecf20): {ref, ClanChannel*} at Client+0x4660 (listened)
// and +0x4670 + clan id * 0x10 (affined); channel +0x30/+0x38 vector of 16-byte {?, member*}, +0x50
// name, s8 +0x69 talk rank, s8 +0x6a kick rank; member s8 +0x08 rank, +0x0c world, +0x10 name.
bool friends_chat(ChatChannel& out) {
    uintptr_t c = client();
    uintptr_t fc = c ? read_at<uintptr_t>(c + 0x4608) : 0;
    if (!fc) return false;
    out.name = read_string(fc + 0xa8);
    out.owner = read_string(fc + 0xd8);
    out.kick_rank = read_at<int8_t>(fc + 0xf0);
    out.my_rank = read_at<int32_t>(fc + 0xf4);
    int n = read_at<int32_t>(fc + 0x0c);
    uintptr_t arr = read_at<uintptr_t>(fc + 0x20);
    if (n < 0 || n > 2000 || !arr) n = 0;
    for (int i = 0; i < n; i++) {
        uintptr_t m = read_at<uintptr_t>(arr + 8u * static_cast<uintptr_t>(i));
        if (!m) continue;
        out.members.push_back({read_string(m + 0x08), read_at<int32_t>(m + 0x68), read_at<int32_t>(m + 0x88)});
    }
    return true;
}
bool clan_channel(int which, ChatChannel& out) {
    uintptr_t c = client();
    if (!c || which < -1 || which > 7) return false;
    uintptr_t ch = read_at<uintptr_t>(c + (which < 0 ? 0x4668 : 0x4678 + 0x10u * static_cast<uintptr_t>(which)));
    if (!ch) return false;
    out.name = read_string(ch + 0x50);
    out.talk_rank = read_at<int8_t>(ch + 0x69);
    out.kick_rank = read_at<int8_t>(ch + 0x6a);
    uintptr_t b = read_at<uintptr_t>(ch + 0x30), e = read_at<uintptr_t>(ch + 0x38);
    if (!b || e < b || (e - b) / 16 > 2000) return true;
    for (uintptr_t p = b; p < e; p += 16) {
        uintptr_t m = read_at<uintptr_t>(p + 8);
        if (!m) continue;
        out.members.push_back({read_string(m + 0x10), read_at<int32_t>(m + 0x0c), read_at<int8_t>(m + 0x08)});
    }
    return true;
}

// Instances (REBUILD_REGION). The scene loader manager is *(Client+0x4868) (passed to both rebuild
// handlers from the packet dispatcher, 0x442610). REBUILD_REGION (0x555510) sets byte +0x54dd to 1,
// both normal rebuild paths (0x5551dc, 0x5555xx) clear it. The region parser (0x5580e4) fills
// [4 levels][13 x][13 z] entries of 16 bytes at manager +0x38: int zoneX (ref >> 14 & 1023), zoneZ
// (ref >> 3 & 2047), level (ref >> 24 & 3), rotation (ref >> 1 & 3); absent zones are all -1.
// The array is not cleared by a normal rebuild (desktop keeps stale chunks too).
bool instance_templates(bool& instanced, int32_t out[4 * 13 * 13]) {
    uintptr_t c = client();
    if (!c) return false;
    uintptr_t mgr = read_at<uintptr_t>(c + 0x4868);
    if (!mgr) return false;
    instanced = read_at<uint8_t>(mgr + 0x54dd) != 0;
    for (int i = 0; i < 4 * 13 * 13; i++) {
        const uintptr_t e = mgr + 0x38 + 16 * static_cast<uintptr_t>(i);
        int32_t x = read_at<int32_t>(e), z = read_at<int32_t>(e + 4), lvl = read_at<int32_t>(e + 8), rot = read_at<int32_t>(e + 12);
        out[i] = (x < 0 || z < 0 || lvl < 0 || rot < 0) ? -1 : (lvl << 24 | x << 14 | z << 3 | rot << 1);
    }
    return true;
}
int weight() { uintptr_t c = client(); return c ? read_at<int16_t>(c + 0x33a4) : 0; }
int run_energy() { uintptr_t c = client(); return c ? read_at<int32_t>(c + 0x33a0) : 0; }

static std::vector<int32_t> varp_table(uintptr_t table) {
    if (!g_build) return {};
    uint32_t count = read_at<uint32_t>(g_base + table + 8);
    auto* values = read_at<const int32_t*>(g_base + table + 0x10);
    if (!values || count > 1 << 20) return {};  // OpenRune has 65660 varps
    return std::vector<int32_t>(values, values + count);
}
std::vector<int32_t> varps() { return g_build ? varp_table(g_build->varps) : std::vector<int32_t>{}; }
std::vector<int32_t> server_varps() { return g_build ? varp_table(g_build->server_varps) : std::vector<int32_t>{}; }

namespace {
// The game's 24-byte string: inline (byte 23 = 23 - length) up to 22 chars, else
// {char*, size, capacity | 0x80 << 56} (heap, owned here). See read_string.
struct GameString {
    alignas(8) uint8_t bytes[24] = {};
    char* heap = nullptr;
    explicit GameString(const std::string& s) {
        if (s.size() <= 22) {
            memcpy(bytes, s.data(), s.size());
            bytes[23] = static_cast<uint8_t>(23 - s.size());
        } else {
            heap = static_cast<char*>(malloc(s.size() + 1));
            memcpy(heap, s.data(), s.size());
            heap[s.size()] = 0;
            uint64_t words[3] = {reinterpret_cast<uint64_t>(heap), s.size(), s.size() | (0x80ull << 56)};
            memcpy(bytes, words, 24);
        }
    }
    ~GameString() { free(heap); }
    GameString(const GameString&) = delete;
    GameString& operator=(const GameString&) = delete;
};
}  // namespace

bool add_chat_message(int type, const std::string& name, const std::string& text) {
    uintptr_t c = client();
    if (!g_build || !g_build->add_chat_message || !c) return false;
    GameString n(name), t(text);
    reinterpret_cast<void (*)(uintptr_t, int, const void*, const void*)>(g_base + g_build->add_chat_message)(c, type, n.bytes, t.bytes);
    return true;
}
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

// Client varcs (Client +0x417b68 → store): sorted vector at +0x48/+0x50 of 0x30-byte entries
// {int id; variant at +8 (int, or 24-byte string); u32 type at +0x28: 1 int, 2 string, 4 object}
// (getVarcInt 0x3f82ac, getVarcString 0x3f8588; opcodes 42/49 in 0x4dd08c).
static uintptr_t varc_entry(int id, int type) {
    uintptr_t c = client();
    if (!c) return 0;
    uintptr_t store = read_at<uintptr_t>(c + 0x417b68);
    if (!store) return 0;
    uintptr_t b = read_at<uintptr_t>(store + 0x48), e = read_at<uintptr_t>(store + 0x50);
    if (!b || e < b || (e - b) / 0x30 > 100000) return 0;
    size_t lo = 0, hi = (e - b) / 0x30;
    while (lo < hi) {
        size_t mid = (lo + hi) / 2;
        if (read_at<int32_t>(b + mid * 0x30) < id) lo = mid + 1; else hi = mid;
    }
    uintptr_t ent = b + lo * 0x30;
    if (ent >= e || read_at<int32_t>(ent) != id || read_at<int32_t>(ent + 0x28) != type) return 0;
    return ent;
}

bool varc_int(int id, int& out) {
    uintptr_t ent = varc_entry(id, 1);
    if (!ent) return false;
    out = read_at<int32_t>(ent + 8);
    return true;
}

bool varc_string(int id, std::string& out) {
    uintptr_t ent = varc_entry(id, 2);
    if (!ent) return false;
    out = read_string(ent + 8);
    return true;
}

static ActorState read_actor(uintptr_t a) {
    ActorState st;
    if (!a) return st;
    st.animation = read_at<int32_t>(a + 0x4d8);
    st.animation_frame = read_at<int16_t>(a + 0x4de);
    st.orientation = read_at<int32_t>(a + 0x3e0) & 0x7ff;
    st.current_orientation = read_at<int32_t>(a + 0x270) & 0x7ff;
    // Face command: kind +0x3a5 (0 = entity), entity type +0x3a7 (1 NPC, 2 player, 3 world
    // entity), target index +0x3a8 (-1 none).
    int kind = read_at<uint8_t>(a + 0x3a5), type = read_at<uint8_t>(a + 0x3a7), index = read_at<int32_t>(a + 0x3a8);
    if (kind == 0 && index >= 0 && index < 32768) {
        if (type == 1) st.interacting = index;
        else if (type == 2) st.interacting = index + 32768;
    }
    for (int i = 0; i < 8; i++) st.pose[i] = read_at<int32_t>(a + 0x280 + 4 * i);
    st.pose_animation = read_at<int32_t>(a + 0x4b8);
    st.pose_frame = read_at<int16_t>(a + 0x4be);
    st.overhead_cycle = read_at<int32_t>(a + 0x2f4);
    if (st.overhead_cycle > 0) st.overhead_text = read_string(a + 0x2d8);
    // Hitmarks (adder 0x472240): std::vector at +0x330/+0x338 of 12-byte {type, amount, end cycle}
    // slots, end cycle = cycle + delay + HitmarkType duration; empty slots are -1.
    uintptr_t hb = read_at<uintptr_t>(a + 0x330), he = read_at<uintptr_t>(a + 0x338);
    if (hb && he > hb && he - hb <= 12 * 16) {
        int n = static_cast<int>((he - hb) / 12);
        for (int i = 0; i < n && i < 4; i++)
            for (int k = 0; k < 3; k++) st.hitsplats[i * 3 + k] = read_at<int32_t>(hb + i * 12 + k * 4);
    }
    // Head bars (adder 0x472780): std::list at +0x388 of nodes {next, prev, ctrl, HealthBar*};
    // HealthBar {ctrl, HealthBarType* +8, updates list +0x10}; update node obj {start cycle, health,
    // health2, cycle offset} (0x475130), ascending by cycle; HealthBarType width (op 14) +0x28.
    uintptr_t sentinel = a + 0x388, node = read_at<uintptr_t>(sentinel);
    if (node && node != sentinel) {
        uintptr_t bar = read_at<uintptr_t>(node + 0x18);
        uintptr_t type = bar ? read_at<uintptr_t>(bar + 8) : 0;
        uintptr_t us = bar + 0x10, last = bar ? read_at<uintptr_t>(us + 8) : 0;
        if (type && last && last != us) {
            uintptr_t upd = read_at<uintptr_t>(last + 0x18);
            if (upd) {
                st.health_ratio = read_at<int32_t>(upd + 4);
                st.health_scale = read_at<int32_t>(type + 0x28);
            }
        }
    }
    return st;
}

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
        if (n.fine_x == 0 && n.fine_y == 0) {
            // Not moved since NPC_INFO added it: +0x1fc/+0x200 are only written by movement, so use
            // the position the add set (+0x268/+0x26c; a runtime ::npc spawn stood still unreported).
            n.fine_x = read_at<int32_t>(npc + 0x268);
            n.fine_y = read_at<int32_t>(npc + 0x26c);
        }
        n.world_x = base_x + (n.fine_x >> 7);
        n.world_y = base_y + (n.fine_y >> 7);
        n.actor = read_actor(npc);
        out.push_back(std::move(n));
    }
    return out;
}

// The live Npc*/Player* for an index right now (the per-cycle snapshot's addresses can be freed
// later in the same cycle, e.g. by a region rebuild). 0 if not active. Game thread only.
uintptr_t live_npc(int index) {
    uintptr_t c = client();
    if (!c || game_state() != 30 || index < 0) return 0;
    auto wv = read_at<uintptr_t>(c + g_build->f_world_view);
    if (!wv) return 0;
    int count = read_at<int32_t>(wv + g_build->wv_npc_count);
    auto indices = read_at<const int32_t*>(wv + g_build->wv_npc_indices);
    if (count <= 0 || count > 65535 || !indices) return 0;
    for (int i = 0; i < count; i++)
        if (indices[i] == index) return npc_lookup(wv, index);
    return 0;
}

uintptr_t live_player(int index) {
    uintptr_t c = client();
    if (!c || game_state() != 30 || index < 0) return 0;
    int active_count = read_at<int32_t>(c + g_build->f_gpi);
    if (active_count < 0 || active_count > 2048) return 0;
    bool active = false;
    for (int i = 0; i < active_count && !active; i++) active = read_at<int32_t>(c + g_build->f_gpi + 4 + i * 4) == index;
    if (!active) return 0;
    auto wv = read_at<uintptr_t>(c + g_build->f_world_view);
    if (!wv) return 0;
    auto buckets = read_at<uintptr_t>(wv + g_build->wv_player_buckets);
    auto count = read_at<uint64_t>(wv + g_build->wv_player_bucket_count) & 0xffffffff;
    if (!buckets || !count || count > 1 << 16) return 0;
    uintptr_t sentinel = read_at<uintptr_t>(buckets + count * 8);
    int guard = 0;
    for (auto node = read_at<uintptr_t>(buckets + (static_cast<uint64_t>(index) % count) * 8); node && node != sentinel && guard++ < 2048;
         node = read_at<uintptr_t>(node + 0x18))
        if (read_at<int32_t>(node) == index) return read_at<uintptr_t>(node + 0x10);
    return 0;
}

Point scene_base() {
    uintptr_t c = client();
    if (!c) return {-1, -1};
    auto wv = read_at<uintptr_t>(c + g_build->f_world_view);
    if (!wv) return {-1, -1};
    return {read_at<int32_t>(wv + g_build->wv_base_x), read_at<int32_t>(wv + g_build->wv_base_y)};
}

// Player +0x728 = Appearance* (shared_ptr {ctrl +0x720, obj}); Player-level fields at +0x730..+0x790.
static void read_appearance(uintptr_t p, PlayerInfo& info) {
    info.combat_level = read_at<int32_t>(p + 0x780);
    info.skull = read_at<int32_t>(p + 0x730);
    info.prayer = read_at<int32_t>(p + 0x734);
    info.team = read_at<int32_t>(p + 0x790);
    uintptr_t app = read_at<uintptr_t>(p + 0x728);
    if (!app) return;
    info.has_appearance = true;
    for (int i = 0; i < 12; i++) {
        info.equipment[i] = read_at<int32_t>(app + 4 * i);
        info.kits[i] = read_at<int32_t>(app + 0x30 + 4 * i);
    }
    for (int i = 0; i < 5; i++) info.colors[i] = read_at<int32_t>(app + 0x60 + 4 * i);
    info.npc_id = read_at<int32_t>(app + 0x74);
    info.gender = read_at<int32_t>(app + 0xa8);
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
            info.actor = read_actor(p);
            read_appearance(p, info);
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

bool play_sound_effect(int id, int loops, int delay) {
    uintptr_t c = client();
    if (!c || id < 0 || id > 0xffff || loops <= 0) return false;
    // The queue (0x4ae508, Ghidra 005ae508.c) is a vector of 0x28-byte entries at +0xc0/+0xc8 of
    // the sound system; the call adds one only while the system's "enabled" callback (slot 0,
    // presumably the sound-effect volume setting) returns non-zero and fewer than 50 are queued.
    // Report whether the game actually queued it, so a muted setting shows up as false.
    uintptr_t sys = c + 0x2610;
    uintptr_t before = read_at<uintptr_t>(sys + 0xc8);
    reinterpret_cast<void (*)(uintptr_t, uint32_t, char, int)>(g_base + 0x4ae508)(sys, id, static_cast<char>(std::min(loops, 127)), delay);
    bool queued = read_at<uintptr_t>(sys + 0xc8) != before;
    static int logs = 0;
    if (logs < 10) {
        logs++;
        LOGI("sound %d: %s by the game", id, queued ? "queued" : "not queued (sound effects off or queue full)");
    }
    return queued;
}

namespace {
uintptr_t top_world_view();  // defined with the scene readers below
}

Viewport viewport() {
    Viewport v;
    uintptr_t c = client();
    if (!c) return v;
    uintptr_t holder_base = read_at<uintptr_t>(c + 0x90);
    if (!holder_base) return v;
    uintptr_t holder = holder_base + 0x1b0;
    uintptr_t vt = read_at<uintptr_t>(holder);
    if (!vt) return v;
    uintptr_t get = read_at<uintptr_t>(vt + 0x18);
    if (!get) return v;
    uintptr_t renderer = reinterpret_cast<uintptr_t (*)(uintptr_t)>(get)(holder);
    if (!renderer) return v;
    uintptr_t vp = read_at<uintptr_t>(renderer + 0x630c60);
    if (!vp) return v;
    v.valid = true;
    v.zoom = read_at<int32_t>(vp + 0x10);
    v.centre_x = read_at<int32_t>(vp + 0x14);
    v.centre_y = read_at<int32_t>(vp + 0x18);
    for (int i = 0; i < 4; i++) v.clip[i] = read_at<int32_t>(vp + 0x24 + 4 * i);
    v.far = read_at<int32_t>(vp + 0x6c);
    uintptr_t wv = top_world_view();
    uintptr_t scene = wv ? read_at<uintptr_t>(wv + 0x10) : 0;
    if (scene) {
        for (int i = 0; i < 3; i++) v.scene_cam[i] = read_at<int32_t>(scene + 0x288 + 4 * i);
        for (int i = 0; i < 4; i++) v.trig[i] = static_cast<int>(read_at<float>(scene + 0x418 + 4 * i) * 65536.0f);
    }
    return v;
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
    cam.focal_x = read_at<int32_t>(b + 0x78);  // camera block +0x89488, focal point +0x89500
    cam.focal_z = read_at<int32_t>(b + 0x7c);
    cam.focal_y = read_at<int32_t>(b + 0x80);
    // The camera/player-position code (0x51d0a4) works in the frame of Client[+0xc940]; the NPC
    // readers use Client[+0xc950]. Debug: report the former's base and whether they match.
    auto rwv = read_at<uintptr_t>(c + 0xc940);
    if (rwv) {
        cam.render_base_x = read_at<int32_t>(rwv + g_build->wv_base_x);
        cam.render_base_y = read_at<int32_t>(rwv + g_build->wv_base_y);
    }
    cam.render_wv_same = rwv == read_at<uintptr_t>(c + g_build->f_world_view);
    return cam;
}

int root_interface() { return g_build ? field(g_build->f_root_interface) : -1; }

// Mirrors the lookup in 0x5d1ad8 without loading missing groups (that has to happen on
// the game's own terms): groups are {?, u64 child count, slot*}, slots are {?, Widget*}.
int widget_dynamic_count(uintptr_t w) {
    if (!w) return 0;
    int n = read_at<int32_t>(w + 0xb50);
    return n < 0 || n > 4096 || !read_at<uintptr_t>(w + 0xb58) ? 0 : n;
}

uintptr_t widget_dynamic(uintptr_t w, int index) {
    if (index < 0 || index >= widget_dynamic_count(w)) return 0;
    return read_at<uintptr_t>(read_at<uintptr_t>(w + 0xb58) + static_cast<size_t>(index) * 0x10 + 8);
}

// IF_SETOBJECT (if3 widgets) and CC_SETOBJECT (op 1200) store {item id (-1 none), quantity}
// at Widget +0xb38 (one 8-byte store in the script VM, 0x5e06dc).
// Script-visible widget properties, from the CC_GET* handlers (0x4e5b20, opcodes 1600-1614) and
// the IF_SET* packet handlers: scroll x/y (+0x84/+0x88), scroll width/height (+0x8c/+0x90),
// model zoom (+0x128), model angle x/y/z (+0x11c/+0x120/+0x124), opacity (+0xb0), model type/id
// (+0xfc/+0x100), sequence (+0x10c).
// 12..: from the CC_SET* handlers (0x4e06dc, ops 1100-1120): 1105 graphic +0xc8, 1107 tiling
// +0xec (bool), 1116 outline +0xf0, 1117 graphic shadow +0xf4, 1118/1119 v/h flip +0xf8/+0xf9
// (bool), 1102 fill +0xa8 (bool), 1115 text shadow +0x194 (bool), 1114 text align x/y +0x18c/
// +0x190 and line height +0x188, 1113 font +0x150.
void widget_props(uintptr_t w, int32_t out[kWidgetProps]) {
    static constexpr struct { uint32_t off; bool byte; } kFields[kWidgetProps] = {
        {0x84}, {0x88}, {0x8c}, {0x90}, {0x128}, {0x11c}, {0x120}, {0x124}, {0xb0}, {0xfc}, {0x100}, {0x10c},
        {0xc8}, {0xec, true}, {0xf0}, {0xf4}, {0xf8, true}, {0xf9, true}, {0xa8, true}, {0x194, true},
        {0x18c}, {0x190}, {0x188}, {0x150},
        // CC_SETPOSITION (1000): original x/y +0x4c/+0x50, x/y modes +0x3c/+0x40; CC_SETSIZE (1001):
        // original width/height +0x54/+0x58, modes +0x44/+0x48; 1005/1006 no click/scroll through.
        {0x4c}, {0x50}, {0x3c}, {0x40}, {0x54}, {0x58}, {0x44}, {0x48}, {0xb98, true}, {0xb99, true},
        // Component type (WidgetType: 0 layer, 3 rectangle, 4 text, 5 graphic, 6 model, 9 line,
        // 12 = the game's text input type with its object at +0xba8), int at +0x28.
        {0x28}};
    for (int i = 0; i < kWidgetProps; i++)
        out[i] = !w ? 0 : kFields[i].byte ? read_at<uint8_t>(w + kFields[i].off) : read_at<int32_t>(w + kFields[i].off);
}

WidgetItem widget_item(uintptr_t w) {
    if (!w) return {};
    return {read_at<int32_t>(w + 0xb38), read_at<int32_t>(w + 0xb3c)};
}

// IF_SETCOLOUR (dispatcher case 0x1d) stores 0xRRGGBB at Widget +0x98, then, if the widget's
// redraw cycle (+0xb78) is the current one (Client +0x4132bc), flags its root area dirty:
// Client +0x4132c0 + Widget[+0xb74] = 1.
int widget_color(uintptr_t w) { return w ? read_at<int32_t>(w + 0x98) & 0xffffff : 0; }

void widget_set_color(uintptr_t w, int rgb) {
    uintptr_t c = client();
    if (!w || !c) return;
    rgb &= 0xffffff;
    auto* colour = reinterpret_cast<volatile int32_t*>(w + 0x98);
    if (*colour == rgb) return;
    *colour = rgb;
    if (read_at<int32_t>(w + 0xb78) == read_at<int32_t>(c + 0x4132bc)) {
        int area = read_at<int32_t>(w + 0xb74);
        if (area >= 0 && area < 0x400) *reinterpret_cast<volatile uint8_t*>(c + 0x4132c0 + area) = 1;
    }
}

namespace {
void widget_mark_dirty(uintptr_t w) {
    uintptr_t c = client();
    if (!w || !c) return;
    if (read_at<int32_t>(w + 0xb78) == read_at<int32_t>(c + 0x4132bc)) {
        int area = read_at<int32_t>(w + 0xb74);
        if (area >= 0 && area < 0x400) *reinterpret_cast<volatile uint8_t*>(c + 0x4132c0 + area) = 1;
    }
}
}  // namespace

void widget_set_hidden(uintptr_t w, bool hidden) {
    if (!w || read_at<uint8_t>(w + 0x78) == hidden) return;
    *reinterpret_cast<volatile uint8_t*>(w + 0x78) = hidden;  // CC_SETHIDE 1003
    widget_mark_dirty(w);
}

bool widget_set_text(uintptr_t w, const std::string& text) {
    if (!w || read_at<int32_t>(w + 0x28) == 12) return false;  // type 12 keeps its text elsewhere (+0xba8)
    GameString s(text);
    // CC_SETTEXT 1112: string assign 0x3f1498(widget +0x158, &text)
    reinterpret_cast<void* (*)(uintptr_t, const void*)>(g_base + 0x3f1498)(w + g_build->w_text, s.bytes);
    widget_mark_dirty(w);
    return true;
}

// Name (CC_SETOPBASE 1305, +0x2c0), target verb (CC_SETTARGETVERB 1306, +0x330) or op `which`
// (0-based, CC_SETOP 1300). An op past the vector's end is first added through setOp 0x5dfae8(widget,
// op, string*), which grows the vector (+0x2d8 header, size +0x2e0, data +0x2e8) and swaps the string
// in (an empty inline one here, so nothing is freed); then the slot is assigned with 0x3f1498 as
// CC_SETTEXT does. Game thread only.
bool widget_set_string(uintptr_t w, int which, const std::string& text) {
    if (!w || which < -2 || which > 31) return false;
    uintptr_t dst;
    if (which == -1) dst = w + 0x2c0;
    else if (which == -2) dst = w + 0x330;
    else {
        if (static_cast<uint64_t>(which) >= read_at<uint64_t>(w + 0x2e0)) {
            if (text.empty()) return true;
            GameString empty{std::string()};
            reinterpret_cast<void (*)(uintptr_t, int, void*)>(g_base + 0x5dfae8)(w, which, empty.bytes);
            if (static_cast<uint64_t>(which) >= read_at<uint64_t>(w + 0x2e0)) return false;
        }
        dst = read_at<uintptr_t>(w + 0x2e8) + static_cast<size_t>(which) * 0x18;
    }
    GameString s(text);
    reinterpret_cast<void* (*)(uintptr_t, const void*)>(g_base + 0x3f1498)(dst, s.bytes);
    return true;
}

// Event flags kept in the widget (+0x2b0): the fallback of the per-component ranges that
// IF_SETEVENTS stores (multimap at Client +0x413c30, lookups 0x460dac / 0x460e74). The menu builder
// 0x525c78 adds op i when bit i+1 is set (0x460e74 returns (flags >> 1) & 0x3ff) or the widget has
// an onOp listener (+0x6a0 / +0x6b0..+0x6b8).
int widget_flags(uintptr_t w) { return w ? read_at<int32_t>(w + 0x2b0) : 0; }
// As the lookups 0x460dac / 0x460e74: bucket = component % count (Client +0x413c38) in the table at
// +0x413c30; walk the chain (next at node +0x18) to the first node of this component, then over its
// nodes {+0 component, +4 start, +8 end, +0xc flags, +0x10 op mask} for a range holding the index.
bool widget_events(uintptr_t w, int out[2]) {
    uintptr_t c = client();
    if (!w || !c) return false;
    uint32_t count = read_at<uint32_t>(c + 0x413c38);
    uintptr_t buckets = read_at<uintptr_t>(c + 0x413c30);
    if (!count || !buckets) return false;
    uint32_t comp = read_at<uint32_t>(w + 4);
    int32_t index = read_at<int32_t>(w + 8);
    uintptr_t n = read_at<uintptr_t>(buckets + static_cast<uintptr_t>(comp % count) * 8);
    int guard = 0;
    while (n && read_at<uint32_t>(n) != comp && ++guard < 100000) n = read_at<uintptr_t>(n + 0x18);
    for (; n && read_at<uint32_t>(n) == comp && ++guard < 100000; n = read_at<uintptr_t>(n + 0x18)) {
        if (read_at<int32_t>(n + 4) <= index && index <= read_at<int32_t>(n + 8)) {
            out[0] = read_at<int32_t>(n + 0xc);
            out[1] = read_at<int32_t>(n + 0x10);
            return true;
        }
    }
    return false;
}
void widget_set_flags(uintptr_t w, int flags) {
    if (w) *reinterpret_cast<volatile int32_t*>(w + 0x2b0) = flags;
}

namespace {
// The {ctrl, Widget*} slot holding a component (group table) or a CC_CREATE child (+0xb58 array).
uintptr_t widget_slot(int component, int index) {
    uintptr_t c = client();
    if (!c || component < 0) return 0;
    auto im = read_at<uintptr_t>(c + g_build->f_interfaces);
    if (!im) return 0;
    uint64_t group = static_cast<uint32_t>(component) >> 16, child = component & 0xffff;
    if (group >= read_at<uint64_t>(im + 0x6888)) return 0;
    uintptr_t g = read_at<uintptr_t>(im + 0x6890) + group * 0x18;
    if (child >= read_at<uint64_t>(g + 8)) return 0;
    auto slots = read_at<uintptr_t>(g + 0x10);
    if (!slots) return 0;
    uintptr_t slot = slots + child * 0x10;
    if (index < 0) return read_at<uintptr_t>(slot + 8) ? slot : 0;
    uintptr_t w = read_at<uintptr_t>(slot + 8);
    if (!w || index >= widget_dynamic_count(w)) return 0;
    slot = read_at<uintptr_t>(w + 0xb58) + static_cast<size_t>(index) * 0x10;
    return read_at<uintptr_t>(slot + 8) ? slot : 0;
}
}  // namespace

// Writable int fields (CC_SET* handlers, docs/offsets.md): a whitelist so a bad offset can't be written.
bool widget_set_field(int component, int index, uint32_t off, int value) {
    static constexpr struct { uint32_t off; bool byte; } kFields[] = {
        {0x3c}, {0x40}, {0x44}, {0x48}, {0x4c}, {0x50}, {0x54}, {0x58},  // modes, original x/y/w/h
        {0xa8, true}, {0xb0}, {0xc8}, {0xec, true}, {0xf0}, {0xf4}, {0xf8, true}, {0xf9, true},
        {0x150}, {0x18c}, {0x190}, {0x194, true}, {0xb98, true}, {0xb99, true},
        {0xb38}, {0xb3c},  // item id / quantity (as widget_item reads them; Widget.setItemId/Quantity)
        {0x84}, {0x88}, {0x8c}, {0x90},  // scroll x/y, scroll width/height (IF_SETSCROLLPOS / CC_SETSCROLLSIZE fields)
        {0x128}, {0x11c}, {0x120}, {0x124},  // model zoom, model angle x/y/z
        {0x13c},  // item quantity mode (CC_SETOBJECT* 1200/1205/1212: 1/3, 0, 3)
        {0x320}, {0x324},  // drag dead zone / dead time (ints, read by the drag block of 0x41d0a4)
        {0xfc}, {0x100}, {0x10c},  // model type / id, animation (CC_SETMODEL* 1108, 1110)
        {0x188},  // line height (CC_SETTEXTALIGN 1114; type 12 keeps it in its +0xba8 object instead)
        {0x5c}, {0x60}, {0x64}, {0x68}};  // laid-out x/y/width/height (what layout writes; desktop's setRelativeX..)
    uintptr_t slot = widget_slot(component, index);
    uintptr_t w = slot ? read_at<uintptr_t>(slot + 8) : 0;
    if (!w) return false;
    for (const auto& f : kFields) {
        if (f.off != off) continue;
        if (f.byte) *reinterpret_cast<volatile uint8_t*>(w + off) = value != 0;
        else *reinterpret_cast<volatile int32_t*>(w + off) = value;
        uintptr_t c = client();
        if (read_at<int32_t>(w + 0xb78) == read_at<int32_t>(c + 0x4132bc)) {
            int area = read_at<int32_t>(w + 0xb74);
            if (area >= 0 && area < 0x400) *reinterpret_cast<volatile uint8_t*>(c + 0x4132c0 + area) = 1;
        }
        return true;
    }
    return false;
}

// Reads one int field of a widget (or a byte for +0x348, the "has a listener" flag the CC_SETON*
// setter 0x4e4fe0 sets); the offsets widget_set_field allows plus +0x348. INT32_MIN if unknown.
int widget_get_field(int component, int index, uint32_t off) {
    uintptr_t slot = widget_slot(component, index);
    uintptr_t w = slot ? read_at<uintptr_t>(slot + 8) : 0;
    if (!w) return INT32_MIN;
    switch (off) {
        case 0x348: return read_at<uint8_t>(w + off);
        case 0x320: case 0x324: case 0x13c: return read_at<int32_t>(w + off);
        default: return INT32_MIN;
    }
}

// What CC_SETPOSITION/CC_SETSIZE run after storing (0x4e06dc ops 1000/1001): 0x44a788(client, slot),
// 0x45e304(client, slot), then for layers (type 0) 0x45ebf4(client, children, slot, 0) with the
// children container from 0x5d38c4(im, group) (static) or the parent's +0xb48 (CC_CREATE child).
bool widget_revalidate(int component, int index) {
    uintptr_t c = client();
    uintptr_t slot = widget_slot(component, index);
    uintptr_t w = slot ? read_at<uintptr_t>(slot + 8) : 0;
    if (!c || !w) return false;
    reinterpret_cast<void (*)(uintptr_t, uintptr_t)>(g_base + 0x44a788)(c, slot);
    reinterpret_cast<void (*)(uintptr_t, uintptr_t)>(g_base + 0x45e304)(c, slot);
    uintptr_t im = reinterpret_cast<uintptr_t (*)(uintptr_t)>(g_base + 0x459be8)(c);
    if (read_at<int32_t>(w + 0x28) == 0 && im) {
        uintptr_t children;
        if (read_at<int32_t>(w + 8) == -1) {
            children = reinterpret_cast<uintptr_t (*)(uintptr_t, long)>(g_base + 0x5d38c4)(im, read_at<int16_t>(w + 6));
        } else {
            uintptr_t parent = reinterpret_cast<uintptr_t (*)(uintptr_t, int)>(g_base + 0x5d1ad8)(im, read_at<int32_t>(w + 0x74));
            children = parent ? read_at<uintptr_t>(parent + 8) + 0xb48 : 0;
        }
        if (children) reinterpret_cast<void (*)(uintptr_t, uintptr_t, uintptr_t, int)>(g_base + 0x45ebf4)(c, children, slot, 0);
    }
    return true;
}

// Component id and CC_CREATE index (-1 static) of a Widget*: a created child keeps its index at
// +0x08 and its parent component at +0x74; a static one only its group (+0x06), so its slot is
// looked up in the group table. Confirmed against the slot, so a bad pointer gives false.
bool widget_ref(uintptr_t w, int* component, int* index) {
    if (!w || !client()) return false;
    int idx = read_at<int32_t>(w + 8);
    if (idx != -1) {
        int parent = read_at<int32_t>(w + 0x74);
        uintptr_t slot = widget_slot(parent, idx);
        if (!slot || read_at<uintptr_t>(slot + 8) != w) return false;
        *component = parent;
        *index = idx;
        return true;
    }
    int group = read_at<uint16_t>(w + 6);
    auto im = read_at<uintptr_t>(client() + g_build->f_interfaces);
    if (!im || static_cast<uint64_t>(group) >= read_at<uint64_t>(im + 0x6888)) return false;
    uintptr_t g = read_at<uintptr_t>(im + 0x6890) + static_cast<uint64_t>(group) * 0x18;
    uint64_t n = read_at<uint64_t>(g + 8);
    auto slots = read_at<uintptr_t>(g + 0x10);
    if (!slots || n > 0x10000) return false;
    for (uint64_t i = 0; i < n; i++)
        if (read_at<uintptr_t>(slots + i * 0x10 + 8) == w) {
            *component = group << 16 | static_cast<int>(i);
            *index = -1;
            return true;
        }
    return false;
}

// CC_CREATE (script op 100 in 0x4e06dc) for Widget.createChild: capacity/replace 0x4f2898(parent
// slot, index, 0) (an existing child at the index is deleted, as CC_CREATE without its 4th arg),
// make_shared<Widget>(out, &parent id) 0x4f290c, the same fields the op writes (+0x00 = 1, +0x08 index,
// +0x28 type, +0x74 parent id), then the move-assign 0x44a538 into the parent's +0xb58 slot.
// Static parents of layer type only; text inputs (type 12) need the op's extra setup. Game thread.
int widget_create_child(int component, int index, int type) {
    if (!client() || type < 0 || type > 11 || index > 4095) return -1;
    uintptr_t slot = widget_slot(component, -1);
    uintptr_t parent = slot ? read_at<uintptr_t>(slot + 8) : 0;
    if (!parent || read_at<int32_t>(parent + 0x28) != 0) return -1;
    if (index < 0) index = widget_dynamic_count(parent);
    if (!(reinterpret_cast<uint64_t (*)(uintptr_t, int, bool)>(g_base + 0x4f2898)(slot, index, false) & 1)) return -1;
    struct SharedPtr {
        uintptr_t ctrl, obj;
        ~SharedPtr() {}
    };
    // Not an x8 return: the out pointer is x0 and the id pointer x1 (disassembly at 0x4f290c).
    SharedPtr sp{0, 0};
    reinterpret_cast<void (*)(SharedPtr*, const int32_t*)>(g_base + 0x4f290c)(&sp, reinterpret_cast<const int32_t*>(parent + 4));
    if (!sp.obj) return -1;
    *reinterpret_cast<uint8_t*>(sp.obj) = 1;
    *reinterpret_cast<int32_t*>(sp.obj + 8) = index;
    *reinterpret_cast<int32_t*>(sp.obj + 0x28) = type;
    *reinterpret_cast<int32_t*>(sp.obj + 0x74) = read_at<int32_t>(parent + 4);
    uintptr_t dst = read_at<uintptr_t>(parent + 0xb58) + static_cast<size_t>(index) * 0x10;
    reinterpret_cast<void* (*)(uintptr_t, SharedPtr*)>(g_base + 0x44a538)(dst, &sp);  // moves sp out
    return index;
}

// Widget.setChildren(Arrays.copyOf(children, n)) (Bank Tags' rebuildTabs): the CC_CREATE child
// vector {?, u64 count +0xb50, data +0xb58} is resized with the game's own resize 0x40a25c(vec, n, 3)
// (flag 2 moves the first n shared_ptrs into the new storage, the rest are released; the same call
// the capacity check 0x4f2898 makes to grow it). Shrinks only. Game thread.
bool widget_truncate_children(int component, int count) {
    uintptr_t slot = widget_slot(component, -1);
    uintptr_t w = slot ? read_at<uintptr_t>(slot + 8) : 0;
    if (!client() || !w || count < 0) return false;
    int n = widget_dynamic_count(w);
    if (count >= n) return count == n;
    reinterpret_cast<void (*)(uintptr_t, uint64_t, uint32_t)>(g_base + 0x40a25c)(w + 0xb48, static_cast<uint64_t>(count), 3);
    return true;
}

// CC_DELETEALL (script op 101): 0x5df8e0(widget) releases every CC_CREATE child, then
// 0x44a788(client, slot) as the op does. Game thread.
bool widget_delete_children(int component) {
    uintptr_t c = client();
    uintptr_t slot = widget_slot(component, -1);
    uintptr_t w = slot ? read_at<uintptr_t>(slot + 8) : 0;
    if (!c || !w) return false;
    reinterpret_cast<void (*)(uintptr_t)>(g_base + 0x5df8e0)(w);
    reinterpret_cast<void (*)(uintptr_t, uintptr_t)>(g_base + 0x44a788)(c, slot);
    return true;
}

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

std::vector<std::string> widget_strings(uintptr_t w) {
    std::vector<std::string> out;
    if (!w) return out;
    out.push_back(read_string(w + 0x2c0));
    out.push_back(read_string(w + 0x330));
    uint64_t n = read_at<uint64_t>(w + 0x2e0);
    uintptr_t ops = read_at<uintptr_t>(w + 0x2e8);
    if (ops && n <= 64)
        for (uint64_t i = 0; i < n; i++) out.push_back(read_string(ops + i * 0x18));
    return out;
}

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
            out.push_back({read_at<int32_t>(node), read_at<int32_t>(node + 4), read_at<int32_t>(node + 8)});
        }
    }
    return out;
}

// Closes the sub-interface `group` mounted in `component`, as IF_CLOSESUB (dispatcher case 0x65) does:
// `0x462384(client, component, group, unload)` removes the component-table node, unloads the group
// when `unload` (bit 0), drops the group's IF_SETEVENTS ranges, marks the parent dirty and lays out
// the root again. Only called when the node is open (the packet handler checks the same).
// Game thread only. Verified 2026-10-17 gen 29 (closing the bank, group 12, in 601:27).
bool close_sub_interface(int component, int group, bool unload) {
    uintptr_t c = client();
    if (!c) return false;
    bool open = false;
    for (const SubInterface& s : open_sub_interfaces()) open |= s.component == component && s.group == group;
    if (!open) return false;
    reinterpret_cast<void (*)(uintptr_t, int, uint32_t, uint64_t)>(g_base + 0x462384)(c, component, static_cast<uint32_t>(group), unload ? 1 : 0);
    return true;
}

// Minimap (`mm = *(Client+0x40e970)`, built by 0x58776c/0x589120): ARGB raster {u64 pixel count
// +0x6440, int* pixels +0x6450, width/height +0x6458/+0x645c}, zoom (px per tile) +0x6470, image
// size zoom*104 + 96; +0xc = the plane drawn into it (the cycle redraws when the render world
// view's plane, Client[+0xc940]+0x18, differs: 0x41f22c). 0x589180(mm, plane, Scene*) clears it to
// 0xff000000 and draws every tile, wall and map scenery of `plane` (desktop's minimap renderer).
// Static RE gen 29 (subagent). Verified 2026-10-17 gen 29 with Instance Map (planes 0 and 2).
bool draw_minimap(int plane, std::vector<int32_t>& out) {
    uintptr_t c = client();
    if (!c || plane < 0 || plane > 3) return false;
    uintptr_t mm = read_at<uintptr_t>(c + 0x40e970);
    uintptr_t wv = read_at<uintptr_t>(c + 0xc940);
    uintptr_t scene = wv ? read_at<uintptr_t>(wv + 0x10) : 0;
    if (!mm || !scene || !read_at<uintptr_t>(mm + 0x65b8)) return false;
    auto* px = read_at<int32_t*>(mm + 0x6450);
    uint64_t count = read_at<uint64_t>(mm + 0x6440);
    int zoom = read_at<int32_t>(mm + 0x6470), w = read_at<int32_t>(mm + 0x6458), h = read_at<int32_t>(mm + 0x645c);
    int size = zoom * 104 + 96;
    if (!px || zoom < 2 || zoom > 8 || w != size || h != size || count != static_cast<uint64_t>(size) * size) return false;
    int shown = read_at<int32_t>(mm + 0xc);
    using Render = void (*)(uintptr_t, int, uintptr_t);
    auto render = reinterpret_cast<Render>(g_base + 0x589180);
    render(mm, plane, scene);
    // Desktop layout: the extended scene (184 tiles, 4 px each = 736 px); the mobile scene's 104
    // tiles are its middle (5 chunks in: 160 px), which is what Instance Map crops with
    // getExpandedMapLoading() = 0. The game's image has its 104 tiles at 48 px, zoom px each:
    // nearest-neighbour when zoom isn't 4.
    out.assign(kInstanceMapSize * kInstanceMapSize, 0);
    for (int y = 0; y < 416; y++) {
        int sy = 48 + y * zoom / 4;
        for (int x = 0; x < 416; x++) {
            int sx = 48 + x * zoom / 4;
            if (sx < size && sy < size) out[(160 + y) * kInstanceMapSize + 160 + x] = px[sy * size + sx] & 0xffffff;
        }
    }
    // Put the game's image back (0x589180 doesn't touch +0xc; at -1 the cycle redraws anyway).
    if (shown >= 0 && shown <= 3) render(mm, shown, scene);
    return true;
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

bool message_set_text(int id, const std::string& text) {
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
        GameString s(text);
        reinterpret_cast<void* (*)(uintptr_t, const void*)>(g_base + 0x3f1498)(msg + 0xa0, s.bytes);
        return true;
    }
    return false;
}

// Chat channels (global map 0xaab5e0, same layout as messages_by_id; node {int type; +0x08
// channel*; +0x10 next}): a ring of 100 {ctrl*, Message*} pairs, +0x640 count, +0x644 newest
// index (0x3ef964 adds). 0x43aacc(client) bumps the chat cycle (Client +0x413e6c) so the chatbox
// rebuilds, as after any new message.
namespace {
constexpr uintptr_t kChatChannels = 0xaab5e0, kChatChanged = 0x43aacc;
// All messages in arrival order: libc++ std::list {prev, next, size} at 0xaab610, newest first
// (push_front in the Message constructor 0x3f0a04); node {prev, next, int id}; Message +0 = node.
// The chatbox's All tab walks it by uid (chat_getprevuid 0x3f061c / chat_getnextuid 0x3f0694).
constexpr uintptr_t kChatList = 0xaab610;
uintptr_t chat_channel(int type) {
    uintptr_t map = g_base + kChatChannels;
    auto buckets = read_at<uintptr_t>(map + 8);
    auto nb = read_at<uint32_t>(map + 0x10);
    if (!buckets || !nb) return 0;
    for (auto node = read_at<uintptr_t>(buckets + (static_cast<uint32_t>(type) % nb) * 8); node;
         node = read_at<uintptr_t>(node + 0x10))
        if (read_at<int32_t>(node) == type) return read_at<uintptr_t>(node + 8);
    return 0;
}
}  // namespace

// Idle logout (0x41d0a4, IDLE = client prot 97 at 0x4216f8): the game logs out when
// settings->AFKTimeout (minutes; Settings* at Client +0x2278, int +0x154c, 5 by default) * 60000
// ms have passed since the last input (Client +0x413270, ms).
// ---- World map. Static RE gen 31 of the worldmap_* handler (ELF 0x4c3cf8): zoom 6603 = float
// wm+0xfc (0x4b7570 maps 1/1.5/2/3/4/else to 25/37/50/75/100/200), isloaded 6605 = byte
// *(wm+0xd8)+0x14 (0x4b6158), display position 6610 = wm+0xe0/+0xe4 + current area's
// regionLowX/Y (+0x50/+0x58) * 64 (0x4b795c/0x4b797c), current map 6616 = *(int*)area (area = wm+0xb8),
// coordinmap 6621 = area lookup by id in the wm+0x78 bucket table (0x4b6be0) and the area's sections
// (+0x68..+0x70, 16-byte {ctrl, section*}) vtable slot 6 contains(level, x, z) (0x5c57f4).
static constexpr uintptr_t kClientWorldMap = 0x418660;

static uintptr_t world_map() {
    uintptr_t c = client();
    return c ? read_at<uintptr_t>(c + kClientWorldMap) : 0;
}

WorldMapState world_map_state() {
    WorldMapState st;
    uintptr_t wm = world_map();
    if (!g_build || !wm) return st;
    st.present = true;
    uintptr_t r = read_at<uintptr_t>(wm + 0xd8);
    st.loaded = r && read_at<uint8_t>(r + 0x14) != 0;
    st.zoom = read_at<float>(wm + 0xfc);
    uintptr_t area = read_at<uintptr_t>(wm + 0xb8);
    if (area) {
        st.map = read_at<int32_t>(area);
        st.x = read_at<int32_t>(wm + 0xe0) + read_at<int32_t>(area + 0x50) * 64;
        st.y = read_at<int32_t>(wm + 0xe4) + read_at<int32_t>(area + 0x58) * 64;
    }
    return st;
}

int world_map_contains(int map, int level, int x, int z) {
    uintptr_t wm = world_map();
    if (!g_build || !wm) return -1;
    uintptr_t buckets = read_at<uintptr_t>(wm + 0x78);
    uint64_t count = read_at<uint64_t>(wm + 0x80);
    if (!buckets || count == 0 || count > 4096) return -1;
    uintptr_t area = 0;
    for (uint64_t b = 0; b < count && !area; b++) {
        int guard = 0;
        for (uintptr_t node = read_at<uintptr_t>(buckets + b * 8); node && guard < 4096; guard++) {
            uintptr_t a = read_at<uintptr_t>(node + 0x20);
            if (a && read_at<int32_t>(a) == map) { area = a; break; }
            uintptr_t next = read_at<uintptr_t>(node + 0x28);
            // The last node of a bucket chains into the next bucket's first node (0x4b6be0 walks
            // on until the end sentinel); stop at the bucket boundary instead.
            if (next == read_at<uintptr_t>(buckets + (b + 1) * 8)) break;
            node = next;
        }
    }
    if (!area) return -1;
    uintptr_t it = read_at<uintptr_t>(area + 0x68), end = read_at<uintptr_t>(area + 0x70);
    if (it > end || end - it > 0x10 * 4096) return -1;
    for (; it < end; it += 0x10) {
        uintptr_t sec = read_at<uintptr_t>(it + 8);
        if (!sec) continue;
        uintptr_t vt = read_at<uintptr_t>(sec);
        auto contains = reinterpret_cast<uint32_t (*)(uintptr_t, int, int, int)>(read_at<uintptr_t>(vt + 0x30));
        if (contains(sec, level, x, z) & 1) return 1;
    }
    return 0;
}

bool world_map_jump(int level, int x, int z) {
    uintptr_t wm = world_map();
    if (!g_build || !wm) return false;
    reinterpret_cast<void (*)(uintptr_t, int, int, int)>(g_base + 0x4b7854)(wm, level, x, z);
    return true;
}

// Map regions and their icons. Static RE gen 32: worldmap_listelement_start (6639, ELF 0x4b8f0c)
// requires the loaded byte *(wm+0xd8)+0x14 and *(*(wm+0xd0)+8), then has the icon map rebuilt from
// the region grid of the manager *(wm+0xd0) (0x5caf1c): rows at +0x68 (count +0x60), 0x18-byte
// {?, count +8, Region** +0x10}; a region holds a std::list of icon shared_ptrs at +0x38 (node: +0
// next, +0x10 control block, +0x18 icon) and a hash table at +0x58 (buckets) / +0x60 (bucket count;
// buckets[count] is the end sentinel; node: +8 control block, +0x10 icon, +0x18 next, a null next
// continues with the next bucket). Never call into the control block (slot 1 frees it). Icon: vtable
// slot 1 = element (map element id, -1 = none), +8 level, +0xc x, +0x10 y (the coordinate 6639
// pushes). Out: per icon {row, col, element, level, x, y}.
bool world_map_icons(int& rows, int& cols, std::vector<int>& out) {
    rows = cols = 0;
    uintptr_t wm = world_map();
    if (!g_build || !wm) return false;
    uintptr_t r = read_at<uintptr_t>(wm + 0xd8), mgr = read_at<uintptr_t>(wm + 0xd0);
    if (!r || !mgr || !read_at<uint8_t>(r + 0x14) || !read_at<uint8_t>(mgr + 8)) return false;
    uint64_t nrows = read_at<uint64_t>(mgr + 0x60);
    uintptr_t rowp = read_at<uintptr_t>(mgr + 0x68);
    if (!rowp || nrows > 512) return false;
    rows = static_cast<int>(nrows);
    auto add = [&](int row, int col, uintptr_t icon) {
        if (!icon || out.size() > 6 * 50000) return;
        uintptr_t vt = read_at<uintptr_t>(icon);
        if (!vt) return;
        int element = reinterpret_cast<int (*)(uintptr_t)>(read_at<uintptr_t>(vt + 8))(icon);
        if (element < 0) return;
        out.insert(out.end(), {row, col, element, read_at<int32_t>(icon + 8), read_at<int32_t>(icon + 0xc),
                               read_at<int32_t>(icon + 0x10)});
    };
    for (uint64_t i = 0; i < nrows; i++) {
        uint64_t ncols = read_at<uint64_t>(rowp + i * 0x18 + 8);
        uintptr_t regs = read_at<uintptr_t>(rowp + i * 0x18 + 0x10);
        if (!regs || ncols > 512) continue;
        if (static_cast<int>(ncols) > cols) cols = static_cast<int>(ncols);
        for (uint64_t j = 0; j < ncols; j++) {
            uintptr_t reg = read_at<uintptr_t>(regs + j * 8);
            if (!reg) continue;
            int guard = 0;
            for (uintptr_t n = read_at<uintptr_t>(reg + 0x38); n && n != reg + 0x38 && guard < 4096; guard++) {
                add(static_cast<int>(i), static_cast<int>(j), read_at<uintptr_t>(n + 0x18));
                n = read_at<uintptr_t>(n);
            }
            uintptr_t buckets = read_at<uintptr_t>(reg + 0x58);
            uint64_t nb = read_at<uint64_t>(reg + 0x60);
            if (!buckets || nb == 0 || nb > 65536) continue;
            // As 0x5caf1c: from the first non-null bucket follow +0x18, skipping to the next
            // non-null bucket on null, until the end sentinel buckets[nb].
            uintptr_t end = read_at<uintptr_t>(buckets + nb * 8);
            uint64_t b = 0;
            uintptr_t n = 0;
            while (b < nb && !(n = read_at<uintptr_t>(buckets + b * 8))) b++;
            for (guard = 0; b < nb && n && n != end && guard < 65536; guard++) {
                add(static_cast<int>(i), static_cast<int>(j), read_at<uintptr_t>(n + 0x10));
                n = read_at<uintptr_t>(n + 0x18);
                while (!n && ++b <= nb) n = read_at<uintptr_t>(buckets + b * 8);
            }
        }
    }
    return true;
}

// Map element (mel) configs, static RE gen 32: CS2 MEC_TEXT/TEXTSIZE/CATEGORY/SPRITE (6693-6696 in
// 0x4c3cf8) read 0x63529c(id) = table[id], the table being the vector {+8 count, +0x10 data} at ELF
// 0x1212620 (filled by the config loader 0x634394; opcode 19 = category +0x180, sprite +4, textsize +0x30).
bool map_element(int id, int& category, int& sprite) {
    if (!g_build || id < 0) return false;
    uint64_t count = read_at<uint64_t>(g_base + 0x1212628);
    uintptr_t data = read_at<uintptr_t>(g_base + 0x1212630);
    if (!data || static_cast<uint64_t>(id) >= count) return false;
    uintptr_t mel = read_at<uintptr_t>(data + static_cast<uintptr_t>(id) * 8);
    if (!mel) return false;
    category = read_at<int32_t>(mel + 0x180);
    sprite = read_at<int32_t>(mel + 4);
    return true;
}

int afk_timeout_minutes() {
    uintptr_t c = client();
    if (!g_build || !c) return -1;
    auto settings = read_at<uintptr_t>(c + 0x2278);
    return settings ? read_at<int32_t>(settings + 0x154c) : -1;
}
bool set_afk_timeout_minutes(int minutes) {
    uintptr_t c = client();
    if (!g_build || !c || minutes < 1 || minutes > 35000) return false;
    auto settings = read_at<uintptr_t>(c + 0x2278);
    if (!settings) return false;
    *reinterpret_cast<int32_t*>(settings + 0x154c) = minutes;
    return true;
}

// Members world. setWorld (0x432748), run when a world is picked from the world list or by a
// world-switch packet, sets Client+0x2070 = flags & 1 and calls setMembersWorld (0x642f14), which
// flushes the obj cache and sets the global members byte (0x1221e90: "(Members)" item names and
// hidden ops when 0). The jav_config path that starts the game on its world only writes the flags
// (Client+0x2f64), so a members world started that way stays free-to-play. Complete that step with
// the game's own function when the flags it received disagree with its members byte.
void sync_members_world() {
    uintptr_t c = client();
    if (!g_build || !c || game_state() < 10) return;
    bool members = read_at<int32_t>(c + 0x2f64) & 1;
    auto* cached = reinterpret_cast<uint8_t*>(c + 0x2070);
    if (static_cast<bool>(*cached & 1) == members) return;
    *cached = members ? 1 : 0;
    reinterpret_cast<void (*)(bool)>(g_base + 0x642f14)(members);
    LOGI("game: members world %d (flags %#x)", members ? 1 : 0, read_at<int32_t>(c + 0x2f64));
}

void chat_changed() {
    uintptr_t c = client();
    if (g_build && c) reinterpret_cast<void (*)(uintptr_t)>(g_base + kChatChanged)(c);
}

// The removed pair is rotated to the oldest position and the count drops by one, so every pair
// stays owned by the ring (the add path reuses or releases it later) and no refcount changes.
bool chat_remove_message(int type, int id) {
    if (!g_build) return false;
    uintptr_t ch = chat_channel(type);
    if (!ch) {
        LOGI("chat: no channel for type %d", type);
        return false;
    }
    int count = read_at<int32_t>(ch + 0x640), newest = read_at<int32_t>(ch + 0x644);
    if (count <= 0 || count > 100 || newest < 0 || newest >= 100) {
        LOGI("chat: channel %d count %d newest %d", type, count, newest);
        return false;
    }
    auto slot = [&](int age) { return reinterpret_cast<uintptr_t*>(ch + ((newest - age) % 100 + 100) % 100 * 0x10); };
    for (int k = 0; k < count; k++) {
        uintptr_t msg = slot(k)[1];
        if (!msg) continue;
        uintptr_t node = read_at<uintptr_t>(msg);
        if (!node || read_at<int32_t>(node + 0x10) != id) continue;
        uintptr_t saved[2] = {slot(k)[0], slot(k)[1]};
        for (int j = k; j < count - 1; j++) {
            slot(j)[0] = slot(j + 1)[0];
            slot(j)[1] = slot(j + 1)[1];
        }
        slot(count - 1)[0] = saved[0];
        slot(count - 1)[1] = saved[1];
        *reinterpret_cast<int32_t*>(ch + 0x640) = count - 1;
        // Unlink from the all-messages list too (else the All tab shows it again). The node is
        // left allocated and Message +0 points at the list head: the channel add (0x3ef964)
        // skips its own unlink/free for that value when it later reuses this ring slot.
        uintptr_t list = g_base + kChatList;
        uintptr_t prev = read_at<uintptr_t>(node), next = read_at<uintptr_t>(node + 8);
        if (node != list && prev && next) {
            *reinterpret_cast<uintptr_t*>(prev + 8) = next;
            *reinterpret_cast<uintptr_t*>(next) = prev;
            *reinterpret_cast<uint64_t*>(list + 0x10) = read_at<uint64_t>(list + 0x10) - 1;
            *reinterpret_cast<uintptr_t*>(msg) = list;
        }
        return true;
    }
    uintptr_t m0 = slot(0)[1], n0 = m0 ? read_at<uintptr_t>(m0) : 0;
    LOGI("chat: id %d not in channel %d (count %d, newest id %d)", id, type, count, n0 ? read_at<int32_t>(n0 + 0x10) : -1);
    return false;
}

int player_plane() { return g_build ? field(g_build->f_plane) : -1; }

int scene_plane() {
    uintptr_t c = client();
    if (!c) return -1;
    auto wv = read_at<uintptr_t>(c + g_build->f_world_view);
    return wv ? read_at<int32_t>(wv + g_build->wv_plane) : -1;
}

bool actor_set_overhead(uintptr_t a, const std::string* text, int cycle) {
    if (!a) return false;
    if (text) {
        GameString s(*text);
        reinterpret_cast<void* (*)(uintptr_t, const void*)>(g_base + 0x3f1498)(a + 0x2d8, s.bytes);
    }
    if (cycle >= 0) *reinterpret_cast<int32_t*>(a + 0x2f4) = cycle;
    return true;
}

bool reload_scene() {
    uintptr_t c = client();
    if (!c || game_state() != 30) return false;
    reinterpret_cast<void (*)(uintptr_t)>(g_base + 0x429900)(c);
    return true;
}

int friend_list_state() {
    uintptr_t c = client();
    auto fm = c ? read_at<uintptr_t>(c + 0x46c0) : 0;
    return fm ? read_at<int32_t>(fm + 0x150) : 0;
}

bool friend_list(bool ignores, std::vector<FriendEntry>& out) {
    out.clear();
    uintptr_t c = client();
    auto fm = c ? read_at<uintptr_t>(c + 0x46c0) : 0;
    if (!fm || read_at<int32_t>(fm + 0x150) != 2) return false;
    uintptr_t box = fm + (ignores ? 0xb8 : 0x08);
    int n = read_at<int32_t>(box + 0xc);
    auto arr = read_at<uintptr_t>(box + 0x20);
    if (n < 0 || n > 1000 || (n > 0 && !arr)) return false;
    for (int i = 0; i < n; i++) {
        auto e = read_at<uintptr_t>(arr + 8 * i);
        if (!e) continue;
        FriendEntry f;
        f.name = read_string(e + 0x08);
        f.prev = read_string(e + 0x38);
        if (!ignores) {
            f.world = read_at<int32_t>(e + 0x68);
            f.rank = read_at<int32_t>(e + 0x88);
        }
        out.push_back(std::move(f));
    }
    return true;
}

Point walk_destination() {
    if (!g_build || !client()) return {-1, -1};
    // The map flag (SET_MAP_FLAG_V2 and the client's own walk): Client +0x40e970 -> {scene x, y},
    // 0,0 when cleared (setter 0x4410b0). OpenRune flags every route; the older Client +0xecdc pair
    // only showed client-side walks on our old server.
    if (auto flag = read_at<uintptr_t>(client() + 0x40e970)) {
        int x = read_at<int32_t>(flag), y = read_at<int32_t>(flag + 4);
        if (x > 0 || y > 0) return {x, y};
    }
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

bool script_stacks(ScriptResult& out) {
    if (!g_build || !g_build->script_vm) return false;
    uintptr_t vm = g_base + g_build->script_vm;
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

bool set_script_int_stack(const int32_t* values, int size) {
    if (!g_build || !g_build->script_vm || size < 0 || size > 1000) return false;
    uintptr_t vm = g_base + g_build->script_vm;
    if (size) memcpy(reinterpret_cast<void*>(vm + 0x50), values, size * 4u);
    *reinterpret_cast<int32_t*>(vm + 0xff0) = size;
    return true;
}

// Script locals of the running frame (frame 0: the VM's live locals, int vector vm+0x08, object
// vector vm+0x38) or of a saved call frame (int vector +0x18, object vector +0x48; the frame a
// RETURN is about to restore, script_hooks::return_frame()). Arrays (define_array, case 0x2c) are
// object locals at slot = desktop array id: a 0x28-byte variant with type 4 (+0x20) holding the
// array object pointer (+0); the array is {u8 dirty, variant storage +0x08, alternative index +0x38},
// alternative 0 = int array, storage a std::vector<int32_t> (static RE of 0x4dd08c cases 0x2c-0x2e,
// 0x5f1974/0x5f1fbc/0x5f200c). // unverified: the vector<int32_t> storage, on the device.
namespace {
bool locals_base(uintptr_t frame, uintptr_t& ints, uintptr_t& objs) {
    if (!g_build || !g_build->script_vm) return false;
    uintptr_t vm = g_base + g_build->script_vm;
    ints = frame ? frame + 0x18 : vm + 0x08;
    objs = frame ? frame + 0x48 : vm + 0x38;
    return true;
}

int32_t* script_int_array(uintptr_t frame, int slot, size_t& n, uintptr_t* array_obj = nullptr) {
    uintptr_t ints, objs;
    if (slot < 0 || slot > 255 || !locals_base(frame, ints, objs)) return nullptr;
    uintptr_t b = read_at<uintptr_t>(objs), e = read_at<uintptr_t>(objs + 8);
    if (!b || e < b || b + static_cast<uintptr_t>(slot + 1) * 0x28 > e) return nullptr;
    uintptr_t v = b + static_cast<uintptr_t>(slot) * 0x28;
    if (read_at<uint32_t>(v + 0x20) != 4) return nullptr;
    uintptr_t arr = read_at<uintptr_t>(v);
    if (!arr || read_at<uint32_t>(arr + 0x38) != 0) return nullptr;
    auto* vb = read_at<int32_t*>(arr + 0x08);
    auto* ve = read_at<int32_t*>(arr + 0x10);
    if (!vb || ve < vb || ve - vb > 1 << 20) return nullptr;
    n = static_cast<size_t>(ve - vb);
    if (array_obj) *array_obj = arr;
    return vb;
}
}  // namespace

bool script_array(uintptr_t frame, int slot, std::vector<int32_t>& out) {
    size_t n = 0;
    int32_t* p = script_int_array(frame, slot, n);
    if (!p) return false;
    out.assign(p, p + n);
    return true;
}

bool set_script_array(uintptr_t frame, int slot, const int32_t* values, int count) {
    size_t n = 0;
    uintptr_t arr = 0;
    int32_t* p = script_int_array(frame, slot, n, &arr);
    if (!p || count < 0) return false;
    size_t k = std::min(n, static_cast<size_t>(count));  // the array keeps its length
    memcpy(p, values, k * 4u);
    *reinterpret_cast<uint8_t*>(arr) = 1;  // as the element write 0x5f200c
    return true;
}

bool script_int_local(uintptr_t frame, int index, int32_t& value, const int32_t* set) {
    uintptr_t ints, objs;
    if (index < 0 || !locals_base(frame, ints, objs)) return false;
    auto* b = read_at<int32_t*>(ints);
    auto* e = read_at<int32_t*>(ints + 8);
    if (!b || e < b || index >= e - b) return false;
    if (set) b[index] = *set;
    value = b[index];
    return true;
}

// Single-slot stack access (callbacks raised per item, e.g. bankSearchFilter: copying whole stacks
// over JNI each time cost ~25 fps). depth 0 = top. Game thread only.
bool script_int_top(int depth, int32_t& value, const int32_t* set) {
    if (!g_build || !g_build->script_vm || depth < 0) return false;
    uintptr_t vm = g_base + g_build->script_vm;
    int n = read_at<int32_t>(vm + 0xff0);
    if (n <= depth || n > 1000) return false;
    auto* slot = reinterpret_cast<int32_t*>(vm + 0x50 + 4u * static_cast<uintptr_t>(n - 1 - depth));
    if (set) *slot = *set;
    value = *slot;
    return true;
}

bool script_string_top(std::string& out) {
    if (!g_build || !g_build->script_vm) return false;
    uintptr_t vm = g_base + g_build->script_vm;
    int m = read_at<int32_t>(vm + 0xac38);
    if (m <= 0 || m > 1000) return false;
    uintptr_t v = vm + 0xff8 + static_cast<uintptr_t>(m - 1) * 0x28;
    if (read_at<uint32_t>(v + 0x20) != 2) return false;
    out = read_string(v);
    return true;
}

bool script_top_string_starts_with(const std::string& prefix) {
    if (!g_build || !g_build->script_vm) return false;
    uintptr_t vm = g_base + g_build->script_vm;
    int m = read_at<int32_t>(vm + 0xac38);
    if (m <= 0 || m > 1000) return false;
    uintptr_t v = vm + 0xff8 + static_cast<uintptr_t>(m - 1) * 0x28;
    if (read_at<uint32_t>(v + 0x20) != 2) return false;
    return read_string(v).compare(0, prefix.size(), prefix) == 0;
}

bool set_script_string(int index, const std::string& text) {
    if (!g_build || !g_build->script_vm) return false;
    uintptr_t vm = g_base + g_build->script_vm;
    int m = read_at<int32_t>(vm + 0xac38);
    if (m < 0 || m > 1000 || index < 0 || index >= m) return false;
    uintptr_t v = vm + 0xff8 + static_cast<uintptr_t>(index) * 0x28;
    if (read_at<uint32_t>(v + 0x20) != 2) return false;
    GameString s(text);
    reinterpret_cast<void* (*)(uintptr_t, const void*)>(g_base + 0x3f1498)(v, s.bytes);
    return true;
}

// Text width in a game font, as CS2 PARAWIDTH (4109, case 0x100d of Ghidra 005ef2ec.c): font =
// 0x43cce0(client, font id) (the font cache at Client +0x414fc8, loads on a miss), and the
// per-line width 0x685370(font, string*, 1) that PARAWIDTH's 0x6871ec takes the max of over the
// wrapped lines. -1 when the font isn't available. Game thread. Static RE (gen 22).
int text_width(int font_id, const std::string& text) {
    uintptr_t c = client();
    if (!c || font_id < 0) return -1;
    uintptr_t font = reinterpret_cast<uintptr_t (*)(uintptr_t, int)>(g_base + 0x43cce0)(c, font_id);
    if (!font) return -1;
    GameString s(text);
    return reinterpret_cast<int (*)(uintptr_t, const void*, int)>(g_base + 0x685370)(font, s.bytes, 1);
}

// The VM's active components (RuneLite's scriptActiveWidget / scriptDotWidget): {ctrl, Widget*}
// pairs at vm +0xded8 (cc_*) and +0xdee8 (.cc_*, the opcode's isLong flag), set by CC_CREATE /
// CC_FIND and read by every CC_* handler in 0x4e06dc (case 0x65 null-checks +0 and bumps its
// count at +8, so +0 is the control block). widget_ref validates the pointer. Static RE (gen 22).
bool script_active_widget(bool dot, int* component, int* index) {
    if (!g_build || !g_build->script_vm) return false;
    uintptr_t vm = g_base + g_build->script_vm;
    return widget_ref(read_at<uintptr_t>(vm + (dot ? 0xdee8 : 0xded8) + 8), component, index);
}

// ScriptEvent +0x40: the source widget (Widget*), null for scripts not run from a widget hook.
bool script_event_source(uintptr_t ev, int* component, int* index) {
    return ev && widget_ref(read_at<uintptr_t>(ev + 0x40), component, index);
}

std::vector<ScriptArg> script_event_args(uintptr_t ev) {
    std::vector<ScriptArg> out;
    if (!ev) return out;
    int argc = read_at<int32_t>(ev + 0x08);
    uintptr_t argv = read_at<uintptr_t>(ev + 0x10);
    if (argc <= 0 || argc > 64 || !argv) return out;
    for (int i = 0; i < argc; i++) {
        uintptr_t v = argv + static_cast<uintptr_t>(i) * 0x28;
        ScriptArg a;
        uint32_t type = read_at<uint32_t>(v + 0x20);
        if (type == 1) a.i = read_at<int32_t>(v);
        else if (type == 2) { a.is_string = true; a.s = read_string(v); }
        else a.i = -1;
        out.push_back(std::move(a));
    }
    return out;
}

}  // namespace game

namespace game {
void release_shared_ptr(uintptr_t ctrl);

// db_getfield (CS2 7502) in 0x5cd7f4(vm, opcode): pops row, packed column (table << 12 |
// column << 4), tuple index; pushes the tuple's values onto the int (+0x50/+0xff0), object
// (+0xff8/+0xac38) and long (+0xac40/+0xcb80) stacks. DBRowType 0x74f824 (+0x30 table id),
// DBTableType 0x6eb2c4, column types 0x6ec320(table, column) → {+0x08 count, +0x10 type*[]},
// each type's +0x08 = the int/long/string descriptor (globals 0x1d8aff8/0x1d8b000/0x1d8b008).
int db_row_table(int row) {
    if (!g_build || !g_build->script_vm || row < 0 || row > 0xffff) return -1;
    struct SharedPtr {
        uintptr_t ctrl, obj;
        ~SharedPtr() {}
    };
    SharedPtr r = reinterpret_cast<SharedPtr (*)(int)>(g_base + 0x64f824)(row);
    int table = r.obj ? read_at<int32_t>(r.obj + 0x30) : -1;
    release_shared_ptr(r.ctrl);
    return table;
}

int db_field_count(int row, int column) {
    if (!g_build || !g_build->script_vm || row < 0 || column < 0 || column > 255) return -1;
    uintptr_t vm = g_base + g_build->script_vm;
    if (read_at<uint8_t>(vm + 4)) return -1;
    int table = db_row_table(row);
    if (table < 0) return -1;
    struct SharedPtr {
        uintptr_t ctrl, obj;
        ~SharedPtr() {}
    };
    // The column must exist (the handler reads its type list).
    SharedPtr t = reinterpret_cast<SharedPtr (*)(int)>(g_base + 0x5eb2c4)(table);
    if (!t.obj) { release_shared_ptr(t.ctrl); return -1; }
    uintptr_t types = reinterpret_cast<uintptr_t (*)(uintptr_t, int)>(g_base + 0x5ec320)(t.obj, column);
    uint64_t ntypes = types ? read_at<uint64_t>(types + 8) : 0;
    release_shared_ptr(t.ctrl);
    if (ntypes == 0) return -1;
    auto* ints = reinterpret_cast<int32_t*>(vm + 0x50);
    *reinterpret_cast<int32_t*>(vm + 0xff0) = 2;
    ints[0] = row;
    ints[1] = (table << 12) | (column << 4);
    int ok = reinterpret_cast<int (*)(uintptr_t, int)>(g_base + 0x4cd7f4)(vm, 7503);
    int n = read_at<int32_t>(vm + 0xff0);
    int count = ok == 1 && n == 1 ? ints[0] : -1;
    *reinterpret_cast<int32_t*>(vm + 0xff0) = 0;
    *reinterpret_cast<int32_t*>(vm + 0xac38) = 0;
    *reinterpret_cast<int32_t*>(vm + 0xcb80) = 0;
    return count;
}

bool db_field(int row, int column, int tuple, std::vector<DbValue>& out) {
    out.clear();
    if (!g_build || !g_build->script_vm || row < 0 || column < 0 || column > 255 || tuple < 0) return false;
    uintptr_t vm = g_base + g_build->script_vm;
    if (read_at<uint8_t>(vm + 4)) return false;
    struct SharedPtr {
        uintptr_t ctrl, obj;
        ~SharedPtr() {}
    };
    SharedPtr r = reinterpret_cast<SharedPtr (*)(int)>(g_base + 0x64f824)(row);
    if (!r.obj) { release_shared_ptr(r.ctrl); return false; }
    int table = read_at<int32_t>(r.obj + 0x30);
    release_shared_ptr(r.ctrl);
    SharedPtr t = reinterpret_cast<SharedPtr (*)(int)>(g_base + 0x5eb2c4)(table);
    if (!t.obj) { release_shared_ptr(t.ctrl); return false; }
    uintptr_t types = reinterpret_cast<uintptr_t (*)(uintptr_t, int)>(g_base + 0x5ec320)(t.obj, column);
    std::vector<int> kinds;
    uintptr_t int_t = read_at<uintptr_t>(g_base + 0x1d8aff8), long_t = read_at<uintptr_t>(g_base + 0x1d8b000),
              str_t = read_at<uintptr_t>(g_base + 0x1d8b008);
    uint64_t n = types ? read_at<uint64_t>(types + 8) : 0;
    uintptr_t arr = types ? read_at<uintptr_t>(types + 0x10) : 0;
    for (uint64_t k = 0; arr && k < n && k < 64; k++) {
        uintptr_t ty = read_at<uintptr_t>(read_at<uintptr_t>(arr + k * 8) + 8);
        kinds.push_back(ty == int_t ? 0 : ty == long_t ? 1 : ty == str_t ? 2 : -1);
    }
    release_shared_ptr(t.ctrl);
    if (kinds.empty()) return false;
    for (int k : kinds) if (k < 0) return false;  // unknown type: the handler would throw
    auto* ints = reinterpret_cast<int32_t*>(vm + 0x50);
    *reinterpret_cast<int32_t*>(vm + 0xff0) = 3;
    *reinterpret_cast<int32_t*>(vm + 0xac38) = 0;
    *reinterpret_cast<int32_t*>(vm + 0xcb80) = 0;
    ints[0] = row;
    ints[1] = (table << 12) | (column << 4);
    ints[2] = tuple;
    int ok = reinterpret_cast<int (*)(uintptr_t, int)>(g_base + 0x4cd7f4)(vm, 7502);
    int ni = read_at<int32_t>(vm + 0xff0), no = read_at<int32_t>(vm + 0xac38), nl = read_at<int32_t>(vm + 0xcb80);
    auto reset = [vm] {
        *reinterpret_cast<int32_t*>(vm + 0xff0) = 0;
        *reinterpret_cast<int32_t*>(vm + 0xac38) = 0;
        *reinterpret_cast<int32_t*>(vm + 0xcb80) = 0;
    };
    if (ok != 1 || ni < 0 || no < 0 || nl < 0 || ni > 64 || no > 64 || nl > 64) { reset(); return false; }
    int ii = 0, io = 0, il = 0;
    for (int k : kinds) {
        DbValue v;
        v.kind = k;
        if (k == 0 && ii < ni) v.i = ints[ii++];
        else if (k == 1 && il < nl) v.i = read_at<int64_t>(vm + 0xac40 + 8 * il++);
        else if (k == 2 && io < no) v.s = read_string(vm + 0xff8 + 0x28 * io++);
        else break;
        out.push_back(std::move(v));
    }
    reset();
    return !out.empty();
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

void release_shared_ptr(uintptr_t ctrl) { release_shared(ctrl); }

bool set_varbit(int id, int value) {
    VarbitDef def;  // also rejects ids without a definition (the setter would dereference null)
    if (!g_build || !g_build->set_varbit || !varbit_def(id, def)) return false;
    reinterpret_cast<void (*)(int, int)>(g_base + g_build->set_varbit)(id, value);
    return true;
}

bool varbit_def(int id, VarbitDef& out) {
    if (!g_build || !g_build->get_varbit_type || id < 0 || id > 0xffff) return false;
    struct SharedPtr {
        uintptr_t ctrl, obj;
        ~SharedPtr() {}
    };
    SharedPtr r = reinterpret_cast<SharedPtr (*)(int)>(g_base + g_build->get_varbit_type)(id);
    uintptr_t ctrl = r.ctrl, o = r.obj;
    if (!o) { release_shared(ctrl); return false; }
    out.varp = read_at<int32_t>(o);
    out.lsb = read_at<int32_t>(o + 4);
    out.msb = read_at<int32_t>(o + 8);
    release_shared(ctrl);
    return out.varp >= 0 && out.varp < varp_count() && out.lsb >= 0 && out.msb < 32 && out.lsb <= out.msb;
}

// GE item search results (OC_FIND 4210 fills them, OC_FINDNEXT 4211 reads them, OC_FINDRESET 4212
// zeroes the index; handlers in 0x4bcf44, cases 0x1072-0x1074): Client +0x417ba8
// std::vector<uint16_t> ids {begin, end, cap}, +0x417bc0 next index, +0x417bc4 count.
// Static RE (gen 21). // unverified: no plugin path exercised on the device yet
namespace {
constexpr uint32_t kGeIds = 0x417ba8, kGeIndex = 0x417bc0, kGeCount = 0x417bc4;
constexpr uintptr_t kOperatorNew = 0x9f4384, kOperatorDelete = 0x9f43f4;
}  // namespace

bool set_ge_search_ids(const int16_t* ids, int n) {
    uintptr_t c = client();
    if (!c || n < 0 || n > 0xffff) return false;
    auto* v = reinterpret_cast<uintptr_t*>(c + kGeIds);  // begin, end, cap
    size_t cap = (v[2] - v[0]) / 2;
    if (static_cast<size_t>(n) > cap) {
        auto* fresh = reinterpret_cast<void* (*)(size_t)>(g_base + kOperatorNew)(static_cast<size_t>(n) * 2);
        if (!fresh) return false;
        if (v[0]) reinterpret_cast<void (*)(void*)>(g_base + kOperatorDelete)(reinterpret_cast<void*>(v[0]));
        v[0] = reinterpret_cast<uintptr_t>(fresh);
        v[2] = v[0] + static_cast<size_t>(n) * 2;
    }
    if (n) memcpy(reinterpret_cast<void*>(v[0]), ids, static_cast<size_t>(n) * 2);
    v[1] = v[0] + static_cast<size_t>(n) * 2;
    return true;
}

bool set_ge_search_int(bool index, int value) {
    uintptr_t c = client();
    if (!c) return false;
    *reinterpret_cast<int32_t*>(c + (index ? kGeIndex : kGeCount)) = value;
    return true;
}

// Minimap dot styles (the `setminimapdotstyle` console command 0x594634): Minimap = *(Client
// +0x40e970) (also the map flag holder); per category 0-6 a shared_ptr dot sprite at +0x64c0 +
// cat*0x10 and dot colour / outline colour / outline thickness at +0x6530 / +0x6534 / +0x6538
// + cat*0x10. setDotStyle(minimap, cat, dot rgb, outline rgb, thickness) 0x588eac rebuilds the
// sprite from the base dot (+0x6610 +0x60). Static RE (gen 21); recolouring verified on device (gen 22)
// The base dot (*(mm +0x6610)) only exists once the minimap is set up after login; with it null,
// setDotStyle's rebuild reads null+0x88 (crashed the game at startup, gen 22). Both functions
// refuse until then.
static uintptr_t minimap_ready() {
    uintptr_t c = client();
    if (!c) return 0;
    uintptr_t mm = read_at<uintptr_t>(c + 0x40e970);
    if (!mm || !read_at<uintptr_t>(mm + 0x6610)) return 0;
    return mm;
}

bool minimap_dot_style(int cat, int dot_rgb, int outline_rgb, int thickness) {
    if (cat < 0 || cat > 6 || thickness < 0) return false;
    uintptr_t mm = minimap_ready();
    if (!mm) return false;
    reinterpret_cast<void (*)(uintptr_t, unsigned, int, int, int)>(g_base + 0x588eac)(mm, cat, dot_rgb, outline_rgb, thickness);
    return true;
}

bool minimap_dot_get(int cat, int out[4]) {
    if (cat < 0 || cat > 6) return false;
    uintptr_t mm = minimap_ready();
    if (!mm) return false;
    uintptr_t e = mm + static_cast<uintptr_t>(cat) * 0x10;
    out[0] = read_at<uintptr_t>(e + 0x64c8) != 0;  // a styled sprite exists
    out[1] = read_at<int32_t>(e + 0x6530);
    out[2] = read_at<int32_t>(e + 0x6534);
    out[3] = read_at<int32_t>(e + 0x6538);
    return true;
}

bool queue_changed_skill(int skill) {
    uintptr_t c = client();
    if (!c || skill < 0 || skill >= 25) return false;
    auto* count = reinterpret_cast<uint32_t*>(c + g_build->f_changed_skill_count);
    uint32_t n = *count;
    reinterpret_cast<int32_t*>(c + g_build->f_changed_skills)[n & 31] = skill;
    *count = n + 1;
    return true;
}

bool set_varp(int id, int value) {
    if (!g_build || id < 0) return false;
    uint32_t count = read_at<uint32_t>(g_base + g_build->varps + 8);
    auto* values = read_at<int32_t*>(g_base + g_build->varps + 0x10);
    if (!values || static_cast<uint32_t>(id) >= count) return false;
    values[id] = value;
    return true;
}

static uintptr_t varc_store() {
    uintptr_t c = client();
    return c ? read_at<uintptr_t>(c + 0x417b68) : 0;
}

bool set_varc_int(int id, int value) {
    uintptr_t store = varc_store();
    if (!store || !g_build->set_varc_int || id < 0 || id > 0xffff) return false;
    reinterpret_cast<void (*)(uintptr_t, int, int)>(g_base + g_build->set_varc_int)(store, id, value);
    return true;
}

bool set_varc_string(int id, const std::string& value) {
    uintptr_t store = varc_store();
    if (!store || !g_build->set_varc_string || id < 0 || id > 0xffff) return false;
    GameString str(value);
    alignas(8) uint8_t variant[0x28] = {};
    memcpy(variant, str.bytes, 24);
    uint32_t index = 2;  // string alternative; the setter copies it, `str` keeps ownership
    memcpy(variant + 0x20, &index, 4);
    reinterpret_cast<void (*)(uintptr_t, int, const void*)>(g_base + g_build->set_varc_string)(store, id, variant);
    return true;
}


// Sprite (graphic) by id through the game's sprite cache: getSprite(cache, id, outline, vflip,
// hflip, shadow, bool* ok) at 0x5d4074 returns (x0) a pointer to the cached {ctrl, Sprite*} slot,
// not add-ref'd; ok stays false while the sprites archive (8) isn't loaded. Cache = Client +0x413a98.
// Frame 0 only. Pixels are ARGB with alpha 0xff (0 = transparent).
bool sprite(int id, std::vector<uint32_t>& pixels, int& width, int& height) {
    uintptr_t c = client();
    if (!g_build || !c || id < 0 || id > 0xfffff) return false;
    // Only once the game is past its loading screens (state >= 10) and the cache has its
    // sprites archive (+0x10): a call during startup crashed the game thread.
    auto cache = read_at<uintptr_t>(c + 0x413a98);
    if (!cache || game_state() < 10 || !read_at<uintptr_t>(cache + 0x10)) return false;
    bool ok = false;
    using Fn = const uintptr_t* (*)(uintptr_t, int, int, bool, bool, int, bool*);
    const uintptr_t* slot = reinterpret_cast<Fn>(g_base + 0x5d4074)(cache, id, 0, false, false, 0, &ok);
    if (!ok || !slot || !slot[1]) return false;
    uintptr_t spr = slot[1];
    width = read_at<int32_t>(spr + 0x18);
    height = read_at<int32_t>(spr + 0x1c);
    auto px = read_at<uintptr_t>(spr + 0x10);
    if (!px || width <= 0 || height <= 0 || width > 2048 || height > 2048) return false;
    pixels.assign(reinterpret_cast<const uint32_t*>(px), reinterpret_cast<const uint32_t*>(px) + width * height);
    return true;
}

// Overwrites the cached sprite's pixels in place (sprite overrides); same size only. The cache
// reloads the original after an LRU eviction, so callers re-apply periodically.
bool sprite_write(int id, const uint32_t* pixels, int width, int height) {
    uintptr_t c = client();
    if (!g_build || !c || id < 0 || id > 0xfffff) return false;
    // Only once the game is past its loading screens (state >= 10) and the cache has its
    // sprites archive (+0x10): a call during startup crashed the game thread.
    auto cache = read_at<uintptr_t>(c + 0x413a98);
    if (!cache || game_state() < 10 || !read_at<uintptr_t>(cache + 0x10)) return false;
    bool ok = false;
    using Fn = const uintptr_t* (*)(uintptr_t, int, int, bool, bool, int, bool*);
    const uintptr_t* slot = reinterpret_cast<Fn>(g_base + 0x5d4074)(cache, id, 0, false, false, 0, &ok);
    if (!ok || !slot || !slot[1]) return false;
    uintptr_t spr = slot[1];
    auto px = read_at<uintptr_t>(spr + 0x10);
    if (!px || read_at<int32_t>(spr + 0x18) != width || read_at<int32_t>(spr + 0x1c) != height) return false;
    memcpy(reinterpret_cast<void*>(px), pixels, static_cast<size_t>(width) * height * 4);
    return true;
}

bool item_sprite(int id, int quantity, int border, int shadow, int text_mode, bool noted,
                 std::vector<uint32_t>& pixels, int& width, int& height) {
    if (!g_build || !g_build->get_obj_type || id < 0 || id > 0xfffff) return false;
    struct SharedPtr {
        uintptr_t ctrl, obj;
        ~SharedPtr() {}
    };
    // createItemSprite dereferences the ObjType unchecked: validate the id first.
    {
        SharedPtr t = reinterpret_cast<SharedPtr (*)(int)>(g_base + g_build->get_obj_type)(id);
        uintptr_t ctrl = t.ctrl, o = t.obj;
        bool ok = o && read_at<int32_t>(o) != -1;
        release_shared(ctrl);
        if (!ok) return false;
    }
    // void createItemSprite(out x8, id, quantity, border, shadow, textMode, flags, width, height,
    // const string* text): the native ItemComposition.getItemSprite with its LRU cache (docs/offsets.md).
    // Only 36x32 is safe (the note overlay and text assume that stride).
    GameString empty{std::string()};
    using Fn = SharedPtr (*)(int, int, int, int, int, int, int, int, const void*);
    SharedPtr r = reinterpret_cast<Fn>(g_base + 0x63ff9c)(id, quantity, border, shadow, text_mode, noted ? 1 : 0, 36, 32, empty.bytes);
    uintptr_t ctrl = r.ctrl, spr = r.obj;
    if (!spr) { release_shared(ctrl); return false; }
    width = read_at<int32_t>(spr + 0x18);
    height = read_at<int32_t>(spr + 0x1c);
    auto px = read_at<uintptr_t>(spr + 0x10);
    bool ok = px && width > 0 && height > 0 && width <= 512 && height <= 512;
    if (ok) pixels.assign(reinterpret_cast<const uint32_t*>(px), reinterpret_cast<const uint32_t*>(px) + width * height);
    release_shared(ctrl);
    return ok;
}

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
    // Icon view, as CC_SETOBJECT (0x4e06dc) copies it into the widget's model zoom/angles: zoom2d +0xbc,
    // xan2d/yan2d/zan2d +0xc0/+0xc4/+0xc8 (static RE).
    out.zoom2d = read_at<int32_t>(o + 0xbc);
    out.xan2d = read_at<int32_t>(o + 0xc0);
    out.yan2d = read_at<int32_t>(o + 0xc4);
    out.zan2d = read_at<int32_t>(o + 0xc8);
    auto iops = read_at<uintptr_t>(o + 0x108);
    for (int i = 0; i < 5; i++) out.inventory_actions[i] = iops ? read_string(iops + i * 0x18) : std::string();
    // Model fields (static RE of the decoder 0x63bfe8 and ObjType::getModel 0x63f1e8): +0x04 inventory
    // model (mobile opcode 0x2c, int32; noted items copy their template's), ambient +0x2c4 (op 113),
    // contrast +0x2c8 (op 114, stored * 5 as on desktop). Recolour / retexture arrays (ops 40/41):
    // {u64 count, int16* data} at +0x60/+0x68 (find), +0x78/+0x80 (replace), +0x90/+0x98, +0xa8/+0xb0.
    out.inventory_model = read_at<int32_t>(o + 0x04);
    out.ambient = read_at<int32_t>(o + 0x2c4);
    out.contrast = read_at<int32_t>(o + 0x2c8);
    auto shorts = [&](uintptr_t at, std::vector<int16_t>& v) {
        auto n = read_at<uint64_t>(at);
        auto data = read_at<uintptr_t>(at + 8);
        if (!data || n == 0 || n > 255) return;
        v.assign(reinterpret_cast<const int16_t*>(data), reinterpret_cast<const int16_t*>(data) + n);
    };
    shorts(o + 0x60, out.recolor_from);
    shorts(o + 0x78, out.recolor_to);
    shorts(o + 0x90, out.retexture_from);
    shorts(o + 0xa8, out.retexture_to);
    release_shared(sp[0]);
    return true;
}

}  // namespace game

namespace game {

// Posed model of an Npc/Player: vtable slot 0 = getModel(this, cycle) → shared_ptr<Model> (x8),
// what the renderer calls every frame (Npc 0x475908, Player 0x45df78). Model: +0x20 vertex count,
// +0x38/+0x50/+0x68 int* x/y/z (local units, y negative = up), +0x18 height. Game thread only.
namespace {
// Renderable vtable slot 0: shared_ptr<Model> getModel(this, cycle) (x8 result). Actors build a
// posed model per call; a static loc's Model returns itself (0x71d604). Appends the vertices,
// offset by (dx, dz), to xyz; height = the model's height.
bool append_model(uintptr_t renderable, int dx, int dz, std::vector<int32_t>& xyz, int& height) {
    if (!renderable) return false;
    uintptr_t vt = read_at<uintptr_t>(renderable);
    if (!vt) return false;
    struct SharedPtr {
        uintptr_t ctrl, obj;
        ~SharedPtr() {}
    };
    auto fn = reinterpret_cast<SharedPtr (*)(uintptr_t, int)>(read_at<uintptr_t>(vt));
    g_own_model_call = true;
    SharedPtr r = fn(renderable, cycle());
    g_own_model_call = false;
    uintptr_t ctrl = r.ctrl, m = r.obj;
    if (!m) { release_shared(ctrl); return false; }
    int n = read_at<int32_t>(m + 0x20);
    auto xs = read_at<const int32_t*>(m + 0x38), ys = read_at<const int32_t*>(m + 0x50), zs = read_at<const int32_t*>(m + 0x68);
    height = std::max(height, read_at<int32_t>(m + 0x18));
    bool ok = false;
    if (n > 0 && n < 20000 && xs && ys && zs) {
        size_t base = xyz.size();
        xyz.resize(base + static_cast<size_t>(n) * 3);
        for (int i = 0; i < n; i++) {
            xyz[base + 3 * i] = xs[i] + dx;
            xyz[base + 3 * i + 1] = ys[i];
            xyz[base + 3 * i + 2] = zs[i] + dz;
        }
        ok = true;
    }
    release_shared(ctrl);
    return ok;
}

// Mesh of a posed model for Model.getVertices*/getFaceIndices*/getFaceColors*: {height, vertex
// count n, face count f, colour flag, x[n], y[n], z[n], a[f], b[f], c[f], colours1[f], colours2[f],
// colours3[f]} (colour arrays only when the flag is 1). Faces: +0x70 count, +0x88/+0xa0/+0xb8 int*
// vertex indices. Lit face colours: +0xd0/+0xe8/+0x100 int* (written by the light pass 0x72aea4 from
// the ModelData's 16-bit HSL colours; colours3 = -1 for a flat face, -2 for a hidden one, as desktop's
// Model.getFaceColors1/2/3).
bool read_mesh(uintptr_t renderable, std::vector<int32_t>& out) {
    out.clear();
    if (!renderable) return false;
    uintptr_t vt = read_at<uintptr_t>(renderable);
    if (!vt) return false;
    struct SharedPtr {
        uintptr_t ctrl, obj;
        ~SharedPtr() {}
    };
    auto fn = reinterpret_cast<SharedPtr (*)(uintptr_t, int)>(read_at<uintptr_t>(vt));
    g_own_model_call = true;
    SharedPtr r = fn(renderable, cycle());
    g_own_model_call = false;
    uintptr_t ctrl = r.ctrl, m = r.obj;
    if (!m) { release_shared(ctrl); return false; }
    int n = read_at<int32_t>(m + 0x20), f = read_at<int32_t>(m + 0x70);
    auto xs = read_at<const int32_t*>(m + 0x38), ys = read_at<const int32_t*>(m + 0x50), zs = read_at<const int32_t*>(m + 0x68);
    auto fa = read_at<const int32_t*>(m + 0x88), fb = read_at<const int32_t*>(m + 0xa0), fc = read_at<const int32_t*>(m + 0xb8);
    auto c1 = read_at<const int32_t*>(m + 0xd0), c2 = read_at<const int32_t*>(m + 0xe8), c3 = read_at<const int32_t*>(m + 0x100);
    bool ok = n > 0 && n < 20000 && f >= 0 && f < 40000 && xs && ys && zs && (f == 0 || (fa && fb && fc));
    if (ok) {
        bool colours = f > 0 && c1 && c2 && c3;
        out.reserve(4 + 3 * static_cast<size_t>(n) + (colours ? 6 : 3) * static_cast<size_t>(f));
        out.push_back(read_at<int32_t>(m + 0x18));
        out.push_back(n);
        out.push_back(f);
        out.push_back(colours ? 1 : 0);
        out.insert(out.end(), xs, xs + n);
        out.insert(out.end(), ys, ys + n);
        out.insert(out.end(), zs, zs + n);
        for (auto arr : {fa, fb, fc}) out.insert(out.end(), arr, arr + f);
        if (colours)
            for (auto arr : {c1, c2, c3}) out.insert(out.end(), arr, arr + f);
        // A face index outside the vertex range would make the Java side throw: reject the model.
        for (size_t i = 4 + 3 * static_cast<size_t>(n), e = i + 3 * static_cast<size_t>(f); i < e; i++)
            if (out[i] < 0 || out[i] >= n) { ok = false; break; }
    }
    release_shared(ctrl);
    if (!ok) out.clear();
    return ok;
}
}  // namespace

bool actor_mesh(uintptr_t actor, std::vector<int32_t>& out) { return read_mesh(actor, out); }

// Draw log (setprop debug.osrsrl.drawlog 1): hooks the Npc vtable slot 0 (getModel) to record
// which Npc objects the renderer asks for a model, then logs them every ~50 cycles next to the
// WorldView's hash map nodes and index list (which entity the game draws vs which one we read).
namespace {
struct DrawSp {
    uintptr_t ctrl, obj;
    ~DrawSp() {}
};
using DrawGetModel = DrawSp (*)(uintptr_t, int);
DrawGetModel g_draw_orig = nullptr;
std::mutex g_draw_mutex;
std::unordered_map<uintptr_t, int> g_drawn;
uintptr_t g_draw_caller = 0;
DrawSp draw_hook(uintptr_t self, int cyc) {
    if (!g_own_model_call) {
        std::lock_guard lock(g_draw_mutex);
        g_drawn[self]++;
        if (!g_draw_caller) g_draw_caller = reinterpret_cast<uintptr_t>(__builtin_return_address(0));
    }
    return g_draw_orig(self, cyc);
}
}  // namespace

void drawlog_cycle() {
    static int counter = 0;
    static bool enabled = false;
    if (++counter % 50 != 0) return;
    char value[PROP_VALUE_MAX] = {};
    __system_property_get("debug.osrsrl.drawlog", value);
    enabled = value[0] == '1';
    if (!enabled) return;
    uintptr_t c = client();
    if (!c || game_state() != 30) return;
    auto wv = read_at<uintptr_t>(c + g_build->f_world_view);
    if (!wv) return;
    auto buckets = read_at<uintptr_t>(wv + g_build->wv_npc_buckets);
    auto nb = read_at<uint64_t>(wv + g_build->wv_npc_bucket_count) & 0xffffffff;
    if (!buckets || !nb || nb > 1000000) return;
    // Install the hook on the first Npc's vtable.
    if (!g_draw_orig) {
        for (uint64_t b = 0; b < nb && !g_draw_orig; b++)
            for (auto node = read_at<uintptr_t>(buckets + b * 8); node; node = read_at<uintptr_t>(node + 24)) {
                uintptr_t npc = read_at<uintptr_t>(node + 16);
                if (!npc) continue;
                uintptr_t vt = read_at<uintptr_t>(npc);
                LOGI("drawlog: Npc vtable %#lx (elf %#lx) slot0 %#lx (elf %#lx)", (unsigned long)vt,
                     (unsigned long)(vt - g_base), (unsigned long)read_at<uintptr_t>(vt),
                     (unsigned long)(read_at<uintptr_t>(vt) - g_base));
                void* old = nullptr;
                auto** slot = reinterpret_cast<void**>(vt);
                long page = sysconf(_SC_PAGESIZE);
                auto start = reinterpret_cast<void*>(vt & ~(page - 1));
                if (mprotect(start, page * 2, PROT_READ | PROT_WRITE) == 0) {
                    old = *slot;
                    g_draw_orig = reinterpret_cast<DrawGetModel>(old);
                    *slot = reinterpret_cast<void*>(draw_hook);
                    mprotect(start, page * 2, PROT_READ);
                    LOGI("drawlog: getModel hooked");
                } else {
                    LOGE("drawlog: mprotect failed");
                }
                break;
            }
        return;
    }
    std::unordered_map<uintptr_t, int> drawn;
    uintptr_t caller;
    {
        std::lock_guard lock(g_draw_mutex);
        drawn.swap(g_drawn);
        caller = g_draw_caller;
    }
    int px = read_at<int32_t>(c + 0xcb7c), py = read_at<int32_t>(c + 0xcb80);
    int bx = read_at<int32_t>(wv + g_build->wv_base_x), by = read_at<int32_t>(wv + g_build->wv_base_y);
    int count = read_at<int32_t>(wv + g_build->wv_npc_count);
    auto indices = read_at<const int32_t*>(wv + g_build->wv_npc_indices);
    std::unordered_map<int, int> in_list;
    for (int i = 0; indices && i < count && i < 65536; i++) in_list[indices[i]]++;
    LOGI("drawlog: player scene %d,%d base %d,%d list %d buckets %lu drawn objects %zu caller elf %#lx world %d flags %#x", px, py, bx, by,
         count, (unsigned long)nb, drawn.size(), (unsigned long)(caller ? caller - g_base : 0), world(), world_flags());
    std::unordered_map<uintptr_t, int> seen;
    for (uint64_t b = 0; b < nb; b++)
        for (auto node = read_at<uintptr_t>(buckets + b * 8); node; node = read_at<uintptr_t>(node + 24)) {
            int index = read_at<int32_t>(node);
            uintptr_t npc = read_at<uintptr_t>(node + 16);
            if (!npc) continue;
            seen[npc]++;
            int fx = read_at<int32_t>(npc + 0x1fc), fy = read_at<int32_t>(npc + 0x200);
            if (std::abs((fx >> 7) - px) > 16 || std::abs((fy >> 7) - py) > 16) continue;
            auto def = read_at<uintptr_t>(npc + g_build->npc_def);
            auto it = drawn.find(npc);
            LOGI("drawlog: node idx %d npc %#lx id %d '%s' fine %d,%d (tile %d,%d) +268 %d,%d path0 %d,%d upd %d list %d drawn %d bucket %lu",
                 index, (unsigned long)npc, def ? read_at<int32_t>(def) : -1, def ? read_string(def + g_build->def_name).c_str() : "",
                 fx, fy, bx + (fx >> 7), by + (fy >> 7), read_at<int32_t>(npc + 0x268), read_at<int32_t>(npc + 0x26c),
                 read_at<int32_t>(npc + 0x3f0), read_at<int32_t>(npc + 0x418), read_at<uint8_t>(npc + 0x768),
                 in_list.count(index) ? in_list[index] : 0, it != drawn.end() ? it->second : 0, (unsigned long)b);
        }
    // Pointers not in the map may be freed already (region rebuild): don't dereference them.
    for (auto& [p, n] : drawn)
        if (!seen.count(p)) LOGI("drawlog: drawn but not in map: %#lx calls %d", (unsigned long)p, n);
}

bool actor_model(uintptr_t actor, std::vector<int32_t>& xyz, int& height) {
    xyz.clear();
    height = 0;
    return append_model(actor, 0, 0, xyz, height);
}

int actor_height(uintptr_t actor) {
    std::vector<int32_t> xyz;
    int field = 0;
    if (!append_model(actor, 0, 0, xyz, field)) return -1;
    int h = 0;
    for (size_t i = 1; i < xyz.size(); i += 3) h = std::max(h, -xyz[i]);
    static int logs = 0;
    if (logs < 3) { logs++; LOGI("actor_height %d (model +0x18 = %d)", h, field); }
    return h;
}

// Scene objects (static RE of the tile draw 0x6e1684 and the object constructors; positions are
// local units, heights absolute). Renderable holders point at {ctrl, obj} pairs.
//   GameObject: Renderable* +0x230, x +0x1dc, height +0x1d8, y +0x1e0, orientation +0x1e4, tag +0x1f0
//   Wall (tile +0x108): A *(*(+0x410)+8), B *(*(+0x418)+8); x/height/y +0x28/+0x2c/+0x30, tag +0x38
//   Wall decoration (+0x110): A *(*(+0x418)+8) offset +0x48/+0x4c, B *(*(+0x420)+8) offset +0x50/+0x54
//   Ground decoration (+0x118): *(*(+0x220)+8)
//   Item pile (+0x120): *(*(+0x5e8)+8), *(*(+0x5f0)+8), *(*(+0x5e0)+8) (unverified)
bool object_model(int plane, int sx, int sy, int layer, uint64_t tag, ObjectModel& out) {
    out = {};
    uintptr_t c = client();
    uintptr_t wv = c && g_build ? read_at<uintptr_t>(c + g_build->f_world_view) : 0;
    uintptr_t scene = wv ? read_at<uintptr_t>(wv + 0x10) : 0;
    if (!scene) return false;
    int planes = read_at<int32_t>(scene + 0x944), w = read_at<int32_t>(scene + 0x948), h = read_at<int32_t>(scene + 0x94c);
    uintptr_t slots = read_at<uintptr_t>(scene + 0x968);
    if (!slots || plane < 0 || plane >= planes || sx < 0 || sx >= w || sy < 0 || sy >= h || planes > 4 || w > 256 || h > 256) return false;
    uintptr_t tile = read_at<uintptr_t>(slots + ((static_cast<size_t>(plane) * w + sx) * h + sy) * 0x10 + 8);
    if (!tile) return false;
    auto held = [](uintptr_t o, uint32_t holder) {
        uintptr_t pair = read_at<uintptr_t>(o + holder);
        return pair ? read_at<uintptr_t>(pair + 8) : 0;
    };
    auto simple = [&](uintptr_t o) {
        out.x = read_at<int32_t>(o + 0x28);
        out.height = read_at<int32_t>(o + 0x2c);
        out.y = read_at<int32_t>(o + 0x30);
    };
    int mh = 0;
    switch (layer) {
        case kGameObject: {
            int n = read_at<int32_t>(tile + 0x34);
            uintptr_t objs = read_at<uintptr_t>(tile + 0x38);
            if (n <= 0 || n > 16 || !objs) return false;
            for (int i = 0; i < n; i++) {
                uintptr_t o = read_at<uintptr_t>(objs + static_cast<size_t>(i) * 0x10 + 8);
                if (!o || read_at<uint64_t>(o + 0x1f0) != tag) continue;
                out.x = read_at<int32_t>(o + 0x1dc);
                out.height = read_at<int32_t>(o + 0x1d8);
                out.y = read_at<int32_t>(o + 0x1e0);
                out.orientation = read_at<int32_t>(o + 0x1e4);
                append_model(read_at<uintptr_t>(o + 0x230), 0, 0, out.xyz, mh);
                break;
            }
            break;
        }
        case kWall: {
            uintptr_t o = read_at<uintptr_t>(tile + 0x108);
            if (!o || read_at<uint64_t>(o + 0x38) != tag) return false;
            simple(o);
            append_model(held(o, 0x410), 0, 0, out.xyz, mh);
            append_model(held(o, 0x418), 0, 0, out.xyz, mh);
            break;
        }
        case kWallDecoration: {
            uintptr_t o = read_at<uintptr_t>(tile + 0x110);
            if (!o || read_at<uint64_t>(o + 0x38) != tag) return false;
            simple(o);
            if (!append_model(held(o, 0x418), read_at<int32_t>(o + 0x48), read_at<int32_t>(o + 0x4c), out.xyz, mh))
                append_model(held(o, 0x420), read_at<int32_t>(o + 0x50), read_at<int32_t>(o + 0x54), out.xyz, mh);
            break;
        }
        case kGroundDecoration: {
            uintptr_t o = read_at<uintptr_t>(tile + 0x118);
            if (!o || read_at<uint64_t>(o + 0x38) != tag) return false;
            simple(o);
            append_model(held(o, 0x220), 0, 0, out.xyz, mh);
            break;
        }
        case kItemPile: {  // tile +0x120: three item renderables (holders +0x5e8/+0x5f0/+0x5e0); tag ignored
            uintptr_t o = read_at<uintptr_t>(tile + 0x120);
            if (!o) return false;
            out.x = sx * 128 + 64;
            out.y = sy * 128 + 64;
            for (uint32_t holder : {0x5e8u, 0x5f0u, 0x5e0u}) append_model(held(o, holder), 0, 0, out.xyz, mh);
            break;
        }
        default: return false;
    }
    out.model_height = mh;
    return !out.xyz.empty();
}

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
    // Multi-NPC (ops 106/118): same layout as LocType's multiloc.
    out.varbit = read_at<int32_t>(o + 0x150);
    out.varp = read_at<int32_t>(o + 0x154);
    auto tn = read_at<uint64_t>(o + 0x140);
    auto tp = read_at<const int32_t*>(o + 0x148);
    out.transforms.clear();
    if (tn > 0 && tn < 4096 && tp) out.transforms.assign(tp, tp + tn);
    auto op_text = reinterpret_cast<uintptr_t (*)(uintptr_t, int)>(g_base + g_build->op_text);
    for (int i = 0; i < 5; i++) {
        uintptr_t str = op_text(o + 0x100, i);
        out.actions[i] = str ? read_string(str) : std::string();
    }
    // Static RE of the inlined decoder (gen 28; unverified live): models {u64 count, int32*} at
    // +0x30/+0x38 (mobile opcode 61, u8 count then int32s), chathead models +0x48/+0x50 (opcode 62),
    // recolour find/replace {u64 count, int16*} +0xa8/+0xb0 and +0xc0/+0xc8 (opcode 40); width/height
    // scale +0x120/+0x124 (NpcType::getModel 0x6380d8 rescales when either isn't 128).
    auto ints = [&](uintptr_t at, std::vector<int32_t>& v) {
        auto n = read_at<uint64_t>(at);
        auto data = read_at<const int32_t*>(at + 8);
        if (data && n > 0 && n <= 255) v.assign(data, data + n);
    };
    auto shorts = [&](uintptr_t at, std::vector<int16_t>& v) {
        auto n = read_at<uint64_t>(at);
        auto data = read_at<const int16_t*>(at + 8);
        if (data && n > 0 && n <= 255) v.assign(data, data + n);
    };
    out.models.clear(); out.chathead_models.clear(); out.recolor_from.clear(); out.recolor_to.clear();
    ints(o + 0x30, out.models);
    ints(o + 0x48, out.chathead_models);
    shorts(o + 0xa8, out.recolor_from);
    shorts(o + 0xc0, out.recolor_to);
    out.width_scale = read_at<int32_t>(o + 0x120);
    out.height_scale = read_at<int32_t>(o + 0x124);
    release_shared(ctrl);
    return true;
}

}  // namespace game

namespace game {

namespace {
// Definition params (opcode 249, decoder 0x62d550): a hash map {+0x08 buckets, +0x10 bucket count,
// +0x18 size}, bucket = key % count; nodes (0x40) {+0x00 u64 key, +0x08 int value, +0x10 string
// (24-byte), +0x28 long value, +0x30 is-string byte, +0x38 next}.
bool read_param(uintptr_t map, int key, TypeParam& out) {
    auto buckets = read_at<uintptr_t>(map + 0x08);
    uint64_t count = read_at<uint64_t>(map + 0x10);
    if (!buckets || !count || count > (1u << 20) || key < 0) return false;
    for (uintptr_t n = read_at<uintptr_t>(buckets + (static_cast<uint64_t>(key) % count) * 8), i = 0; n && i < 4096;
         n = read_at<uintptr_t>(n + 0x38), i++) {
        if (read_at<uint64_t>(n) != static_cast<uint64_t>(key)) continue;
        out.is_string = read_at<uint8_t>(n + 0x30) == 1;
        out.int_value = read_at<int32_t>(n + 0x08);
        if (out.is_string) out.string_value = read_string(n + 0x10);
        return true;
    }
    return false;
}
}  // namespace

// Params of an obj (kind 0, ObjType +0x2d8), npc (1, NpcType +0x170), loc (2, LocType +0x160) or
// struct (3, StructType +0x08; getStructType 0x64a69c loads config group 34, returns via x8).
bool type_param(int kind, int id, int key, TypeParam& out) {
    if (!g_build || id < 0 || id > 0xfffff) return false;
    if (kind == 2) {
        if (!g_build->get_loc_type) return false;
        auto sp = reinterpret_cast<const uintptr_t* (*)(int)>(g_base + g_build->get_loc_type)(id);
        return sp && sp[1] && read_param(sp[1] + 0x160, key, out);
    }
    constexpr uintptr_t kGetStructType = 0x64a69c;  // build 241.4
    uintptr_t fn = kind == 0 ? g_build->get_obj_type : kind == 1 ? g_build->get_npc_type : kind == 3 ? kGetStructType : 0;
    if (!fn) return false;
    struct SharedPtr {
        uintptr_t ctrl, obj;
        ~SharedPtr() {}
    };
    SharedPtr r = reinterpret_cast<SharedPtr (*)(int)>(g_base + fn)(id);
    uintptr_t ctrl = r.ctrl, o = r.obj;
    bool ok = o && read_param(o + (kind == 0 ? 0x2d8 : kind == 1 ? 0x170 : 0x08), key, out);
    release_shared(ctrl);
    return ok;
}

bool loc_def(int id, LocDef& out) {
    if (!g_build || !g_build->get_loc_type || id < 0 || id > 0xfffff) return false;
    auto sp = reinterpret_cast<const uintptr_t* (*)(int)>(g_base + g_build->get_loc_type)(id);
    if (!sp) return false;
    uintptr_t o = sp[1];  // the cache keeps its reference while we read (game thread)
    if (!o) return false;
    out.id = read_at<int32_t>(o + 0x08);
    out.name = read_string(o + 0x40);
    out.size_x = read_at<int32_t>(o + 0xbc);
    out.size_y = read_at<int32_t>(o + 0xc0);
    out.varbit = read_at<int32_t>(o + 0x150);
    out.varp = read_at<int32_t>(o + 0x154);
    auto n = read_at<uint64_t>(o + 0x140);
    auto t = read_at<const int32_t*>(o + 0x148);
    out.transforms.clear();
    if (n > 0 && n < 4096 && t) out.transforms.assign(t, t + n);
    auto op_text = reinterpret_cast<uintptr_t (*)(uintptr_t, int)>(g_base + g_build->op_text);
    for (int i = 0; i < 5; i++) {
        uintptr_t str = op_text(o + 0xe8, i);
        out.actions[i] = str ? read_string(str) : std::string();
    }
    return true;
}

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

bool menu_close() {
    uintptr_t m = menu_manager();
    if (!m || !g_build->menu_clear_model) return false;
    if (!read_at<uintptr_t>(m + kMgrShown)) return true;
    reinterpret_cast<void (*)(uintptr_t)>(g_base + g_build->menu_clear_model)(m + kMgrMenu);
    reinterpret_cast<void (*)(uintptr_t)>(g_base + g_build->menu_clear_display)(m + 0x540);
    *reinterpret_cast<uintptr_t*>(m + kMgrShown) = 0;
    return true;
}

bool menu_reorder(const std::vector<int>& order) {
    uintptr_t m = menu_manager();
    if (!m || !g_build->menu_build_display) return false;
    uintptr_t display = m + 0x540, model = m + kMgrMenu;
    uintptr_t layout = read_at<uintptr_t>(m + 0x7c8);
    // Shown is the model itself for long-press menus (drawn from it directly), or the display
    // built from it (0x599f68), which is rebuilt below.
    uintptr_t shown = read_at<uintptr_t>(m + kMgrShown);
    bool rebuild = shown == display;
    if ((shown != display && shown != model) || (rebuild && !layout)) {
        LOGE("menu reorder: shown %#lx, model %#lx", (unsigned long)shown, (unsigned long)model);
        return false;
    }
    uintptr_t nb = read_at<uintptr_t>(model + kMenuNodes), ne = read_at<uintptr_t>(model + kMenuNodes + 8);
    size_t n = nb && ne > nb ? (ne - nb) / kNodeSize : 0;
    if (n < 2 || n > 512 || order.size() != n) {
        LOGE("menu reorder: %zu nodes, %zu in the order", n, order.size());
        return false;
    }
    std::vector<bool> seen(n);
    for (size_t i = 0; i < n; i++) {
        if (order[i] < 0 || static_cast<size_t>(order[i]) >= n || seen[order[i]]) return false;
        seen[order[i]] = true;
    }
    // The game sorts a menu by ordering these {EntryList*, entry index, children*} nodes over its
    // unsorted entry list; do the same.
    std::vector<uint8_t> nodes(reinterpret_cast<const uint8_t*>(nb), reinterpret_cast<const uint8_t*>(ne));
    if (rebuild) reinterpret_cast<void (*)(uintptr_t)>(g_base + g_build->menu_clear_display)(display);
    for (size_t i = 0; i < n; i++)
        memcpy(reinterpret_cast<void*>(nb + i * kNodeSize), nodes.data() + order[i] * kNodeSize, kNodeSize);
    if (rebuild)
        reinterpret_cast<void (*)(uintptr_t, uintptr_t, uintptr_t)>(g_base + g_build->menu_build_display)(display, model, layout);
    return true;
}

void pick_at(int screen_x, int screen_y) {
    if (!g_build || !g_build->pick_set) return;
    // As a touch down: one-shot pick of this point on the next rendered frame; clears "picked".
    reinterpret_cast<void (*)(int, int, unsigned)>(g_base + g_build->pick_set)(screen_x, screen_y, 0);
}

bool pick_ready() {
    return g_build && g_build->pick_ready && read_at<uint8_t>(g_base + g_build->pick_ready) != 0 &&
           read_at<int32_t>(g_base + g_build->pick_mode) == 3;
}

bool menu_build_at(int x, int y) {
    uintptr_t m = menu_manager();
    if (!m || !g_build->menu_build_at || read_at<uintptr_t>(m + kMgrShown)) return false;
    // The build also reads the touch handler's point (MenuManager +0x878 -> +0x2c/+0x30, canvas
    // units, 0x518838) for widget entries: point it at (x, y) for the build, then restore it.
    uintptr_t handler = read_at<uintptr_t>(m + 0x878);
    int32_t saved[2] = {0, 0};
    if (handler) {
        saved[0] = read_at<int32_t>(handler + 0x2c);
        saved[1] = read_at<int32_t>(handler + 0x30);
        *reinterpret_cast<int32_t*>(handler + 0x2c) = x;
        *reinterpret_cast<int32_t*>(handler + 0x30) = y;
    }
    reinterpret_cast<void (*)(uintptr_t, int, int)>(g_base + g_build->menu_build_at)(m, x, y);
    if (handler) {
        *reinterpret_cast<int32_t*>(handler + 0x2c) = saved[0];
        *reinterpret_cast<int32_t*>(handler + 0x30) = saved[1];
    }
    // +0xa20 = 2 once built (1 = deferred to the next frame, which we don't use).
    if (read_at<uint8_t>(m + 0xa20) != 2) {
        menu_discard();
        return false;
    }
    return true;
}

void menu_discard() {
    uintptr_t m = menu_manager();
    if (!m || read_at<uintptr_t>(m + kMgrShown)) return;
    // As the touch handler after a tap's select: clear the model, build state back to 0, so the
    // show branch of 0x599f68 (nodes non-empty) never shows it.
    reinterpret_cast<void (*)(uintptr_t)>(g_base + g_build->menu_clear_model)(m + kMgrMenu);
    *reinterpret_cast<uint8_t*>(m + 0xa20) = 0;
}

bool menu_point_raw(int& x, int& y) {
    uintptr_t m = menu_manager();
    if (!m) return false;
    x = read_at<int32_t>(m + 0x2a8);
    y = read_at<int32_t>(m + 0x2ac);
    return true;
}

bool menu_point(int& x, int& y) {
    uintptr_t m = menu_manager();
    if (!m || !read_at<uintptr_t>(m + kMgrShown)) return false;
    // Long-press point in UI-canvas units (menu model +0x168); the menu is drawn centred on x
    // with its top at y. Found by diffing MenuManager with menus open at two points (2026-10-16).
    x = read_at<int32_t>(m + 0x2a8);
    y = read_at<int32_t>(m + 0x2ac);
    return true;
}

std::vector<MenuEntry> read_menu() { return read_menu(menu_object()); }

std::vector<MenuEntry> read_menu(uintptr_t menu) {
    std::vector<MenuEntry> out;
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

namespace {
// Menu entry strings (+0x50 target, +0xb8 option) are the game's arena-backed string: the usual
// 24-byte layout (inline when byte 23 >= 0, else {char*, size, cap | 1 << 63}) followed by an arena
// whose address is at +0x20; the entry constructor (0x4bc564) fills them with append 0x48e860(str,
// begin, end), which reallocates past the capacity and frees the old buffer unless it is the arena.
void set_entry_string(uintptr_t str, const std::string& value) {
    auto* b = reinterpret_cast<uint8_t*>(str);
    if (static_cast<int8_t>(b[23]) < 0) {
        auto* words = reinterpret_cast<uint64_t*>(str);
        words[1] = 0;
        if (words[0]) *reinterpret_cast<char*>(words[0]) = 0;
    } else {
        b[0] = 0;
        b[23] = 23;
    }
    if (!value.empty())
        reinterpret_cast<void* (*)(uintptr_t, const char*, const char*)>(g_base + 0x48e860)(str, value.data(), value.data() + value.size());
}
}  // namespace

// index = position in read_menu() (top-level nodes, game order). Game thread only.
bool menu_set_text(size_t index, const std::string* option, const std::string* target) {
    uintptr_t menu = menu_object();
    if (!menu || !menu_open()) return false;
    auto b = read_at<uintptr_t>(menu + kMenuNodes), e = read_at<uintptr_t>(menu + kMenuNodes + 8);
    if (!b || e < b || index >= (e - b) / kNodeSize) return false;
    uintptr_t entry = node_entry(b + index * kNodeSize);
    if (!entry) return false;
    if (option) set_entry_string(entry + 0xb8, *option);
    if (target) set_entry_string(entry + 0x50, *target);
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
// Obj +0x40 id, +0x44 quantity, +0x48/+0x4c visible/despawn tick, +0x50 ownership, +0x54 private
// (OBJ_ADD 0x45775c).
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
                                       read_at<int32_t>(obj + 0x40), read_at<int32_t>(obj + 0x44),
                                       read_at<int32_t>(obj + 0x48), read_at<int32_t>(obj + 0x4c),
                                       read_at<int32_t>(obj + 0x50), read_at<uint8_t>(obj + 0x54) != 0});
                    node = read_at<uintptr_t>(node + 8);
                }
            }
        }
    }
    return out;
}

}  // namespace game

// ---- Mod icons (<img=N> in text). The font reads the table at the global obj pointer on every
// call (game thread); we point it at our own header holding shallow copies of the game's sprites
// plus plugin sprites, and never touch the game's ctrl or array (docs/offsets.md "Mod icons").
namespace game {
namespace {
struct IdxSprite {
    uint64_t unused, pixel_count;
    const uint8_t* pixels;
    uint64_t palette_count;
    uintptr_t palette_ctrl;
    const uint32_t* palette;
    int32_t w, h, ox, oy, full_w, full_h;
};
static_assert(sizeof(IdxSprite) == 0x48, "IndexedSprite layout");
struct IconHeader {
    uint64_t unused;
    uint64_t count;  // read as int32
    IdxSprite* data;
};
using Entries = std::vector<ModIcon>;
std::mutex g_icon_mutex;
std::shared_ptr<const Entries> g_icon_want;  // guarded by g_icon_mutex
struct BuiltIcons {
    IconHeader header{};
    std::vector<IdxSprite> sprites;
    std::shared_ptr<const Entries> data;
    uintptr_t game_obj = 0;
};
std::unique_ptr<BuiltIcons> g_icons_built, g_icons_prev;  // game thread only

uintptr_t game_icon_obj() {
    if (!g_build || !g_build->mod_icons) return 0;
    uintptr_t cur = read_at<uintptr_t>(g_base + g_build->mod_icons);
    if (g_icons_built && cur == reinterpret_cast<uintptr_t>(&g_icons_built->header)) return g_icons_built->game_obj;
    return cur;
}
}  // namespace

void set_mod_icons(std::vector<ModIcon> icons) {
    auto e = std::make_shared<const Entries>(std::move(icons));
    std::lock_guard<std::mutex> lock(g_icon_mutex);
    g_icon_want = std::move(e);
}

std::vector<int32_t> mod_icon_sizes() {
    std::vector<int32_t> out;
    uintptr_t obj = game_icon_obj();
    if (!obj) return out;
    int n = read_at<int32_t>(obj + 8);
    auto* data = read_at<const IdxSprite*>(obj + 0x10);
    if (n <= 0 || n > 4096 || !data) return out;
    for (int i = 0; i < n; i++) {
        out.push_back(data[i].full_w);
        out.push_back(data[i].full_h);
    }
    return out;
}

void mod_icons_cycle() {
    std::shared_ptr<const Entries> want;
    {
        std::lock_guard<std::mutex> lock(g_icon_mutex);
        want = g_icon_want;
    }
    if (!want || !g_build || !g_build->mod_icons) return;
    auto* slot = reinterpret_cast<uintptr_t*>(g_base + g_build->mod_icons);
    uintptr_t cur = *slot;
    bool ours = g_icons_built && cur == reinterpret_cast<uintptr_t>(&g_icons_built->header);
    if (ours && g_icons_built->data == want) return;
    uintptr_t game_obj = ours ? g_icons_built->game_obj : cur;
    if (!game_obj) return;  // the table isn't loaded yet
    int gcount = read_at<int32_t>(game_obj + 8);
    auto* gdata = read_at<const IdxSprite*>(game_obj + 0x10);
    if (gcount < 0 || gcount > 4096 || (gcount && !gdata)) return;
    auto b = std::make_unique<BuiltIcons>();
    b->data = want;
    b->game_obj = game_obj;
    b->sprites.resize(want->size());
    for (size_t i = 0; i < want->size(); i++) {
        const ModIcon& m = (*want)[i];
        IdxSprite& d = b->sprites[i];
        d = {};
        if (m.game_index >= 0) {
            if (m.game_index < gcount) d = gdata[m.game_index];
            continue;
        }
        if (m.w <= 0 || m.h <= 0 || m.pixels.size() < static_cast<size_t>(m.w) * m.h || m.palette.empty()) continue;
        d.pixel_count = static_cast<uint64_t>(m.w) * m.h;
        d.pixels = m.pixels.data();
        d.palette_count = m.palette.size();
        d.palette = m.palette.data();
        d.w = m.w;
        d.h = m.h;
        d.ox = m.ox;
        d.oy = m.oy;
        d.full_w = m.full_w;
        d.full_h = m.full_h;
    }
    b->header.count = b->sprites.size();
    b->header.data = b->sprites.data();
    *slot = reinterpret_cast<uintptr_t>(&b->header);
    LOGI("mod icons: %zu (game %d)%s", b->sprites.size(), gcount, ours ? "" : " installed");
    g_icons_prev = std::move(g_icons_built);  // the font may still hold it within this cycle? keep one
    g_icons_built = std::move(b);
}
}  // namespace game

// ---- Inventories as of each chat message. Desktop posts events in packet order, so a ChatMessage
// handler sees the containers before a later UPDATE_INV in the same cycle (Loot Tracker snapshots
// the reward inventory on the casket message, then diffs on its ItemContainerChanged). Our events
// are derived after the cycle, so the Message constructor is hooked to copy the inventories at the
// moment each line is created; client_thread replays the changes in that order.
namespace game {
namespace {
const uint32_t kMessageCtorCode[4] = {0xd102c3ff, 0xa9067bfd, 0xf9003bf9, 0xa9085ff8};
using MessageCtor = uintptr_t (*)(uintptr_t, uintptr_t, uintptr_t, uintptr_t, uintptr_t);
MessageCtor g_message_ctor = nullptr;
std::mutex g_msg_inv_mutex;
std::unordered_map<int, std::vector<Inventory>> g_msg_inv;  // message id -> inventories at creation

uintptr_t hook_message_ctor(uintptr_t self, uintptr_t type, uintptr_t name, uintptr_t sender, uintptr_t text) {
    int id = next_message_id() + 1;  // the constructor increments the counter first
    std::vector<Inventory> inv = read_inventories();
    {
        std::lock_guard<std::mutex> lock(g_msg_inv_mutex);
        if (g_msg_inv.size() > 256) g_msg_inv.clear();  // never collected (no cycle hook)
        g_msg_inv[id] = std::move(inv);
    }
    return g_message_ctor(self, type, name, sender, text);
}
}  // namespace

void install_message_hook() {
    if (!g_build || !g_build->message_ctor) return;
    g_message_ctor = reinterpret_cast<MessageCtor>(
        inline_hook(g_base + g_build->message_ctor, kMessageCtorCode, reinterpret_cast<void*>(hook_message_ctor)));
    LOGI("game: message hook %s", g_message_ctor ? "installed" : "FAILED");
}

namespace {
constexpr uintptr_t kMenuOpenCheck = 0x58da0c, kDragCheckRet = 0x41ffe8;
constexpr uint32_t kMenuOpenCheckCode[4] = {0xf943e008, 0xf100011f, 0x1a9f07e0, 0xd65f03c0};
void (*g_drag_cb)() = nullptr;
}
}  // namespace game

// The menu-open check is a leaf the compiler may call assuming only x0/x8 change, so the stub
// (script_stubs.S) saves every caller-saved register before calling this.
extern "C" {
__attribute__((visibility("hidden"))) void* osrsrl_drag_check_orig = nullptr;
void osrsrl_drag_check_stub();
void osrsrl_drag_check_cb(uintptr_t lr) {
    if (game::g_drag_cb && lr == game::g_base + game::kDragCheckRet) game::g_drag_cb();
}
}

namespace game {
void install_drag_hook(void (*cb)()) {
    g_drag_cb = cb;
    osrsrl_drag_check_orig = inline_hook(g_base + kMenuOpenCheck, kMenuOpenCheckCode, reinterpret_cast<void*>(osrsrl_drag_check_stub));
    LOGI("game: drag hook %s", osrsrl_drag_check_orig ? "installed" : "FAILED");
}

namespace {
// 0x4ae508 starts `tst w2, #0xff; b.eq` (PC-relative), so the hook goes at 0x4ae510, where no stack
// or register has changed yet: returning from the replacement returns to 0x4ae508's caller.
constexpr uintptr_t kSoundQueueBody = 0x4ae510;
constexpr uint32_t kSoundQueueBodyCode[4] = {0xd101c3ff, 0xa9037bfd, 0xf90023f7, 0xa90557f6};
using SoundQueueFn = void (*)(uintptr_t, uint32_t, uint32_t, uint32_t);
SoundQueueFn g_sound_orig = nullptr;
bool (*g_sound_cb)(int, int, int) = nullptr;
void hook_sound_queue(uintptr_t sys, uint32_t id, uint32_t loops, uint32_t delay) {
    if (g_sound_cb && g_sound_cb(static_cast<int>(id & 0xffff), static_cast<int>(loops & 0xff), static_cast<int>(delay)))
        return;
    g_sound_orig(sys, id, loops, delay);
}
}  // namespace

void install_sound_hook(bool (*cb)(int id, int loops, int delay)) {
    g_sound_cb = cb;
    g_sound_orig = reinterpret_cast<SoundQueueFn>(
        inline_hook(g_base + kSoundQueueBody, kSoundQueueBodyCode, reinterpret_cast<void*>(hook_sound_queue)));
    LOGI("game: sound hook %s", g_sound_orig ? "installed" : "FAILED");
}

namespace {
// Area sounds: 0x4ae650(sys, plane, id, x, z, range, dropOff, loops, delay, flag) appends a
// 0x28-byte entry to the same queue as 0x4ae508 (x/z are scene tiles, packed as bytes). Callers:
// the SOUND_AREA zone prot (0x46f9c0, in the zone handler 0x46edb0) and the animation frame-sound
// handler (0x480168 in 0x47ff8c). Its prologue has no PC-relative code, so the hook sits on entry.
constexpr uintptr_t kAreaSound = 0x4ae650;
constexpr uint32_t kAreaSoundCode[4] = {0xd10243ff, 0xa9037bfd, 0xa9046ffc, 0xa90567fa};
// Frame-sound handler: a stack object {vtable 0xa6c5a8, +0x08 plane*, +0x10/+0x14 fine x/z of the
// animated thing, +0x18 "is the local player", +0x20 sound system} built by the actor update
// (0x435240: x/z = actor +0x268/+0x26c), loc animations (0x49af40: the loc's x/z) and others, and
// passed to the sequence advance 0x659534, which calls slot 2 = 0x47ff8c(handler, SeqType*, frame).
constexpr uintptr_t kFrameSoundSlot = 0xa6c5b8;
using AreaSoundFn = void (*)(uintptr_t, uint32_t, uint32_t, uint32_t, uint32_t, uint32_t, uint32_t, uint32_t, uint32_t, uint32_t);
using FrameSoundFn = void (*)(uintptr_t, uintptr_t, int);
AreaSoundFn g_area_orig = nullptr;
FrameSoundFn g_frame_orig = nullptr;
uintptr_t g_frame_handler = 0;  // the handler while its slot 2 runs (game thread)
bool (*g_area_cb)(const AreaSound&) = nullptr;

void hook_frame_sound(uintptr_t self, uintptr_t seq, int frame) {
    uintptr_t prev = g_frame_handler;
    g_frame_handler = self;
    g_frame_orig(self, seq, frame);
    g_frame_handler = prev;
}

// The NPC or player standing exactly at the fine position (actor +0x268/+0x26c, as the actor
// update copies into the handler); kind 1 = NPC, 2 = player, 0 = none.
void actor_at(int fx, int fz, bool local, AreaSound& out) {
    uintptr_t c = client();
    if (!c) return;
    auto wv = read_at<uintptr_t>(c + g_build->f_world_view);
    if (!wv) return;
    int me = read_at<int32_t>(c + g_build->f_local_player_index);
    if (local) {
        out.source_kind = 2;
        out.source_index = me;
        return;
    }
    auto at = [&](uintptr_t a) { return a && read_at<int32_t>(a + 0x268) == fx && read_at<int32_t>(a + 0x26c) == fz; };
    int active_count = read_at<int32_t>(c + g_build->f_gpi);
    for (int i = 0; active_count > 0 && active_count <= 2048 && i < active_count; i++) {
        int index = read_at<int32_t>(c + g_build->f_gpi + 4 + i * 4);
        if (index != me && at(live_player(index))) {
            out.source_kind = 2;
            out.source_index = index;
            return;
        }
    }
    int count = read_at<int32_t>(wv + g_build->wv_npc_count);
    auto indices = read_at<const int32_t*>(wv + g_build->wv_npc_indices);
    for (int i = 0; indices && count > 0 && count <= 65535 && i < count; i++) {
        if (at(npc_lookup(wv, indices[i]))) {
            out.source_kind = 1;
            out.source_index = indices[i];
            return;
        }
    }
}

void hook_area_sound(uintptr_t sys, uint32_t plane, uint32_t id, uint32_t x, uint32_t z, uint32_t range,
                     uint32_t drop_off, uint32_t loops, uint32_t delay, uint32_t flag) {
    if (g_area_cb) {
        AreaSound s;
        s.id = static_cast<int>(id & 0xffff);
        s.scene_x = static_cast<int>(x & 0xff);
        s.scene_y = static_cast<int>(z & 0xff);
        s.range = static_cast<int>(range & 0xff);
        s.drop_off = static_cast<int>(drop_off & 0xff);
        s.loops = static_cast<int>(loops & 0xff);
        s.delay = static_cast<int>(delay);
        s.plane = static_cast<int>(plane);
        if (g_frame_handler) {
            s.from_animation = true;
            actor_at(read_at<int32_t>(g_frame_handler + 0x10), read_at<int32_t>(g_frame_handler + 0x14),
                     read_at<uint8_t>(g_frame_handler + 0x18) != 0, s);
        }
        if (g_area_cb(s)) return;
    }
    g_area_orig(sys, plane, id, x, z, range, drop_off, loops, delay, flag);
}
}  // namespace

void install_area_sound_hook(bool (*cb)(const AreaSound&)) {
    g_area_cb = cb;
    g_area_orig = reinterpret_cast<AreaSoundFn>(inline_hook(g_base + kAreaSound, kAreaSoundCode, reinterpret_cast<void*>(hook_area_sound)));
    auto** slot = reinterpret_cast<void**>(g_base + kFrameSoundSlot);
    if (g_area_orig && *slot == reinterpret_cast<void*>(g_base + 0x47ff8c)) {
        long page = sysconf(_SC_PAGESIZE);
        auto start = reinterpret_cast<void*>((g_base + kFrameSoundSlot) & ~static_cast<uintptr_t>(page - 1));
        if (mprotect(start, page, PROT_READ | PROT_WRITE) == 0) {
            g_frame_orig = reinterpret_cast<FrameSoundFn>(*slot);
            *slot = reinterpret_cast<void*>(hook_frame_sound);
            mprotect(start, page, PROT_READ);  // .data.rel.ro
        }
    }
    LOGI("game: area sound hook %s, frame-sound slot %s", g_area_orig ? "installed" : "FAILED", g_frame_orig ? "patched" : "not patched");
}

bool take_message_inventories(int id, std::vector<Inventory>& out) {
    std::lock_guard<std::mutex> lock(g_msg_inv_mutex);
    auto it = g_msg_inv.find(id);
    if (it == g_msg_inv.end()) return false;
    out = std::move(it->second);
    g_msg_inv.erase(it);
    return true;
}
}  // namespace game
