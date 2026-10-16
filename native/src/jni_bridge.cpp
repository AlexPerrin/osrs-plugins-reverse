#include "jni_bridge.h"

#include <fcntl.h>
#include <sys/stat.h>
#include <sys/system_properties.h>
#include <unistd.h>

#include <atomic>
#include <cstdio>
#include <string>
#include <thread>
#include <vector>

#include "client_thread.h"
#include "game.h"
#include "log.h"
#include "overlay.h"

extern "C" const char osrsrl_host_jar[];
extern "C" const char osrsrl_host_jar_end[];

namespace {

JavaVM* g_vm = nullptr;
jclass g_host = nullptr;  // global ref
jmethodID g_on_event = nullptr;
std::atomic<const char*> g_status{"starting"};

bool check(JNIEnv* env, const char* what) {
    if (!env->ExceptionCheck()) return true;
    LOGE("jni: %s threw", what);
    env->ExceptionDescribe();  // prints the Java stack trace to logcat
    env->ExceptionClear();
    return false;
}

std::string package_name() {
    char buf[256] = {};
    int fd = open("/proc/self/cmdline", O_RDONLY);
    if (fd >= 0) {
        read(fd, buf, sizeof buf - 1);
        close(fd);
    }
    return buf;
}

bool write_file(const std::string& path, const char* data, size_t len) {
    unlink(path.c_str());  // the previous copy is read-only
    int fd = open(path.c_str(), O_WRONLY | O_CREAT | O_TRUNC, 0600);
    if (fd < 0) return false;
    size_t off = 0;
    while (off < len) {
        ssize_t n = write(fd, data + off, len - off);
        if (n <= 0) break;
        off += n;
    }
    close(fd);
    chmod(path.c_str(), 0400);  // Android 14+ refuses writable dex files
    return off == len;
}

// net.runelite.host.Native
jint n_game_state(JNIEnv*, jclass) { return game::game_state(); }
jint n_game_cycle(JNIEnv*, jclass) { return game::cycle(); }
jint n_server_tick(JNIEnv*, jclass) { return game::server_tick(); }
jint n_varp(JNIEnv*, jclass, jint id) { return game::varp(id); }
jint n_server_varp(JNIEnv*, jclass, jint id) { return game::server_varp(id); }
jint n_varbit(JNIEnv* env, jclass, jint id) {
    if (!client_thread::on_client_thread()) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "getVarbitValue must be called on the client thread");
        return 0;
    }
    return game::varbit(id);
}
// Object[] {int[] intStack, String[] objectStack (null for non-string entries)}.
jobjectArray n_run_script(JNIEnv* env, jclass, jobjectArray jargs) {
    if (!client_thread::on_client_thread()) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "runScript must be called on the client thread");
        return nullptr;
    }
    jclass integer = env->FindClass("java/lang/Integer");
    jclass string = env->FindClass("java/lang/String");
    jmethodID int_value = env->GetMethodID(integer, "intValue", "()I");
    std::vector<game::ScriptArg> args;
    jsize n = jargs ? env->GetArrayLength(jargs) : 0;
    for (jsize i = 0; i < n; i++) {
        jobject o = env->GetObjectArrayElement(jargs, i);
        game::ScriptArg a;
        if (o && env->IsInstanceOf(o, integer)) {
            a.i = env->CallIntMethod(o, int_value);
        } else if (o && env->IsInstanceOf(o, string)) {
            a.is_string = true;
            const char* c = env->GetStringUTFChars(static_cast<jstring>(o), nullptr);
            a.s = c ? c : "";
            if (c) env->ReleaseStringUTFChars(static_cast<jstring>(o), c);
        } else {
            env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"), "runScript: only Integer and String arguments are supported");
            return nullptr;
        }
        env->DeleteLocalRef(o);
        args.push_back(std::move(a));
    }
    game::ScriptResult r;
    std::string error;
    if (!game::run_script(args, r, error)) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), ("runScript: " + error).c_str());
        return nullptr;
    }
    jintArray ints = env->NewIntArray(static_cast<jsize>(r.ints.size()));
    if (!r.ints.empty()) env->SetIntArrayRegion(ints, 0, static_cast<jsize>(r.ints.size()), r.ints.data());
    jobjectArray objs = env->NewObjectArray(static_cast<jsize>(r.objects.size()), string, nullptr);
    for (size_t i = 0; i < r.objects.size(); i++) {
        if (!r.object_is_string[i]) continue;
        jstring js = env->NewStringUTF(r.objects[i].c_str());
        env->SetObjectArrayElement(objs, static_cast<jsize>(i), js);
        env->DeleteLocalRef(js);
    }
    jobjectArray res = env->NewObjectArray(2, env->FindClass("java/lang/Object"), nullptr);
    env->SetObjectArrayElement(res, 0, ints);
    env->SetObjectArrayElement(res, 1, objs);
    return res;
}
// Object[] {int[] {price, weight, category, stackable, members, tradeable?, linkedNote,
// noteTemplate, placeholder, placeholderTemplate}, String[] {name, 5 inventory actions}}.
jobjectArray n_item_def(JNIEnv* env, jclass, jint id) {
    if (!client_thread::on_client_thread()) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "getItemDefinition must be called on the client thread");
        return nullptr;
    }
    game::ItemDef d;
    if (!game::item_def(id, d)) return nullptr;
    jint v[10] = {d.price, d.weight, d.category, d.stackable, d.members, d.tradeable_guess,
                  d.linked_note, d.note_template, d.placeholder, d.placeholder_template};
    jintArray ints = env->NewIntArray(10);
    env->SetIntArrayRegion(ints, 0, 10, v);
    jclass string = env->FindClass("java/lang/String");
    jobjectArray strs = env->NewObjectArray(6, string, nullptr);
    for (int i = 0; i < 6; i++) {
        const std::string& t = i == 0 ? d.name : d.inventory_actions[i - 1];
        if (i > 0 && t.empty()) continue;
        jstring js = env->NewStringUTF(t.c_str());
        env->SetObjectArrayElement(strs, i, js);
        env->DeleteLocalRef(js);
    }
    jobjectArray res = env->NewObjectArray(2, env->FindClass("java/lang/Object"), nullptr);
    env->SetObjectArrayElement(res, 0, ints);
    env->SetObjectArrayElement(res, 1, strs);
    return res;
}
// Object[] {int[] {id, size, combatLevel, minimapVisible, interactable}, String[] {name, 5 actions}}.
jobjectArray n_npc_def(JNIEnv* env, jclass, jint id) {
    if (!client_thread::on_client_thread()) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "getNpcDefinition must be called on the client thread");
        return nullptr;
    }
    game::NpcDef d;
    if (!game::npc_def(id, d)) return nullptr;
    jint v[5] = {d.id, d.size, d.combat_level, d.minimap_visible, d.interactable};
    jintArray ints = env->NewIntArray(5);
    env->SetIntArrayRegion(ints, 0, 5, v);
    jclass string = env->FindClass("java/lang/String");
    jobjectArray strs = env->NewObjectArray(6, string, nullptr);
    for (int i = 0; i < 6; i++) {
        const std::string& t = i == 0 ? d.name : d.actions[i - 1];
        if (i > 0 && t.empty()) continue;
        jstring js = env->NewStringUTF(t.c_str());
        env->SetObjectArrayElement(strs, i, js);
        env->DeleteLocalRef(js);
    }
    jobjectArray res = env->NewObjectArray(2, env->FindClass("java/lang/Object"), nullptr);
    env->SetObjectArrayElement(res, 0, ints);
    env->SetObjectArrayElement(res, 1, strs);
    return res;
}
void n_sidebar_set_model(JNIEnv* env, jclass, jstring model) {
    const char* c = model ? env->GetStringUTFChars(model, nullptr) : nullptr;
    sidebar_set_model(c ? c : "");
    if (c) env->ReleaseStringUTFChars(model, c);
}
jintArray n_sidebar_poll_events(JNIEnv* env, jclass) {
    std::vector<int> ev = sidebar_poll_events();
    if (ev.empty()) return nullptr;
    jintArray arr = env->NewIntArray(static_cast<jsize>(ev.size()));
    env->SetIntArrayRegion(arr, 0, static_cast<jsize>(ev.size()), ev.data());
    return arr;
}
jboolean n_sidebar_open(JNIEnv*, jclass) { return sidebar_open(); }
jboolean n_overlay_touch(JNIEnv*, jclass, jint action, jfloat x, jfloat y) { return overlay_touch(action, x, y); }
// {planes, sizeX, sizeY, heights..., settings...} (layouts in game.h), or null.
jintArray n_scene_heights(JNIEnv* env, jclass) {
    if (!client_thread::on_client_thread()) return nullptr;
    game::SceneHeights h;
    if (!game::scene_heights(h)) return nullptr;
    std::vector<jint> v = {h.planes, h.size_x, h.size_y};
    v.insert(v.end(), h.heights.begin(), h.heights.end());
    v.insert(v.end(), h.settings.begin(), h.settings.end());
    jintArray arr = env->NewIntArray(static_cast<jsize>(v.size()));
    env->SetIntArrayRegion(arr, 0, static_cast<jsize>(v.size()), v.data());
    return arr;
}
// Object[] {int[] {keyType, valueType, defaultInt}, String defaultString, int[] keys, int[] intValues, String[] stringValues}.
jobjectArray n_enum_def(JNIEnv* env, jclass, jint id) {
    if (!client_thread::on_client_thread()) return nullptr;
    game::EnumDef d;
    if (!game::enum_def(id, d)) return nullptr;
    jclass string = env->FindClass("java/lang/String");
    jint head[3] = {d.key_type, d.value_type, d.default_int};
    jintArray h = env->NewIntArray(3);
    env->SetIntArrayRegion(h, 0, 3, head);
    jintArray keys = env->NewIntArray(static_cast<jsize>(d.keys.size()));
    if (!d.keys.empty()) env->SetIntArrayRegion(keys, 0, static_cast<jsize>(d.keys.size()), d.keys.data());
    jintArray ints = env->NewIntArray(static_cast<jsize>(d.int_values.size()));
    if (!d.int_values.empty()) env->SetIntArrayRegion(ints, 0, static_cast<jsize>(d.int_values.size()), d.int_values.data());
    jobjectArray strs = env->NewObjectArray(static_cast<jsize>(d.string_values.size()), string, nullptr);
    for (size_t i = 0; i < d.string_values.size(); i++) {
        jstring js = env->NewStringUTF(d.string_values[i].c_str());
        env->SetObjectArrayElement(strs, static_cast<jsize>(i), js);
        env->DeleteLocalRef(js);
    }
    jobjectArray res = env->NewObjectArray(5, env->FindClass("java/lang/Object"), nullptr);
    env->SetObjectArrayElement(res, 0, h);
    env->SetObjectArrayElement(res, 1, env->NewStringUTF(d.default_string.c_str()));
    env->SetObjectArrayElement(res, 2, keys);
    env->SetObjectArrayElement(res, 3, ints);
    env->SetObjectArrayElement(res, 4, strs);
    return res;
}
jboolean n_is_client_thread(JNIEnv*, jclass) { return client_thread::on_client_thread(); }
jintArray n_player_position(JNIEnv* env, jclass) {
    if (game::game_state() != 30) return nullptr;
    game::Point w = game::player_world(), s = game::player_local_tile(), l = game::player_fine();
    jint v[8] = {w.x, w.y, s.x, s.y, l.x, l.y, game::player_plane(), game::scene_plane()};
    jintArray arr = env->NewIntArray(8);
    env->SetIntArrayRegion(arr, 0, 8, v);
    return arr;
}

