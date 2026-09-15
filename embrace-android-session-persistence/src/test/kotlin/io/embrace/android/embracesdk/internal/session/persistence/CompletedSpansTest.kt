package io.embrace.android.embracesdk.internal.session.persistence

import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

internal class CompletedSpansTest {

    private companion object {
        private fun span(id: String, name: String) = SpanProto(
            trace_id = "0af7651916cd43dd8448eb211c80319c",
            span_id = id,
            name = name,
            start_time_unix_nano = 1_700_000_000_000_000_000L,
            end_time_unix_nano = 1_700_000_001_000_000_000L,
            status = SpanProto.Status.OK,
            attributes = listOf(AttributeProto("emb.type", "perf")),
        )
    }

    @Test
    fun `empty log round-trips`() {
        val log = CompletedSpans()
        assertEquals(emptyList<SpanProto>(), CompletedSpans.ADAPTER.decode(CompletedSpans.ADAPTER.encode(log)).spans)
    }

    @Test
    fun `multi span log round-trips in order`() {
        val log = CompletedSpans(
            spans = listOf(span("aaaaaaaaaaaaaaa1", "first"), span("aaaaaaaaaaaaaaa2", "second")),
        )
        assertEquals(log, CompletedSpans.ADAPTER.decode(CompletedSpans.ADAPTER.encode(log)))
    }

    @Test
    fun `concatenated records decode as a single log in append order`() {
        val spans = listOf(
            span("aaaaaaaaaaaaaaa1", "first"),
            span("aaaaaaaaaaaaaaa2", "second"),
            span("aaaaaaaaaaaaaaa3", "third"),
        )

        val appended = Buffer().apply {
            spans.forEach { write(CompletedSpans.ADAPTER.encode(CompletedSpans(spans = listOf(it)))) }
        }

        val decoded = CompletedSpans.ADAPTER.decode(appended.readByteArray())

        assertEquals(spans, decoded.spans)
    }

    @Test
    fun `decode throws on a torn final record instead of returning the complete ones`() {
        val complete = CompletedSpans.ADAPTER.encode(
            CompletedSpans(spans = listOf(span("aaaaaaaaaaaaaaa1", "first"), span("aaaaaaaaaaaaaaa2", "second"))),
        )
        val torn = complete.copyOf(complete.size - 1)

        try {
            CompletedSpans.ADAPTER.decode(torn)
            fail("expected decoding a torn record to throw")
        } catch (expected: IOException) {
            // the records before the torn one are unreachable, hence the tolerant reader
        }
    }
}
