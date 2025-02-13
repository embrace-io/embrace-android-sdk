package io.embrace.gradle.plugin.instrumentation

import io.embrace.android.gradle.plugin.instrumentation.visitor.OkHttpClassAdapter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OkHttpClassFilterTest {

    @Test
    fun testClassAccepted() {
        val ctx = FakeClassContext("okhttp3.OkHttpClient\$Builder")
        assertTrue(OkHttpClassAdapter.accept(ctx))
    }

    @Test
    fun testClassNotAccepted() {
        val ctx = FakeClassContext("okhttp3.OkHttpClient")
        assertFalse(OkHttpClassAdapter.accept(ctx))
    }
}