jobjectArray n_npcs(JNIEnv* env, jclass) {
    std::vector<game::Npc> npcs = client_thread::npcs();
    jsize n = static_cast<jsize>(npcs.size());
    std::vector<jint> packed;
    packed.reserve(n * 6);
    jobjectArray names = env->NewObjectArray(n, env->FindClass("java/lang/String"), nullptr);
    for (jsize i = 0; i < n; i++) {
        const game::Npc& npc = npcs[i];
        packed.insert(packed.end(), {npc.index, npc.id, npc.fine_x, npc.fine_y, npc.world_x, npc.world_y});
        jstring name = env->NewStringUTF(npc.name.c_str());
        env->SetObjectArrayElement(names, i, name);
        env->DeleteLocalRef(name);
    }
    jintArray ints = env->NewIntArray(n * 6);
    env->SetIntArrayRegion(ints, 0, n * 6, packed.data());
    jobjectArray out = env->NewObjectArray(2, env->FindClass("java/lang/Object"), nullptr);
    env->SetObjectArrayElement(out, 0, ints);
    env->SetObjectArrayElement(out, 1, names);
    return out;
}

// Game strings are CP1252/Latin-1 bytes; JNI wants (modified) UTF-8.
jstring latin1_string(JNIEnv* env, const std::string& s) {
    std::string u;
    u.reserve(s.size());
    for (unsigned char c : s) {
        if (c >= 0x80) {
            u.push_back(static_cast<char>(0xc0 | (c >> 6)));
            u.push_back(static_cast<char>(0x80 | (c & 0x3f)));
        } else if (c) {
            u.push_back(static_cast<char>(c));
        }
    }
    return env->NewStringUTF(u.c_str());
}

