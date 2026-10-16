#include "jni_bridge.h"
#include "gl_trace.h"

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
jint n_weight(JNIEnv*, jclass) { return game::weight(); }
jint n_world(JNIEnv*, jclass) { return game::world(); }
jint n_world_flags(JNIEnv*, jclass) { return game::world_flags(); }
jintArray n_viewport_zoom(JNIEnv* env, jclass) {
    int v[5];
    if (!game::viewport_zoom(v)) return nullptr;
    jintArray a = env->NewIntArray(5);
    env->SetIntArrayRegion(a, 0, 5, v);
    return a;
}
// getDBTableField: Object[] of Integer/Long/String, or null (game thread).
jobjectArray n_db_field(JNIEnv* env, jclass, jint row, jint column, jint tuple) {
    std::vector<game::DbValue> vals;
    if (!game::db_field(row, column, tuple, vals)) return nullptr;
    jclass obj = env->FindClass("java/lang/Object");
    jclass ic = env->FindClass("java/lang/Integer"), lc = env->FindClass("java/lang/Long");
    jmethodID iv = env->GetStaticMethodID(ic, "valueOf", "(I)Ljava/lang/Integer;");
    jmethodID lv = env->GetStaticMethodID(lc, "valueOf", "(J)Ljava/lang/Long;");
    jobjectArray a = env->NewObjectArray(static_cast<jsize>(vals.size()), obj, nullptr);
    for (size_t k = 0; k < vals.size(); k++) {
        jobject o = vals[k].kind == 0 ? env->CallStaticObjectMethod(ic, iv, static_cast<jint>(vals[k].i))
                  : vals[k].kind == 1 ? env->CallStaticObjectMethod(lc, lv, static_cast<jlong>(vals[k].i))
                                      : static_cast<jobject>(env->NewStringUTF(vals[k].s.c_str()));
        env->SetObjectArrayElement(a, static_cast<jsize>(k), o);
        env->DeleteLocalRef(o);
    }
    return a;
}
// The GPU's ViewTransforms block (100 floats), or null before one was captured.
jfloatArray n_view_transforms(JNIEnv* env, jclass) {
    float v[100];
    if (!gl_view_transforms(v)) return nullptr;
    jfloatArray a = env->NewFloatArray(100);
    env->SetFloatArrayRegion(a, 0, 100, v);
    return a;
}
jint n_run_energy(JNIEnv*, jclass) { return game::run_energy(); }
// {height, x0, y0, z0, x1, ...} of an NPC's (player=false) or player's posed model; null if none.
jintArray n_actor_model(JNIEnv* env, jclass, jboolean player, jint index) {
    if (!client_thread::on_client_thread()) return nullptr;
    // Resolve now, not from the cycle's snapshot: entities can be freed after it was taken.
    uintptr_t a = player ? game::live_player(index) : game::live_npc(index);
    std::vector<int32_t> xyz;
    int height = 0;
    if (!game::actor_model(a, xyz, height)) return nullptr;
    jintArray arr = env->NewIntArray(static_cast<jsize>(xyz.size() + 1));
    env->SetIntArrayRegion(arr, 0, 1, &height);
    env->SetIntArrayRegion(arr, 1, static_cast<jsize>(xyz.size()), xyz.data());
    return arr;
}
// {x, y, height, orientation, modelHeight, vx, vy, vz, ...} of a scene object's model, or null.
jintArray n_object_model(JNIEnv* env, jclass, jint plane, jint sx, jint sy, jint layer, jlong tag) {
    if (!client_thread::on_client_thread()) return nullptr;
    game::ObjectModel m;
    if (!game::object_model(plane, sx, sy, layer, static_cast<uint64_t>(tag), m)) return nullptr;
    jint head[5] = {m.x, m.y, m.height, m.orientation, m.model_height};
    jintArray arr = env->NewIntArray(static_cast<jsize>(m.xyz.size() + 5));
    env->SetIntArrayRegion(arr, 0, 5, head);
    env->SetIntArrayRegion(arr, 5, static_cast<jsize>(m.xyz.size()), m.xyz.data());
    return arr;
}
jintArray n_varps(JNIEnv* env, jclass) {
    std::vector<int32_t> v = game::varps();
    jintArray arr = env->NewIntArray(static_cast<jsize>(v.size()));
    env->SetIntArrayRegion(arr, 0, static_cast<jsize>(v.size()), v.data());
    return arr;
}
jint n_varc_int(JNIEnv*, jclass, jint id) { int v = 0; return game::varc_int(id, v) ? v : 0; }
jstring latin1_string(JNIEnv* env, const std::string& s);
jstring n_varc_string(JNIEnv* env, jclass, jint id) {
    std::string s;
    return game::varc_string(id, s) ? latin1_string(env, s) : nullptr;
}
jboolean n_add_chat_message(JNIEnv* env, jclass, jint type, jstring name, jstring text) {
    if (!client_thread::on_client_thread()) return false;
    auto str = [env](jstring s) {
        if (!s) return std::string();
        const char* c = env->GetStringUTFChars(s, nullptr);
        std::string r(c ? c : "");
        if (c) env->ReleaseStringUTFChars(s, c);
        return r;
    };
    return game::add_chat_message(type, str(name), str(text));
}
jint n_server_varp(JNIEnv*, jclass, jint id) { return game::server_varp(id); }
// Client-side setters (game thread only; false off it or for unknown ids).
jboolean n_set_varbit(JNIEnv*, jclass, jint id, jint value) {
    return client_thread::on_client_thread() && game::set_varbit(id, value);
}
jboolean n_set_varp(JNIEnv*, jclass, jint id, jint value) {
    return client_thread::on_client_thread() && game::set_varp(id, value);
}
jboolean n_set_varc_int(JNIEnv*, jclass, jint id, jint value) {
    return client_thread::on_client_thread() && game::set_varc_int(id, value);
}
jboolean n_set_varc_string(JNIEnv* env, jclass, jint id, jstring value) {
    if (!client_thread::on_client_thread()) return false;
    std::string latin1;
    if (value) {
        const jchar* c = env->GetStringChars(value, nullptr);
        jsize n = env->GetStringLength(value);
        for (jsize i = 0; i < n; i++) latin1.push_back(c[i] < 256 ? static_cast<char>(c[i]) : '?');
        env->ReleaseStringChars(value, c);
    }
    return game::set_varc_string(id, latin1);
}
// {varp, lsb, msb}, or null.
jintArray n_varbit_def(JNIEnv* env, jclass, jint id) {
    game::VarbitDef d;
    if (!client_thread::on_client_thread() || !game::varbit_def(id, d)) return nullptr;
    jint v[3] = {d.varp, d.lsb, d.msb};
    jintArray arr = env->NewIntArray(3);
    env->SetIntArrayRegion(arr, 0, 3, v);
    return arr;
}
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
// Object[] {int[] {id, size, combatLevel, minimapVisible, interactable, varbit, varp}, String[] {name, 5 actions}, int[] transforms or null}.
jobjectArray n_npc_def(JNIEnv* env, jclass, jint id) {
    if (!client_thread::on_client_thread()) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "getNpcDefinition must be called on the client thread");
        return nullptr;
    }
    game::NpcDef d;
    if (!game::npc_def(id, d)) return nullptr;
    jint v[7] = {d.id, d.size, d.combat_level, d.minimap_visible, d.interactable, d.varbit, d.varp};
    jintArray ints = env->NewIntArray(7);
    env->SetIntArrayRegion(ints, 0, 7, v);
    jclass string = env->FindClass("java/lang/String");
    jobjectArray strs = env->NewObjectArray(6, string, nullptr);
    for (int i = 0; i < 6; i++) {
        const std::string& t = i == 0 ? d.name : d.actions[i - 1];
        if (i > 0 && t.empty()) continue;
        jstring js = env->NewStringUTF(t.c_str());
        env->SetObjectArrayElement(strs, i, js);
        env->DeleteLocalRef(js);
    }
    jintArray tr = nullptr;
    if (!d.transforms.empty()) {
        tr = env->NewIntArray(static_cast<jsize>(d.transforms.size()));
        env->SetIntArrayRegion(tr, 0, static_cast<jsize>(d.transforms.size()), d.transforms.data());
    }
    jobjectArray res = env->NewObjectArray(3, env->FindClass("java/lang/Object"), nullptr);
    env->SetObjectArrayElement(res, 0, ints);
    env->SetObjectArrayElement(res, 1, strs);
    env->SetObjectArrayElement(res, 2, tr);
    return res;
}
// Object[] {int[] {id, sizeX, sizeY, varbit, varp}, String[] {name, 5 actions}, int[] transforms or null}.
jobjectArray n_loc_def(JNIEnv* env, jclass, jint id) {
    if (!client_thread::on_client_thread()) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "getObjectDefinition must be called on the client thread");
        return nullptr;
    }
    game::LocDef d;
    if (!game::loc_def(id, d)) return nullptr;
    jint v[5] = {d.id, d.size_x, d.size_y, d.varbit, d.varp};
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
    jintArray tr = nullptr;
    if (!d.transforms.empty()) {
        tr = env->NewIntArray(static_cast<jsize>(d.transforms.size()));
        env->SetIntArrayRegion(tr, 0, static_cast<jsize>(d.transforms.size()), d.transforms.data());
    }
    jobjectArray res = env->NewObjectArray(3, env->FindClass("java/lang/Object"), nullptr);
    env->SetObjectArrayElement(res, 0, ints);
    env->SetObjectArrayElement(res, 1, strs);
    env->SetObjectArrayElement(res, 2, tr);
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

