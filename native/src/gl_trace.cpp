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

// Uniform-buffer capture: the scene camera lives in the ViewTransforms UBO (std140: vec3 camera
// position @0, view @16, inverse view @80, projection @144, view-projection @208, ...). Uploads
// are scanned for a perspective matrix (zeros off the diagonal, m[11] = +-1, m[15] = 0).
using BufferSubDataFn = void (*)(GLenum, GLintptr, GLsizeiptr, const void*);
using BufferDataFn = void (*)(GLenum, GLsizeiptr, const void*, GLenum);
using MapBufferRangeFn = void* (*)(GLenum, GLintptr, GLsizeiptr, GLbitfield);
using FlushMappedFn = void (*)(GLenum, GLintptr, GLsizeiptr);
using UnmapBufferFn = GLboolean (*)(GLenum);
BufferSubDataFn g_orig_subdata = nullptr;
BufferDataFn g_orig_data = nullptr;
MapBufferRangeFn g_orig_map = nullptr;
FlushMappedFn g_orig_flush = nullptr;
UnmapBufferFn g_orig_unmap = nullptr;
std::map<GLenum, std::pair<const uint8_t*, size_t>> g_mapped;
uint64_t g_calls[5] = {};
struct Ubo {
    float block[100] = {};  // from the camera position (projection offset - 144 bytes)
    bool valid = false, full = false;
    const char* via = "";
    size_t offset = 0, size = 0;
    uint64_t found = 0;
} g_ubo;

bool near0(float f) { return f > -1e-4f && f < 1e-4f; }

void scan(const void* data, size_t size, const char* via) {
    if (!data || size < 64) return;
    auto* f = static_cast<const float*>(data);
    size_t n = size / 4;
    for (size_t i = 0; i + 16 <= n; i += 4) {
        const float* m = f + i;
        if (!(m[0] > 0.05f && m[5] > 0.05f && m[0] < 100 && m[5] < 100)) continue;
        auto one = [](float f) { return (f > 0.999f && f < 1.001f) || (f < -0.999f && f > -1.001f); };
        bool col = near0(m[1]) && near0(m[2]) && near0(m[3]) && near0(m[4]) && near0(m[6]) && near0(m[7]) &&
                   near0(m[12]) && near0(m[13]) && near0(m[15]) && one(m[11]);
        bool row = near0(m[1]) && near0(m[3]) && near0(m[4]) && near0(m[7]) && near0(m[8]) && near0(m[9]) &&
                   near0(m[12]) && near0(m[13]) && near0(m[15]) && one(m[14]);
        if (!col && !row) continue;
        std::lock_guard lock(g_mutex);
        g_ubo = {};
        g_ubo.valid = true;
        g_ubo.via = via;
        g_ubo.offset = i * 4;
        g_ubo.size = size;
        if (i >= 36 && i - 36 + 100 <= n) {
            for (int k = 0; k < 100; k++) g_ubo.block[k] = f[i - 36 + k];
            g_ubo.full = true;
        } else {
            for (int k = 0; k < 16; k++) g_ubo.block[36 + k] = m[k];
        }
        g_ubo.found++;
        return;
    }
}

void hook_subdata(GLenum target, GLintptr offset, GLsizeiptr size, const void* data) {
    g_calls[0]++;
    scan(data, static_cast<size_t>(size), "subdata");
    g_orig_subdata(target, offset, size, data);
}

void hook_data(GLenum target, GLsizeiptr size, const void* data, GLenum usage) {
    g_calls[1]++;
    scan(data, static_cast<size_t>(size), "data");
    g_orig_data(target, size, data, usage);
}

void* hook_map(GLenum target, GLintptr offset, GLsizeiptr length, GLbitfield access) {
    g_calls[2]++;
    void* p = g_orig_map(target, offset, length, access);
    if (p && (access & GL_MAP_WRITE_BIT)) g_mapped[target] = {static_cast<const uint8_t*>(p), static_cast<size_t>(length)};
    return p;
}

void hook_flush(GLenum target, GLintptr offset, GLsizeiptr length) {
    g_calls[3]++;
    auto it = g_mapped.find(target);
    if (it != g_mapped.end() && static_cast<size_t>(offset + length) <= it->second.second)
        scan(it->second.first + offset, static_cast<size_t>(length), "flush");
    g_orig_flush(target, offset, length);
}

