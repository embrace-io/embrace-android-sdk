package io.embrace.android.gradle.plugin.instrumentation.config

import io.embrace.android.gradle.plugin.instrumentation.ASM_API_VERSION
import io.embrace.android.gradle.plugin.instrumentation.config.arch.ReturnType
import org.junit.Assert.assertEquals
import org.junit.Test
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type

/**
 * Generates a class whose method returns an enum, instruments it, then loads and invokes it. Unlike
 * the other return types this emits a static field read rather than a constant load, so it is worth
 * proving that the bytecode actually verifies.
 */
class EnumReturnValueMethodVisitorTest {

    private companion object {
        private const val SUBJECT_NAME = "EnumReturnValueSubject"
        private val ENUM_INTERNAL_NAME: String = Type.getInternalName(ReturnType::class.java)
        private val ENUM_DESCRIPTOR = "L$ENUM_INTERNAL_NAME;"
    }

    @Test
    fun `the instrumented constant is returned`() {
        assertEquals(ReturnType.MAP, invokeInstrumented(replacedValue = "MAP"))
    }

    @Test
    fun `every constant can be returned`() {
        ReturnType.entries.forEach { expected ->
            assertEquals(expected, invokeInstrumented(replacedValue = expected.name))
        }
    }

    private fun invokeInstrumented(replacedValue: String): Any? {
        val bytecode = generateSubject(replacedValue)
        val cls = SubjectClassLoader(bytecode).loadClass(SUBJECT_NAME)
        val instance = cls.getDeclaredConstructor().newInstance()
        return cls.getMethod("value").invoke(instance)
    }

    private fun generateSubject(replacedValue: String): ByteArray {
        val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, SUBJECT_NAME, null, "java/lang/Object", null)

        writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null).apply {
            visitCode()
            visitVarInsn(Opcodes.ALOAD, 0)
            visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
            visitInsn(Opcodes.RETURN)
            visitMaxs(0, 0)
            visitEnd()
        }

        // the uninstrumented body returns BOOLEAN, standing in for the SDK's own default
        val method = writer.visitMethod(Opcodes.ACC_PUBLIC, "value", "()$ENUM_DESCRIPTOR", null, null)
        with(EnumReturnValueMethodVisitor(ENUM_INTERNAL_NAME, replacedValue, ASM_API_VERSION, method)) {
            visitCode()
            visitFieldInsn(Opcodes.GETSTATIC, ENUM_INTERNAL_NAME, ReturnType.BOOLEAN.name, ENUM_DESCRIPTOR)
            visitInsn(Opcodes.ARETURN)
            visitMaxs(0, 0)
            visitEnd()
        }

        writer.visitEnd()
        return writer.toByteArray()
    }

    private class SubjectClassLoader(
        private val bytecode: ByteArray,
    ) : ClassLoader(SubjectClassLoader::class.java.classLoader) {

        override fun findClass(name: String): Class<*> {
            return when (name) {
                SUBJECT_NAME -> defineClass(name, bytecode, 0, bytecode.size)
                else -> super.findClass(name)
            }
        }
    }
}
