package io.embrace.android.gradle.plugin.instrumentation.visitor

import io.embrace.android.gradle.plugin.instrumentation.ASM_API_VERSION
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.objectweb.asm.Opcodes

class OnLongClickClassAdapterTest {

    private val adapter = OnLongClickClassAdapter(ASM_API_VERSION, null) {}

    @Test
    fun testOnLongClickVisited() {
        val visitor = adapter.visitMethod(
            Opcodes.ACC_PUBLIC,
            "onLongClick",
            "(Landroid/view/View;)Z",
            null,
            emptyArray()
        )
        assertTrue(visitor is OnLongClickMethodAdapter)
    }

    @Test
    fun testStaticOnLongClickVisited() {
        val access = Opcodes.ACC_STATIC.plus(Opcodes.ACC_SYNTHETIC)
        val visitor =
            adapter.visitMethod(access, "onLongClick", "(Landroid/view/View;)Z", null, emptyArray())
        assertTrue(visitor is OnLongClickStaticMethodAdapter)
    }

    @Test
    fun testMethodNotVisited() {
        var visitor =
            adapter.visitMethod(Opcodes.ACC_PUBLIC, "onLongClick", "()V", null, emptyArray())
        assertFalse(visitor is OnLongClickMethodAdapter)

        visitor = adapter.visitMethod(
            Opcodes.ACC_PUBLIC,
            "foo",
            "(Landroid/view/View;)Z",
            null,
            emptyArray()
        )
        assertFalse(visitor is OnLongClickMethodAdapter)
    }
}
