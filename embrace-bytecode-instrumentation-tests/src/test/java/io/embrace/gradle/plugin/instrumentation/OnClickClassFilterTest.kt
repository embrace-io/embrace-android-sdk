package io.embrace.gradle.plugin.instrumentation

import io.embrace.android.gradle.plugin.instrumentation.visitor.OnClickClassAdapter
import org.junit.Assert.assertTrue
import org.junit.Test

class OnClickClassFilterTest {

    @Test
    fun testClassAccepted() {
        val ctx = FakeClassContext("org/test/FooBar")
        assertTrue(OnClickClassAdapter.accept(ctx))
    }
}
