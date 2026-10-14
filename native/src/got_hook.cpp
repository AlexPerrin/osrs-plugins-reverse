#include <cstdint>
#include <cstring>
#include <elf.h>
#include <link.h>
#include <sys/mman.h>
#include <unistd.h>

#include "got_hook.h"
#include "log.h"

namespace {

struct ModuleInfo {
    const char* substr;
    uintptr_t base = 0;
    const ElfW(Phdr)* phdr = nullptr;
    int phnum = 0;
};

int find_module_cb(dl_phdr_info* info, size_t, void* data) {
    auto* m = static_cast<ModuleInfo*>(data);
    if (info->dlpi_name && strstr(info->dlpi_name, m->substr)) {
        m->base = info->dlpi_addr;
        m->phdr = info->dlpi_phdr;
        m->phnum = info->dlpi_phnum;
        return 1;
    }
    return 0;
}

bool find_module(ModuleInfo& m) {
    dl_iterate_phdr(find_module_cb, &m);
    return m.base != 0;
}

bool write_slot(uintptr_t slot, void* value) {
    long page = sysconf(_SC_PAGESIZE);
    uintptr_t start = slot & ~(uintptr_t)(page - 1);
    // The GOT normally lives in PT_GNU_RELRO (read-only after relocation).
    if (mprotect(reinterpret_cast<void*>(start), page, PROT_READ | PROT_WRITE) != 0) {
        LOGE("mprotect(%p) failed", reinterpret_cast<void*>(start));
        return false;
    }
    __atomic_store_n(reinterpret_cast<void**>(slot), value, __ATOMIC_SEQ_CST);
    mprotect(reinterpret_cast<void*>(start), page, PROT_READ);
    __builtin___clear_cache(reinterpret_cast<char*>(slot), reinterpret_cast<char*>(slot + sizeof(void*)));
    return true;
}

}  // namespace

uintptr_t module_base(const char* module_substr) {
    ModuleInfo m{module_substr};
    return find_module(m) ? m.base : 0;
}

void* got_hook(const char* module_substr, const char* symbol, void* replacement) {
    ModuleInfo m{module_substr};
    if (!find_module(m)) {
        LOGE("got_hook: module %s not loaded", module_substr);
        return nullptr;
    }

    const ElfW(Dyn)* dyn = nullptr;
    for (int i = 0; i < m.phnum; i++)
        if (m.phdr[i].p_type == PT_DYNAMIC) dyn = reinterpret_cast<const ElfW(Dyn)*>(m.base + m.phdr[i].p_vaddr);
    if (!dyn) return nullptr;

    const ElfW(Sym)* symtab = nullptr;
    const char* strtab = nullptr;
    const ElfW(Rela)* jmprel = nullptr;
    size_t jmprel_sz = 0;
    const ElfW(Rela)* rela = nullptr;
    size_t rela_sz = 0;
    for (const ElfW(Dyn)* d = dyn; d->d_tag != DT_NULL; d++) {
        switch (d->d_tag) {
            // Bionic's linker leaves these as unrelocated vaddrs.
            case DT_SYMTAB: symtab = reinterpret_cast<const ElfW(Sym)*>(m.base + d->d_un.d_ptr); break;
            case DT_STRTAB: strtab = reinterpret_cast<const char*>(m.base + d->d_un.d_ptr); break;
            case DT_JMPREL: jmprel = reinterpret_cast<const ElfW(Rela)*>(m.base + d->d_un.d_ptr); break;
            case DT_PLTRELSZ: jmprel_sz = d->d_un.d_val; break;
            case DT_RELA: rela = reinterpret_cast<const ElfW(Rela)*>(m.base + d->d_un.d_ptr); break;
            case DT_RELASZ: rela_sz = d->d_un.d_val; break;
        }
    }
    if (!symtab || !strtab) return nullptr;

    void* previous = nullptr;
    auto scan = [&](const ElfW(Rela)* table, size_t size) {
        if (!table) return;
        for (size_t i = 0; i < size / sizeof(ElfW(Rela)); i++) {
            uint32_t type = ELF64_R_TYPE(table[i].r_info);
            if (type != R_AARCH64_JUMP_SLOT && type != R_AARCH64_GLOB_DAT && type != R_AARCH64_ABS64) continue;
            uint32_t sym = ELF64_R_SYM(table[i].r_info);
            if (sym == 0 || strcmp(strtab + symtab[sym].st_name, symbol) != 0) continue;
            uintptr_t slot = m.base + table[i].r_offset;
            void* current = *reinterpret_cast<void**>(slot);
            if (current == replacement) continue;
            if (!previous) previous = current;
            if (write_slot(slot, replacement)) LOGI("got_hook: %s slot %p patched", symbol, reinterpret_cast<void*>(slot));
        }
    };
    scan(jmprel, jmprel_sz);
    scan(rela, rela_sz);
    if (!previous) LOGE("got_hook: %s not imported by %s", symbol, module_substr);
    return previous;
}
