package io.embrace.gradle.plugin.instrumentation

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.util.CheckClassAdapter
import org.objectweb.asm.util.TraceClassVisitor
import java.io.PrintWriter
import java.io.StringWriter

private val REGEX_TRAILING_WHITESPACE = "[ ]*\n".toRegex()
private val REGEX_PACKAGE_NAME = "embrace-bytecode-instrumentation-tests_(debug|release)".toRegex()
private const val REPLACEMENT_PACKAGE_NAME = "embrace-bytecode-instrumentation-tests_release"

object InstrumentationRunner {

    /**
     * Loads a class & runs instrumentation against it, returning a string representation of
     * the bytecode output. This output is then compared against a known valid output & the test
     * is failed if it differs.
     */
    fun runInstrumentationAndCompareOutput(params: BytecodeTestParams) {
        val output = runInstrumentation(params)
        assertEquals(
            "The bytecode representation has changed from the known valid " +
                "output. Please confirm whether this is intentional by comparing the " +
                "input/output generated via TraceClassVisitor.",
            loadResourceAsText(params.expectedOutput),
            sanitizeOutput(output)
        )
    }

    /**
     * Loads a class & runs instrumentation against it, returning a string representation of
     * the bytecode output.
     */
    private fun runInstrumentation(
        params: BytecodeTestParams
    ): String {
        val reader = ClassReader(params.qualifiedClzName)
        val writer = ClassWriter(reader, ASM_CLASS_WRITER_FLAGS)

        // visit the class with a TraceClassVisitor which prints a textual representation
        // of the generated bytecode.
        val stringWriter = StringWriter()
        val traceVisitor = TraceClassVisitor(writer, PrintWriter(stringWriter))
        reader.accept(params.factory(traceVisitor, params), ASM_CLASS_READER_FLAGS)

        // perform a sanity check that the bytecode is well-formed
        val bytecode = writer.toByteArray()
        sanityCheckBytecode(bytecode)

        // return the output of the TraceClassVisitor
        return stringWriter.toString()
    }

    /**
     * Loads a resource and reads it as a [String].
     */
    private fun loadResourceAsText(resName: String): String {
        val classLoader = checkNotNull(javaClass.classLoader)
        val res = classLoader.getResourceAsStream(resName)
            ?: error("Could not find expected fixture resource named '$resName'")
        return res.bufferedReader().use { it.readText() }
    }

    /**
     * Removes trailing whitespace from the output, and normalize the package name
     * (which is set in Kotlin Metadata).
     */
    private fun sanitizeOutput(output: String): String {
        return output.replace(REGEX_TRAILING_WHITESPACE, "\n")
            .replace(REGEX_PACKAGE_NAME, REPLACEMENT_PACKAGE_NAME)
    }

    /**
     * Uses a [CheckClassAdapter] which sanity checks that the generated bytecode is well formed.
     */
    private fun sanityCheckBytecode(bytes: ByteArray) {
        val reader = ClassReader(bytes)
        val writer = ClassWriter(reader, ASM_CLASS_WRITER_FLAGS)
        val adapter = CheckClassAdapter(writer, true)
        reader.accept(adapter, ASM_CLASS_READER_FLAGS)
        assertArrayEquals(bytes, writer.toByteArray())
    }
}
