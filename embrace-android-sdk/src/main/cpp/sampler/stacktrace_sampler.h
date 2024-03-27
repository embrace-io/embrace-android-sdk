#ifndef EMBRACE_NATIVE_STACKTRACE_SAMPLER_H
#define EMBRACE_NATIVE_STACKTRACE_SAMPLER_H

#include "sampler_structs.h"
#include "../jnibridge/emb_ndk_manager.h"

/**
 * Performs one-time initialization required to sample native threads
 */
bool emb_setup_native_thread_sampler(emb_env *emb_env, bool _is32bit);

/**
 * Initializes a signal handler for SIGUSR2 on the current thread. Signals can then be sent from
 * other threads when a stacktrace is required.
 */
bool emb_monitor_current_thread();

/**
 * Raises a user-defined signal (SIGUSR2) on the target thread at regular intervals.
 * This allows us to sample the thread that was monitored in emb_setup_native_thread_sampler().
 */
int emb_start_thread_sampler(long interval_ms);

/**
 * Stops raised SIGUSR2 on the target thread at regular intervals.
 */
int emb_stop_thread_sampler();

/**
 * Prepares the native layer for imminent stacktrace sampling.
 */
void emb_set_unwinder(int unwinder);

/**
 * Fetches the samples for the current interval.
 */
emb_interval *emb_current_interval();

#endif //EMBRACE_NATIVE_STACKTRACE_SAMPLER_H
