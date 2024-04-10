#include <stdlib.h>
#include <stdio.h>
#include <assert.h>
#include <jni.h>
#include <string_utils.h>
#include <android/log.h>

#define GREATEST_FPRINTF(ignore, fmt, ...) __android_log_print(ANDROID_LOG_WARN, "EmbraceNdkTest", fmt, ##__VA_ARGS__)

#include <greatest/greatest.h>
#include "utils/string_utils.h"

/* Add definitions that need to be in the test runner's main file. */
GREATEST_MAIN_DEFS();

/* Forward declarations of test suites. These should live in separate files to avoid
 * bloating this file. */
SUITE(suite_utilities);
SUITE(suite_unwinder_dlinfo);
SUITE(suite_sampler_stack_unwind);
SUITE(suite_file_writer);

/* Runs a suite of tests and returns 0 if they succeeded, 1 otherwise.*/
int run_test_suite(void (*suite)(void)) {
    int argc = 0;
    char *argv[] = {};
    GREATEST_MAIN_BEGIN();
    RUN_SUITE(suite);
    GREATEST_MAIN_END();
}

/* JNI functions to bind to the instrumentation tests. Each invocation runs a test suite. */

JNIEXPORT int JNICALL
Java_io_embrace_android_embracesdk_ndk_utils_StringUtilsTestSuite_run(JNIEnv *_env, jobject _this) {
    return run_test_suite(suite_utilities);
}

JNIEXPORT int JNICALL
Java_io_embrace_android_embracesdk_ndk_sampler_UnwinderDlinfoTestSuite_run(JNIEnv *_env, jobject _this) {
    return run_test_suite(suite_unwinder_dlinfo);
}

JNIEXPORT int JNICALL
Java_io_embrace_android_embracesdk_ndk_sampler_SamplerStackUnwindTestSuite_run(JNIEnv *_env, jobject _this) {
    return run_test_suite(suite_sampler_stack_unwind);
}

void emb_setup_fake_intervals();

JNIEXPORT void JNICALL
Java_io_embrace_android_embracesdk_ndk_sampler_SamplerJniCallTestSuite_run(JNIEnv *_env, jobject _this) {
    emb_setup_fake_intervals();
}

void emb_setup_file_writer_tests(const char *path, const char *json);

JNIEXPORT int JNICALL
Java_io_embrace_android_embracesdk_ndk_serializer_FileWriterTestSuite_run(JNIEnv *_env, jobject _this, jstring path, jstring json) {
    const char *abs_path = (*_env)->GetStringUTFChars(_env, path, NULL);
    const char *expected_json = (*_env)->GetStringUTFChars(_env, json, NULL);
    emb_setup_file_writer_tests(abs_path, expected_json);
    return run_test_suite(suite_file_writer);
}
