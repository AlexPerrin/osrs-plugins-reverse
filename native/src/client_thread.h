#pragma once
#include <functional>
#include <string>
#include <vector>

#include "game.h"

// The game's own thread, entered once per client cycle (20 ms) through a hook on
// Client::doCycle. Equivalent of RuneLite's ClientThread plus the events it posts.
namespace client_thread {

enum class EventType {
    ClientTick,        // every client cycle
    GameTick,          // a SERVER_TICK_END arrived (once per 600 ms game tick)
    GameStateChanged,  // a = new state (RuneLite GameState value), b = old state
    VarbitChanged,     // a = varp id, b = new value (any varp write, server or script)
    NpcSpawned,        // a = NPC index, b = NPC id
    NpcDespawned,      // a = NPC index, b = NPC id
    ChatMessage,       // a = message id (see message()), b = type
    WidgetLoaded,      // a = interface group id
    WidgetClosed,      // a = interface group id
    PlayerSpawned,     // a = player index
    PlayerDespawned,   // a = player index
    ItemContainerChanged,  // a = inventory id
    StatChanged,       // a = skill index
    MenuOpened,        // the long-press menu was shown (entries: game::read_menu())
    MenuOptionClicked, // an entry was selected (clicked_menu_entry()); runs before the game acts
    MenuTap,           // a plain tap is about to run node a (the default entry); entries: tap_menu();
                       // a listener may call redirect_menu_tap() (Menu Entry Swapper's left-click swaps)
};

struct Event {
    EventType type;
    int a = 0, b = 0;
};

using Listener = std::function<void(const Event&)>;

// Patches Client vtable slot 5. False if the game build is unknown or the slot looks wrong.
bool install();
bool installed();
bool on_client_thread();

// Listeners run on the game thread, after the game's own cycle.
void subscribe(Listener listener);

// Runs `task` on the game thread: immediately if already there, else after the next cycle.
void invoke(std::function<void()> task);

// For the overlay: the most recent events (newest last), ClientTick excluded.
std::vector<std::string> recent_events();
uint64_t cycles();

// A chat message captured when it was added (the last 256 are kept); safe from any thread.
bool message(int id, game::ChatLine& out);

// Inventories as of the end of the last cycle; safe from any thread.
std::vector<game::Inventory> inventories();

// Players as of the end of the last cycle; safe from any thread.
std::vector<game::PlayerInfo> players();

// NPCs as of the end of the last cycle; safe from any thread.
std::vector<game::Npc> npcs();

// The entry being selected while MenuOptionClicked is posted; consume_menu_click() from a
// listener stops the game from running it.
const game::MenuEntry& clicked_menu_entry();
void consume_menu_click();
// During MenuTap: the menu model being tapped (0 otherwise), and the top-level node to run instead.
uintptr_t tap_menu();
void redirect_menu_tap(int index);

}  // namespace client_thread
