#pragma once
#include <cstddef>
#include <cstdint>

// Redirects a dynamic import of the first loaded module whose path contains
// `module_substr` by rewriting its GOT slot(s). Returns the previous target, or
// nullptr if the module or symbol was not found.
void* got_hook(const char* module_substr, const char* symbol, void* replacement);

// Load base of the first module whose path contains `module_substr`, or 0.
uintptr_t module_base(const char* module_substr);
