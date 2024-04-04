#include <stdlib.h>
#include "greatest/greatest.h"
#include "unwinder_dlinfo.h"
#include "utilities.h"
#include "sampler_unwinder_stack.h"

TEST unwind_with_libunwind(void) {
    emb_env env = {0};
    emb_sample sample = {0};
    int result = emb_unwind_with_libunwind(&env, &sample, false, NULL, NULL);
    ASSERT_EQ(0, sample.result);
    ASSERT_EQ(result, sample.num_sframes);
    ASSERT_NEQ(0, sample.num_sframes);

    for (int k = 0; k < sample.num_sframes; k++) {
        emb_sample_stackframe frame = sample.stack[k];
        ASSERT_EQ(0, frame.result);
        ASSERT_NEQ(0, frame.pc);
        ASSERT_NEQ(0, frame.so_load_addr);
        ASSERT_NEQ(NULL, frame.so_path);
    }
    PASS();
}

TEST libunwind_currently_handling(void) {
    emb_env env = {0};
    env.currently_handling = true;
    emb_sample sample = {0};
    int result = emb_unwind_with_libunwind(&env, &sample, false, NULL, NULL);
    ASSERT_EQ(0, sample.result);
    ASSERT_EQ(result, sample.num_sframes);
    ASSERT_EQ(0, sample.num_sframes);
    PASS();
}

TEST unwind_with_libunwindstack(void) {
    emb_env env = {0};
    emb_sample sample = {0};

    // provide dummy data for context that is used to construct register info
    emb_env data = {0};
    int result = emb_unwind_with_libunwindstack(&env, &sample, &data);
    ASSERT_EQ(1, result);
    ASSERT_EQ(0, sample.result);
    ASSERT_EQ(1, sample.num_sframes);

    emb_sample_stackframe frame = sample.stack[0];
    ASSERT_EQ(0, frame.pc);
    ASSERT_EQ(0, frame.result);
    ASSERT_EQ(0, frame.so_load_addr);
    PASS();
}

TEST libunwindstack_currently_handling(void) {
    emb_env env = {0};
    env.currently_handling = true;
    emb_sample sample = {0};
    int result = emb_unwind_with_libunwindstack(&env, &sample, NULL);
    ASSERT_EQ(EMB_ERROR_ENV_TERMINATING, sample.result);
    ASSERT_EQ(result, sample.num_sframes);
    ASSERT_EQ(0, sample.num_sframes);
    PASS();
}

SUITE(suite_sampler_stack_unwind) {
    RUN_TEST(unwind_with_libunwind);
    RUN_TEST(libunwind_currently_handling);
    RUN_TEST(unwind_with_libunwindstack);
    RUN_TEST(libunwindstack_currently_handling);
}
