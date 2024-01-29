package io.embrace.android.embracesdk.internal.spans

import io.mockk.mockk
import io.opentelemetry.sdk.common.CompletableResultCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

internal class SpansSinkImplTests {
    private lateinit var spansSink: SpansSink

    @Before
    fun setup() {
        spansSink = SpansSinkImpl()
    }

    @Test
    fun `verify default state`() {
        assertEquals(0, spansSink.completedSpans().size)
        assertEquals(0, spansSink.flushSpans().size)
        assertEquals(CompletableResultCode.ofSuccess(), spansSink.storeCompletedSpans(listOf()))
    }

    @Test
    fun `flushing clears completed spans and current session span`() {
        spansSink.storeCompletedSpans(listOf(mockk(relaxed = true), mockk(relaxed = true)))
        val snapshot = spansSink.completedSpans()
        assertEquals(2, snapshot.size)

        val flushedSpans = spansSink.flushSpans()
        assertEquals(2, flushedSpans.size)
        repeat(2) {
            assertSame(snapshot[it], flushedSpans[it])
        }
        assertEquals(0, spansSink.completedSpans().size)
    }
}
