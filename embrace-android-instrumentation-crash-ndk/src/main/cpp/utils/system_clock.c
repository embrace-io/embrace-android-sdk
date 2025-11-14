//
// Created by Jamie Lynch on 14/10/2022.
//

#include <time.h>
#include <stdbool.h>
#include "system_clock.h"

#define SECONDS_TO_MS 1000
#define NANOS_TO_MS 1000000

static volatile int64_t baseline_ms = -1;

static int64_t get_time_impl(clockid_t clock) {
    struct timespec time = {0};
    if (clock_gettime(clock, &time) != 0) {
        return -1;
    }
    int64_t seconds = time.tv_sec;
    int64_t nano_seconds = time.tv_nsec;
    return (seconds * SECONDS_TO_MS) + (nano_seconds / NANOS_TO_MS);
}

/**
 * We want to report the time in milliseconds but using the monotonic clock as we are
 * timing intervals. This creates a baseline ms that is added to any future monotonic
 * time - effectively converting it to realtime but without the downsides.
 */
static void initialize_baseline_ms() {
    int64_t realtime_ms = get_time_impl(CLOCK_REALTIME);
    int64_t monotonic_ms = get_time_impl(CLOCK_MONOTONIC);
    baseline_ms = realtime_ms - monotonic_ms;
}

int64_t emb_get_time_ms() {
    if (baseline_ms == -1) {
        initialize_baseline_ms();
    }
    int64_t now = get_time_impl(CLOCK_MONOTONIC);

    if (now == -1 || baseline_ms == -1) {
        return -1;
    }
    return baseline_ms + now;
}
