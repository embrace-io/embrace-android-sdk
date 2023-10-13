//
// Created by Eric Lanz on 5/5/20.
//

#include "unwinder.h"
#include "unwinder_stack.h"
#include "../utilities.h"
#include "../emb_log.h"
#include <dlfcn.h>

void emb_fix_fileinfo(ssize_t frame_count,
                         emb_sframe stacktrace[kEMBMaxSFrames]) {
    static Dl_info info;
    for (int i = 0; i < frame_count; ++i) {
        if (dladdr((void *)stacktrace[i].frame_addr, &info) != 0) {
            stacktrace[i].module_addr = (uintptr_t)info.dli_fbase;
            stacktrace[i].offset_addr = (uintptr_t)info.dli_saddr;
            stacktrace[i].line_num =
                    stacktrace[i].frame_addr - stacktrace[i].module_addr;
            if (info.dli_fname != NULL) {
                emb_strncpy(stacktrace[i].filename, (char *)info.dli_fname, sizeof(stacktrace[i].filename));
            }
            if (info.dli_sname != NULL) {
                emb_strncpy(stacktrace[i].method, (char *)info.dli_sname, sizeof(stacktrace[i].method));
            }
        }
    }
}

ssize_t emb_process_capture(emb_env *env, siginfo_t *info, void *user_context) {
    ssize_t frame_count;

    frame_count = emb_process_stack(env, info, user_context);

    emb_fix_fileinfo(frame_count, env->crash.capture.stacktrace);

    return frame_count;
}