constexpr int kMenuInts = 12;  // per entry: kind, hasChildren, raw[10]
jobjectArray menu_array(JNIEnv* env, const std::vector<game::MenuEntry>& entries) {
    jsize n = static_cast<jsize>(entries.size());
    std::vector<jint> packed;
    jobjectArray strs = env->NewObjectArray(n * 2, env->FindClass("java/lang/String"), nullptr);
    for (jsize i = 0; i < n; i++) {
        const game::MenuEntry& e = entries[i];
        packed.push_back(e.kind);
        packed.push_back(e.has_children);
        packed.insert(packed.end(), e.raw, e.raw + 10);
        jstring o = latin1_string(env, e.option), t = latin1_string(env, e.target);
        env->SetObjectArrayElement(strs, i * 2, o);
        env->SetObjectArrayElement(strs, i * 2 + 1, t);
        env->DeleteLocalRef(o);
        env->DeleteLocalRef(t);
    }
    jintArray ints = env->NewIntArray(n * kMenuInts);
    if (n) env->SetIntArrayRegion(ints, 0, n * kMenuInts, packed.data());
    jobjectArray out = env->NewObjectArray(2, env->FindClass("java/lang/Object"), nullptr);
    env->SetObjectArrayElement(out, 0, ints);
    env->SetObjectArrayElement(out, 1, strs);
    return out;
}

