//
// Created by Fredric Newberg on 10/14/21.
//

#include "anr.h"
#include <jni.h>
#include <errno.h>
#include <pthread.h>
#include <semaphore.h>
#include <string.h>
#include <unistd.h>
#include <sys/syscall.h>
#include "../safejni/safe_jni.h"
#include "../utils/emb_log.h"

static bool enabled = false;
static bool installed = false;

static jmethodID anr_mid = NULL;
static jobject anr_service_obj = NULL;
static JavaVM *emb_jvm = NULL;

static volatile bool watchdog_thread_triggered = false;
static int64_t last_ts_ms = 0;

static pid_t pid = -1;
static pid_t google_thread_id = GOOGLE_THREAD_ID_DEFAULT;

static pthread_mutex_t emb_anr_install_lock = PTHREAD_MUTEX_INITIALIZER;

static pthread_t watchdog_thread;
static sem_t watchdog_semaphore;
static bool have_semaphore = false;

static const useconds_t polling_delay_ms = 100000;

/*
 * time helper
 */
static inline int64_t get_timestamp_millis() {
    struct timespec ts;
    if (clock_gettime(CLOCK_REALTIME, &ts) != 0) {
        return 0;
    }

    return ((int64_t) ts.tv_sec * 1000) + ((int64_t) ts.tv_nsec / 1000000);
}


/*
 * Block/unblock SIGQUIT helpers
 */

static inline void manage_sigquit(int how) {
    sigset_t quit_set;
    sigemptyset(&quit_set);
    sigaddset(&quit_set, SIGQUIT);
    if (pthread_sigmask(how, &quit_set, NULL) != 0) {
        // TODO: add log
    }
}

static inline void block_sigquit() {
    manage_sigquit(SIG_BLOCK);
}

static inline void unblock_sigquit() {
    manage_sigquit(SIG_UNBLOCK);
}


static inline void watchdog_wait_for_trigger() {
    // Use sem_wait() if possible, fall back to polling if not available.
    watchdog_thread_triggered = false;
    if (!have_semaphore || sem_wait(&watchdog_semaphore) != 0) {
        EMB_LOGINFO("Waiting for watchdog to trigger.");
        while (!watchdog_thread_triggered) {
            usleep(polling_delay_ms);
        }
        EMB_LOGINFO("Watchdog has triggered.");
    }
}

static inline void kick_google() {
    if (google_thread_id <= 0) {
        EMB_LOGINFO("No Google ANR thread to kick...");
        return;
    }
    EMB_LOGINFO("Kicking Google ANR reporting.");
    syscall(SYS_tgkill, pid, google_thread_id, SIGQUIT);
}

static void process_anr() {
    bool did_attach = false;
    int attach_res;
    JNIEnv *env;
    int res = (*emb_jvm)->GetEnv(emb_jvm, (void **) &env, JNI_VERSION_1_4);
    switch (res) {
        case JNI_OK:
            break;
        case JNI_EDETACHED:
            attach_res = (*emb_jvm)->AttachCurrentThread(emb_jvm, &env, NULL);
            if (attach_res != 0) {
                EMB_LOGERROR("Failed to call attach current thread: %d", attach_res);
                return;
            }
            did_attach = true;
            EMB_LOGINFO("Had to attach current thread to report ANR");
            break;
        default:
            EMB_LOGERROR("Failed to get JNI environment: %d", res);
            return;
    }

    if (anr_service_obj != NULL && anr_mid != NULL) {
        // last_ts_ms was set in the signal handler to ensure the most accurate time possible
        if (emb_jni_call_void_method(env, anr_service_obj, anr_mid, last_ts_ms)) {
            EMB_LOGERROR("Failed to report ANR through JNI.");
        } else {
            EMB_LOGINFO("Reported ANR through JNI.");
        }
    } else {
        EMB_LOGERROR("Failed to capture ANR - null JNI methods.");
    }

    if (did_attach) {
        (*emb_jvm)->DetachCurrentThread(emb_jvm);
    }
}


_Noreturn static void *watchdog_thread_main(void *_) {
    for (;;) {
        watchdog_wait_for_trigger();

        // Trigger Google ANR processing (occurs on a different thread).
        kick_google();

        if (enabled) {
            process_anr();
        }

        // Unblock SIGQUIT again so that handle_sigquit() will run again.
        unblock_sigquit();
    }
}


