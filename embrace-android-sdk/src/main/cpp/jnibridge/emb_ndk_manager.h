//
// Created by Eric Lanz on 5/5/20.
//

#ifndef EMBRACE_NATIVE_CRASHES_EMB_NDK_MANAGER_H
#define EMBRACE_NATIVE_CRASHES_EMB_NDK_MANAGER_H

#include "../schema/stack_frames.h"
#include <stdbool.h>

#ifdef CLANG_ANALYZE_ASYNCSAFE
#define __asyncsafe __attribute__((asyncsafe));
#else
#define __asyncsafe
#endif

#define CRASH_REPORT_VERSION1 "v1"
#define CRASH_REPORT_CURRENT_VERSION CRASH_REPORT_VERSION1

typedef struct {
    char report_path[EMB_PATH_SIZE];
    char crash_marker_path[EMB_PATH_SIZE];
    bool currently_handling;
    bool already_handled_crash;
    emb_crash crash;
} emb_env;

#endif //EMBRACE_NATIVE_CRASHES_EMB_NDK_MANAGER_H
