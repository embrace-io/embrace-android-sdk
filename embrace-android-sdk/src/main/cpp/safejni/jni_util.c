#include "jni_util.h"
#include <stdbool.h>

void emb_jni_release_string_utf_chars(JNIEnv *env, jstring string, const char *utf) {
    if (env != NULL && string != NULL && utf != NULL) {
        (*env)->ReleaseStringUTFChars(env, string, utf);
    }
}
