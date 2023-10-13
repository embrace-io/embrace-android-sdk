#include <stddef.h>
#include <stdlib.h>
#include <signal.h>
#include <errno.h>
#include <string.h>
#include "signal_utils.h"
#include "../emb_log.h"

#define EMB_ALT_STACK_SIZE SIGSTKSZ * 2

static char emb_alt_stack[EMB_ALT_STACK_SIZE] = {0};

bool emb_sig_stk_setup(stack_t stack) {
    stack.ss_sp = &emb_alt_stack;
    stack.ss_size = EMB_ALT_STACK_SIZE;
    stack.ss_flags = 0;
    if (sigaltstack(&stack, 0) < 0) {
        EMB_LOGWARN("Sig Stack set failed: %s", strerror(errno));
        return false;
    }
    return true;
}

void emb_trigger_prev_handler(int signum, siginfo_t *info, void *user_context, struct sigaction prev_handler) {
    if (prev_handler.sa_flags & SA_SIGINFO) {
        prev_handler.sa_sigaction(signum, info, user_context);
    } else if (prev_handler.sa_handler == SIG_DFL) {
        raise(signum);
    } else if (prev_handler.sa_handler != SIG_IGN) {
        void (*prev_func)(int) = prev_handler.sa_handler;
        prev_func(signum);
    }
}
