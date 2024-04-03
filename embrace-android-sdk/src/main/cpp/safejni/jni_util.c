#include "jni_util.h"
#include <stdbool.h>

JavaVM *emb_JVM;

jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    emb_JVM = vm;
    return JNI_VERSION_1_6;
}

#ifdef __cplusplus
extern "C" {
#endif
bool emb_jniIsAttached() {
    JNIEnv *env;
    int status = (*emb_JVM)->GetEnv(emb_JVM, (void **)&env, JNI_VERSION_1_6);

    return status == JNI_OK;
}

void emb_jni_release_string_utf_chars(JNIEnv *env, jstring string, const char *utf) {
    if (env != NULL && string != NULL && utf != NULL) {
        (*env)->ReleaseStringUTFChars(env, string, utf);
    }
}

#ifdef __cplusplus
}
#endif
