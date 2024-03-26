#include <sys/types.h>
#include <unistd.h>
#include <sys/syscall.h>
#include <string.h>
#include <stdlib.h>
#include <pthread.h>
#include <errno.h>
#include "../unwinders/unwinder_stack.h"
#include "sampler_unwinder_unwind.h"
#include "sampler_unwinder_stack.h"
#include "../signals/signal_utils.h"
#include "../utilities.h"
#include "stacktrace_sampler.h"
#include "../emb_log.h"
#include "../utils/system_clock.h"
#include "emb_timer.h"

/* The unwinder - by default libunwind is used */
static volatile enum sample_unwinder_type unwind_type = LIBUNWIND;

/* Whether the architecture is 32 bit or not */
static volatile bool is32bit;

/* Struct which holds the samples for the interval. */
static emb_interval _interval = {0};
static volatile emb_interval *interval = &_interval;
static volatile bool is_thread_sampler_started = false;

/* Handle to the thread which we want to sample. */
static volatile pthread_t target_thread = -1;

/* The global embrace environment. This may be null if NDK error reporting hasn't been enabled. */
static emb_env *env = NULL;

/* The current signal handler for EMB_TARGET_THREAD_SIGNUM */
static struct sigaction _handler = {0};
static struct sigaction *handler = &_handler;

/* The previous signal handler for EMB_TARGET_THREAD_SIGNUM, if any was set. */
static struct sigaction _prev_handler = {0};
static struct sigaction *prev_handler = &_prev_handler;

/* Holds information about the timer action */
static struct sigevent timer_action = {0};

/* Holds the ID of the current timer */
static timer_t timer_id = NULL;

/* Holds information about the timer interval + delay */
static struct itimerspec timerspec = {0};

/* alternative stack to avoid overflow when unwinding */
static stack_t emb_sample_stack = {0};

emb_interval *emb_current_interval() {
    return interval;
}

emb_sample *emb_current_sample() {
    return &interval->samples[interval->num_samples];
}

static void emb_remove_stackframes(int result, emb_sample *sample) {
    // serialize the first stackframe so we can see where things went wrong.
    sample->num_sframes = 1;
    sample->result = result;
}

static void emb_process_captured_sample(emb_sample *sample) {
    if (sample->num_sframes <= 1) {
        return;
    }

    // avoid serializing useless frames reported by libunwindstack
    if (sample->result == EMB_ERROR_UNWIND_STACK_FAILURE) {
        emb_remove_stackframes(EMB_ERROR_UNWIND_STACK_FAILURE, sample);
        return;
    }

    // avoid serializing useless frames from unwinders stuck in an infinite loop
    for (int k = 1; k < sample->num_sframes; k++) {
        volatile emb_sample_stackframe *prev_frame = &sample->stack[k - 1];
        volatile emb_sample_stackframe *frame = &sample->stack[k];

        if (frame->pc != prev_frame->pc) {
            return;
        }
    }
    emb_remove_stackframes(EMB_UNWIND_INFINITE_LOOP, sample);
}

/**
 * Samples the target thread by attempting to gather a sample.
 */
static void emb_sample_target_thread(siginfo_t *info, void *user_context, emb_sample *sample) {
    switch (unwind_type) {
        case LIBUNWIND:
            emb_unwind_with_libunwind(env, sample, is32bit, info, user_context);
            break;
        case LIBUNWINDSTACK:
            emb_unwind_with_libunwindstack(env, sample, user_context);
            break;
        default:
            sample->result = EMB_UNKNOWN_UNWIND_TYPE;
            emb_log_last_error(env, EMB_UNKNOWN_UNWIND_TYPE, unwind_type);
    }

    // check we captured something sensible.
    emb_process_captured_sample(sample);
}

static void emb_start_sample(emb_sample *sample) {
    int64_t timestamp = sample->timestamp_ms;
    memset((void *) sample, 0, sizeof(emb_sample));
    sample->timestamp_ms = timestamp;

    // This shouldn't happen, but let's be extra paranoid and set an error code that will show up
    // if there is a data race between emb_fetch_sample() and other C code.
    // This could explains some (but not all) of the unusual stacktraces.
    sample->result = EMB_SAMPLE_DATA_RACE;
}

static void emb_end_sample(emb_sample *sample) {
    // Reset an error code that will show up if there is a data race between emb_fetch_sample()
    // and other C code, that was set in emb_start_sample().
    if (sample->result == EMB_SAMPLE_DATA_RACE) {
        sample->result = 0;
    }

    // record timestamp
    int64_t now = emb_get_time_ms();
    sample->duration_ms = now - sample->timestamp_ms;
    interval->num_samples++;
}

static bool is_installed() {
    return env != NULL;
}

static bool has_reached_sample_limit() {
    return !is_installed() || interval->num_samples >= kEMBMaxSamples;
}

/**
 * Handles EMB_TARGET_THREAD_SIGNUM signals sent on the target thread.
 */
static void emb_handle_target_signal(int signum, siginfo_t *info, void *user_context) __asyncsafe {
    if (has_reached_sample_limit()) {
        return;
    }

    // make a best effort to avoid sampling in the case of a fatal signal.
    emb_sample *sample = emb_current_sample();
    if (env != NULL && !env->currently_handling) {
        emb_start_sample(sample);
        emb_sample_target_thread(info, user_context, sample);
    }
    emb_end_sample(sample);

    // ignore the previous handler. We don't want to end in an infinite loop if that
    // handler raises SIGUSR2.
}

