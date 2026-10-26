package io.embrace.android.embracesdk.internal.instrumentation.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class W3cTraceparentTest {

    @Test
    fun `well-formed traceparents are valid`() {
        assertTrue(W3cTraceparent.isValid(VALID))
        assertTrue(W3cTraceparent.isValid("00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-00"))
        assertTrue(W3cTraceparent.isValid("00-00000000000000000000000000000001-0000000000000001-ff"))
    }

    @Test
    fun `malformed traceparents are invalid`() {
        listOf(
            "",
            "not-a-traceparent",
            "b7ad6b7169203331",
            "0af7651916cd43dd8448eb211c80319c",
            "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331",
            "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01-extra",
            "00-0af7-b7ad6b7169203331-01",
            "00-0af7651916cd43dd8448eb211c80319c-b7ad-01",
            "00-0AF7651916CD43DD8448EB211C80319C-B7AD6B7169203331-01",
            "ff-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01",
            "01-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01",
            "00-00000000000000000000000000000000-b7ad6b7169203331-01",
            "00-0af7651916cd43dd8448eb211c80319c-0000000000000000-01",
            "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-0g",
            "00_0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01",
            "00-0af7651916cd43dd8448eb211c80319c_b7ad6b7169203331-01",
            "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331_01",
        ).forEach {
            assertFalse(it, W3cTraceparent.isValid(it))
        }
    }

    @Test
    fun `traceparents with the same trace id share a trace`() {
        assertTrue(W3cTraceparent.isSameTrace(VALID, "00-0af7651916cd43dd8448eb211c80319c-1111111111111111-00"))
        assertFalse(W3cTraceparent.isSameTrace(VALID, "00-1af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01"))
        assertFalse(W3cTraceparent.isSameTrace(VALID, "not-a-traceparent"))
    }

    private companion object {
        const val VALID = "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01"
    }
}
