#include "string.h"
#include "sampler_unwinder_stack.h"
#include <stdlib.h>
#include <ucontext.h>
#include <dlfcn.h>
#include <unwindstack/RegsArm64.h>
#include "../utilities.h"
#include "unwinder_dlinfo.h"
#include "unwindstack/AndroidUnwinder.h"

static ssize_t emb_unwindstack_impl(emb_env *env, emb_unwind_state *sample, void *user_context) {
    if (env != nullptr && env->currently_handling) {
        sample->result = EMB_ERROR_ENV_TERMINATING;
        return 0;
    }

    unwindstack::AndroidUnwinder *unwinder = unwindstack::AndroidUnwinder::Create(getpid());
    unwindstack::AndroidUnwinderData android_unwinder_data = unwindstack::AndroidUnwinderData();

    if (unwinder->Unwind(user_context, android_unwinder_data)) {
        int i = 0;
        for (const auto &frame: android_unwinder_data.frames) {
            sample->stack[i++] = frame.pc;
        }
    } else {
        sample->result = EMB_ERROR_UNWIND_STACK_FAILURE;
        sample->num_sframes = 0;
        return 0;
    }

    int frame_count = static_cast<int>(android_unwinder_data.frames.size());

    sample->num_sframes = frame_count;

    return frame_count;
}

size_t emb_unwind_with_libunwindstack(emb_env *env, emb_sample *sample,
                                      void *user_context) {

    emb_unwind_state _unwind_state = {0};
    emb_unwind_state *unwind_state = &_unwind_state;
    ssize_t frame_count = emb_unwindstack_impl(env, unwind_state, user_context);

    // copy frames from temporary unwind_state struct to more permament emb_sample
    // (allows truncating the stack)
    emb_copy_frames(sample, unwind_state);
    emb_symbolicate_stacktrace(sample);
    return frame_count;
}
