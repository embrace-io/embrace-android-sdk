#ifndef EMBRACE_UNWINDER_DLINFO_H
#define EMBRACE_UNWINDER_DLINFO_H

#include "sampler_unwinder_unwind.h"

#ifdef __cplusplus
extern "C" {
#endif

typedef struct {
    uint64_t stack[kEMBSampleUnwindLimit]; // unwind up to 256, then copy everything to other struct.
    uint16_t num_sframes;
    uint8_t result;
} emb_unwind_state;

/**
 * Uses dladdr to get information on the shared object load address, symbol address, and path.
 * This information may not always be available.
 *
 * Returns the result from dladdr.
 * See: https://man7.org/linux/man-pages/man3/dladdr.3.html
 */
int emb_get_dlinfo_for_ip(uint64_t ip, size_t index, emb_sample_stackframe *frame);

/**
 * Uses dladdr to get information on the shared object load address, symbol address, and path
 * for the entire stacktrace.
 * This information may not always be available.
 *
 * See: https://man7.org/linux/man-pages/man3/dladdr.3.html
 */
void emb_symbolicate_stacktrace(emb_sample *sample);

/**
 * Copies stackframes from the unwind_state to the emb_sample struct, applying limits
 * on stacktrace size as necessary. The bottom-most frames will always be preferred
 * (this leads to better flamegraph grouping for traces that exceed the max frame limit).
 */
void emb_copy_frames(emb_sample *sample, const emb_unwind_state *unwind_state);

#ifdef __cplusplus
}
#endif
#endif //EMBRACE_UNWINDER_DLINFO_H
