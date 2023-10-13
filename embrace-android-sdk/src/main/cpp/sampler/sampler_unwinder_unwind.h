#ifndef EMBRACE_SAMPLER_UNWINDER_UNWIND_H
#define EMBRACE_SAMPLER_UNWINDER_UNWIND_H

#include <stdlib.h>
#include <pthread.h>
#include "sampler_structs.h"
#include "../emb_ndk_manager.h"

/**
 * Unwind the stack using the libunwind unwinder.
 */
size_t emb_unwind_with_libunwind(emb_env *env, emb_sample *sample, bool is32bit, siginfo_t *info, void *user_context);

#endif //EMBRACE_SAMPLER_UNWINDER_UNWIND_H
