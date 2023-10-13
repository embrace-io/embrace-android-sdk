//
// Created by Raul Striglio on 01/08/2022.
//

/* Wrapper Class to add extra stack frame to each error */
#include "CrashSamplesImplClass.cpp"

class CrashSampleClass {
public:
    void sigill();

    void sigfpe();

    void sigsegv();

    void sigabort();

    void throwException();
};

void CrashSampleClass::sigill(){
    CrashSampleImplClass crashSampleImplClass;
    crashSampleImplClass.sigill();
}

void CrashSampleClass::sigfpe(){
    CrashSampleImplClass crashSampleImplClass;
    crashSampleImplClass.sigfpe();
}

void CrashSampleClass::sigsegv(){
    CrashSampleImplClass crashSampleImplClass;
    crashSampleImplClass.sigsegv();
}

void CrashSampleClass::sigabort(){
    CrashSampleImplClass crashSampleImplClass;
    crashSampleImplClass.sigabort();
}

void CrashSampleClass::throwException(){
    CrashSampleImplClass crashSampleImplClass;
    crashSampleImplClass.throwException();
}
