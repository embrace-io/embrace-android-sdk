//
// Created by Raul Striglio on 01/08/2022.
//

/* Wrapper Class to add extra stack frame to each error */
#include <vector>
#include <iostream>
#include <setjmp.h>

using namespace std;

class CrashSampleImplClass {
public:
    void sigill();

    void sigfpe();

    void sigsegv();

    void sigabort();

    void throwException();
};

void CrashSampleImplClass::sigill() {
    asm(".byte 0x0f, 0x0b");
}

int do_div_by_0() {
    int i;
    std::string a = "0";
    int j = 0;
    for (i = 20; i >= 0; i--) {
        j = 10 / j;
    }

    return j;
}

void CrashSampleImplClass::sigfpe() {
    printf("%d", do_div_by_0());
}

void CrashSampleImplClass::sigsegv() {
    *((char *) 0xdeadbaad) = 39;
}

void CrashSampleImplClass::sigabort() {
    abort();
}

void myterminate() {
    auto const ep = std::current_exception();
    throw ep;
}

void CrashSampleImplClass::throwException() {
    std::set_terminate(myterminate);
    throw std::runtime_error("Embrace Ndk Crash");
}
