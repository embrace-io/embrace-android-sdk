#ifndef EMBRACE_SAFE_JNI_H
#define EMBRACE_SAFE_JNI_H

/**
 * Calls (*env)->FindClass and clears any pending exceptions, returning
 * null if an exception occurred.
 */
_Nullable jclass emb_jni_find_class(_Nonnull JNIEnv *_Nonnull env,
                                    const char *_Nonnull clz);

/**
 * Calls (*env)->NewStringUtf and clears any pending exceptions, returning
 * null if an exception occurred.
 */
_Nullable jstring emb_jni_new_string_utf(_Nonnull JNIEnv *_Nonnull env,
                                         const char *_Nonnull src);

/**
 * Calls (*env)->GetMethodId and clears any pending exceptions, returning
 * null if an exception occurred.
 */
_Nullable jmethodID emb_jni_get_method_id(_Nonnull JNIEnv *_Nonnull env,
                                          const _Nonnull jclass clz,
                                          const char *_Nonnull name,
                                          const char *_Nonnull sig);

/**
 * Calls (*env)->FindClass and creates a global ref. This method also clears any
 * pending exceptions, and returns null if an exception occurred.
 */
_Nullable jclass emb_jni_find_class_global_ref(_Nonnull JNIEnv *_Nonnull env,
                                               const char *_Nonnull clz);

/**
 * Calls (*env)->NewObject and clears any pending exceptions, returning
 * null if an exception occurred.
 */
_Nullable jobject emb_jni_new_object(_Nonnull JNIEnv *_Nonnull env,
                                     const _Nonnull jclass clz,
                                     const _Nonnull jmethodID mthd,
                                     ...);

/**
 * Calls (*env)->CallBooleanMethod and clears any pending exceptions, returning
 * false if an exception occurred.
 */
jboolean emb_jni_call_boolean_method(_Nonnull JNIEnv *_Nonnull env,
                                     const _Nonnull jclass clz,
                                     const _Nonnull jmethodID mthd,
                                     ...);

/**
 * Calls (*env)->CallVoidMethod and clears any pending exceptions, returning
 * false if an exception occurred.
 */
jboolean emb_jni_call_void_method(_Nonnull JNIEnv *_Nonnull env,
                                     const _Nonnull jclass clz,
                                     const _Nonnull jmethodID mthd,
                                     ...);

/**
 * Calls (*env)->DeleteLocalRef.
 */
void emb_jni_delete_local_ref(_Nonnull JNIEnv *_Nonnull env,
                              _Nonnull jobject obj);


#endif //EMBRACE_SAFE_JNI_H
