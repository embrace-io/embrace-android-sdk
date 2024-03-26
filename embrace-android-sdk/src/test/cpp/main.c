#include <stdlib.h>
#include <stdio.h>
#include <assert.h>
#include <jni.h>
#include <string.h>
#include <greatest/greatest.h>
#include "utilities.h"

/* Add definitions that need to be in the test runner's main file. */
GREATEST_MAIN_DEFS();

TEST example_test_case(void) {
    char dst[16];
    char src[] = "Hello, World!";
    emb_strncpy(dst, src, sizeof(src));

    ASSERT_STR_EQ(dst, "Hello, World!");
    PASS();
}

int run_test_case(enum greatest_test_res (*test_case)(void)) {
    int argc = 0;
    char *argv[] = {};
    GREATEST_MAIN_BEGIN();
    RUN_TEST(test_case);
    GREATEST_MAIN_END();
}

JNIEXPORT int JNICALL Java_io_embrace_android_embracesdk_ndk_EmbraceNativeLayerTest_run(
        JNIEnv *_env, jobject _this) {
    return run_test_case(example_test_case);
}
