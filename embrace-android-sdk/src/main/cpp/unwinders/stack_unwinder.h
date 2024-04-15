//
// Created by Eric Lanz on 5/12/20.
//

#ifndef EMBRACE_NATIVE_CRASHES_UNWINDER_STACK_H
#define EMBRACE_NATIVE_CRASHES_UNWINDER_STACK_H

#include "../jnibridge/emb_ndk_manager.h"
#include <signal.h>

#ifdef __cplusplus
extern "C" {
#endif

ssize_t emb_unwind_stack(emb_env *env, void *user_context);

#ifdef __cplusplus
}
#endif

#endif //EMBRACE_NATIVE_CRASHES_UNWINDER_STACK_H
