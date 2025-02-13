# Embrace Gradle Plugin Test Fixtures

This module contains test fixture classes which are instrumented in unit tests. The bytecode is then compared against
known good output to confirm the functionality is working.

To add a new test case you should:

1. Create a new JVM class
2. Add the class to the parameterized `InstrumentedBytecodeTest` and run the test using the `defaultFactory` (this does not manipulate bytecode)
3. Create a resource named `$clz.simpleName_expected.txt` containing the default bytecode representation
4. Alter the test so that the factory returns the `ClassVisitor` that will manipulate its bytecode
