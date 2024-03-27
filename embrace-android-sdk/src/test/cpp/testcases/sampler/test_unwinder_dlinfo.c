#include <stdlib.h>
#include "greatest/greatest.h"
#include "unwinder_dlinfo.h"
#include "utilities.h"

TEST empty_structs(void) {
    emb_sample sample = {0};
    emb_unwind_state unwind_state = {0};
    emb_copy_frames(&sample, &unwind_state);
    ASSERT_EQ(0, sample.result);
    ASSERT_EQ(0, sample.num_sframes);
    PASS();
}

TEST small_stacktrace(void) {
    emb_sample sample = {0};
    emb_unwind_state unwind_state = {0};
    unwind_state.num_sframes = 3;

    for (int k = 0; k < unwind_state.num_sframes; k++) {
        unwind_state.stack[k] = k;
    }
    emb_copy_frames(&sample, &unwind_state);

    ASSERT_EQ(0, sample.result);
    ASSERT_EQ(3, sample.num_sframes);
    for (int k = 0; k < unwind_state.num_sframes; k++) {
        ASSERT_EQ(k, sample.stack[k].pc);
    }
    PASS();
}

TEST truncated_stacktrace(void) {
    emb_sample sample = {0};
    emb_unwind_state unwind_state = {0};
    unwind_state.num_sframes = 200;

    for (int k = 0; k < unwind_state.num_sframes; k++) {
        unwind_state.stack[k] = k;
    }
    emb_copy_frames(&sample, &unwind_state);

    ASSERT_EQ(EMB_ERROR_TRUNCATED_STACKTRACE, sample.result);
    ASSERT_EQ(kEMBMaxSampleSFrames, sample.num_sframes);
    for (int k = 0; k < sample.num_sframes; k++) {
        ASSERT_EQ(k + 100, sample.stack[k].pc);
    }
    PASS();
}

TEST symbolicate_stacktrace(void) {
    emb_sample sample = {0};
    sample.num_sframes = 1;
    sample.stack[0].pc = (uint64_t) &truncated_stacktrace;
    emb_symbolicate_stacktrace(&sample);

    ASSERT_NEQ(0, sample.stack[0].so_load_addr);
    char *path = (char *) sample.stack[0].so_path;
    ASSERT_NEQ(NULL, strstr(path, "libembrace-native-test.so"));
    PASS();
}

SUITE(suite_unwinder_dlinfo) {
    RUN_TEST(empty_structs);
    RUN_TEST(small_stacktrace);
    RUN_TEST(truncated_stacktrace);
    RUN_TEST(symbolicate_stacktrace);
}
