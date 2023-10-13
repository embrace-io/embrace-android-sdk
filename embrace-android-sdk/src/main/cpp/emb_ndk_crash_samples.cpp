#include <cstdlib>
#include <csignal>
#include <string>
#include <jni.h>
#include "CrashSampleClass.cpp"

using namespace std;

/* Wrapper Class to add extra stack frame to each error */
class EmbCrashSampleClass {
public:
    static void sigill();

    static void sigfpe();

    static void sigsegv();

    static void sigabort();

    static void throwException();
};

void EmbCrashSampleClass::throwException() {
    CrashSampleClass crashSampleImplClass;
    crashSampleImplClass.throwException();
}

void EmbCrashSampleClass::sigabort() {
    CrashSampleClass crashSampleImplClass;
    crashSampleImplClass.sigabort();
}

void EmbCrashSampleClass::sigsegv() {
    CrashSampleClass crashSampleImplClass;
    crashSampleImplClass.sigsegv();
}

void EmbCrashSampleClass::sigill() {
    CrashSampleClass crashSampleImplClass;
    crashSampleImplClass.sigill();
}

void EmbCrashSampleClass::sigfpe() {
    CrashSampleClass crashSampleImplClass;
    crashSampleImplClass.sigfpe();
}

extern "C"
JNIEXPORT void JNICALL
Java_io_embrace_android_embracesdk_samples_EmbraceCrashSamplesNdkDelegateImpl_sigfpe(JNIEnv *env,
                                                                                     jobject thiz) {
    EmbCrashSampleClass embCrashSampleClass;
    embCrashSampleClass.sigfpe();
}

extern "C"
JNIEXPORT void JNICALL
Java_io_embrace_android_embracesdk_samples_EmbraceCrashSamplesNdkDelegateImpl_sigsegv(JNIEnv *env,
                                                                                      jobject thiz) {
    EmbCrashSampleClass embCrashSampleClass;
    embCrashSampleClass.sigsegv();
}

extern "C"
JNIEXPORT void JNICALL
Java_io_embrace_android_embracesdk_samples_EmbraceCrashSamplesNdkDelegateImpl_sigAbort(JNIEnv *env,
                                                                                       jobject thiz) {
    EmbCrashSampleClass embCrashSampleClass;
    embCrashSampleClass.sigabort();
}

extern "C"
JNIEXPORT void JNICALL
Java_io_embrace_android_embracesdk_samples_EmbraceCrashSamplesNdkDelegateImpl_sigIllegalInstruction(
        JNIEnv *env, jobject thiz) {
    EmbCrashSampleClass embCrashSampleClass;
    embCrashSampleClass.sigill();
}
extern "C"
JNIEXPORT void JNICALL
Java_io_embrace_android_embracesdk_samples_EmbraceCrashSamplesNdkDelegateImpl_throwException(
        JNIEnv *env,
        jobject thiz) {
    EmbCrashSampleClass embCrashSampleClass;
    embCrashSampleClass.throwException();
}