// {int[] packed (kMenuInts per entry), String[] {option, target} per entry}; game thread only.
jobjectArray n_menu_entries(JNIEnv* env, jclass) {
    if (!client_thread::on_client_thread()) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "getMenuEntries must be called on the client thread");
        return nullptr;
    }
    return menu_array(env, game::read_menu());
}
jobjectArray n_clicked_menu_entry(JNIEnv* env, jclass) {
    return menu_array(env, {client_thread::clicked_menu_entry()});
}
jboolean n_menu_open(JNIEnv*, jclass) { return game::menu_open(); }

bool require_client_thread(JNIEnv* env, const char* what) {
    if (client_thread::on_client_thread()) return true;
    std::string msg = std::string(what) + " must be called on the client thread";
    env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), msg.c_str());
    return false;
}

// 5 ints per object: plane << 24 | layer << 16 | x << 8 | y, id, tag low, tag high, size_x << 8 | size_y.
jintArray n_scene_objects(JNIEnv* env, jclass) {
    if (!require_client_thread(env, "sceneObjects")) return nullptr;
    auto objs = game::scene_objects();
    std::vector<jint> packed;
    packed.reserve(objs.size() * 5);
    for (const auto& o : objs) {
        packed.push_back(o.plane << 24 | o.layer << 16 | o.x << 8 | o.y);
        packed.push_back(o.id);
        packed.push_back(static_cast<jint>(o.tag & 0xffffffffu));
        packed.push_back(static_cast<jint>(o.tag >> 32));
        packed.push_back(o.size_x << 8 | o.size_y);
    }
    jintArray out = env->NewIntArray(static_cast<jsize>(packed.size()));
    if (out && !packed.empty()) env->SetIntArrayRegion(out, 0, static_cast<jsize>(packed.size()), packed.data());
    return out;
}

