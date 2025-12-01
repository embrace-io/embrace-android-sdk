//
// Created by Francisco Prieto on 27/05/2022.
//

#include <cstdlib>
#include "AnotherClass.cpp"

/* This class was made just to add another layer of nested classes. It's far from what normal code
 * should look like, but it lets us test the signals 2 classes away from the jni function. */

class TestClass {
public:
    void sigill();

    void sigtrap();

    void sigbus();

    void sigfpe();

    void sigsegv();

    void sigabort();

    void throwException();
};

void TestClass::sigill() {
    AnotherClass anotherTestClass;
    anotherTestClass.sigill();
}

void TestClass::sigtrap() {
    AnotherClass anotherTestClass;
    anotherTestClass.sigtrap();
}

void TestClass::sigbus() {
    AnotherClass anotherTestClass;
    anotherTestClass.sigbus();
}

void TestClass::sigfpe() {
    AnotherClass anotherTestClass;
    anotherTestClass.sigfpe();
}

void TestClass::sigsegv() {
    AnotherClass anotherTestClass;
    anotherTestClass.sigsegv();
}

void TestClass::sigabort() {
    AnotherClass anotherTestClass;
    anotherTestClass.sigabort();
}

void TestClass::throwException() {
    AnotherClass anotherTestClass;
    anotherTestClass.throwException();
}
