//
// Created by Francisco Prieto on 27/05/2022.
//

#include <cstdlib>
#include <csignal>
#include <string>

using namespace std;

class AnotherClass {
public:
    void sigill();

    void sigtrap();

    void sigbus();

    void sigfpe();

    void sigsegv();

    void sigabort();

    void throwException();
};

void AnotherClass::sigill(){
    asm(".byte 0x0f, 0x0b");
}

void AnotherClass::sigtrap(){
    raise(SIGTRAP);
}

void AnotherClass::sigbus(){
    raise(SIGBUS);
}

void AnotherClass::sigfpe(){
    raise(SIGFPE);
}

void AnotherClass::sigsegv(){
    *(char *)0 = 0;
}

void AnotherClass::sigabort(){
    abort();
}

void AnotherClass::throwException(){
    throw "Hola";
}

