//
// Created by Eric Lanz on 5/12/20.
//

#include "string.h"
#include "unwinder_stack.h"
#include <stdlib.h>
#include <ucontext.h>
#include "../utilities.h"
#include "../3rdparty/libunwindstack-ndk/MemoryLocal.h"
#include "unwindstack/AndroidUnwinder.h"

ssize_t
emb_process_stack(emb_env *env, siginfo_t *info, void *user_context) {
    if (user_context == NULL) {
        emb_log_last_error(env, EMB_ERROR_NO_USER_CONTEXT, 0);
        return 0;
    }

    unwindstack::AndroidUnwinder *unwinder = unwindstack::AndroidUnwinder::Create(getpid());
    unwindstack::AndroidUnwinderData android_unwinder_data = unwindstack::AndroidUnwinderData();
    emb_sframe *stacktrace = env->crash.capture.stacktrace;

    bool unwindSuccessful = unwinder->Unwind(user_context, android_unwinder_data);

    env->crash.unwinder_error = android_unwinder_data.error.code;

    // required to demangle function names locally
    android_unwinder_data.DemangleFunctionNames();

    if (unwindSuccessful) {
        int i = 0;


        for (const auto &frame: android_unwinder_data.frames) {
            emb_sframe *data = &stacktrace[i++];

            // populate the link register for the first value only
            if (i == 0 && android_unwinder_data.saved_initial_regs.has_value()) {
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
    } else {
        return 0;
    }
    return static_cast<int>(android_unwinder_data.frames.size());

}