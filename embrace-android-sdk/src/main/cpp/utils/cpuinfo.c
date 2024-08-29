//
// Created by ignacio saslavsky on 14/12/2022.
//

#include <jni.h>
#include <stdbool.h>
#include "emb_log.h"
#include "../safejni/safe_jni.h"

// defined PROP_VALUE_MAX  92 in system_properties.h
// https://android.googlesource.com/platform/bionic/+/466dbe4/libc/include/sys/system_properties.h

#define CPUINFO_BUILD_PROP_VALUE_MAX 92
#define CPUINFO_CPU_NAME_KEY "ro.board.platform"
#define CPUINFO_EGL_KEY "ro.hardware.egl"


void emb_cpuinfo_android_property_get(const char* key, char* value) {
    __system_property_get(key, value);
}

jstring emb_get_property(JNIEnv *env, const char* key) {
    char property_value[CPUINFO_BUILD_PROP_VALUE_MAX];
    emb_cpuinfo_android_property_get(key, property_value);
    return emb_jni_new_string_utf(env, property_value);
}

JNIEXPORT jstring JNICALL
Java_io_embrace_android_embracesdk_internal_capture_cpu_EmbraceCpuInfoNdkDelegate_getNativeCpuName(JNIEnv* env, jobject thiz) {
    return emb_get_property(env, CPUINFO_CPU_NAME_KEY);
}

JNIEXPORT jstring JNICALL
Java_io_embrace_android_embracesdk_internal_capture_cpu_EmbraceCpuInfoNdkDelegate_getNativeEgl(JNIEnv* env, jobject thiz) {
    return emb_get_property(env, CPUINFO_EGL_KEY);
}
