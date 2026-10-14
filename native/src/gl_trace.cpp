#include "gl_trace.h"

#include <GLES3/gl3.h>
#include <sys/system_properties.h>

#include <chrono>
#include <cstdio>
#include <map>
#include <mutex>
#include <string>
#include <utility>

#include "got_hook.h"
#include "log.h"

namespace {

using UniformMatrix4fvFn = void (*)(GLint, GLsizei, GLboolean, const GLfloat*);
using ViewportFn = void (*)(GLint, GLint, GLsizei, GLsizei);
UniformMatrix4fvFn g_orig_matrix = nullptr;
ViewportFn g_orig_viewport = nullptr;

struct Entry {
    GLsizei count = 0;
    GLboolean transpose = 0;
    GLfloat m[16] = {};
    uint64_t calls = 0;
};
std::mutex g_mutex;
std::map<std::pair<GLint, GLint>, Entry> g_matrices;  // (program, location) -> last upload
GLint g_viewport[4] = {};
bool g_enabled = false;

void hook_matrix(GLint location, GLsizei count, GLboolean transpose, const GLfloat* value) {
    GLint program = 0;
    glGetIntegerv(GL_CURRENT_PROGRAM, &program);
    {
        std::lock_guard lock(g_mutex);
        Entry& e = g_matrices[{program, location}];
        e.count = count;
        e.transpose = transpose;
        for (int i = 0; i < 16; i++) e.m[i] = value[i];
        e.calls++;
    }
    g_orig_matrix(location, count, transpose, value);
}

void hook_viewport(GLint x, GLint y, GLsizei w, GLsizei h) {
    {
        std::lock_guard lock(g_mutex);
        g_viewport[0] = x; g_viewport[1] = y; g_viewport[2] = w; g_viewport[3] = h;
    }
    g_orig_viewport(x, y, w, h);
}

}  // namespace

void gl_trace_init(const char* module) {
    char value[PROP_VALUE_MAX] = "";
    if (__system_property_get("debug.osrsrl.gltrace", value) <= 0 || value[0] != '1') return;
    g_orig_matrix = reinterpret_cast<UniformMatrix4fvFn>(
        got_hook(module, "glUniformMatrix4fv", reinterpret_cast<void*>(hook_matrix)));
    g_orig_viewport = reinterpret_cast<ViewportFn>(got_hook(module, "glViewport", reinterpret_cast<void*>(hook_viewport)));
    if (!g_orig_matrix) g_orig_matrix = glUniformMatrix4fv;
    if (!g_orig_viewport) g_orig_viewport = glViewport;
    g_enabled = true;
    LOGI("gl trace enabled");
}

void gl_trace_frame() {
    if (!g_enabled) return;
    static auto next = std::chrono::steady_clock::now();
    auto now = std::chrono::steady_clock::now();
    if (now < next) return;
    next = now + std::chrono::seconds(5);
    std::lock_guard lock(g_mutex);
    LOGI("gltrace: viewport %d %d %d %d, %zu matrices", g_viewport[0], g_viewport[1], g_viewport[2], g_viewport[3],
         g_matrices.size());
    for (auto& [key, e] : g_matrices) {
        char buf[512];
        int o = snprintf(buf, sizeof buf, "gltrace: prog %d loc %d n %d t %d calls %llu:", key.first, key.second, e.count,
                         e.transpose, (unsigned long long)e.calls);
        for (int i = 0; i < 16 && o < (int)sizeof buf - 16; i++) o += snprintf(buf + o, sizeof buf - o, " %.5g", e.m[i]);
        LOGI("%s", buf);
    }
}
