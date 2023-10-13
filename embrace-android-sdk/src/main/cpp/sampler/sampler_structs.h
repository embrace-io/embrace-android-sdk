#ifndef EMBRACE_SAMPLER_STRUCTS_H
#define EMBRACE_SAMPLER_STRUCTS_H

#include "../stack_frames.h"

// Android blocks SIGUSR1 in the same way as SIGQUIT as it uses the signal
// to force garbage collection. We therefore use SIGUSR2 instead.
// https://cs.android.com/android/platform/superproject/+/android-12.0.0_r1:art/runtime/signal_catcher.cc;l=187
#define EMB_TARGET_THREAD_SIGNUM SIGUSR2

enum sample_unwinder_type {
    LIBUNWIND,
    LIBUNWINDSTACK,
};

/**
 * Holds info on a stackframe captured during a native sample.
 */
typedef struct {
    /**
     * The program counter
     */
    uint64_t pc;

    /**
     * The load address of shared object. This information may not be available
     * in which case the value will be 0x0.
     */
    uint64_t so_load_addr;

    /**
     * The absolute path of the shared object. This information may not be available
     * in which case the string will be empty.
     */
    char so_path[kEMBSamplePathMaxLen];

    /**
     * The result for unwinding this particular stackframe. Non-zero values indicate an error.
     */
    int result;
} volatile emb_sample_stackframe;

/**
 * Holds info on a stacktrace captured during a native sample.
 */
typedef struct {

    /**
     * The number of stackframes captured.
     */
    size_t num_sframes;

    /**
     * All the stackframes which have been captured during the current sample.
     */
    emb_sample_stackframe stack[kEMBMaxSampleSFrames];

    /**
     * A zero value indicates the sample was successful. A non-zero value indicates
     * that something went wrong with the sample. Error codes match those defined in utilities.h.
     */
    uint8_t result;

    /**
     * The timestamp at which the sample started
     */
    int64_t timestamp_ms;

    /**
     * The duration of the overall sample
     */
    int64_t duration_ms;
} volatile emb_sample;

/**
 * Holds all the samples captured during an interval.
 */
typedef struct {

    /**
     * The number of samples made.
     */
    size_t num_samples;

    /**
     * All the samples that have been captured during this interval.
     */
    emb_sample samples[kEMBMaxSamples];

} volatile emb_interval;

#endif //EMBRACE_SAMPLER_STRUCTS_H
