#include <jni.h>
#include <string>
#include <android/log.h>
#include <vector>
#include "TestClass.cpp"

using namespace std;

#ifndef EMB_LOGINFO
#define EMB_LOGINFO(fmt, ...)                                                  \
  __android_log_print(ANDROID_LOG_INFO, "emb_ndk", fmt, ##__VA_ARGS__)
#endif

extern "C" JNIEXPORT void JNICALL
Java_io_embrace_ndktestapp_NativeDelegate_abort(
        JNIEnv *env,
        jobject /* this */) {
    EMB_LOGINFO("Attempt to crash the first one: calling abort() method");
    abort();
}

extern "C" JNIEXPORT void JNICALL
Java_io_embrace_ndktestapp_NativeDelegate_divideByZero(
        JNIEnv *env,
        jobject /* this */) {
    int a = 0;
    int b;
    EMB_LOGINFO("Attempt to crash the second one: trying to divide 1/0");
    b = 1 / a;
}

extern "C" JNIEXPORT void JNICALL
Java_io_embrace_ndktestapp_NativeDelegate_arrayOutOfBounds(
        JNIEnv *env,
        jobject /* this */) {
    EMB_LOGINFO("array out of bounds");
    int foo[10];
    for (int i = 0; i <= 10; i++) foo[i] = i;
}

//This ANR never finishes.
extern "C" JNIEXPORT void JNICALL
Java_io_embrace_ndktestapp_NativeDelegate_indefiniteAnr(
        JNIEnv *env,
        jobject /* this */) {
    EMB_LOGINFO("Generating ANR");
    vector<jstring> memoryEater;
    char *a = NULL;
    a = (char *) malloc(SIZE_MAX);
    while (true) {
        memoryEater.push_back(env->NewStringUTF(a));
    }
}

int finiteANR(int iterations) {
    //passing iterations as a parameter avoids compiler optimizations
    int a;
    for (int i = 0; i < iterations; i++) {
        if (i % 1000000000 == 0) {
            // to show less logs, increment modulo function
            EMB_LOGINFO("Remaining = %d", INT_MAX - i);
        }
        a = i * 200 + 100;
    }
    return a;
}

//This ANR is momentary, and should end after a while.
extern "C" JNIEXPORT void JNICALL
Java_io_embrace_ndktestapp_NativeDelegate_definiteAnr(
        JNIEnv *env,
        jobject /* this */) {
    EMB_LOGINFO("Causing a finite ANR");
    finiteANR(INT_MAX);
}

extern "C" JNIEXPORT void JNICALL
Java_io_embrace_ndktestapp_NativeDelegate_executeSIGILLSignal(
        JNIEnv *env,
        jobject /* this */) {
    TestClass test;
    test.sigill();
}

extern "C" JNIEXPORT void JNICALL
Java_io_embrace_ndktestapp_NativeDelegate_executeSIGTRAPSignal(
        JNIEnv *env,
        jobject /* this */) {
    TestClass test;
    test.sigtrap();
}

extern "C" JNIEXPORT void JNICALL
Java_io_embrace_ndktestapp_NativeDelegate_executeSIGBUSSignal(
        JNIEnv *env,
        jobject /* this */) {
    TestClass test;
    test.sigbus();
}

extern "C" JNIEXPORT void JNICALL
Java_io_embrace_ndktestapp_NativeDelegate_executeSIGFPESignal(
        JNIEnv *env,
        jobject /* this */) {
    TestClass test;
    test.sigfpe();
}

extern "C" JNIEXPORT void JNICALL
Java_io_embrace_ndktestapp_NativeDelegate_executeSIGSEGVSignal(
        JNIEnv *env,
        jobject /* this */) {
    TestClass test;
    test.sigsegv();
}

extern "C" JNIEXPORT void JNICALL
Java_io_embrace_ndktestapp_NativeDelegate_executeSIGABRTSignal(
        JNIEnv *env,
        jobject /* this */) {
    TestClass test;
    test.sigabort();
}

extern "C" JNIEXPORT void JNICALL
Java_io_embrace_ndktestapp_NativeDelegate_throwCPPException(
        JNIEnv *env,
        jobject /* this */) {
    TestClass test;
    test.throwException();
}