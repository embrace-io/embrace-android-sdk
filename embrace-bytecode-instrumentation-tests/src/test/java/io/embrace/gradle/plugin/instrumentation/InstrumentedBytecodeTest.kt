package io.embrace.gradle.plugin.instrumentation

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.util.TraceClassVisitor

/**
 * Verifies that a [ClassVisitor] produces the correct bytecode output for a given class.
 *
 * For example, if a class implements [View.OnClickListener] then the embrace gradle plugin should
 * instrument the bytecode so that the first line of the onClick method contains a call to
 * [ViewSwazzledHooks._preOnClick]. If a class does not implement the interface, then its
 * bytecode should remain the same.
 *
 * The test achieves this verification using the following approach:
 *
 * 1. Define a class and load it via the default ClassLoader
 * 2. Create a [ClassReader] instance that reads the class in WebObject ASM
 * 3. Process the class using the [ClassVisitor] instance that instruments the bytecode
 * 4. Compare the bytecode representation obtained from a [TraceClassVisitor] with a known output
 *
 * To add more test cases please use [instrumentedBytecodeTestCases] and read the README.
 *
 * For more information on WebObject ASM, see https://asm.ow2.io/
 */
@RunWith(Parameterized::class)
class InstrumentedBytecodeTest(
    private val params: BytecodeTestParams
) {

    /**
     * To add more test cases please use [instrumentedBytecodeTestCases] and read the README.
     */
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{index}: {0}")
        fun testCases() = instrumentedBytecodeTestCases()
    }

    @Test
    fun testInstrumentedBytecode() {
        InstrumentationRunner.runInstrumentationAndCompareOutput(params)
    }
}