// 3 ints per item: plane << 16 | x << 8 | y, id, quantity.
jintArray n_ground_items(JNIEnv* env, jclass) {
    if (!require_client_thread(env, "groundItems")) return nullptr;
    auto items = game::ground_items();
    std::vector<jint> packed;
    packed.reserve(items.size() * 3);
    for (const auto& i : items) {
        packed.push_back(i.plane << 16 | i.x << 8 | i.y);
        packed.push_back(i.id);
        packed.push_back(i.quantity);
    }
    jintArray out = env->NewIntArray(static_cast<jsize>(packed.size()));
    if (out && !packed.empty()) env->SetIntArrayRegion(out, 0, static_cast<jsize>(packed.size()), packed.data());
    return out;
}
void n_consume_menu_click(JNIEnv*, jclass) { client_thread::consume_menu_click(); }

// {name, sender, text}, or null if the message is no longer kept.
jobjectArray n_message(JNIEnv* env, jclass, jint id) {
    game::ChatLine line;
    if (!client_thread::message(id, line)) return nullptr;
    jobjectArray out = env->NewObjectArray(3, env->FindClass("java/lang/String"), nullptr);
    const std::string* parts[] = {&line.name, &line.sender, &line.text};
    for (int i = 0; i < 3; i++) {
        jstring str = env->NewStringUTF(parts[i]->c_str());
        env->SetObjectArrayElement(out, i, str);
        env->DeleteLocalRef(str);
    }
    return out;
}

jintArray n_screen_size(JNIEnv* env, jclass) {
    jint v[2];
    overlay_screen_size(&v[0], &v[1]);
    jintArray arr = env->NewIntArray(2);
    env->SetIntArrayRegion(arr, 0, 2, v);
    return arr;
}

jobject n_overlay_buffer(JNIEnv* env, jclass, jint w, jint h) {
    uint8_t* p = overlay_back_buffer(w, h);
    return p ? env->NewDirectByteBuffer(p, static_cast<jlong>(w) * h * 4) : nullptr;
}

void n_overlay_publish(JNIEnv*, jclass, jint w, jint h) { overlay_publish(w, h); }

jintArray n_camera(JNIEnv* env, jclass) {
    game::Camera c = game::camera();
    jint v[5] = {c.x, c.y, c.z, c.pitch, c.yaw};
    jintArray arr = env->NewIntArray(5);
    env->SetIntArrayRegion(arr, 0, 5, v);
    return arr;
}

jstring n_property(JNIEnv* env, jclass, jstring name) {
    const char* n = env->GetStringUTFChars(name, nullptr);
    char value[PROP_VALUE_MAX] = "";
    __system_property_get(n, value);
    env->ReleaseStringUTFChars(name, n);
    return env->NewStringUTF(value);
}

jboolean n_widget_exists(JNIEnv*, jclass, jint id) { return game::widget(id) != 0; }
jboolean n_widget_hidden(JNIEnv*, jclass, jint id) { return game::widget_hidden(game::widget(id)); }
jstring n_widget_text(JNIEnv* env, jclass, jint id) {
    uintptr_t w = game::widget(id);
    return w ? env->NewStringUTF(game::widget_text(w).c_str()) : nullptr;
}
jint n_root_interface(JNIEnv*, jclass) { return game::root_interface(); }
// {x, y, width, height, parentId}, or null if the widget isn't loaded.
jintArray n_widget_geometry(JNIEnv* env, jclass, jint id) {
    uintptr_t w = game::widget(id);
    if (!w) return nullptr;
    game::WidgetGeometry g = game::widget_geometry(w);
    jint v[5] = {g.x, g.y, g.width, g.height, g.parent};
    jintArray arr = env->NewIntArray(5);
    env->SetIntArrayRegion(arr, 0, 5, v);
    return arr;
}
jint n_widget_child_count(JNIEnv*, jclass, jint group) { return game::widget_child_count(group); }
// {itemId, quantity} of a component (index < 0) or of its CC_CREATE child `index`; null if absent.
jintArray n_widget_item(JNIEnv* env, jclass, jint id, jint index) {
    uintptr_t w = game::widget(id);
    if (w && index >= 0) w = game::widget_dynamic(w, index);
    if (!w) return nullptr;
    game::WidgetItem it = game::widget_item(w);
    jint v[2] = {it.id, it.quantity};
    jintArray arr = env->NewIntArray(2);
    env->SetIntArrayRegion(arr, 0, 2, v);
    return arr;
}

