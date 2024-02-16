package io.embrace.android.embracesdk.internal.spans

import io.mockk.mockk
import io.opentelemetry.sdk.common.CompletableResultCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

internal class SpanSinkImplTests {
    private lateinit var spanSink: SpanSink

    @Before
    fun setup() {
        spanSink = SpanSinkImpl()
    }

    @Test
    fun `verify default state`() {
        assertEquals(0, spanSink.completedSpans().size)
        assertEquals(0, spanSink.flushSpans().size)
        assertEquals(CompletableResultCode.ofSuccess(), spanSink.storeCompletedSpans(listOf()))
    }

    @Test
    fun `flushing clears completed spans and current session span`() {
        spanSink.storeCompletedSpans(listOf(mockk(relaxed = true), mockk(relaxed = true)))
        val snapshot = spanSink.completedSpans()
        assertEquals(2, snapshot.size)

        val flushedSpans = spanSink.flushSpans()
        assertEquals(2, flushedSpans.size)
        repeat(2) {
            assertSame(snapshot[it], flushedSpans[it])
        }
        assertEquals(0, spanSink.completedSpans().size)
    }
}
