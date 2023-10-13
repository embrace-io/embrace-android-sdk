//
// Created by Fredric Newberg on 10/14/21.
//

#ifndef EMBRACE_ANDROID_SDK3_ANR_H
#define EMBRACE_ANDROID_SDK3_ANR_H

#include <jni.h>
#include <stdbool.h>

#define GOOGLE_THREAD_ID_DEFAULT -1

#define EMB_ANR_INSTALL_NO_SEMAPHORE 1 << 0
#define EMB_ANR_INSTALL_WATCHDOG_THREAD_CREATE_FAIL 1 << 1
#define EMB_ANR_INSTALL_HANDLER_FAIL 1 << 2

int emb_install_google_anr_handler(JNIEnv *env, jobject anr_service, jint _google_thread_id);

#endif //EMBRACE_ANDROID_SDK3_ANR_H
