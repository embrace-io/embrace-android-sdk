package io.embrace.android.gradle.plugin.instrumentation.config.local

import io.embrace.android.gradle.plugin.instrumentation.ASM_API_VERSION
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes

/**
 * Generates a class shaped like a generated SDK local config object, instruments it, then invokes its getters.
 */
class LocalConfigClassVisitorTest {

    private val json = """{"sdk_config":{"flag":true,"count":7,"size":9000000000,"name":"x","unset":null}}"""

    @Test
    fun `annotated getters return configured values`() {
        val subject = instrument(json)
        assertEquals(listOf(true, 7, 9000000000L, "x"), listOf("flag", "count", "size", "name").map(subject))
    }

    @Test
    fun `getters keep defaults when the path is absent, null or unannotated`() {
        assertEquals(listOf(false, 1, 2L, "default", 1), listOf("flag", "count", "size", "name", "unkeyed").map(instrument("{}")))
        assertEquals(1, instrument(json, mapOf("unset" to "()I"))("unset"))
    }

    @Test
    fun `mistyped values fail the build`() {
        val exc = assertThrows(IllegalArgumentException::class.java) { instrument(json, mapOf("name" to "()Z")) }
        assertEquals("embrace-config.json: 'sdk_config.name' is not a valid value for ()Z", exc.message)
    }

    private fun instrument(
        json: String,
        getters: Map<String, String> =
            mapOf("flag" to "()Z", "count" to "()I", "size" to "()J", "name" to "()Ljava/lang/String;"),
    ): (String) -> Any? {
        val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
        ClassReader(generateSubject(getters)).accept(LocalConfigClassVisitor(json, ASM_API_VERSION, writer), 0)
        val bytecode = writer.toByteArray()
        val cls = object : ClassLoader(javaClass.classLoader) {
            val subject: Class<*> = defineClass("Subject", bytecode, 0, bytecode.size)
        }.subject
        val instance = cls.getDeclaredConstructor().newInstance()
        return { name -> cls.getMethod(name).invoke(instance) }
    }

    private fun generateSubject(getters: Map<String, String>): ByteArray {
        val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "Subject", null, "java/lang/Object", null)
        writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null).apply {
            visitCode()
            visitVarInsn(Opcodes.ALOAD, 0)
            visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
            visitInsn(Opcodes.RETURN)
            visitMaxs(0, 0)
            visitEnd()
        }
        (getters + ("unkeyed" to "()I")).forEach { (name, descriptor) ->
            writer.visitMethod(Opcodes.ACC_PUBLIC, name, descriptor, null, null).apply {
                if (name != "unkeyed") {
                    visitAnnotation("Lio/embrace/android/embracesdk/internal/config/instrumented/LocalConfigKey;", false).apply {
                        visit("path", "sdk_config.$name")
                        visitEnd()
                    }
                }
                visitCode()
                when (descriptor) {
                    "()Z" -> visitInsn(Opcodes.ICONST_0).also { visitInsn(Opcodes.IRETURN) }
                    "()I" -> visitInsn(Opcodes.ICONST_1).also { visitInsn(Opcodes.IRETURN) }
                    "()J" -> visitLdcInsn(2L).also { visitInsn(Opcodes.LRETURN) }
                    else -> visitLdcInsn("default").also { visitInsn(Opcodes.ARETURN) }
                }
                visitMaxs(0, 0)
                visitEnd()
            }
        }
        writer.visitEnd()
        return writer.toByteArray()
    }
}
