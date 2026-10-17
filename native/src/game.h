#pragma once
#include <cstdint>
#include <string>
#include <vector>

// Read-only views of game state, resolved per game build (see game.cpp).
namespace game {

// Resolves offsets for the loaded game library; false if the build is unknown,
// in which case every accessor below returns its "unavailable" value.
bool init();
bool available();
const char* build_name();

int cycle();       // client cycle, +1 every 20 ms; -1 if unavailable
int game_state();  // RuneLite GameState values (10 login screen, 30 logged in, ...); -1 if unavailable
uintptr_t client_vtable();  // address of the Client vtable in the loaded library; 0 if unavailable
uintptr_t do_cycle();       // Client::doCycle, the expected value of vtable slot 5

int server_tick();  // number of SERVER_TICK_END packets received (one per 600 ms game tick)

int varp_count();
int varp(int id);         // client-side varp value (what scripts see); 0 if out of range
int server_varp(int id);  // last value the server sent
// Calls the game's varbit lookup (definition from the cache + bit extraction). Game thread only:
// the definition cache isn't thread-safe.
int varbit(int id);
// Client.queueChangedSkill: records `skill` in the changed-skills ring as UPDATE_STAT does, so the
// skill tab's onStatTransmit scripts redraw it. Game thread only.
bool queue_changed_skill(int skill);
// The client records each varp id the server changes in a 32-entry ring; `changed_varp_count`
// is the running total, entry n lives at slot n % 32.
uint32_t changed_varp_count();
int changed_varp(uint32_t n);

struct Point { int x, y; };

// Friends / ignore lists: FriendSystem = *(Client +0x46c0); list state +0x150 (0 none, 1 loaded
// (FRIENDLIST_LOADED), 2 after UPDATE_FRIENDLIST); friends container +0x08, ignores +0xb8
// ({count +0xc, Element** +0x20}); element: name string +0x08, previous name +0x38, friend world
// +0x68 (0 offline), rank +0x88. From static RE of the CS2 friend ops (0x4ebdb0) and parsers
// 0x56e448/0x56ee54 (2026-10-16).
struct FriendEntry { std::string name, prev; int world = 0, rank = -1; };
int friend_list_state();
// setGameState(LOADING): the game's own settings-changed reload (0x429900(client): when logged in,
// game state 30 -> 25 and the scene is rebuilt). Client thread only; false when not logged in.
bool reload_scene();
// Actor overhead text (+0x2d8, assigned with the game's string assign 0x3f1498) and its timer
// (+0x2f4, ticks left). `text` null keeps the string. Game thread only.
bool actor_set_overhead(uintptr_t actor, const std::string* text, int cycle);
bool friend_list(bool ignores, std::vector<FriendEntry>& out);
Point scene_base();        // world tile of scene tile (0, 0) (top-level WorldView)
Point player_world();       // world tile (x, y) = scene base + scene tile; {-1, -1} if unavailable
Point walk_destination();
int player_plane();         // 0-3; -1 if unavailable
int scene_plane();          // WorldView plane (what RuneLite's WorldView.getPlane returns)   // tile the player was told to walk to; {-1, -1} when none
Point player_local_tile();  // tile within the loaded scene
Point player_fine();        // scene position in 1/128 tile units (RuneLite LocalPoint)

// Actor fields shared by Npc and Player (Actor ctor 0x474a2c; offsets from static RE of NPC_INFO
// 0x469778 / face tick 0x435240, docs/offsets.md "Actor").
struct ActorState {
    int animation = -1, animation_frame = -1;  // +0x4d8 sequence id, +0x4de frame (s16)
    int orientation = 0, current_orientation = 0;  // +0x3e0 target, +0x270 current (0..2047)
    int interacting = -1;  // RuneLite encoding: NPC index, or player index + 32768; -1 none
    int overhead_cycle = 0;  // +0x2f4 ticks left for the overhead text
    std::string overhead_text;  // +0x2d8
    // Pose animations copied from NpcType +0x58..+0x74 by NPC_INFO (players: from the appearance):
    // idle, idle rotate left/right, walk, walk 180/left/right, run.
    int pose[8] = {-1, -1, -1, -1, -1, -1, -1, -1};  // +0x280..+0x29c
    int pose_animation = -1, pose_frame = -1;      // +0x4b8 current pose sequence, +0x4be frame (s16)
    // First head bar (+0x388 list): last update's health and the bar type's width; -1 when none.
    int health_ratio = -1, health_scale = -1;
    // Hitmark slots (+0x330 vector of {type, amount, end cycle}, -1 = empty), up to 4.
    int hitsplats[12] = {-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
};

struct Npc {
    int index = -1;  // server NPC index
    int id = -1;     // NpcType id
    std::string name;
    int fine_x = 0, fine_y = 0;    // scene position, 1/128 tile
    int world_x = 0, world_y = 0;  // world tile
    uintptr_t address = 0;         // Npc object; changes when the index is reused
    ActorState actor;
};
// RuneLite conventions: x/y horizontal (local units), z height (negative is up).
struct Camera {
    bool valid = false;
    int x = 0, y = 0, z = 0;
    int pitch = 0, yaw = 0;  // 0..16383
    int focal_x = 0, focal_z = 0, focal_y = 0;  // +0x89500: smoothed player position (camera frame)
    int render_base_x = 0, render_base_y = 0, render_wv_same = 0;  // debug: WorldView at Client+0xc940
};
Camera camera();
// The 3D viewport the scene renderer projects with (drawRenderable 0x72db3c): screen offset =
// r * zoom / depth, centre, clip rect, far plane. Reached as renderer = holder->vtbl[3](holder),
// holder = *(Client +0x90) + 0x1b0 (scene draw caller 0x457128), viewport = *(renderer +0x630c60).
// Game thread only. Unverified.
struct Viewport {
    bool valid = false;
    int zoom = 0, centre_x = 0, centre_y = 0, clip[4] = {}, far = 0;
    // The renderer's camera from the top-level Scene (WorldView +0x10): position +0x288/+0x28c/
    // +0x290 and sin/cos pitch, sin/cos yaw floats +0x418..+0x424 (x 65536).
    int scene_cam[3] = {}, trig[4] = {};
};
Viewport viewport();
// Queue a synth sound effect as SYNTH_SOUND (0x4ae508(Client +0x2610, id, loops, delay)). Game thread only.
bool play_sound_effect(int id, int loops, int delay);

int root_interface();
// Widget object for a component id (group << 16 | child), or 0 if its group isn't loaded.
uintptr_t widget(int component);
bool widget_hidden(uintptr_t widget);
struct WidgetGeometry { int x = 0, y = 0, width = 0, height = 0, parent = -1, group = -1; };  // x/y relative to parent
WidgetGeometry widget_geometry(uintptr_t widget);
int widget_child_count(int group);  // components in a loaded group (0 if not loaded)
std::string widget_text(uintptr_t widget);
// {name (CC_SETOPBASE, +0x2c0), target verb (CC_SETTARGETVERB, +0x330), ops... (CC_SETOP: vector of
// strings, size +0x2e0, data +0x2e8)} (handlers in 0x4e06dc; setOp 0x5dfae8).
std::vector<std::string> widget_strings(uintptr_t widget);
// Children created by CC_CREATE: Widget +0xb50 count, +0xb58 array of shared_ptr {ctrl, Widget*}.
int widget_dynamic_count(uintptr_t widget);
uintptr_t widget_dynamic(uintptr_t widget, int index);  // 0 if empty or out of range
struct WidgetItem { int id = -1, quantity = 0; };
WidgetItem widget_item(uintptr_t widget);
// scrollX, scrollY, scrollWidth, scrollHeight, modelZoom, rotationX, rotationY, rotationZ,
// opacity, modelType, modelId, animationId.
constexpr int kWidgetProps = 35;
// Writes a whitelisted int/bool widget field (CC_SET* offsets) and marks the widget dirty;
// index >= 0 addresses a CC_CREATE child. Game thread only.
bool widget_set_field(int component, int index, uint32_t offset, int value);
// Re-runs the game's layout for a widget after position/size changes (what CC_SETPOSITION does).
bool widget_revalidate(int component, int index);
int widget_create_child(int component, int index, int type);
bool widget_delete_children(int component);
void widget_props(uintptr_t widget, int32_t out[kWidgetProps]);  // item set by IF_SETOBJECT / CC_SETOBJECT
int widget_color(uintptr_t widget);        // 0xRRGGBB (IF_SETCOLOUR), text colour for text widgets
void widget_set_color(uintptr_t widget, int rgb);  // game thread only; marks the widget's area dirty
void widget_set_hidden(uintptr_t widget, bool hidden);  // game thread only, same
bool widget_set_text(uintptr_t widget, const std::string& text);  // game thread only, same (latin-1)
// which: -1 name (opbase), -2 target verb, 0..31 op (0-based; RuneLite setAction index). Game thread.
bool widget_set_string(uintptr_t widget, int which, const std::string& text);
int widget_flags(uintptr_t widget);  // +0x2b0: bit i+1 = op i shown (when no IF_SETEVENTS range covers it)
void widget_set_flags(uintptr_t widget, int flags);  // game thread only
struct SubInterface { int component, group, type; };  // `group` is open inside `component`; type = IF_OPENSUB modal mode (node +8)
std::vector<SubInterface> open_sub_interfaces();

constexpr int kSkillCount = 25;
struct Skills {
    int32_t current[kSkillCount] = {}, real[kSkillCount] = {}, xp[kSkillCount] = {};  // current = boosted
    bool operator==(const Skills& o) const {
        for (int i = 0; i < kSkillCount; i++)
            if (current[i] != o.current[i] || real[i] != o.real[i] || xp[i] != o.xp[i]) return false;
        return true;
    }
};
Skills skills();

struct Inventory {
    int id = -1;                       // InventoryID / inv id (93 = inventory, 94 = worn, 95 = bank)
    std::vector<int32_t> ids, quantities;  // per slot; id -1 = empty
};
// Every inventory the client holds. Game thread only.
std::vector<Inventory> read_inventories();
// The inventories as they were when chat message `id` was created (Message constructor hook);
// each id can be taken once. False if the hook didn't record it.
bool take_message_inventories(int id, std::vector<Inventory>& out);

struct ChatLine {
    int id = -1, type = 0, cycle = 0;
    std::string name, sender, text;
};
int next_message_id();
// Looks up a chat message by id (the game keeps the last 100 per type). Game thread only.
bool read_message(int id, ChatLine& out);
// Replaces a message's text (string assign 0x3f1498 on Message +0xa0). Game thread only.
bool message_set_text(int id, const std::string& text);

struct PlayerInfo {
    int index = -1;
    bool local = false;
    std::string name;
    int fine_x = 0, fine_y = 0, world_x = 0, world_y = 0;
    uintptr_t address = 0;
    ActorState actor;
    // Appearance (PLAYER_INFO mask 0x10, decoder 0x4762d4). Equipment uses RuneLite's encoding
    // (0 empty, kit + 0x100, item + 0x800); has_appearance is false before the first block.
    bool has_appearance = false;
    int equipment[12] = {}, kits[12] = {}, colors[5] = {};
    int gender = 0, npc_id = -1;
    int combat_level = 0, skull = -1, prayer = -1, team = 0;
};
// Walks the top-level WorldView's player table. Game thread only.
std::vector<PlayerInfo> read_players();

// Walks the top-level WorldView's NPC table. Game thread only (the table is rebuilt by
// NPC_INFO); other threads use client_thread::npcs().
std::vector<Npc> read_npcs();

// Client scripts (CS2). Runs a script synchronously in the game's VM, the way the client runs
// widget listeners; args[0] is the script id. Game thread only, and not from inside a script.
struct ScriptArg {
    bool is_string = false;
    int i = 0;
    std::string s;
};
struct ScriptResult {
    std::vector<int32_t> ints;               // int stack after the script returned
    std::vector<std::string> objects;        // object stack (strings; "" for other types)
    std::vector<bool> object_is_string;
};
bool run_script(const std::vector<ScriptArg>& args, ScriptResult& out, std::string& error);
// The VM's live stacks, for ScriptPreFired/PostFired listeners (game thread). The int stack is
// copied whole (size entries); set_script_int_stack writes values back and sets the size.
bool script_stacks(ScriptResult& out);
bool set_script_int_stack(const int32_t* values, int size);
// Replaces the string at object stack slot `index` (0 = bottom) through the game's string assign
// 0x3f1498, as CC_SETTEXT stores text. False when the slot isn't a string. Game thread only.
bool set_script_string(int index, const std::string& text);
// True when the top of the running script's object stack is a string starting with `prefix`.
bool script_top_string_starts_with(const std::string& prefix);
// A ScriptEvent's arguments (+0x08 argc, +0x10 0x28-byte variants); non-int/string args come
// back as an empty string with is_string = false and i = -1.
std::vector<ScriptArg> script_event_args(uintptr_t ev);
bool script_event_source(uintptr_t ev, int* component, int* index);
bool widget_ref(uintptr_t w, int* component, int* index);

// One tuple of a DB row's column, through the script VM's db_getfield (7502): values in column-type
// order, each an int (kind 0), long (1) or string (2). False if the row/column doesn't exist.
struct DbValue {
    int kind = 0;
    int64_t i = 0;
    std::string s;
};
bool db_field(int row, int column, int tuple, std::vector<DbValue>& out);
// The table a DB row belongs to (DBRowType +0x30), or -1 if the row doesn't exist. Game thread.
int db_row_table(int row);
// Number of tuples in a DB row column (db_getfieldcount, 7503); -1 if the row/column is missing.
int db_field_count(int row, int column);

// Item (obj) definitions, from the game's definition cache (loads on demand). Game thread only.
struct ItemDef {
    int id = -1;
    std::string name;
    int price = 0, weight = 0, category = -1;
    bool stackable = false, members = false, tradeable_guess = false;
    int linked_note = -1, note_template = -1, placeholder = -1, placeholder_template = -1;
    std::string inventory_actions[5];
};
bool item_def(int id, ItemDef& out);
// Item icon as the inventory draws it (createItemSprite 0x63ff9c): 36x32 ARGB pixels, 0 = transparent.
// border 0/1/2, shadow colour (0 none), text_mode 0 none / 1 quantity, noted. Game thread only.
// False while the model isn't loaded yet (retry later) or for an unknown id.
// Sprite by id (frame 0) from the game's sprite cache; false while not loaded. Game thread only.
bool sprite(int id, std::vector<uint32_t>& pixels, int& width, int& height);
// Writes a same-size sprite's pixels into the game's cached copy (overrides). Game thread only.
bool sprite_write(int id, const uint32_t* pixels, int width, int height);
bool item_sprite(int id, int quantity, int border, int shadow, int text_mode, bool noted,
                 std::vector<uint32_t>& pixels, int& width, int& height);

// NPC definitions (NpcType), from the game's cache. Game thread only.
struct NpcDef {
    int id = -1;
    std::string name;
    int size = 1, combat_level = 0;
    bool minimap_visible = true, interactable = true;
    std::string actions[5];  // current text (varp/varbit-conditional ops resolved by the game)
    int varbit = -1, varp = -1;
    std::vector<int32_t> transforms;  // multi-NPC ids, last = default (-1 = none); empty if none
};
bool npc_def(int id, NpcDef& out);
// Vertices {x, y, z}* of an Npc/Player's posed model this frame (unrotated, local units, y
// negative = up) and its height. Game thread only.
void drawlog_cycle();  // debug.osrsrl.drawlog: which Npc objects the renderer draws (game thread)
bool actor_model(uintptr_t actor, std::vector<int32_t>& xyz, int& height);
// The posed model's height (max -y over its vertices, as the desktop Model.height that
// Actor.getLogicalHeight reports), or -1. Game thread only.
int actor_height(uintptr_t actor);
// A scene object's model vertices this frame (layer = SceneLayer, tag = the object's tag; same
// vertex convention as actor_model, wall decoration offsets applied), with its local position,
// absolute height and orientation. Game thread only.
struct ObjectModel {
    int x = 0, y = 0, height = 0, orientation = 0, model_height = 0;
    std::vector<int32_t> xyz;
};
bool object_model(int plane, int sx, int sy, int layer, uint64_t tag, ObjectModel& out);
// The live Npc*/Player* for an index at this moment (0 if not active). Game thread only.
uintptr_t live_npc(int index);
uintptr_t live_player(int index);

// Loc (scene object) definitions (LocType), from the game's cache. Game thread only.
struct LocDef {
    int id = -1;
    std::string name;
    int size_x = 1, size_y = 1;
    int varbit = -1, varp = -1;
    std::vector<int32_t> transforms;  // multiloc ids, last = default (-1 = none); empty if not a multiloc
    std::string actions[5];
};
bool loc_def(int id, LocDef& out);

// Ground heights of the top-level scene: heights[(plane * (size_x + 1) + x) * (size_y + 1) + y]
// (corner vertices, negative = up), settings[(plane * size_x + x) * size_y + y] (bit 2 = bridge).
struct SceneHeights {
    int planes = 0, size_x = 0, size_y = 0;
    std::vector<int32_t> heights;
    std::vector<uint8_t> settings;
};
bool scene_heights(SceneHeights& out);  // game thread only

// Enums (cache config type), from the game's cache. Game thread only.
struct EnumDef {
    int key_type = 0, value_type = 0, default_int = 0;
    std::string default_string;
    std::vector<int32_t> keys, int_values;
    std::vector<std::string> string_values;  // when value_type == 's'
};
bool enum_def(int id, EnumDef& out);
// Definition params (opcode 249). kind: 0 obj, 1 npc, 2 loc. Game thread only.
struct TypeParam { bool is_string = false; int int_value = 0; std::string string_value; };
bool type_param(int kind, int id, int key, TypeParam& out);

// Menus (long-press menu). Entries are what the game shows, top-level first as stored.
struct MenuEntry {
    std::string option, target;
    int32_t raw[10] = {};  // entry +0x120..+0x147: parameters (meaning per entry kind; see offsets.md)
    uint8_t kind = 0;      // entry +0x14f
    bool has_children = false;
    uintptr_t address = 0;
};
uintptr_t menu_object();  // the menu model (MenuManager +0x140), 0 if unavailable
bool menu_open();
// Closes the open menu as the game does when a tap lands outside it. Game thread only.
bool menu_close();
// Reorders the open menu: new top-level position i gets the entry at old position order[i] (0 = top).
// Permutes the menu's top-level nodes (as the game's sort does); rebuilds the display if one is shown.
// Game thread only.
bool menu_reorder(const std::vector<int>& order);
// Builds the game's long-press menu at (x, y) in UI canvas units without showing it (no menu may be
// open); read it with read_menu(), then menu_discard(). Game thread only.
bool menu_build_at(int x, int y);
// Scene picking for menu builds: pick_at asks the next frame to ray-pick (x, y) in screen pixels
// (as a touch down does); pick_ready once that pick is done, so menu_build_at sees its hits.
void pick_at(int screen_x, int screen_y);
bool pick_ready();
void menu_discard();
// The point the model was last built at (MenuManager +0x2a8/+0x2ac), open or not.
bool menu_point_raw(int& x, int& y);
// The client-side varp table (RuneLite getVarps()).
std::vector<int32_t> varps();
int weight();      // kg (UPDATE_RUNWEIGHT)
int world();       // Client +0x3174 (CS2 map_world 3318; jav_config param 12)
int world_flags(); // Client +0x2f64 (CS2 worldflags 3324)
// 3D viewport projection: {zoom, x, y, width, height} in UI canvas pixels (Client +0x46d4, +0x46e0..+0x46ec,
// computed by 0x5561e8 from the CS2 viewport zoom/fov settings; RuneLite's getScale/viewport).
bool viewport_zoom(int out[5]);
int run_energy();  // 0..10000 (UPDATE_RUNENERGY)
// The game's addChatMessage (what MESSAGE_GAME calls). Game thread only.
bool add_chat_message(int type, const std::string& name, const std::string& text);
// Removes a message from its chat channel's ring (chatbox lines), by type and message id;
// chat_changed makes the chatbox rebuild. Game thread only.
bool chat_remove_message(int type, int id);
void chat_changed();
// Applies the members flag of the world flags the game received (setWorld's step the jav_config
// start skips). Game thread, per cycle; no-op when they agree.
void sync_members_world();
// Idle logout timeout in minutes (the game's AFKTimeout setting); -1 if unknown. Game thread only.
int afk_timeout_minutes();
bool set_afk_timeout_minutes(int minutes);
// Client varcs (VarClientInt/VarClientStr); false when unset. Game thread only.
bool varc_int(int id, int& out);
bool varc_string(int id, std::string& out);
// Client-side writes through the game's own script-VM setters. Game thread only.
struct VarbitDef { int varp = -1, lsb = 0, msb = 0; };
bool varbit_def(int id, VarbitDef& out);
bool set_varbit(int id, int value);
bool set_varp(int id, int value);  // client varps table only (RuneLite queueChangedVarp semantics)
bool set_varc_int(int id, int value);
bool set_varc_string(int id, const std::string& value);
// Where the open menu was requested (UI-canvas units); false when no menu is shown.
bool menu_point(int& x, int& y);
std::vector<MenuEntry> read_menu();  // game thread only
std::vector<MenuEntry> read_menu(uintptr_t menu);  // a given menu model (the one a tap selects in)
bool menu_entry_for_path(uintptr_t menu, const uint64_t* path, size_t len, MenuEntry& out);
// Rewrites an open menu entry's option/target (null = keep); index as in read_menu().
bool menu_set_text(size_t index, const std::string* option, const std::string* target);
uintptr_t menu_vtable();  // menu model vtable; slot 1 = select
uintptr_t menu_select();  // expected value of that slot

// Scene objects of the top-level scene, all planes (docs/offsets.md "Scene objects"). Game objects
// are listed once, at their south-west tile, with their size in tiles. Game thread only.
enum SceneLayer : uint8_t { kWall = 0, kWallDecoration = 1, kGameObject = 2, kGroundDecoration = 3, kItemPile = 4 };
struct SceneObject {
    uint8_t layer, plane, x, y, size_x = 1, size_y = 1;
    int32_t id;
    uint64_t tag;  // the game's object tag: scene x bits 0-6, y bits 7-13, id bits 20-35
};
std::vector<SceneObject> scene_objects();
struct GroundItem {
    uint8_t plane, x, y;
    int32_t id, quantity;
    int32_t visible_tick, despawn_tick, ownership;  // server ticks (Client +0x3058 clock); ownership 0 none, 1 self, 2 other, 3 group
    bool is_private;
};
std::vector<GroundItem> ground_items();  // per tile in stack order (top first)

// Mod icons (font <img=N> table): game_index >= 0 keeps the game's icon, else a palette sprite
// (index 0 transparent, palette ARGB with alpha 0xff). Applied by mod_icons_cycle on the game thread.
struct ModIcon {
    int game_index = -1;
    int w = 0, h = 0, ox = 0, oy = 0, full_w = 0, full_h = 0;
    std::vector<uint8_t> pixels;
    std::vector<uint32_t> palette;
};
void set_mod_icons(std::vector<ModIcon> icons);
std::vector<int32_t> mod_icon_sizes();  // {fullW, fullH} per game icon; empty before load
void mod_icons_cycle();
}  // namespace game
