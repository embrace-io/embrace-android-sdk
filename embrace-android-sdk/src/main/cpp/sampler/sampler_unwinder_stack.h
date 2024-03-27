#ifndef EMBRACE_SAMPLER_UNWINDER_STACK_H
#define EMBRACE_SAMPLER_UNWINDER_STACK_H

#include <stdlib.h>
#include <pthread.h>
#include "sampler_structs.h"
#include "../jnibridge/emb_ndk_manager.h"

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Unwind the stack using the libunwindstack unwinder.
 *
 * unwinder.h documents that this is supported back to API 15 with no restrictions on
 * CPU architecture, so no conditional checks will be put in place.
 */
size_t emb_unwind_with_libunwindstack(emb_env *_env, emb_sample *sample,
                                      void *user_context);

#ifdef __cplusplus
}
#endif
#endif //EMBRACE_SAMPLER_UNWINDER_STACK_H