// Dynamic (CC_CREATE) children: index into the component's created-children array.
jint n_widget_dynamic_count(JNIEnv*, jclass, jint id) { return game::widget_dynamic_count(game::widget(id)); }
jboolean n_widget_dynamic_hidden(JNIEnv*, jclass, jint id, jint i) {
    uintptr_t w = game::widget_dynamic(game::widget(id), i);
    return !w || game::widget_hidden(w);
}
jstring n_widget_dynamic_text(JNIEnv* env, jclass, jint id, jint i) {
    uintptr_t w = game::widget_dynamic(game::widget(id), i);
    return w ? env->NewStringUTF(game::widget_text(w).c_str()) : nullptr;
}
// {x, y, width, height, parentId (= id)}, or null.
jintArray n_widget_dynamic_geometry(JNIEnv* env, jclass, jint id, jint i) {
    uintptr_t w = game::widget_dynamic(game::widget(id), i);
    if (!w) return nullptr;
    game::WidgetGeometry g = game::widget_geometry(w);
    jint v[5] = {g.x, g.y, g.width, g.height, id};
    jintArray out = env->NewIntArray(5);
    if (out) env->SetIntArrayRegion(out, 0, 5, v);
    return out;
}

// {int[] packed (index, local, localX, localY, worldX, worldY), String[] names}
jobjectArray n_players(JNIEnv* env, jclass) {
    std::vector<game::PlayerInfo> players = client_thread::players();
    jsize n = static_cast<jsize>(players.size());
    std::vector<jint> packed;
    jobjectArray names = env->NewObjectArray(n, env->FindClass("java/lang/String"), nullptr);
    for (jsize i = 0; i < n; i++) {
        const game::PlayerInfo& p = players[i];
        packed.insert(packed.end(), {p.index, p.local ? 1 : 0, p.fine_x, p.fine_y, p.world_x, p.world_y});
        jstring name = env->NewStringUTF(p.name.c_str());
        env->SetObjectArrayElement(names, i, name);
        env->DeleteLocalRef(name);
    }
    jintArray ints = env->NewIntArray(n * 6);
    env->SetIntArrayRegion(ints, 0, n * 6, packed.data());
    jobjectArray out = env->NewObjectArray(2, env->FindClass("java/lang/Object"), nullptr);
    env->SetObjectArrayElement(out, 0, ints);
    env->SetObjectArrayElement(out, 1, names);
    return out;
}

// {id0, id1, ...} of an inventory's items followed by quantities (2 * slots ints), or null.
jintArray n_inventory(JNIEnv* env, jclass, jint id) {
    for (const auto& inv : client_thread::inventories()) {
        if (inv.id != id) continue;
        jsize n = static_cast<jsize>(inv.ids.size());
        jintArray arr = env->NewIntArray(2 * n);
        env->SetIntArrayRegion(arr, 0, n, inv.ids.data());
        env->SetIntArrayRegion(arr, n, n, inv.quantities.data());
        return arr;
    }
    return nullptr;
}

// {current[25], real[25], xp[25]}
jintArray n_skills(JNIEnv* env, jclass) {
    game::Skills sk = game::skills();
    jintArray arr = env->NewIntArray(3 * game::kSkillCount);
    env->SetIntArrayRegion(arr, 0, game::kSkillCount, sk.current);
    env->SetIntArrayRegion(arr, game::kSkillCount, game::kSkillCount, sk.real);
    env->SetIntArrayRegion(arr, 2 * game::kSkillCount, game::kSkillCount, sk.xp);
    return arr;
}

