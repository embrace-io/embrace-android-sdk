package io.embrace.gradle.plugin.instrumentation

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import kotlin.reflect.KClass

// constants checked against AGP 8.9.1 and 9.5.3

// com.android.build.gradle.internal.instrumentation.InstrumentationUtils: ASM_API_VERSION_FOR_INSTRUMENTATION (AGP 8),
// ASM_API_VERSION (AGP 9)
internal const val ASM_API_VERSION = Opcodes.ASM9

// com.android.build.gradle.internal.instrumentation.AsmInstrumentationManager#getClassReaderFlags, for the
// plugin's FramesComputationMode.COMPUTE_FRAMES_FOR_INSTRUMENTED_METHODS
internal const val ASM_CLASS_READER_FLAGS = ClassReader.EXPAND_FRAMES

// com.android.build.gradle.internal.instrumentation.AsmInstrumentationManager#getClassWriterFlags, for the
// plugin's FramesComputationMode.COMPUTE_FRAMES_FOR_INSTRUMENTED_METHODS
internal const val ASM_CLASS_WRITER_FLAGS = ClassWriter.COMPUTE_FRAMES

internal typealias ClassVisitorFactory = (nextVisitor: ClassVisitor, params: BytecodeTestParams) -> ClassVisitor

/**
 * Test parameters used to instrument bytecode.
 */
class BytecodeTestParams(
    clz: KClass<*>,
    val qualifiedClzName: String = clz.java.name,
    val simpleClzName: String = clz.java.simpleName,
    val expectedOutput: String = "${simpleClzName}_expected.txt",
    val factory: ClassVisitorFactory = { nextVisitor, _ ->
        nextVisitor
    },
) {

    companion object {
        fun forInnerClass(
            clz: KClass<*>,
            innerClzName: String,
            factory: ClassVisitorFactory = { nextVisitor, _ ->
                nextVisitor
            },
        ): BytecodeTestParams {
            return BytecodeTestParams(
                clz,
                qualifiedClzName = "${clz.java.name}$innerClzName",
                factory = factory
            )
        }
    }

    override fun toString() = "$simpleClzName => $expectedOutput"
}
