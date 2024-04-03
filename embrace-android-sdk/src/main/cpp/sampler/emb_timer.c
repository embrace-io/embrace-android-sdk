#include <stdbool.h>
#include <errno.h>
#include "emb_timer.h"
#include "../utils/emb_log.h"

// see https://man7.org/linux/man-pages/man2/timer_create.2.html

static long millis_to_nanos(long ms) {
    return ms * 1000000;
}

static void millis_to_timespec(struct timespec *timespec, long ms) {
    if (timespec == NULL) {
        return;
    }
    // reset fields
    timespec->tv_sec = 0;
    timespec->tv_nsec = 0;

    // populate seconds
    timespec->tv_sec = ms / 1000;
    ms -= timespec->tv_sec * 1000;

    // populate nanoseconds
    timespec->tv_nsec = millis_to_nanos(ms);
}

int emb_create_timer(timer_t *timer,
                     struct sigevent *sigevent,
                     void (*function)(union sigval)) {
    if (timer == NULL || sigevent == NULL || function == NULL) {
        return -1;
    }
    sigevent->sigev_notify = SIGEV_THREAD;
    sigevent->sigev_signo = SIGRTMIN;
    sigevent->sigev_notify_function = function;
    return timer_create(CLOCK_MONOTONIC, sigevent, timer);
}

int emb_start_timer(timer_t timer,
                    struct itimerspec *timerspec,
                    long initial_delay_ms,
                    long interval_ms) {
    if (timer == NULL || timerspec == NULL) {
        return -1;
    }
    millis_to_timespec(&timerspec->it_value, initial_delay_ms);
    millis_to_timespec(&timerspec->it_interval, interval_ms);
    return timer_settime(timer, 0, timerspec, NULL);
}

int emb_stop_timer(timer_t timer, struct itimerspec *timerspec) {
    if (timer == NULL || timerspec == NULL) {
        return -1;
    }
    // setting delay + interval to 0 stops the timer
    return emb_start_timer(timer, timerspec, 0, 0);
}