/**
 * Installs a signal handler for EMB_TARGET_THREAD_SIGNUM. This should be called on the
 * target thread - the caller is responsible for enforcing this.
 *
 * @return true if the handler was installed, otherwise false.
 */
static bool emb_install_signal_handler() {
    EMB_LOGDEV("Setting up signal handler for EMB_TARGET_THREAD_SIGNUM.");

    bool result = true;
    EMB_LOGDEV("Populating handler with information.");
    handler->sa_sigaction = emb_handle_target_signal;
    handler->sa_flags = SA_SIGINFO | SA_ONSTACK;

    // block other SIGUSR2 signals when the handler is already executing
    // to avoid reentrant calls
    // https://www.gnu.org/software/libc/manual/html_node/Blocking-for-Handler.html
    sigemptyset(&handler->sa_mask);
    sigaddset(&handler->sa_mask, EMB_TARGET_THREAD_SIGNUM);

    const int signal = EMB_TARGET_THREAD_SIGNUM;
    int success = sigaction(signal, handler, prev_handler);
    if (success != 0) {
        EMB_LOGERROR("Sig install failed: %s", strerror(errno));
        result = false;
    } else {
        EMB_LOGDEV("Successfully installed handler for EMB_TARGET_THREAD_SIGNUM.");
    }
    return result;
}

void emb_set_unwinder(int unwinder) {
    EMB_LOGDEV("Called emb_set_unwinder(), unwinder=%d", unwinder);
    unwind_type = unwinder;
    EMB_LOGDEV("Preparing to sample native thread.");
}

/**
 * Initiates sampling of the target thread.
 */
static int raise_signal_on_target_thread() {
    int status = 0;

    if (target_thread != -1) {
        int result = pthread_kill(target_thread, EMB_TARGET_THREAD_SIGNUM);
        if (result != 0) {
            EMB_LOGWARN("Failed to send signal to target thread: %d", result);
            status = EMB_ERROR_SIGUSR2_FAILED;
        } else {
            EMB_LOGINFO("Sent signal to target thread with ID %ld, result=%d", (long) target_thread, result);
        }
    } else {
        EMB_LOGWARN("target_thread not set, skipping sending signal to target thread.");
        status = EMB_ERROR_TARGET_THREAD_NULL;
    }
    return status;
}

void emb_sigev_notify_function(union sigval sigval) {
    if (has_reached_sample_limit()) {
        emb_stop_timer(timer_id, &timerspec);
        return;
    } else {
        emb_current_sample()->timestamp_ms = emb_get_time_ms();
        raise_signal_on_target_thread();
    }
}

/**
 * Raises SIGUSR2 on the target thread.
 */
int emb_start_thread_sampler(long interval_ms) {
    EMB_LOGDEV("Called emb_start_thread_sampler().");
    if (is_thread_sampler_started) {
        return -1;
    }
    is_thread_sampler_started = true;

    if (!is_installed()) {
        return EMB_ERROR_NOT_INSTALLED;
    }
    interval->num_samples = 0;

    EMB_LOGDEV("Starting timer for sampling.");
    if (emb_start_timer(timer_id, &timerspec, 1, interval_ms) != 0) {
        EMB_LOGERROR("Failure starting timer, errno=%d", errno);
        return EMB_ERROR_TIMER_FAILED;
    }
    return 0;
}

/**
 * Raises SIGUSR2 on the target thread.
 */
int emb_stop_thread_sampler() {
    EMB_LOGDEV("Called emb_stop_thread_sampler().");
    if (!is_thread_sampler_started) {
        return -1;
    }
    is_thread_sampler_started = false;

    if (!is_installed()) {
        return EMB_ERROR_NOT_INSTALLED;
    }

    EMB_LOGDEV("Stopping timer.");
    if (emb_stop_timer(timer_id, &timerspec) != 0) {
        EMB_LOGERROR("Failure stopping timer, errno=%d", errno);
    }
    return 0;
}

bool emb_monitor_current_thread() {
    if (!emb_sig_stk_setup(emb_sample_stack)) {
        return false;
    }
    EMB_LOGDEV("Called emb_monitor_current_thread().");
    bool result = true;
    static pthread_mutex_t _emb_signal_mutex = PTHREAD_MUTEX_INITIALIZER;
    pthread_mutex_lock(&_emb_signal_mutex);

    EMB_LOGINFO("Installing SIGUSR2 handler.");
    target_thread = pthread_self();
    EMB_LOGDEV("Target thread ID=%ld", target_thread);
    result = emb_install_signal_handler();

    pthread_mutex_unlock(&_emb_signal_mutex);
    return result;
}

bool emb_setup_native_thread_sampler(emb_env *emb_env, bool _is32bit) {
    EMB_LOGDEV("Called emb_setup_native_thread_sampler().");

    static pthread_mutex_t _emb_timer_mutex = PTHREAD_MUTEX_INITIALIZER;
    bool result = true;
    is32bit = _is32bit;

    pthread_mutex_lock(&_emb_timer_mutex);
    if (!is_installed()) {
        EMB_LOGINFO("Installing SIGUSR2 handler.");
        env = emb_env;

        EMB_LOGDEV("Creating timer for sampling.");
        if (emb_create_timer(&timer_id, &timer_action, emb_sigev_notify_function) != 0) {
            EMB_LOGERROR("Failure creating timer, errno=%d", errno);
            result = false;
        }
    }
    pthread_mutex_unlock(&_emb_timer_mutex);
    return result;
}
