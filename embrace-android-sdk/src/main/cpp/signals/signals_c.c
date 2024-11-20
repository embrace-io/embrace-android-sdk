//
// Created by Eric Lanz on 5/5/20.
//

#include <sys/types.h>
#include <stdlib.h>
#include <pthread.h>
#include <signal.h>
#include <errno.h>
#include <string.h>
#include <unistd.h>
#include <dlfcn.h>
#include "signals_c.h"
#include "../unwinders/unwinder.h"
#include "../utils/utilities.h"
#include "../crashmarker/file_marker.h"
#include "../serializer/file_writer.h"
#include "signal_utils.h"
#include "../utils/emb_log.h"
#include "../utils/string_utils.h"

#define EMB_SIG_HANDLER_COUNT 6

struct emb_sig_handler_entry {
    int signum;
    char *sig_name;
    char *sig_msg;
    struct sigaction action;
    struct sigaction prev_action;
};

struct emb_sig_handler_entry handler_entries[EMB_SIG_HANDLER_COUNT] = {
        {SIGILL,  "SIGILL",  "Illegal instruction",   {0}, {0}},
        {SIGTRAP, "SIGTRAP", "Trace/breakpoint trap", {0}, {0}},
        {SIGABRT, "SIGABRT", "Abort program",         {0}, {0}},
        {SIGBUS,  "SIGBUS",  "Memory error",          {0}, {0}},
        {SIGFPE,  "SIGFPE",  "FP exception",          {0}, {0}},
        {SIGSEGV, "SIGSEGV", "Segmentation fault",    {0}, {0}},
};

// ref to global state
static volatile emb_env *_emb_env = NULL;

// unwind stack
static stack_t emb_sig_stack = {0};

static struct emb_sig_handler_entry *find_handler_by_signum(const int signum) {
    for (int k = 0; k < EMB_SIG_HANDLER_COUNT; ++k) {
        if (signum == handler_entries[k].signum) {
            return &handler_entries[k];
        }
    }
    return NULL;
}

void emb_remove_c_sig_handlers() {
    if (!_emb_env) {
        return;
    }
    for (int k = 0; k < EMB_SIG_HANDLER_COUNT; k++) {
        struct emb_sig_handler_entry *entry = &handler_entries[k];
        sigaction(entry->signum, &entry->prev_action, NULL);
    }
}

static inline void invoke_prev_sigaction(int signum, siginfo_t *info,
                                         void *user_context) __asyncsafe {
    emb_remove_c_sig_handlers();
    struct emb_sig_handler_entry *entry = find_handler_by_signum(signum);
    if (entry != NULL && _emb_env != NULL) {
        _emb_env = NULL;
        emb_trigger_prev_handler(signum, info, user_context, entry->prev_action);
    }
    _emb_env = NULL;
}

void emb_handle_signal(int signum, siginfo_t *info, void *user_context) __asyncsafe {
    emb_env *env = (emb_env *) _emb_env;
    if (env == NULL) {
        emb_log_last_error(env, EMB_ERROR_C_SIGNAL_HANDLER_NOT_INSTALLED, 0);
        return;
    }
    if (env->currently_handling) {
        if (env->already_handled_crash) {
            invoke_prev_sigaction(signum, info, user_context);
        }
        return;
    }
    env->currently_handling = true;

    emb_set_crash_time(env);

    env->crash.unhandled = true;
    env->crash.sig_code = info->si_code;
    env->crash.sig_errno = info->si_errno;
    env->crash.sig_no = info->si_signo;
    env->crash.fault_addr = (uintptr_t) info->si_addr;
    env->crash.unhandled_count++;
    env->crash.capture.num_sframes = emb_process_capture(env, info, user_context);

    struct emb_sig_handler_entry *entry = find_handler_by_signum(signum);

    if (entry != NULL) {
        emb_strncpy(env->crash.capture.name,
                    entry->sig_name,
                    sizeof(env->crash.capture.name));
        emb_strncpy(env->crash.capture.message,
                    entry->sig_msg,
                    sizeof(env->crash.capture.message));
    }

    emb_write_crash_to_file(env);

    // Used to determine during the next launch if we crashed on the previous launch.
    emb_write_crash_marker_file(env, CRASH_MARKER_SOURCE_SIGNAL);

    if (env->err_fd > 0) {
        close(env->err_fd);
    }
    invoke_prev_sigaction(signum, info, user_context);
}

bool emb_install_signal_handlers() {
    if (!emb_sig_stk_setup(emb_sig_stack)) {
        return false;
    }
    for (int k = 0; k < EMB_SIG_HANDLER_COUNT; k++) {
        struct emb_sig_handler_entry *entry = &handler_entries[k];
        struct sigaction *action = &entry->action;

        // prepare for sigaction
        sigemptyset(&action->sa_mask);
        action->sa_sigaction = emb_handle_signal;
        action->sa_flags = SA_SIGINFO | SA_ONSTACK;

        struct sigaction *old_action = &entry->prev_action;
        int success = sigaction(entry->signum, action, old_action);
        if (success != 0) {
            EMB_LOGWARN("Sig install failed: %s", strerror(errno));
            return false;
        }
    }
    return true;
}

bool emb_setup_c_signal_handlers(emb_env *env) {
    bool result = true;
    static pthread_mutex_t _emb_signal_mutex = PTHREAD_MUTEX_INITIALIZER;
    pthread_mutex_lock(&_emb_signal_mutex);

    if (_emb_env) {
        EMB_LOGINFO("c handler already installed.");
    } else {
        _emb_env = env;
        result = emb_install_signal_handlers();
    }
    pthread_mutex_unlock(&_emb_signal_mutex);
    return result;
}