std::map<size_t, uint64_t> g_unmap_sizes;
GLboolean hook_unmap(GLenum target) {
    g_calls[4]++;
    {
        auto it2 = g_mapped.find(target);
        if (it2 != g_mapped.end()) g_unmap_sizes[it2->second.second * 0x10000 + target]++;
    }
    auto it = g_mapped.find(target);
    if (it != g_mapped.end()) {
        static auto last_big = std::chrono::steady_clock::now();
        auto now = std::chrono::steady_clock::now();
        if (it->second.second <= (1u << 20)) scan(it->second.first, it->second.second, "unmap");
        else if (now - last_big > std::chrono::milliseconds(500)) {
            last_big = now;
            scan(it->second.first, it->second.second, target == GL_ARRAY_BUFFER ? "unmap-big-array" : "unmap-big");
        }
        g_mapped.erase(it);
    }
    return g_orig_unmap(target);
}

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
    g_orig_subdata = reinterpret_cast<BufferSubDataFn>(got_hook(module, "glBufferSubData", reinterpret_cast<void*>(hook_subdata)));
    g_orig_data = reinterpret_cast<BufferDataFn>(got_hook(module, "glBufferData", reinterpret_cast<void*>(hook_data)));
    g_orig_map = reinterpret_cast<MapBufferRangeFn>(got_hook(module, "glMapBufferRange", reinterpret_cast<void*>(hook_map)));
    g_orig_flush = reinterpret_cast<FlushMappedFn>(got_hook(module, "glFlushMappedBufferRange", reinterpret_cast<void*>(hook_flush)));
    g_orig_unmap = reinterpret_cast<UnmapBufferFn>(got_hook(module, "glUnmapBuffer", reinterpret_cast<void*>(hook_unmap)));
    LOGI("gl trace: hooks subdata %d data %d map %d flush %d unmap %d", g_orig_subdata != nullptr, g_orig_data != nullptr,
         g_orig_map != nullptr, g_orig_flush != nullptr, g_orig_unmap != nullptr);
    if (!g_orig_subdata) g_orig_subdata = glBufferSubData;
    if (!g_orig_data) g_orig_data = glBufferData;
    if (!g_orig_map) g_orig_map = glMapBufferRange;
    if (!g_orig_flush) g_orig_flush = glFlushMappedBufferRange;
    if (!g_orig_unmap) g_orig_unmap = glUnmapBuffer;
    if (!g_orig_matrix) g_orig_matrix = glUniformMatrix4fv;
    if (!g_orig_viewport) g_orig_viewport = glViewport;
    g_enabled = true;
    LOGI("gl trace enabled");
}

bool gl_view_transforms(float out[100]) {
    std::lock_guard lock(g_mutex);
    if (!g_ubo.valid || !g_ubo.full) return false;
    for (int i = 0; i < 100; i++) out[i] = g_ubo.block[i];
    return true;
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
    LOGI("gltrace: calls subdata %llu data %llu map %llu flush %llu unmap %llu; ubo found %llu via %s offset %zu size %zu full %d",
         (unsigned long long)g_calls[0], (unsigned long long)g_calls[1], (unsigned long long)g_calls[2],
         (unsigned long long)g_calls[3], (unsigned long long)g_calls[4], (unsigned long long)g_ubo.found, g_ubo.via,
         g_ubo.offset, g_ubo.size, g_ubo.full);
    {
        char buf[512];
        int o = snprintf(buf, sizeof buf, "gltrace: unmap sizes:");
        for (auto& [k, c] : g_unmap_sizes) {
            if (o < 0 || o >= (int)sizeof buf - 40) break;
            int written = snprintf(buf + o, sizeof buf - static_cast<size_t>(o), " %zu@%#zx x%llu", k / 0x10000,
                                   k % 0x10000, (unsigned long long)c);
            if (written < 0 || written >= (int)sizeof buf - o) break;
            o += written;
        }
        LOGI("%s", buf);
    }
    if (g_ubo.valid) {
        for (int row = 0; row < 7; row++) {
            char buf[512];
            int o = snprintf(buf, sizeof buf, "gltrace: ubo[%d..]:", row * 16);
            for (int i = row * 16; i < row * 16 + 16 && i < 100; i++) {
                if (o < 0 || o >= (int)sizeof buf) break;
                int written = snprintf(buf + o, sizeof buf - static_cast<size_t>(o), " %.6g", g_ubo.block[i]);
                if (written < 0 || written >= (int)sizeof buf - o) break;
                o += written;
            }
            LOGI("%s", buf);
        }
    }
    for (auto& [key, e] : g_matrices) {
        char buf[512];
        int o = snprintf(buf, sizeof buf, "gltrace: prog %d loc %d n %d t %d calls %llu:", key.first, key.second, e.count,
                         e.transpose, (unsigned long long)e.calls);
        for (int i = 0; i < 16; i++) {
            if (o < 0 || o >= (int)sizeof buf - 16) break;
            int written = snprintf(buf + o, sizeof buf - static_cast<size_t>(o), " %.5g", e.m[i]);
            if (written < 0 || written >= (int)sizeof buf - o) break;
            o += written;
        }
        LOGI("%s", buf);
    }
}
