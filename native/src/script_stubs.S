// Entry stubs for inline hooks that only need to look at a function's arguments: save x0-x8 and
// the return address, call `<name>_cb(const uint64_t regs[9], uintptr_t lr)`, restore everything
// and continue in the original function through the trampoline stored in `<name>_orig`.
    .text

    .macro PREHOOK name
    .global \name\()_stub
    .hidden \name\()_stub
    .type \name\()_stub, %function
    .p2align 2
\name\()_stub:
    stp x29, x30, [sp, #-0x60]!
    mov x29, sp
    stp x0, x1, [sp, #0x10]
    stp x2, x3, [sp, #0x20]
    stp x4, x5, [sp, #0x30]
    stp x6, x7, [sp, #0x40]
    str x8, [sp, #0x50]
    add x0, sp, #0x10
    mov x1, x30
    bl \name\()_cb
    ldp x0, x1, [sp, #0x10]
    ldp x2, x3, [sp, #0x20]
    ldp x4, x5, [sp, #0x30]
    ldp x6, x7, [sp, #0x40]
    ldr x8, [sp, #0x50]
    ldp x29, x30, [sp], #0x60
    adrp x16, \name\()_orig
    ldr x16, [x16, :lo12:\name\()_orig]
    br x16
    .size \name\()_stub, . - \name\()_stub
    .endm

    PREHOOK osrsrl_script_lookup
    PREHOOK osrsrl_script_swap
