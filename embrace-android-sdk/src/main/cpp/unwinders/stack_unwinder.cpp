//
// Created by Eric Lanz on 5/12/20.
//

#include "stack_unwinder.h"
#include <stdlib.h>
#include <ucontext.h>
#include "../utils/utilities.h"
#include "../3rdparty/libunwindstack-ndk/MemoryLocal.h"
#include "unwindstack/AndroidUnwinder.h"
#include "../utils/string_utils.h"

static inline void emb_copy_frame_data(unwindstack::AndroidUnwinderData &android_unwinder_data,
                                       emb_sframe *stacktrace) {
    // required to demangle function names locally
    android_unwinder_data.DemangleFunctionNames();
    int k = 0;

    for (const auto &frame: android_unwinder_data.frames) {
        emb_sframe *data = &stacktrace[k++];

        // populate the link register for the first value only
        if (k == 0 && android_unwinder_data.saved_initial_regs.has_value()) {
            data->lr = android_unwinder_data.saved_initial_regs->get()->lr();
        }

        data->frame_addr = frame.pc;
        const auto map_info = frame.map_info;

        // populate additional information.
        // FrameData
        data->rel_pc = frame.rel_pc;
        data->pc = frame.pc;
        data->sp = frame.sp;
        data->function_offset = frame.function_offset;

        // need to call DemangleFunctionNames() for this.
        emb_strncpy(data->function_name, frame.function_name.c_str(), EMB_FRAME_STR_SIZE);

        // map info
        data->elf_file_not_readable = map_info->ElfFileNotReadable();
        data->start = map_info->start();
        data->end = map_info->end();
        data->offset = map_info->offset();
        data->flags = map_info->flags();
        emb_strncpy(data->full_name, map_info->GetFullName().c_str(), EMB_FRAME_STR_SIZE);
    }
}

ssize_t emb_unwind_stack(emb_env *env, void *user_context) {
    unwindstack::AndroidUnwinder *unwinder = unwindstack::AndroidUnwinder::Create(getpid());
    unwindstack::AndroidUnwinderData android_unwinder_data = unwindstack::AndroidUnwinderData();
    emb_sframe *stacktrace = env->crash.capture.stacktrace;
    bool unwindSuccessful;

    if (user_context != NULL) {
        unwindSuccessful = unwinder->Unwind(user_context, android_unwinder_data);
    } else { // fallback to local registers for C++ termination handler
        unwindstack::Regs *regs = unwindstack::Regs::CreateFromLocal();
        unwindSuccessful = unwinder->Unwind(regs, android_unwinder_data);
    }
    env->crash.unwinder_error = android_unwinder_data.error.code;

    if (unwindSuccessful) {
        emb_copy_frame_data(android_unwinder_data, stacktrace);
    } else {
        return 0;
    }
    return static_cast<int>(android_unwinder_data.frames.size());
}
