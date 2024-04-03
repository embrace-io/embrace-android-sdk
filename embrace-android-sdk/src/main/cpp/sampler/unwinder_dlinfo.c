#include <dlfcn.h>
#include <string.h>
#include "sampler_unwinder_unwind.h"
#include "../utils/utilities.h"
#include "../utils/emb_log.h"
#include "unwinder_dlinfo.h"
#include "../utils/string_utils.h"

// controls whether extra debug info is logged for development purposes
#define EMB_LOG_DLADDR_INFO false

void convert_to_hex_addr(uint64_t addr, char *buffer);

static void emb_log_debug_info(uint64_t ip, size_t index, const emb_sample_stackframe *frame,
                               const Dl_info *info, int result) {
    char frame_buf[kEMBSampleAddrLen] = {0};
    char symbol_buf[kEMBSampleAddrLen] = {0};
    char load_buf[kEMBSampleAddrLen] = {0};

    convert_to_hex_addr(frame->pc, frame_buf);
    convert_to_hex_addr(frame->so_load_addr, load_buf);

    // now get the symbol address (if it was set)
    if (info->dli_saddr != NULL && info->dli_sname != NULL) {
        convert_to_hex_addr((uint64_t) info->dli_saddr, symbol_buf);
    }

    if (result == 0) {
        EMB_LOGINFO("Frame %d: the address %s could not be matched to a shared object.",
                    (int) index, frame_buf);
    } else {
        if (info->dli_saddr == NULL && info->dli_sname == NULL) {
            uint64_t base_addr = ip - (uint64_t) info->dli_fbase;
            convert_to_hex_addr(base_addr, symbol_buf);

            EMB_LOGINFO(
                    "Frame %d: the address was matched to a shared object, "
                    "but not a symbol within the shared object. so_path=%s, pc=%s, so_load_addr=%s, base_addr=%s",
                    (int) index,
                    frame->so_path,
                    frame_buf,
                    load_buf,
                    symbol_buf
            );
        } else {
            EMB_LOGINFO("Frame %d: %s %s, pc=%s, so_load_addr=%s, so_symbol_addr=%s",
                        (int) index,
                        frame->so_path,
                        info->dli_sname,
                        frame_buf,
                        load_buf,
                        symbol_buf
            );
        }
    }
}

int emb_get_dlinfo_for_ip(uint64_t ip, size_t index, emb_sample_stackframe *frame) {
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

#pragma clang diagnostic push
#pragma ide diagnostic ignored "Simplify"
    if (EMB_LOG_DLADDR_INFO) {
        emb_log_debug_info(ip, index, frame, &info, result);
    }
#pragma clang diagnostic pop

    return result;
}

void emb_symbolicate_stacktrace(emb_sample *sample) {
    for (int k = 0; k < sample->num_sframes; k++) {
        emb_sample_stackframe *frame = &sample->stack[k];
        emb_get_dlinfo_for_ip(frame->pc, k, frame);
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
