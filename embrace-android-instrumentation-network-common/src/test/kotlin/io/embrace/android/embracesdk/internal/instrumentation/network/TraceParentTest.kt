package io.embrace.android.embracesdk.internal.instrumentation.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class TraceParentTest {

    @Test
    fun `well-formed traceparents are parsed`() {
        assertNotNull(TraceParent.parse(VALID))
        assertNotNull(TraceParent.parse("00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-00"))
        assertNotNull(TraceParent.parse("00-00000000000000000000000000000001-0000000000000001-ff"))
    }

    @Test
    fun `malformed traceparents are not parsed`() {
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
            assertNull(it, TraceParent.parse(it))
        }
    }

    @Test
    fun `fields are read from the traceparent`() {
        with(checkNotNull(TraceParent.parse(VALID))) {
            assertEquals(VALID, value)
            assertEquals("0af7651916cd43dd8448eb211c80319c", traceId)
            assertEquals("b7ad6b7169203331", spanId)
            assertEquals("01", flags)
        }
    }

    @Test
    fun `traceparents with the same trace id share a trace`() {
        val traceparent = checkNotNull(TraceParent.parse(VALID))
        assertTrue(traceparent.isSameTrace("00-0af7651916cd43dd8448eb211c80319c-1111111111111111-00"))
        assertFalse(traceparent.isSameTrace("00-1af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01"))
        assertFalse(traceparent.isSameTrace("not-a-traceparent"))
    }

    private companion object {
        const val VALID = "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01"
    }
}
