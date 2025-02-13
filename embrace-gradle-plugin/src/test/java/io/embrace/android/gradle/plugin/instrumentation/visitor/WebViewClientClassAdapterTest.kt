package io.embrace.android.gradle.plugin.instrumentation.visitor

import io.embrace.android.gradle.plugin.instrumentation.ASM_API_VERSION
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.objectweb.asm.Opcodes

class WebViewClientClassAdapterTest {

    private val adapter = WebViewClientClassAdapter(ASM_API_VERSION, null) {}

    @Test
    fun testOnPageStartedVisited() {
        val visitor = adapter.visitMethod(
            Opcodes.ACC_PUBLIC,
            "onPageStarted",
            "(Landroid/webkit/WebView;Ljava/lang/String;Landroid/graphics/Bitmap;)V",
            null,
            emptyArray()
        )
        assertTrue(visitor is WebViewClientMethodAdapter)
    }

    @Test
    fun testMethodNotVisited() {
        var visitor = adapter.visitMethod(
            Opcodes.ACC_PUBLIC,
            "onPageStarted",
            "(Landroid/webkit/WebView;Ljava/lang/Boolean;Landroid/graphics/Bitmap;)V",
            null,
            emptyArray()
        )
        assertFalse(visitor is WebViewClientMethodAdapter)

        visitor = adapter.visitMethod(
            Opcodes.ACC_PUBLIC,
            "onPageStart",
            "(Landroid/webkit/WebView;Ljava/lang/String;Landroid/graphics/Bitmap;)V",
            null,
            emptyArray()
        )
        assertFalse(visitor is WebViewClientMethodAdapter)
    }
}
