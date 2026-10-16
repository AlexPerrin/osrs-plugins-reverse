#include "crash_log.h"

#include <dlfcn.h>
#include <signal.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <sys/system_properties.h>
#include <ucontext.h>
#include <unistd.h>

#include "got_hook.h"
#include "log.h"

namespace {

uintptr_t g_game_base = 0, g_self_base = 0;
struct sigaction g_old[32];
const int kSignals[] = {SIGSEGV, SIGBUS, SIGILL, SIGFPE, SIGABRT};

// "osclient+0x1234" / "libosrsrl.so:sym+0x10" / raw address.
void describe(uintptr_t a, char* out, size_t n) {
    Dl_info info{};
    if (a && dladdr(reinterpret_cast<void*>(a), &info) && info.dli_fbase) {
        const char* f = info.dli_fname ? strrchr(info.dli_fname, '/') : nullptr;
        f = f ? f + 1 : (info.dli_fname ? info.dli_fname : "?");
        uintptr_t off = a - reinterpret_cast<uintptr_t>(info.dli_fbase);
        if (info.dli_sname)
            snprintf(out, n, "%s+0x%lx (%s+0x%lx)", f, static_cast<unsigned long>(off), info.dli_sname,
                     static_cast<unsigned long>(a - reinterpret_cast<uintptr_t>(info.dli_saddr)));
        else
            snprintf(out, n, "%s+0x%lx", f, static_cast<unsigned long>(off));
        return;
    }
    snprintf(out, n, "0x%lx", static_cast<unsigned long>(a));
}

bool readable(uintptr_t p) { return p && (p & 7) == 0 && p > 0x10000 && p < (1ull << 48); }

uintptr_t module_of(uintptr_t a) {
    Dl_info info{};
    return a && dladdr(reinterpret_cast<void*>(a), &info) ? reinterpret_cast<uintptr_t>(info.dli_fbase) : 0;
}

void handler(int sig, siginfo_t* si, void* uc_v) {
    static volatile int once = 0;
    bool ours = false;
#if defined(__aarch64__)
    {
        uintptr_t mod = module_of(static_cast<ucontext_t*>(uc_v)->uc_mcontext.pc);
        ours = mod && (mod == g_game_base || mod == g_self_base);
    }
#endif
    // Only faults in the game library or ours are logged; everything else goes straight on.
    if (ours && !once) {
        once = 1;
        char a[160], b[160];
#if defined(__aarch64__)
        auto* uc = static_cast<ucontext_t*>(uc_v);
        auto& m = uc->uc_mcontext;
        describe(m.pc, a, sizeof a);
        describe(m.regs[30], b, sizeof b);
        LOGE("crash: signal %d code %d fault 0x%lx tid %d", sig, si->si_code,
             static_cast<unsigned long>(reinterpret_cast<uintptr_t>(si->si_addr)), gettid());
        LOGE("crash: pc %s", a);
        LOGE("crash: lr %s", b);
        for (int i = 0; i < 30; i += 3)
            LOGE("crash: x%-2d %016lx x%-2d %016lx x%-2d %016lx", i, static_cast<unsigned long>(m.regs[i]), i + 1,
                 static_cast<unsigned long>(m.regs[i + 1]), i + 2, static_cast<unsigned long>(m.regs[i + 2]));
        LOGE("crash: sp %016lx", static_cast<unsigned long>(m.sp));
        // Frame-pointer chain (x29): {prev fp, return address}.
        uintptr_t fp = m.regs[29];
        for (int i = 0; i < 24 && readable(fp); i++) {
            auto* f = reinterpret_cast<uintptr_t*>(fp);
            if (!f[1]) break;
            describe(f[1], a, sizeof a);
            LOGE("crash: #%02d %s", i, a);
            if (f[0] <= fp) break;
            fp = f[0];
        }
        LOGE("crash: game lib base 0x%lx (offsets above are file offsets; Ghidra = +0x100000)",
             static_cast<unsigned long>(g_game_base));
#else
        (void)uc_v;
        LOGE("crash: signal %d fault %p", sig, si->si_addr);
#endif
    }
    // Always hand over to the previous handler (debuggerd, the runtime's chain); never swallow.
    const struct sigaction& o = g_old[sig];
    if (o.sa_flags & SA_SIGINFO) {
        if (o.sa_sigaction) { o.sa_sigaction(sig, si, uc_v); return; }
    } else if (o.sa_handler != SIG_DFL && o.sa_handler != SIG_IGN) {
        o.sa_handler(sig);
        return;
    }
    // No previous handler: default action (faults re-trigger on return, others are raised again).
    signal(sig, SIG_DFL);
    if (sig == SIGABRT || sig == SIGFPE) raise(sig);
}

}  // namespace

void crash_log_install(const char* game_lib) {
    // Off by default: `setprop debug.osrsrl.crashlog 1`, then restart the game.
    char on[PROP_VALUE_MAX] = {};
    if (__system_property_get("debug.osrsrl.crashlog", on) <= 0 || on[0] != '1') return;
    g_game_base = module_base(game_lib);
    g_self_base = module_of(reinterpret_cast<uintptr_t>(&crash_log_install));
    for (int s : kSignals) {
        struct sigaction sa{};
        sa.sa_sigaction = handler;
        sa.sa_flags = SA_SIGINFO | SA_ONSTACK | SA_RESTART;
        sigemptyset(&sa.sa_mask);
        sigaction(s, &sa, &g_old[s]);
    }
    LOGI("crash log installed");
}
