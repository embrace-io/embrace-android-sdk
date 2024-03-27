//
// Created by Eric Lanz on 5/5/20.
//

#ifndef EMBRACE_NATIVE_CRASHES_UNWINDER_H
#define EMBRACE_NATIVE_CRASHES_UNWINDER_H

#ifdef __cplusplus
extern "C" {
#endif

#include <stdbool.h>
#include <sys/types.h>
#include <asm/siginfo.h>
#include "../jnibridge/emb_ndk_manager.h"
#include "../schema/stack_frames.h"

ssize_t emb_process_capture(emb_env *env, siginfo_t *info, void *user_context);

#ifdef __cplusplus
}
#endif

#endif //EMBRACE_NATIVE_CRASHES_UNWINDER_H
