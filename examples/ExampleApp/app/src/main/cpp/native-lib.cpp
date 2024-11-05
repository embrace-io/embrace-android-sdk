#include <jni.h>
#include <string>

extern "C"
JNIEXPORT void JNICALL
Java_io_embrace_android_exampleapp_ui_examples_NdkCrashExampleKt_abort(JNIEnv *env, jclass clazz) {
    abort();
}

extern "C"
JNIEXPORT void JNICALL
Java_io_embrace_android_exampleapp_ui_examples_NdkCrashExampleKt_segfault(JNIEnv *env,
                                                                          jclass clazz) {
    memset((char *) 0x123, 1, 100);
}
extern "C"
JNIEXPORT void JNICALL
Java_io_embrace_android_exampleapp_ui_examples_NdkCrashExampleKt_sigill(JNIEnv *env, jclass clazz) {
    asm(".byte 0x0f, 0x0b");
}
extern "C"
JNIEXPORT void JNICALL
Java_io_embrace_android_exampleapp_ui_examples_NdkCrashExampleKt_throwException(JNIEnv *env,
                                                                                jclass clazz) {
    throw "Hola";
}
