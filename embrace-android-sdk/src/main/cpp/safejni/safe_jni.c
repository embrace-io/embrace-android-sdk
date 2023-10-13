#include <jni.h>
#include <stdbool.h>
#include "safe_jni.h"

/**
 * Clears any pending exceptions, and returns true if the JNI has a pending exception.
 * This usually indicates a programming error or an OutOfMemoryError. Embrace should
 * usually deal with this by gracefully no-oping from whatever JNI call we were
 * attempting to perform, although the recovery code depends on the exact scenario.
 *
 * @param env the JNI env
 * @return true if there is a pending exception.
 */
static bool jni_has_pending_exception(_Nonnull JNIEnv *_Nonnull env) {
    bool result = (*env)->ExceptionCheck(env);
    if (result) {
        (*env)->ExceptionClear(env);
    }
    return result;
}

_Nullable jclass emb_jni_find_class(_Nonnull JNIEnv *_Nonnull env,
                                    const char *_Nonnull clz) {
    jclass obj = (*env)->FindClass(env, clz);

    if (jni_has_pending_exception(env)) {
        return NULL;
    }
    return obj;
}

_Nullable jstring emb_jni_new_string_utf(_Nonnull JNIEnv *_Nonnull env,
                                         const char *_Nonnull src) {
    jstring obj = (*env)->NewStringUTF(env, src);

    if (jni_has_pending_exception(env)) {
        return NULL;
    }
    return obj;
}

_Nullable jmethodID emb_jni_get_method_id(_Nonnull JNIEnv *_Nonnull env,
                                          const _Nonnull jclass clz,
                                          const char *_Nonnull name,
                                          const char *_Nonnull sig) {
    jmethodID id = (*env)->GetMethodID(env, clz, name, sig);

    if (jni_has_pending_exception(env)) {
        return NULL;
    }
    return id;
}

_Nullable jclass emb_jni_find_class_global_ref(_Nonnull JNIEnv *_Nonnull env,
                                               const char *_Nonnull clz) {
    jclass obj = emb_jni_find_class(env, clz);

    if (obj == NULL) {
        return NULL;
    }
    return (*env)->NewGlobalRef(env, obj);
}

_Nullable jobject emb_jni_new_object(_Nonnull JNIEnv *_Nonnull env,
                                     const _Nonnull jclass clz,
                                     const _Nonnull jmethodID mthd,
                                     ...) {
    va_list vargs;
    va_start(vargs, mthd);
    jobject obj = (*env)->NewObjectV(env, clz, mthd, vargs);
    va_end(vargs);

    if (jni_has_pending_exception(env)) {
        return NULL;
    } else {
        return obj;
    }
}

jboolean emb_jni_call_boolean_method(_Nonnull JNIEnv *_Nonnull env,
                                     const _Nonnull jclass clz,
                                     const _Nonnull jmethodID mthd,
                                     ...) {
    va_list vargs;
    va_start(vargs, mthd);
    jboolean result = (*env)->CallBooleanMethodV(env, clz, mthd, vargs);
    va_end(vargs);

    if (jni_has_pending_exception(env)) {
        return false;
    } else {
        return result;
    }
}


jboolean emb_jni_call_void_method(_Nonnull JNIEnv *_Nonnull env,
                                     const _Nonnull jclass clz,
                                     const _Nonnull jmethodID mthd,
                                     ...) {
    va_list vargs;
    va_start(vargs, mthd);
    (*env)->CallVoidMethodV(env, clz, mthd, vargs);
    va_end(vargs);

    return jni_has_pending_exception(env);
}

void emb_jni_delete_local_ref(_Nonnull JNIEnv *_Nonnull env,
                              _Nonnull jobject obj) {
    (*env)->DeleteLocalRef(env, obj);
}
