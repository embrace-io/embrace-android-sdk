#include <dlfcn.h>
#include <string.h>
#include "sampler_unwinder_unwind.h"
#include "../utils/utilities.h"
#include "../utils/emb_log.h"
#include "unwinder_dlinfo.h"
#include "../utils/string_utils.h"

int emb_get_dlinfo_for_ip(uint64_t ip, emb_sample_stackframe *frame) {
    size_t size = sizeof(Dl_info);
    Dl_info info = {0};
    memset(&info, 0, size);

    int result = dladdr((const void *) ip, &info);

    if (result != 0) {
        // get the shared object load address
        frame->so_load_addr = (uint64_t) (&info)->dli_fbase;
        const char *path = (&info)->dli_fname;

        if (path != NULL) {
            emb_strncpy((char *) frame->so_path, path, sizeof frame->so_path);
        }
    }
    return result;
}

void emb_symbolicate_stacktrace(emb_sample *sample) {
    for (int k = 0; k < sample->num_sframes; k++) {
        emb_sample_stackframe *frame = &sample->stack[k];
        emb_get_dlinfo_for_ip(frame->pc, frame);
    }
}

static size_t calculate_frame_start_pos(const emb_unwind_state *unwind_state) {
    size_t start = 0;
    if (unwind_state->num_sframes > kEMBMaxSampleSFrames) {
        start = unwind_state->num_sframes - kEMBMaxSampleSFrames;
    }
    return start;
}

static size_t calculate_stacktrace_size(const emb_unwind_state *unwind_state) {
    size_t size = unwind_state->num_sframes;
    return size > kEMBMaxSampleSFrames ? kEMBMaxSampleSFrames : size;
}

void emb_copy_frames(emb_sample *sample, const emb_unwind_state *unwind_state) {
    sample->result = unwind_state->result;
    size_t start = calculate_frame_start_pos(unwind_state);
    sample->num_sframes = calculate_stacktrace_size(unwind_state);

    bool truncated = sample->num_sframes != unwind_state->num_sframes;

    if (truncated) {
        sample->result = EMB_ERROR_TRUNCATED_STACKTRACE;
    }

    for (size_t k = 0; k < sample->num_sframes; k++) {
        sample->stack[k].pc = unwind_state->stack[start + k];
    }
}
