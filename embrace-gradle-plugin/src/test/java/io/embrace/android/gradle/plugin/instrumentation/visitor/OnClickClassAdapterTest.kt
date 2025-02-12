package io.embrace.android.gradle.plugin.instrumentation.visitor

import io.embrace.android.gradle.plugin.instrumentation.ASM_API_VERSION
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.objectweb.asm.Opcodes

class OnClickClassAdapterTest {

    private val adapter = OnClickClassAdapter(ASM_API_VERSION, null) {}

    @Test
    fun testOnClickVisited() {
        val visitor = adapter.visitMethod(
            Opcodes.ACC_PUBLIC,
            "onClick",
            "(Landroid/view/View;)V",
            null,
            emptyArray()
        )
        assertTrue(visitor is OnClickMethodAdapter)
    }

    @Test
    fun testStaticOnClickVisited() {
        val access = Opcodes.ACC_STATIC.plus(Opcodes.ACC_SYNTHETIC)
        val visitor =
            adapter.visitMethod(access, "onClick", "(Landroid/view/View;)V", null, emptyArray())
        assertTrue(visitor is OnClickStaticMethodAdapter)
    }

    @Test
    fun testMethodNotVisited() {
        var visitor = adapter.visitMethod(Opcodes.ACC_PUBLIC, "onClick", "()V", null, emptyArray())
        assertFalse(visitor is OnClickMethodAdapter)

        visitor = adapter.visitMethod(
            Opcodes.ACC_PUBLIC,
            "foo",
            "(Landroid/view/View;)V",
            null,
            emptyArray()
        )
        assertFalse(visitor is OnClickMethodAdapter)
    }
}
