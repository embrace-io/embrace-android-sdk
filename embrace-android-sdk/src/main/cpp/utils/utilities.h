//
// Created by Eric Lanz on 5/12/20.
//

#ifndef EMBRACE_NATIVE_CRASHES_UTILITIES_H
#define EMBRACE_NATIVE_CRASHES_UTILITIES_H

#ifdef __cplusplus
extern "C" {
#endif

#include <sys/types.h>
#include "../jnibridge/emb_ndk_manager.h"

#define EMB_ERROR_UNWIND_STACK_FAILURE 8
#define EMB_UNKNOWN_UNWIND_TYPE 9
#define EMB_UNWIND_INFINITE_LOOP 11
#define EMB_SAMPLE_DATA_RACE 12
#define EMB_ERROR_ENV_TERMINATING 14
#define EMB_ERROR_TARGET_THREAD_NULL 16
#define EMB_ERROR_SIGUSR2_FAILED 17
#define EMB_ERROR_UNW_CONTEXT_FAILED 18
#define EMB_ERROR_UNW_INIT_LOCAL_FAILED 19
#define EMB_ERROR_NOT_INSTALLED 20
#define EMB_ERROR_TIMER_FAILED 21
#define EMB_ERROR_TRUNCATED_STACKTRACE 22

// the backend can handle error codes between 0-255.

void emb_set_crash_time(emb_env *env);

#ifdef __cplusplus
}
#endif

#endif //EMBRACE_NATIVE_CRASHES_UTILITIES_H