const JNINativeMethod kNatives[] = {
    {"skills", "()[I", reinterpret_cast<void*>(n_skills)},
    {"inventory", "(I)[I", reinterpret_cast<void*>(n_inventory)},
    {"players", "()[Ljava/lang/Object;", reinterpret_cast<void*>(n_players)},
    {"widgetExists", "(I)Z", reinterpret_cast<void*>(n_widget_exists)},
    {"widgetHidden", "(I)Z", reinterpret_cast<void*>(n_widget_hidden)},
    {"widgetText", "(I)Ljava/lang/String;", reinterpret_cast<void*>(n_widget_text)},
    {"rootInterface", "()I", reinterpret_cast<void*>(n_root_interface)},
    {"widgetGeometry", "(I)[I", reinterpret_cast<void*>(n_widget_geometry)},
    {"widgetChildCount", "(I)I", reinterpret_cast<void*>(n_widget_child_count)},
    {"widgetDynamicCount", "(I)I", reinterpret_cast<void*>(n_widget_dynamic_count)},
    {"widgetDynamicHidden", "(II)Z", reinterpret_cast<void*>(n_widget_dynamic_hidden)},
    {"widgetItem", "(II)[I", reinterpret_cast<void*>(n_widget_item)},
    {"widgetDynamicText", "(II)Ljava/lang/String;", reinterpret_cast<void*>(n_widget_dynamic_text)},
    {"widgetDynamicGeometry", "(II)[I", reinterpret_cast<void*>(n_widget_dynamic_geometry)},
    {"property", "(Ljava/lang/String;)Ljava/lang/String;", reinterpret_cast<void*>(n_property)},
    {"camera", "()[I", reinterpret_cast<void*>(n_camera)},
    {"screenSize", "()[I", reinterpret_cast<void*>(n_screen_size)},
    {"overlayBuffer", "(II)Ljava/nio/ByteBuffer;", reinterpret_cast<void*>(n_overlay_buffer)},
    {"overlayPublish", "(II)V", reinterpret_cast<void*>(n_overlay_publish)},
    {"message", "(I)[Ljava/lang/String;", reinterpret_cast<void*>(n_message)},
    {"npcs", "()[Ljava/lang/Object;", reinterpret_cast<void*>(n_npcs)},
    {"menuEntries", "()[Ljava/lang/Object;", reinterpret_cast<void*>(n_menu_entries)},
    {"clickedMenuEntry", "()[Ljava/lang/Object;", reinterpret_cast<void*>(n_clicked_menu_entry)},
    {"menuOpen", "()Z", reinterpret_cast<void*>(n_menu_open)},
    {"sceneObjects", "()[I", reinterpret_cast<void*>(n_scene_objects)},
    {"groundItems", "()[I", reinterpret_cast<void*>(n_ground_items)},
    {"consumeMenuClick", "()V", reinterpret_cast<void*>(n_consume_menu_click)},
    {"gameState", "()I", reinterpret_cast<void*>(n_game_state)},
    {"gameCycle", "()I", reinterpret_cast<void*>(n_game_cycle)},
    {"serverTick", "()I", reinterpret_cast<void*>(n_server_tick)},
    {"varp", "(I)I", reinterpret_cast<void*>(n_varp)},
    {"serverVarp", "(I)I", reinterpret_cast<void*>(n_server_varp)},
    {"varbit", "(I)I", reinterpret_cast<void*>(n_varbit)},
    {"playerPosition", "()[I", reinterpret_cast<void*>(n_player_position)},
    {"itemDef", "(I)[Ljava/lang/Object;", reinterpret_cast<void*>(n_item_def)},
    {"npcDef", "(I)[Ljava/lang/Object;", reinterpret_cast<void*>(n_npc_def)},
    {"sidebarSetModel", "(Ljava/lang/String;)V", reinterpret_cast<void*>(n_sidebar_set_model)},
    {"sidebarPollEvents", "()[I", reinterpret_cast<void*>(n_sidebar_poll_events)},
    {"sidebarOpen", "()Z", reinterpret_cast<void*>(n_sidebar_open)},
    {"overlayTouch", "(IFF)Z", reinterpret_cast<void*>(n_overlay_touch)},
    {"sceneHeights", "()[I", reinterpret_cast<void*>(n_scene_heights)},
    {"enumDef", "(I)[Ljava/lang/Object;", reinterpret_cast<void*>(n_enum_def)},
    {"runScript", "([Ljava/lang/Object;)[Ljava/lang/Object;", reinterpret_cast<void*>(n_run_script)},
    {"isClientThread", "()Z", reinterpret_cast<void*>(n_is_client_thread)},
};

