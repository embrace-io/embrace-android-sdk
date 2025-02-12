package io.embrace.gradle.plugin.instrumentation

import io.embrace.android.gradle.plugin.instrumentation.visitor.WebViewClientClassAdapter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebViewClassFilterTest {

    @Test
    fun testClassAccepted() {
        val ctx = FakeClassContext(
            FakeClassData(
                "",
                superClasses = listOf("android.webkit.WebViewClient")
            )
        )
        assertTrue(WebViewClientClassAdapter.accept(ctx))
    }

    @Test
    fun testClassNotAccepted() {
        val ctx = FakeClassContext(
            FakeClassData(
                "",
                superClasses = emptyList()
            )
        )
        assertFalse(WebViewClientClassAdapter.accept(ctx))
    }
}
