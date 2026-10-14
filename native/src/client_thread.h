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

// NPCs as of the end of the last cycle; safe from any thread.
std::vector<game::Npc> npcs();

}  // namespace client_thread
