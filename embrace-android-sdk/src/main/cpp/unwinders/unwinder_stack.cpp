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

    if (unwindSuccessful) {
        int i = 0;
        for (const auto &frame: android_unwinder_data.frames) {
            emb_sframe *data = &stacktrace[i++];
            data->frame_addr = frame.pc;
            const auto map_info = frame.map_info;
            emb_strncpy(data->build_id, map_info->GetPrintableBuildID().c_str(), EMB_BUILD_ID_SIZE);
        }
    } else {
        return 0;
    }
    return static_cast<int>(android_unwinder_data.frames.size());

}