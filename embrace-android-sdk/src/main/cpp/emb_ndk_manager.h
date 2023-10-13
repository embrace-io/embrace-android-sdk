//
// Created by Eric Lanz on 5/5/20.
//

#ifndef EMBRACE_NATIVE_CRASHES_EMB_NDK_MANAGER_H
#define EMBRACE_NATIVE_CRASHES_EMB_NDK_MANAGER_H

#include "stack_frames.h"
#include <stdbool.h>

#ifdef CLANG_ANALYZE_ASYNCSAFE
#define __asyncsafe __attribute__((asyncsafe));
#else
#define __asyncsafe
#endif

#define CRASH_REPORT_VERSION1 "v1"
#define CRASH_REPORT_CURRENT_VERSION CRASH_REPORT_VERSION1

typedef struct {
    int num;
    int context;
} emb_error;


// "/proc/<largest PID possible>/maps" + \0 is 22 bytes long
#define MAP_SRC_PATH_SIZE 22

typedef struct {
    char base_path[EMB_PATH_SIZE];
    char crash_marker_path[EMB_PATH_SIZE];
    char report_path[EMB_PATH_SIZE];
    char map_path[EMB_PATH_SIZE];
    char error_path[EMB_PATH_SIZE];
    char map_src_path[MAP_SRC_PATH_SIZE];
    int err_fd;
    bool currently_handling;
    bool already_handled_crash;
    emb_crash crash;
    emb_error last_error;
    int errors_captured;
} emb_env;

#endif //EMBRACE_NATIVE_CRASHES_EMB_NDK_MANAGER_H
