package io.embrace.android.embracesdk.internal.utils

import org.junit.Assert.assertNull
import org.junit.Test

internal class ThrowableUtilsTest {

    @Test
    fun `test safe stacktrace`() {
        assertNull(DangerousException().getSafeStackTrace())
    }

    class DangerousException : Exception("DangerousException") {
        override fun getStackTrace(): Array<StackTraceElement> = error("lol")
    }
}
