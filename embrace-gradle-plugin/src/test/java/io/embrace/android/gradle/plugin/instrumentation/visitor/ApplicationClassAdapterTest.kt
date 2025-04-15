package io.embrace.android.gradle.plugin.instrumentation.visitor

import io.embrace.android.gradle.plugin.instrumentation.ASM_API_VERSION
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.objectweb.asm.Opcodes

class ApplicationClassAdapterTest {

    private val adapter = ApplicationClassAdapter(ASM_API_VERSION, null)

    @Test
    fun testOnCreateVisited() {
        val visitor = adapter.visitMethod(
            Opcodes.ACC_PUBLIC,
            "onCreate",
            "()V",
            null,
            emptyArray()
        )
        assertTrue(visitor is ApplicationMethodAdapter)
    }

    @Test
    fun testMethodNotVisited() {
        var visitor = adapter.visitMethod(
            Opcodes.ACC_PUBLIC,
            "onCreate",
            "(Landroid/content/Context;)V",
            null,
            emptyArray()
        )
        assertFalse(visitor is ApplicationMethodAdapter)

        visitor = adapter.visitMethod(
            Opcodes.ACC_PUBLIC,
            "onStart",
            "()V",
            null,
            emptyArray()
        )
        assertFalse(visitor is ApplicationMethodAdapter)
    }
}
