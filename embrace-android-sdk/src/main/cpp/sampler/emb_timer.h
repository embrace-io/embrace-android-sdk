#ifndef EMBRACE_EMB_TIMER_H
#define EMBRACE_EMB_TIMER_H

#include <pthread.h>

/**
 * Creates a pthread timer that notifies on the given function pointer as though a new
 * thread had been started.
 */
int emb_create_timer(timer_t *timer,
                     struct sigevent *sigevent,
                     void (*function)(union sigval));

/**
 * Starts a pthread timer with the configured timer & intervals.
 *
 * IMPORTANT: initial_delay_ms MUST be greater than zero, otherwise the timer won't start.
 */
int emb_start_timer(timer_t timer,
                    struct itimerspec *timerspec,
                    long initial_delay_ms,
                    long interval_ms);

/**
 * Stops (but does not delete) a pthread timer.
 */
int emb_stop_timer(timer_t timer,
                   struct itimerspec *timerspec);

#endif //EMBRACE_EMB_TIMER_H
