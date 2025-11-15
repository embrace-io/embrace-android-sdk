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

void emb_set_crash_time(emb_env *env) {
    struct timespec ts;
    clock_gettime(CLOCK_REALTIME, &ts);
    // get timestamp in millis
    env->crash.crash_ts = ((int64_t) ts.tv_sec * 1000) + ((int64_t) ts.tv_nsec / 1000000);
}
