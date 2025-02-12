package io.embrace.android.gradle.plugin.instrumentation.visitor

import io.embrace.android.gradle.plugin.instrumentation.ASM_API_VERSION
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.objectweb.asm.Opcodes

class OkHttpClassAdapterTest {

    private val adapter = OkHttpClassAdapter(ASM_API_VERSION, null) {}

    @Test
    fun testCtorVisited() {
        val visitor = adapter.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, emptyArray())
        assertFalse(visitor is OkHttpMethodAdapter)
    }

    @Test
    fun testBuildVisited() {
        val visitor = adapter.visitMethod(
            Opcodes.ACC_PUBLIC,
            "build",
            "()Lokhttp3/OkHttpClient;",
            null,
            emptyArray()
        )
        assertTrue(visitor is OkHttpMethodAdapter)
    }

    @Test
    fun testMethodNotVisited() {
        var visitor = adapter.visitMethod(
            Opcodes.ACC_PUBLIC,
            "foo",
            "()Lokhttp3/OkHttpClient;",
            null,
            emptyArray()
        )
        assertFalse(visitor is OkHttpMethodAdapter)

        visitor = adapter.visitMethod(
            Opcodes.ACC_PUBLIC,
            "build",
            "()Lokhttp3/OkHttpClient\$Builder;",
            null,
            emptyArray()
        )
        assertFalse(visitor is OkHttpMethodAdapter)
    }
}
