//
// Created by Eric Lanz on 5/12/20.
//

#ifndef EMBRACE_NATIVE_CRASHES_UNWINDER_STACK_H
#define EMBRACE_NATIVE_CRASHES_UNWINDER_STACK_H

#include "../jnibridge/emb_ndk_manager.h"
#include <signal.h>
#ifdef __cplusplus
extern "C"
#endif
ssize_t
emb_process_stack(emb_env *env, siginfo_t *info, void *user_context);

#endif //EMBRACE_NATIVE_CRASHES_UNWINDER_STACK_H
