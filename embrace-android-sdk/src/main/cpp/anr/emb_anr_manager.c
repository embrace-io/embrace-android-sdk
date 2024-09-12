//
// Created by Fredric Newberg on 10/14/21.
//

#include "anr.h"
#include <android/log.h>
#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jint JNICALL
Java_io_embrace_android_embracesdk_internal_anr_sigquit_EmbraceSigquitNdkDelegate_installGoogleAnrHandler(
        JNIEnv *env,
        jobject thiz,
        jint google_thread_id,
        jobject sigquit_data_source
        ) {
    return emb_install_google_anr_handler(env, sigquit_data_source, google_thread_id);
}

#ifdef __cplusplus
}
#endif


