#include "jni_util.h"
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

void emb_jni_release_string_utf_chars(JNIEnv *env, jstring string, const char *utf) {
    if (env != NULL && string != NULL && utf != NULL) {
        (*env)->ReleaseStringUTFChars(env, string, utf);
    }
}

#ifdef __cplusplus
}
#endif
