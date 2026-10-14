#pragma once

// GOT-hooks connect/getaddrinfo in `module` to log outgoing connections, if
// debug.osrsrl.nettrace=1.
void net_trace_init(const char* module);