JNIEnv* game_thread_env() {
    thread_local JNIEnv* env = nullptr;
    if (!env) {
        JavaVMAttachArgs args{JNI_VERSION_1_6, "osrsrl-game", nullptr};
        if (g_vm->AttachCurrentThread(&env, &args) != JNI_OK) env = nullptr;
    }
    return env;
}

jclass load_class(JNIEnv* env, jobject loader, const char* name) {
    jclass cl = env->FindClass("java/lang/ClassLoader");
    jmethodID load = env->GetMethodID(cl, "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;");
    jstring jname = env->NewStringUTF(name);
    auto c = static_cast<jclass>(env->CallObjectMethod(loader, load, jname));
    return check(env, name) ? c : nullptr;
}

bool start_host(JNIEnv* env) {
    std::string pkg = package_name();
    std::string dir = "/data/data/" + pkg + "/code_cache/osrsrl";
    mkdir(("/data/data/" + pkg + "/code_cache").c_str(), 0700);
    mkdir(dir.c_str(), 0700);
    std::string jar = dir + "/host.jar";
    if (!write_file(jar, osrsrl_host_jar, osrsrl_host_jar_end - osrsrl_host_jar)) {
        LOGE("jni: could not write %s", jar.c_str());
        return false;
    }
    std::string plugins = "/sdcard/Android/data/" + pkg + "/files/plugins";

    jclass cl_loader = env->FindClass("java/lang/ClassLoader");
    jobject system = env->CallStaticObjectMethod(
        cl_loader, env->GetStaticMethodID(cl_loader, "getSystemClassLoader", "()Ljava/lang/ClassLoader;"));
    jclass dex_loader = env->FindClass("dalvik/system/DexClassLoader");
    jmethodID ctor = env->GetMethodID(
        dex_loader, "<init>", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/ClassLoader;)V");
    jobject loader = env->NewObject(dex_loader, ctor, env->NewStringUTF(jar.c_str()),
                                    env->NewStringUTF(dir.c_str()), nullptr, system);
    if (!check(env, "DexClassLoader")) return false;

    jclass native_cls = load_class(env, loader, "net.runelite.host.Native");
    jclass host = load_class(env, loader, "net.runelite.host.Host");
    if (!native_cls || !host) return false;
    if (env->RegisterNatives(native_cls, kNatives, sizeof kNatives / sizeof kNatives[0]) != JNI_OK) {
        check(env, "RegisterNatives");
        return false;
    }
    jmethodID start = env->GetStaticMethodID(host, "start", "(Ljava/lang/String;Ljava/lang/String;)V");
    g_on_event = env->GetStaticMethodID(host, "onEvent", "(III)V");
    if (!check(env, "Host methods") || !start || !g_on_event) return false;
    env->CallStaticVoidMethod(host, start, env->NewStringUTF(plugins.c_str()), env->NewStringUTF(dir.c_str()));
    if (!check(env, "Host.start")) return false;
    g_host = static_cast<jclass>(env->NewGlobalRef(host));
    return true;
}

void host_thread() {
    JNIEnv* env = nullptr;
    JavaVMAttachArgs args{JNI_VERSION_1_6, "osrsrl-host", nullptr};
    if (g_vm->AttachCurrentThread(&env, &args) != JNI_OK) {
        g_status = "failed";
        return;
    }
    LOGI("jni: starting plugin host (%zu byte jar)", static_cast<size_t>(osrsrl_host_jar_end - osrsrl_host_jar));
    bool ok = start_host(env);
    g_vm->DetachCurrentThread();
    if (!ok) {
        g_status = "failed";
        return;
    }
    client_thread::subscribe([](const client_thread::Event& e) {
        JNIEnv* env = game_thread_env();
        if (!env) return;
        env->CallStaticVoidMethod(g_host, g_on_event, static_cast<jint>(e.type), e.a, e.b);
        check(env, "Host.onEvent");
    });
    g_status = "ready";
    LOGI("jni: plugin host ready");
}

}  // namespace

void jni_bridge_start(JavaVM* vm) {
    char value[PROP_VALUE_MAX] = "";
    __system_property_get("debug.osrsrl.host", value);
    if (value[0] == '0' || !client_thread::installed()) {
        g_status = "disabled";
        return;
    }
    g_vm = vm;
    std::thread(host_thread).detach();
}

const char* jni_bridge_status() { return g_status.load(); }
