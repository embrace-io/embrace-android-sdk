#include <stdlib.h>
#include <unistd.h>
#include "greatest/greatest.h"
#include "utilities.h"
#include "sampler_unwinder_stack.h"
#include "stacktrace_sampler.h"

static emb_env fake_env = {0};

static volatile int signal_count = 0;
static struct sigaction prev_handler = {0};

void emb_handle_target_signal(int signum, siginfo_t *info, void *user_context);

void test_handler(int signum, siginfo_t *info, void *user_context) {
    prev_handler.sa_sigaction(signum, info, user_context);
    signal_count++;
}

TEST thread_sampling(void) {
    // setup stacktrace capture prerequisites
    signal_count = 0;
    ASSERT_EQ(true, emb_setup_native_thread_sampler(&fake_env, false));
    ASSERT_EQ(true, emb_monitor_current_thread());

    // install a signal handler for SIGUSR2. This allows us to count how often a signal is received
    // and block the test until the necessary samples have been collected.
    struct sigaction handler = {0};
    handler.sa_sigaction = test_handler;
    handler.sa_flags = SA_SIGINFO;
    sigaction(SIGUSR2, &handler, &prev_handler);

    // start sampling thread
    emb_start_thread_sampler(1);
    while (signal_count < 10);
    emb_stop_thread_sampler();

    // assert on the captured data intervals
    emb_interval *interval = emb_current_interval();
    ASSERT_EQ(10, interval->num_samples);

    for (int k = 0; k < interval->num_samples; k++) {
        emb_sample *sample = &interval->samples[k];
        ASSERT_EQ(0, sample->result);
        ASSERT_NEQ(0, sample->num_sframes);
        ASSERT_NEQ(0, sample->stack[0].pc);
        ASSERT_NEQ(0, sample->stack[0].so_load_addr);
        ASSERT_NEQ(NULL, sample->stack[0].so_path);
    }
    PASS();
}

TEST install_sig_handler(void) {
    bool result = emb_monitor_current_thread();
    ASSERT_EQ(true, result);

    struct sigaction sa = {0};
    sigaction(SIGUSR2, NULL, &sa);
    ASSERT_EQ(SA_SIGINFO | SA_ONSTACK, sa.sa_flags);
    ASSERT_EQ(emb_handle_target_signal, sa.sa_sigaction);
    PASS();
}

TEST sampler_already_installed(void) {
    emb_setup_native_thread_sampler(&fake_env, false);
    bool result = emb_setup_native_thread_sampler(&fake_env, false);
    ASSERT_EQ(true, result);
    PASS();
}

TEST setup_native_thread_sampler(void) {
    bool result = emb_setup_native_thread_sampler(&fake_env, false);
    ASSERT_EQ(true, result);
    PASS();
}

TEST fetch_current_interval(void) {
    ASSERT_NEQ(NULL, emb_current_interval());
    PASS();
}

SUITE(suite_stacktrace_sampler) {
//    RUN_TEST(thread_sampling);
    RUN_TEST(install_sig_handler);
//    RUN_TEST(sampler_already_installed);
    RUN_TEST(setup_native_thread_sampler);
    RUN_TEST(fetch_current_interval);
}
