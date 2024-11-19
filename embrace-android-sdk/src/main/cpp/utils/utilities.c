//
// Created by Eric Lanz on 5/12/20.
//

#include <fcntl.h>
#include <stdio.h>
#include <string.h>
#include <time.h>
#include <unistd.h>
#include "inttypes.h"
#include "utilities.h"
#include "emb_log.h"

void emb_set_report_paths(emb_env *env, const char *session_id) {
    snprintf(env->report_path, EMB_PATH_SIZE, "%s/emb_ndk.%s.%s.%" PRId64 ".crash", env->base_path,
            CRASH_REPORT_CURRENT_VERSION, session_id, env->crash.start_ts);
    EMB_LOGINFO("report path: %s", env->report_path);
    snprintf(env->error_path, EMB_PATH_SIZE, "%s/emb_ndk.%s.%s.%" PRId64 ".error", env->base_path,
            CRASH_REPORT_CURRENT_VERSION, session_id, env->crash.start_ts);
    EMB_LOGINFO("error path: %s", env->error_path);
    snprintf(env->map_path, EMB_PATH_SIZE, "%s/emb_ndk.%s.%s.%" PRId64 ".map", env->base_path,
            CRASH_REPORT_CURRENT_VERSION, session_id, env->crash.start_ts);
    EMB_LOGINFO("map path: %s", env->map_path);
    snprintf(env->map_src_path, MAP_SRC_PATH_SIZE, "/proc/%d/maps", getpid());
}

int emb_dump_map(emb_env *env) {
    int fd_in = open(env->map_src_path, O_RDONLY | O_CLOEXEC);
    if (fd_in == -1) {
        return -1;
    }

    int fd_out = open(env->map_path, O_WRONLY | O_CREAT, 0644);
    if (fd_out == -1) {
        close(fd_in);
        return -2;
    }

    char buf[1024];
    int size;
    int rv = 0;

    while (true) {
        size = read(fd_in, &buf, sizeof(buf));
        if (size == 0) {
            break;
        }
        if (size < 0) {
            rv = -3;
            goto cleanup;
        }
        write(fd_out, &buf, size);
    }

    cleanup:

    close(fd_in);
    close(fd_out);

    return rv;
}


void emb_set_crash_time(emb_env *env) {
    struct timespec ts;
    clock_gettime(CLOCK_REALTIME, &ts);
    // get timestamp in millis
    env->crash.crash_ts = ((int64_t) ts.tv_sec * 1000) + ((int64_t) ts.tv_nsec / 1000000);
}

void emb_log_last_error(emb_env *env, int num, int context) {
    if (env == NULL) {
        return;
    }
    if (env->errors_captured >= EMB_MAX_ERRORS) {
        return;
    }

    if (!env->err_fd) {
        env->err_fd = open(env->error_path, O_WRONLY | O_CREAT | O_APPEND, 0644);
        if (env->err_fd <= 0) {
            return;
        }
    }
    env->last_error.context = context;
    env->last_error.num = num;
    write(env->err_fd, &env->last_error, sizeof(emb_error));
}