static inline void trigger_sigquit_watchdog_thread() {
    // Set the trigger flag for the fallback spin-lock in
    // sigquit_watchdog_thread_main()
    watchdog_thread_triggered = true;

    if (have_semaphore) {
        sem_post(&watchdog_semaphore);
    }
}

static void handle_sigquit(__unused int signum, __unused siginfo_t *info, __unused void *user_context) {
    // Re-block SIGQUIT so that the Google handler can trigger.
    // Do it in this handler so that the signal pending flags flip on the next
    // context switch and will be off when the next sigquit_watchdog_thread_main()
    // loop runs.
    block_sigquit();

    last_ts_ms = get_timestamp_millis();

    // TODO: capture stacktrace?

    trigger_sigquit_watchdog_thread();
}


static int64_t install_signal_handler() {
    EMB_LOGDEV("Native - Installing Google ANR signal handler.");
    int64_t result = 0;
    if (google_thread_id == GOOGLE_THREAD_ID_DEFAULT) {
        EMB_LOGWARN("Cannot configure Google ANR reporting since we do not have the watcher thread ID");
    }

    if (sem_init(&watchdog_semaphore, 0, 0) == 0) {
        EMB_LOGDEV("We are on a modern platform and we can use a semaphore for alerting. Yay!");
        have_semaphore = true;
    } else {
        result |= EMB_ANR_INSTALL_NO_SEMAPHORE;
        EMB_LOGDEV("We are on an old platform and we have to fall back on polling... bummer...");
    }

    // Start the watchdog thread
    if (pthread_create(&watchdog_thread, NULL, watchdog_thread_main, NULL) != 0) {
        result |= EMB_ANR_INSTALL_WATCHDOG_THREAD_CREATE_FAIL;
        // TODO: do cleanup to enable Google ANR reporting?
        EMB_LOGINFO("We failed to start the watchdog thread. We will not be able to capture Google ANRs");
        return result;
    }

    struct sigaction handler;
    sigemptyset(&handler.sa_mask);
    handler.sa_sigaction = handle_sigquit;
    handler.sa_flags = SA_SIGINFO;
    if (sigaction(SIGQUIT, &handler, NULL) != 0) {
        EMB_LOGERROR("failed to install sigquit handler: %s", strerror(errno));

        result |= EMB_ANR_INSTALL_HANDLER_FAIL;
        return result;
    }
    EMB_LOGDEV("installed sigquit handler");

    unblock_sigquit();
    return result;
}

static bool configure_reporting(JNIEnv *env) {
    EMB_LOGDEV("Configuring Google ANR reporting");
    if (env == NULL) {
        return false;
    }
    int result = (*env)->GetJavaVM(env, &emb_jvm);
    if (result != 0) {
        EMB_LOGERROR("Reporting config failed, could not get Java VM");
        return false;
    }

    jclass anr_class = emb_jni_find_class(env, "io/embrace/android/embracesdk/internal/anr/sigquit/SigquitDataSource");
    if (anr_class == NULL) {
        EMB_LOGERROR("Reporting config failed, could not find SigquitDataSource class");
        return false;
    }
    EMB_LOGDEV("got ANR class id %p", anr_class);
    anr_mid = emb_jni_get_method_id(env, anr_class, "saveSigquit", "(J)V");
    return true;
}

int emb_install_google_anr_handler(JNIEnv *env, jobject anr_service, jint _google_thread_id) {
    pthread_mutex_lock(&emb_anr_install_lock);
    int res = 0;
    EMB_LOGDEV("anr_service %p", anr_service);

    if (!installed) {
        pid = getpid();
        google_thread_id = _google_thread_id;

        enabled = true;
        if (configure_reporting(env) && anr_service != NULL) {
            anr_service_obj = (*env)->NewGlobalRef(env, anr_service);
            res = install_signal_handler();
            installed = true;
        } else {
            res = EMB_ANR_INSTALL_REPORTING_CONFIGURATION_FAIL;
        }
    }
    pthread_mutex_unlock(&emb_anr_install_lock);

    return res;
}
