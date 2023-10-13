//
// Created by Fredric Newberg on 8/18/21.
//

#ifndef EMBRACE_ANDROID_SDK3_JNI_UTIL_H
#define EMBRACE_ANDROID_SDK3_JNI_UTIL_H

#include <jni.h>

void emb_jni_release_string_utf_chars(JNIEnv *env, jstring string, const char *utf);

#endif //EMBRACE_ANDROID_SDK3_JNI_UTIL_H
