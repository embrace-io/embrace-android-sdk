#ifndef EMBRACE_SIGNAL_UTILS_H
#define EMBRACE_SIGNAL_UTILS_H

#include <asm/signal.h>
#include <stdbool.h>

/**
 * Creates an alternate stack for use by a signal handler.
 * https://man7.org/linux/man-pages/man2/sigaltstack.2.html
 *
 * @param stack a struct where the alternate stack will be stored
 * @return true if stack setup was successful.
 */
bool emb_sig_stk_setup(stack_t stack);

/**
 * Invokes the previous signal handler if it was set. This respects SA_SIGINFO, SIG_DFL,
 * and SIG_IGN.
 *
 * @param signum the signal number
 * @param info the signal handler info
 * @param user_context the signal handler context
 * @param prev_handler the previous handler
 */
void emb_trigger_prev_handler(int signum, siginfo_t *info, void *user_context,
                              struct sigaction prev_handler);

#endif //EMBRACE_SIGNAL_UTILS_H