// NPC/player snapshots: 6 ints of identity/position, then the actor state (see Native.java).
constexpr int kActorStride = 36;
void pack_actor(std::vector<jint>& v, const game::ActorState& a) {
    v.insert(v.end(), {a.animation, a.animation_frame, a.orientation, a.current_orientation, a.interacting, a.overhead_cycle});
    v.insert(v.end(), a.pose, a.pose + 8);
    v.insert(v.end(), {a.pose_animation, a.pose_frame, a.health_ratio, a.health_scale});
    v.insert(v.end(), a.hitsplats, a.hitsplats + 12);
}
jstring latin1_string(JNIEnv* env, const std::string& s);
void set_overhead(JNIEnv* env, jobjectArray arr, jsize i, const game::ActorState& a) {
    if (a.overhead_text.empty()) return;
    jstring t = latin1_string(env, a.overhead_text);
    env->SetObjectArrayElement(arr, i, t);
    env->DeleteLocalRef(t);
}

jobjectArray n_npcs(JNIEnv* env, jclass) {
    std::vector<game::Npc> npcs = client_thread::npcs();
    jsize n = static_cast<jsize>(npcs.size());
    std::vector<jint> packed;
    packed.reserve(n * kActorStride);
    jobjectArray names = env->NewObjectArray(n, env->FindClass("java/lang/String"), nullptr);
    jobjectArray overheads = env->NewObjectArray(n, env->FindClass("java/lang/String"), nullptr);
    for (jsize i = 0; i < n; i++) {
        const game::Npc& npc = npcs[i];
        packed.insert(packed.end(), {npc.index, npc.id, npc.fine_x, npc.fine_y, npc.world_x, npc.world_y});
        pack_actor(packed, npc.actor);
        jstring name = env->NewStringUTF(npc.name.c_str());
        env->SetObjectArrayElement(names, i, name);
        env->DeleteLocalRef(name);
        set_overhead(env, overheads, i, npc.actor);
    }
    jintArray ints = env->NewIntArray(n * kActorStride);
    env->SetIntArrayRegion(ints, 0, n * kActorStride, packed.data());
    jobjectArray out = env->NewObjectArray(3, env->FindClass("java/lang/Object"), nullptr);
    env->SetObjectArrayElement(out, 0, ints);
    env->SetObjectArrayElement(out, 1, names);
    env->SetObjectArrayElement(out, 2, overheads);
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

// {String[] names, String[] previous names (null when none), int[] {world, rank} pairs}, or null
// while the list isn't loaded.
jobjectArray n_friends(JNIEnv* env, jclass, jboolean ignores) {
    std::vector<game::FriendEntry> list;
    if (!game::friend_list(ignores, list)) return nullptr;
    jsize n = static_cast<jsize>(list.size());
    jclass str = env->FindClass("java/lang/String");
    jobjectArray names = env->NewObjectArray(n, str, nullptr), prevs = env->NewObjectArray(n, str, nullptr);
    std::vector<jint> wr;
    for (jsize i = 0; i < n; i++) {
        jstring t = latin1_string(env, list[i].name);
        env->SetObjectArrayElement(names, i, t);
        env->DeleteLocalRef(t);
        if (!list[i].prev.empty()) {
            t = latin1_string(env, list[i].prev);
            env->SetObjectArrayElement(prevs, i, t);
            env->DeleteLocalRef(t);
        }
        wr.push_back(list[i].world);
        wr.push_back(list[i].rank);
    }
    jintArray ints = env->NewIntArray(2 * n);
    env->SetIntArrayRegion(ints, 0, 2 * n, wr.data());
    jobjectArray out = env->NewObjectArray(3, env->FindClass("java/lang/Object"), nullptr);
    env->SetObjectArrayElement(out, 0, names);
    env->SetObjectArrayElement(out, 1, prevs);
    env->SetObjectArrayElement(out, 2, ints);
    return out;
}
jint n_friend_list_state(JNIEnv*, jclass) { return game::friend_list_state(); }
jintArray n_sub_interfaces(JNIEnv* env, jclass) {
    std::vector<jint> v;
    for (const auto& s : game::open_sub_interfaces()) v.insert(v.end(), {s.component, s.group, s.type});
    jintArray arr = env->NewIntArray(static_cast<jsize>(v.size()));
    env->SetIntArrayRegion(arr, 0, static_cast<jsize>(v.size()), v.data());
    return arr;
}
jboolean n_reload_scene(JNIEnv*, jclass) { return client_thread::on_client_thread() && game::reload_scene(); }

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
jintArray n_menu_point(JNIEnv* env, jclass) {
    int xy[2];
    if (!game::menu_point(xy[0], xy[1])) return nullptr;
    jintArray r = env->NewIntArray(2);
    env->SetIntArrayRegion(r, 0, 2, xy);
    return r;
}

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

// 7 ints per item: plane << 16 | x << 8 | y, id, quantity, visible tick, despawn tick, ownership,
// private.
jintArray n_ground_items(JNIEnv* env, jclass) {
    if (!require_client_thread(env, "groundItems")) return nullptr;
    auto items = game::ground_items();
    std::vector<jint> packed;
    packed.reserve(items.size() * 7);
    for (const auto& i : items) {
        packed.push_back(i.plane << 16 | i.x << 8 | i.y);
        packed.push_back(i.id);
        packed.push_back(i.quantity);
        packed.push_back(i.visible_tick);
        packed.push_back(i.despawn_tick);
        packed.push_back(i.ownership);
        packed.push_back(i.is_private ? 1 : 0);
    }
    jintArray out = env->NewIntArray(static_cast<jsize>(packed.size()));
    if (out && !packed.empty()) env->SetIntArrayRegion(out, 0, static_cast<jsize>(packed.size()), packed.data());
    return out;
}
void n_consume_menu_click(JNIEnv*, jclass) { client_thread::consume_menu_click(); }
namespace {
std::string to_latin1(JNIEnv* env, jstring text) {
    std::string out;
    const jchar* c = env->GetStringChars(text, nullptr);
    jsize n = env->GetStringLength(text);
    for (jsize i = 0; i < n; i++) out.push_back(c[i] < 256 ? static_cast<char>(c[i]) : '?');
    env->ReleaseStringChars(text, c);
    return out;
}
}  // namespace
jboolean n_menu_set_text(JNIEnv* env, jclass, jint index, jstring option, jstring target) {
    if (!require_client_thread(env, "menuSetText") || index < 0) return false;
    std::string o, t;
    if (option) o = to_latin1(env, option);
    if (target) t = to_latin1(env, target);
    return game::menu_set_text(static_cast<size_t>(index), option ? &o : nullptr, target ? &t : nullptr);
}


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

// Walk destination {x, y} as stored (Client +0xecdc/+0xece0; scene tiles, -1 when none).
jintArray n_walk_destination(JNIEnv* env, jclass) {
    game::Point p = game::walk_destination();
    jint v[2] = {p.x, p.y};
    jintArray arr = env->NewIntArray(2);
    env->SetIntArrayRegion(arr, 0, 2, v);
    return arr;
}

// A definition param: Integer, String, or null when the definition has no such param.
jobject n_type_param(JNIEnv* env, jclass, jint kind, jint id, jint key) {
    if (!require_client_thread(env, "typeParam")) return nullptr;
    game::TypeParam p;
    if (!game::type_param(kind, id, key, p)) return nullptr;
    if (p.is_string) return latin1_string(env, p.string_value);
    jclass integer = env->FindClass("java/lang/Integer");
    return env->CallStaticObjectMethod(integer, env->GetStaticMethodID(integer, "valueOf", "(I)Ljava/lang/Integer;"), p.int_value);
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
    jint v[11] = {c.x, c.y, c.z, c.pitch, c.yaw, c.focal_x, c.focal_y, c.focal_z,
                  c.render_base_x, c.render_base_y, c.render_wv_same};
    jintArray arr = env->NewIntArray(11);
    env->SetIntArrayRegion(arr, 0, 11, v);
    return arr;
}

// {valid, zoom, centreX, centreY, clip x0, y0, x1, y1, far, scene camera x/h/y, sin/cos pitch, sin/cos yaw x65536} (game thread only; valid 0 otherwise).
jintArray n_viewport(JNIEnv* env, jclass) {
    game::Viewport v;
    if (client_thread::on_client_thread()) v = game::viewport();
    jint a[16] = {v.valid, v.zoom, v.centre_x, v.centre_y, v.clip[0], v.clip[1], v.clip[2], v.clip[3], v.far,
                  v.scene_cam[0], v.scene_cam[1], v.scene_cam[2], v.trig[0], v.trig[1], v.trig[2], v.trig[3]};
    jintArray arr = env->NewIntArray(16);
    env->SetIntArrayRegion(arr, 0, 16, a);
    return arr;
}

jboolean n_play_sound(JNIEnv*, jclass, jint id, jint loops, jint delay) {
    return client_thread::on_client_thread() && game::play_sound_effect(id, loops, delay);
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

static uintptr_t widget_or_child(jint id, jint index) {
    uintptr_t w = game::widget(id);
    return w && index >= 0 ? game::widget_dynamic(w, index) : w;
}
jboolean n_widget_set_field(JNIEnv*, jclass, jint id, jint index, jint offset, jint value) {
    return client_thread::on_client_thread() && game::widget_set_field(id, index, static_cast<uint32_t>(offset), value);
}
jboolean n_widget_revalidate(JNIEnv*, jclass, jint id, jint index) {
    return client_thread::on_client_thread() && game::widget_revalidate(id, index);
}
jintArray n_widget_props(JNIEnv* env, jclass, jint id, jint index) {
    uintptr_t w = widget_or_child(id, index);
    if (!w) return nullptr;
    int32_t v[game::kWidgetProps];
    game::widget_props(w, v);
    jintArray arr = env->NewIntArray(game::kWidgetProps);
    env->SetIntArrayRegion(arr, 0, game::kWidgetProps, v);
    return arr;
}
// {name, targetVerb, op1..opN} (null entries for empty strings), or null.
jobjectArray n_widget_strings(JNIEnv* env, jclass, jint id, jint index) {
    uintptr_t w = widget_or_child(id, index);
    if (!w) return nullptr;
    std::vector<std::string> v = game::widget_strings(w);
    jobjectArray arr = env->NewObjectArray(static_cast<jsize>(v.size()), env->FindClass("java/lang/String"), nullptr);
    for (size_t i = 0; i < v.size(); i++)
        if (!v[i].empty()) {
            jstring js = latin1_string(env, v[i]);
            env->SetObjectArrayElement(arr, static_cast<jsize>(i), js);
            env->DeleteLocalRef(js);
        }
    return arr;
}
void n_widget_set_hidden(JNIEnv*, jclass, jint id, jint index, jboolean hidden) {
    if (client_thread::on_client_thread()) game::widget_set_hidden(widget_or_child(id, index), hidden);
}
void n_widget_set_text(JNIEnv* env, jclass, jint id, jint index, jstring text) {
    if (!client_thread::on_client_thread()) return;
    std::string latin1;
    if (text) {
        const jchar* c = env->GetStringChars(text, nullptr);
        jsize n = env->GetStringLength(text);
        for (jsize i = 0; i < n; i++) latin1.push_back(c[i] < 256 ? static_cast<char>(c[i]) : '?');
        env->ReleaseStringChars(text, c);
    }
    game::widget_set_text(widget_or_child(id, index), latin1);
}
jint n_widget_color(JNIEnv*, jclass, jint id, jint index) { return game::widget_color(widget_or_child(id, index)); }
void n_widget_set_color(JNIEnv*, jclass, jint id, jint index, jint rgb) {
    if (client_thread::on_client_thread()) game::widget_set_color(widget_or_child(id, index), rgb);
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
    std::vector<jint> packed, app;  // app: kAppearanceStride ints per player (see Native.players())
    jobjectArray names = env->NewObjectArray(n, env->FindClass("java/lang/String"), nullptr);
    jobjectArray overheads = env->NewObjectArray(n, env->FindClass("java/lang/String"), nullptr);
    for (jsize i = 0; i < n; i++) {
        const game::PlayerInfo& p = players[i];
        packed.insert(packed.end(), {p.index, p.local ? 1 : 0, p.fine_x, p.fine_y, p.world_x, p.world_y});
        pack_actor(packed, p.actor);
        jstring name = env->NewStringUTF(p.name.c_str());
        env->SetObjectArrayElement(names, i, name);
        env->DeleteLocalRef(name);
        set_overhead(env, overheads, i, p.actor);
        app.push_back(p.has_appearance ? 1 : 0);
        app.insert(app.end(), p.equipment, p.equipment + 12);
        app.insert(app.end(), p.kits, p.kits + 12);
        app.insert(app.end(), p.colors, p.colors + 5);
        app.insert(app.end(), {p.gender, p.npc_id, p.combat_level, p.skull, p.prayer, p.team});
    }
    jintArray ints = env->NewIntArray(n * kActorStride);
    env->SetIntArrayRegion(ints, 0, n * kActorStride, packed.data());
    jintArray apps = env->NewIntArray(static_cast<jsize>(app.size()));
    env->SetIntArrayRegion(apps, 0, static_cast<jsize>(app.size()), app.data());
    jobjectArray out = env->NewObjectArray(4, env->FindClass("java/lang/Object"), nullptr);
    env->SetObjectArrayElement(out, 0, ints);
    env->SetObjectArrayElement(out, 1, names);
    env->SetObjectArrayElement(out, 2, overheads);
    env->SetObjectArrayElement(out, 3, apps);
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
    {"widgetColor", "(II)I", reinterpret_cast<void*>(n_widget_color)},
    {"widgetProps", "(II)[I", reinterpret_cast<void*>(n_widget_props)},
    {"widgetSetField", "(IIII)Z", reinterpret_cast<void*>(n_widget_set_field)},
    {"widgetRevalidate", "(II)Z", reinterpret_cast<void*>(n_widget_revalidate)},
    {"widgetStrings", "(II)[Ljava/lang/String;", reinterpret_cast<void*>(n_widget_strings)},
    {"widgetSetColor", "(III)V", reinterpret_cast<void*>(n_widget_set_color)},
    {"widgetSetHidden", "(IIZ)V", reinterpret_cast<void*>(n_widget_set_hidden)},
    {"widgetSetText", "(IILjava/lang/String;)V", reinterpret_cast<void*>(n_widget_set_text)},
    {"widgetDynamicText", "(II)Ljava/lang/String;", reinterpret_cast<void*>(n_widget_dynamic_text)},
    {"widgetDynamicGeometry", "(II)[I", reinterpret_cast<void*>(n_widget_dynamic_geometry)},
    {"property", "(Ljava/lang/String;)Ljava/lang/String;", reinterpret_cast<void*>(n_property)},
    {"camera", "()[I", reinterpret_cast<void*>(n_camera)},
    {"viewport", "()[I", reinterpret_cast<void*>(n_viewport)},
    {"playSound", "(III)Z", reinterpret_cast<void*>(n_play_sound)},
    {"screenSize", "()[I", reinterpret_cast<void*>(n_screen_size)},
    {"walkDestination", "()[I", reinterpret_cast<void*>(n_walk_destination)},
    {"friends", "(Z)[Ljava/lang/Object;", reinterpret_cast<void*>(n_friends)},
    {"friendListState", "()I", reinterpret_cast<void*>(n_friend_list_state)},
    {"reloadScene", "()Z", reinterpret_cast<void*>(n_reload_scene)},
    {"subInterfaces", "()[I", reinterpret_cast<void*>(n_sub_interfaces)},
    {"typeParam", "(III)Ljava/lang/Object;", reinterpret_cast<void*>(n_type_param)},
    {"overlayBuffer", "(II)Ljava/nio/ByteBuffer;", reinterpret_cast<void*>(n_overlay_buffer)},
    {"overlayPublish", "(II)V", reinterpret_cast<void*>(n_overlay_publish)},
    {"message", "(I)[Ljava/lang/String;", reinterpret_cast<void*>(n_message)},
    {"npcs", "()[Ljava/lang/Object;", reinterpret_cast<void*>(n_npcs)},
    {"menuEntries", "()[Ljava/lang/Object;", reinterpret_cast<void*>(n_menu_entries)},
    {"menuSetText", "(ILjava/lang/String;Ljava/lang/String;)Z", reinterpret_cast<void*>(n_menu_set_text)},
    {"clickedMenuEntry", "()[Ljava/lang/Object;", reinterpret_cast<void*>(n_clicked_menu_entry)},
    {"menuOpen", "()Z", reinterpret_cast<void*>(n_menu_open)},
    {"menuPoint", "()[I", reinterpret_cast<void*>(n_menu_point)},
    {"sceneObjects", "()[I", reinterpret_cast<void*>(n_scene_objects)},
    {"groundItems", "()[I", reinterpret_cast<void*>(n_ground_items)},
    {"consumeMenuClick", "()V", reinterpret_cast<void*>(n_consume_menu_click)},
    {"gameState", "()I", reinterpret_cast<void*>(n_game_state)},
    {"gameCycle", "()I", reinterpret_cast<void*>(n_game_cycle)},
    {"serverTick", "()I", reinterpret_cast<void*>(n_server_tick)},
    {"varp", "(I)I", reinterpret_cast<void*>(n_varp)},
    {"varps", "()[I", reinterpret_cast<void*>(n_varps)},
    {"weight", "()I", reinterpret_cast<void*>(n_weight)},
    {"world", "()I", reinterpret_cast<void*>(n_world)},
    {"worldFlags", "()I", reinterpret_cast<void*>(n_world_flags)},
    {"viewportZoom", "()[I", reinterpret_cast<void*>(n_viewport_zoom)},
    {"viewTransforms", "()[F", reinterpret_cast<void*>(n_view_transforms)},
    {"dbField", "(III)[Ljava/lang/Object;", reinterpret_cast<void*>(n_db_field)},
    {"runEnergy", "()I", reinterpret_cast<void*>(n_run_energy)},
    {"actorModel", "(ZI)[I", reinterpret_cast<void*>(n_actor_model)},
    {"objectModel", "(IIIIJ)[I", reinterpret_cast<void*>(n_object_model)},
    {"varcInt", "(I)I", reinterpret_cast<void*>(n_varc_int)},
    {"varcString", "(I)Ljava/lang/String;", reinterpret_cast<void*>(n_varc_string)},
    {"setVarbit", "(II)Z", reinterpret_cast<void*>(n_set_varbit)},
    {"setVarp", "(II)Z", reinterpret_cast<void*>(n_set_varp)},
    {"setVarcInt", "(II)Z", reinterpret_cast<void*>(n_set_varc_int)},
    {"setVarcString", "(ILjava/lang/String;)Z", reinterpret_cast<void*>(n_set_varc_string)},
    {"varbitDef", "(I)[I", reinterpret_cast<void*>(n_varbit_def)},
    {"addChatMessage", "(ILjava/lang/String;Ljava/lang/String;)Z", reinterpret_cast<void*>(n_add_chat_message)},
    {"serverVarp", "(I)I", reinterpret_cast<void*>(n_server_varp)},
    {"varbit", "(I)I", reinterpret_cast<void*>(n_varbit)},
    {"playerPosition", "()[I", reinterpret_cast<void*>(n_player_position)},
    {"itemDef", "(I)[Ljava/lang/Object;", reinterpret_cast<void*>(n_item_def)},
    {"npcDef", "(I)[Ljava/lang/Object;", reinterpret_cast<void*>(n_npc_def)},
    {"locDef", "(I)[Ljava/lang/Object;", reinterpret_cast<void*>(n_loc_def)},
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
