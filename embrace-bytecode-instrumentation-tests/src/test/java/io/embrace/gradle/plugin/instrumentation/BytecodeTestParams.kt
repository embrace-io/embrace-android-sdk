package io.embrace.gradle.plugin.instrumentation

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import kotlin.reflect.KClass

// constants checked against AGP 7.4.2

// com.android.build.gradle.internal.instrumentation.InstrumentationUtils.ASM_API_VERSION_FOR_INSTRUMENTATION
internal const val ASM_API_VERSION = Opcodes.ASM9

// com.android.build.gradle.internal.instrumentation.AsmInstrumentationManager#getClassReaderFlags
internal const val ASM_CLASS_READER_FLAGS = ClassReader.EXPAND_FRAMES

// com.android.build.gradle.internal.instrumentation.AsmInstrumentationManager#getClassWriterFlags
internal const val ASM_CLASS_WRITER_FLAGS = ClassWriter.COMPUTE_FRAMES

internal typealias ClassVisitorFactory = (nextVisitor: ClassVisitor) -> ClassVisitor

/**
 * Test parameters used to instrument bytecode.
 */
class BytecodeTestParams(
    clz: Class<*>,
    val qualifiedClzName: String = clz.name,
    val simpleClzName: String = clz.simpleName,
    val expectedOutput: String = "${simpleClzName}_expected.txt",
    val factory: ClassVisitorFactory = { nextVisitor ->
        nextVisitor
    }
) {

    companion object {
        fun forInnerClass(
            kClass: KClass<*>,
            innerClzName: String,
            factory: ClassVisitorFactory = { nextVisitor ->
                nextVisitor
            }
        ): BytecodeTestParams {
            return BytecodeTestParams(
                kClass.java,
                qualifiedClzName = "${kClass.java.name}$innerClzName",
                factory = factory
            )
        }
    }

    override fun toString() = "$simpleClzName => $expectedOutput"
}
