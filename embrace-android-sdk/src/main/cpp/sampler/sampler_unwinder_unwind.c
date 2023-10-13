#include <dlfcn.h>
#include <unwind.h>
#include "sampler_unwinder_unwind.h"
#include "unwinder_dlinfo.h"
#include "../utilities.h"

#if defined(__arm__)
#include <libunwind.h>
#endif

static emb_env *env = NULL;

static _Unwind_Reason_Code emb_libunwind_sampling_callback(struct _Unwind_Context *context,
                                                           void *arg) {
    if (env != NULL && env->currently_handling) {
        return _URC_NO_REASON;
    }
    emb_unwind_state *state = (emb_unwind_state *) arg;
    uint64_t ip = _Unwind_GetIP(context);

    size_t index = state->num_sframes;
    if (index >= kEMBSampleUnwindLimit) {
        return _URC_END_OF_STACK;
    } else if (index > 0 && (void *) ip == NULL) {
        return _URC_NO_REASON;
    }

    state->stack[index] = ip;
    state->num_sframes++;
    return _URC_NO_REASON;
}

#if defined(__arm__)
static ssize_t emb_unwind_32bit_stack(emb_unwind_state *state,
                       siginfo_t *info, void *user_context) __asyncsafe {
    unw_cursor_t cursor;
    unw_context_t uc;
    int index = 0;

    if (unw_getcontext(&uc) != 0) {
        state->result = EMB_ERROR_UNW_CONTEXT_FAILED;
        return 0;
    }
    int local = unw_init_local(&cursor, &uc);
    if (local != 0) {
        state->result = EMB_ERROR_UNW_INIT_LOCAL_FAILED;
        return 0;
    }
    if (user_context != NULL) {
        const ucontext_t *signal_ucontext = (const ucontext_t *)user_context;
        const struct sigcontext *signal_mcontext = &(signal_ucontext->uc_mcontext);
        unw_set_reg(&cursor, UNW_ARM_R0, signal_mcontext->arm_r0);
        unw_set_reg(&cursor, UNW_ARM_R1, signal_mcontext->arm_r1);
        unw_set_reg(&cursor, UNW_ARM_R2, signal_mcontext->arm_r2);
        unw_set_reg(&cursor, UNW_ARM_R3, signal_mcontext->arm_r3);
        unw_set_reg(&cursor, UNW_ARM_R4, signal_mcontext->arm_r4);
        unw_set_reg(&cursor, UNW_ARM_R5, signal_mcontext->arm_r5);
        unw_set_reg(&cursor, UNW_ARM_R6, signal_mcontext->arm_r6);
        unw_set_reg(&cursor, UNW_ARM_R7, signal_mcontext->arm_r7);
        unw_set_reg(&cursor, UNW_ARM_R8, signal_mcontext->arm_r8);
        unw_set_reg(&cursor, UNW_ARM_R9, signal_mcontext->arm_r9);
        unw_set_reg(&cursor, UNW_ARM_R10, signal_mcontext->arm_r10);
        unw_set_reg(&cursor, UNW_ARM_R11, signal_mcontext->arm_fp);
        unw_set_reg(&cursor, UNW_ARM_R12, signal_mcontext->arm_ip);
        unw_set_reg(&cursor, UNW_ARM_R13, signal_mcontext->arm_sp);
        unw_set_reg(&cursor, UNW_ARM_R14, signal_mcontext->arm_lr);
        unw_set_reg(&cursor, UNW_ARM_R15, signal_mcontext->arm_pc);
        unw_set_reg(&cursor, UNW_REG_IP, signal_mcontext->arm_pc);
        unw_set_reg(&cursor, UNW_REG_SP, signal_mcontext->arm_sp);
        state->stack[index++] = signal_mcontext->arm_pc;
        state->num_sframes++;
    }

    while (unw_step(&cursor) > 0 && index < kEMBSampleUnwindLimit) {
        unw_word_t ip = 0;
        unw_get_reg(&cursor, UNW_REG_IP, &ip);
        state->stack[index++] = ip;
        state->num_sframes++;
    }
    return index;
}
#endif

static _Unwind_Reason_Code calculate_unwind_result(_Unwind_Reason_Code code) {
    return code == _URC_END_OF_STACK || code == _URC_NO_REASON ? 0 : code;
}

size_t emb_unwind_with_libunwind(emb_env *_env, emb_sample *sample, bool is32bit,
                                 siginfo_t *info, void *user_context) {
    env = _env;
    emb_unwind_state _unwind_state = {0};
    emb_unwind_state *unwind_state = &_unwind_state;
    bool unwound = false;

#if defined(__arm__)
    if (is32bit) {
        emb_unwind_32bit_stack(unwind_state, info, user_context);
        sample->result = unwind_state->result;
        unwound = true;
    }
#endif
    if (!unwound) {
        _Unwind_Reason_Code code = _Unwind_Backtrace(emb_libunwind_sampling_callback,
                                                     (void *) unwind_state);
        sample->result = calculate_unwind_result(code);
    }

    // copy frames from temporary unwind_state struct to more permament emb_sample
    // (allows truncating the stack)
    emb_copy_frames(sample, unwind_state);
    emb_symbolicate_stacktrace(sample);
    return sample->num_sframes;
}
