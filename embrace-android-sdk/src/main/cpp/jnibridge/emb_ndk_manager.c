#include <jni.h>
#include <string.h>
#include <stdlib.h>
#include <time.h>
#include <pthread.h>
#include "emb_ndk_manager.h"
#include "../serializer/file_writer.h"
#include "../signals/signals_c.h"
#include "../signals/signals_cpp.h"
#include "../unwinders/unwinder.h"
#include "../utils/utilities.h"
#include "inttypes.h"
#include "../safejni/jni_util.h"
#include "../utils/emb_log.h"
#include "../safejni/safe_jni.h"

#ifdef __cplusplus
extern "C" {
#endif

#define WARNING_LOG_BUFFER_SIZE 1024

#pragma clang diagnostic push
#pragma ide diagnostic ignored "OCUnusedGlobalDeclarationInspection"
#pragma clang diagnostic push
#pragma ide diagnostic ignored "UnusedParameter"

static JNIEnv *__emb_jni_env = NULL;
static emb_env __impl_emb_env = {0};
static emb_env *__emb_env = &__impl_emb_env;

JNIEXPORT void JNICALL
Java_io_embrace_android_embracesdk_internal_ndk_jni_JniDelegateImpl_installSignalHandlers(JNIEnv *env,
                                                                                      jobject thiz,
                                                                                      jstring _crash_marker_path,
                                                                                      jstring _app_state,
                                                                                      jstring _report_id,
                                                                                      jint api_level,
                                                                                      jboolean is_32bit,
                                                                                      jboolean dev_logging) {
    if (dev_logging) {
        emb_enable_dev_logging();
    }
    EMB_LOGINFO("Installing Signal Handlers");
    if (__emb_jni_env) {
        EMB_LOGINFO("handler already installed.");
        return;
    }
    __emb_jni_env = env;

    EMB_LOGDEV("unwinder args: apiLevel=%d, 32bit=%d", api_level, is_32bit);

    EMB_LOGDEV("Setting up initial state.");
    const char *report_id = (*env)->GetStringUTFChars(env, _report_id, 0);
    snprintf(__emb_env->crash.report_id, EMB_REPORT_ID_SIZE, "%s", report_id);

    EMB_LOGDEV("Setting up crash marker path.");
    const char *crash_marker_path = (*env)->GetStringUTFChars(env, _crash_marker_path, 0);
    snprintf(__emb_env->crash_marker_path, EMB_PATH_SIZE, "%s", crash_marker_path);
    EMB_LOGINFO("crash marker path: %s", crash_marker_path);

    EMB_LOGDEV("Recording start timestamp.");
    struct timespec ts;
    clock_gettime(CLOCK_REALTIME, &ts);
    // get timestamp in millis
    __emb_env->crash.start_ts = ((int64_t) ts.tv_sec * 1000) + ((int64_t) ts.tv_nsec / 1000000);

    // install signal handlers
    if (!emb_setup_c_signal_handlers(__emb_env)) {
        EMB_LOGWARN("failed to install c handlers.");
    } else {
        EMB_LOGINFO("c handlers installed.");
    }
    if (!emb_setup_cpp_sig_handler(__emb_env)) {
        EMB_LOGWARN("failed to install cpp handlers.");
    } else {
        EMB_LOGINFO("cpp handlers installed.");
    }
    EMB_LOGDEV("Completed signal handler install.");
}

JNIEXPORT void JNICALL
Java_io_embrace_android_embracesdk_internal_ndk_jni_JniDelegateImpl_onSessionChange(JNIEnv *env,
                                                                                   jobject thiz,
                                                                                   jstring _session_id,
                                                                                   jstring _report_path) {
    const char *session_id = (*env)->GetStringUTFChars(env, _session_id, 0);
    snprintf(__emb_env->crash.session_id, EMB_SESSION_ID_SIZE, "%s", session_id);
    const char *report_path = (*env)->GetStringUTFChars(env, _report_path, 0);
    snprintf(__emb_env->report_path, EMB_PATH_SIZE, "%s", report_path);
}

JNIEXPORT jstring JNICALL
Java_io_embrace_android_embracesdk_internal_ndk_jni_JniDelegateImpl_getCrashReport(
        JNIEnv *env, jobject _this, jstring _report_path) {
    EMB_LOGDEV("Called getCrashReport().");
    static pthread_mutex_t crash_reader_mutex = PTHREAD_MUTEX_INITIALIZER;
    pthread_mutex_lock(&crash_reader_mutex);
    const char *crash_path = NULL;
    emb_crash *crash = NULL;
    char *payload = NULL;
    jstring payload_str = NULL;

    crash_path = (*env)->GetStringUTFChars(env, _report_path, NULL);
    if (crash_path == NULL) {
        EMB_LOGERROR("Failed to allocate crash path.");
        goto cleanup;
    } else {
        EMB_LOGDEV("Loading crash from %s", crash_path);
    }

    crash = emb_read_crash_from_file(crash_path);
    if (crash != NULL) {
        EMB_LOGDEV("Successfully read emb_crash struct into memory.");

        payload = emb_crash_to_json(crash);
        if (payload == NULL) {
            EMB_LOGERROR("failed to convert crash report to JSON at %s", crash_path);
        } else {
            EMB_LOGDEV("Serialized emb_crash into JSON payload.");
        }
    } else {
        EMB_LOGERROR("failed to read crash report at %s", crash_path);
    }

    payload_str = (*env)->NewStringUTF(env, payload);

    if (payload_str != NULL) {
        EMB_LOGDEV("Creating UTF string for payload.");
    } else {
        EMB_LOGDEV("Failed to create UTF string for payload.");
    }

    cleanup:
    pthread_mutex_unlock(&crash_reader_mutex);
    if (crash != NULL) {
        free(crash);
    }
    if (payload != NULL) {
        free(payload);
    }
    emb_jni_release_string_utf_chars(env, _report_path, crash_path);

    return payload_str;
}

JNIEXPORT jstring JNICALL
Java_io_embrace_android_embracesdk_internal_ndk_jni_JniDelegateImpl_checkForOverwrittenHandlers(JNIEnv *env,
                                                                                            jobject thiz) {
    char buffer[WARNING_LOG_BUFFER_SIZE];
    EMB_LOGINFO("Checking for Overwritten handlers");
    if (emb_check_for_overwritten_handlers(buffer, WARNING_LOG_BUFFER_SIZE)) {
        return emb_jni_new_string_utf(env, buffer);
    } else {
        return NULL;
    }
}

JNIEXPORT jboolean JNICALL
Java_io_embrace_android_embracesdk_internal_ndk_jni_JniDelegateImpl_reinstallSignalHandlers(JNIEnv *env,
                                                                                        jobject thiz) {
    EMB_LOGINFO("About to reinstall 3rd party handlers");

    // install signal handlers
    if (!emb_setup_c_signal_handlers(__emb_env)) {
        EMB_LOGWARN("failed to reinstall c handlers.");
    } else {
        EMB_LOGINFO("c handlers reinstalled.");
    }
    if (!emb_setup_cpp_sig_handler(__emb_env)) {
        EMB_LOGWARN("failed to reinstall cpp handlers.");
    } else {
        EMB_LOGINFO("cpp handlers reinstalled.");
    }
    EMB_LOGDEV("Completed signal handler reinstall.");
    return false;
}

#pragma clang diagnostic pop
#pragma clang diagnostic pop

#ifdef __cplusplus
}
#endif
