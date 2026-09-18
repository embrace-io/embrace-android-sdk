package io.embrace.android.embracesdk.internal.session.persistence

import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

internal class SpanCollectionTest {

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
    fun `empty collection round-trips`() {
        val collection = SpanCollection()
        val decoded = SpanCollection.ADAPTER.decode(SpanCollection.ADAPTER.encode(collection))
        assertEquals(emptyList<SpanProto>(), decoded.spans)
    }

    @Test
    fun `multi span collection round-trips in order`() {
        val collection = SpanCollection(
            spans = listOf(
                span("aaaaaaaaaaaaaaa1", "first"),
                span("aaaaaaaaaaaaaaa2", "second"),
                span("aaaaaaaaaaaaaaa3", "third"),
            ),
        )
        val decoded = SpanCollection.ADAPTER.decode(SpanCollection.ADAPTER.encode(collection))
        assertEquals(collection, decoded)
        assertEquals(listOf("first", "second", "third"), decoded.spans.map(SpanProto::name))
    }

    @Test
    fun `in-flight spans keep their null end time`() {
        val collection = SpanCollection(
            spans = listOf(SpanProto(span_id = "aaaaaaaaaaaaaaa1", end_time_unix_nano = null)),
        )
        val decoded = SpanCollection.ADAPTER.decode(SpanCollection.ADAPTER.encode(collection))
        assertNull(decoded.spans.single().end_time_unix_nano)
    }

    @Test
    fun `concatenated records decode as a single collection in append order`() {
        val spans = listOf(
            span("aaaaaaaaaaaaaaa1", "first"),
            span("aaaaaaaaaaaaaaa2", "second"),
            span("aaaaaaaaaaaaaaa3", "third"),
        )
        val appended = Buffer().apply {
            spans.forEach { write(SpanCollection.ADAPTER.encode(SpanCollection(spans = listOf(it)))) }
        }
        assertEquals(spans, SpanCollection.ADAPTER.decode(appended.readByteArray()).spans)
    }

    @Test
    fun `format version survives on an otherwise empty collection`() {
        val collection = SpanCollection(format_version = FORMAT_VERSION)
        val decoded = SpanCollection.ADAPTER.decode(SpanCollection.ADAPTER.encode(collection))
        assertEquals(FORMAT_VERSION, decoded.format_version)
    }

    @Test
    fun `a message holding no data at all decodes to format version zero`() {
        assertEquals(0, SpanCollection.ADAPTER.decode(ByteArray(0)).format_version)
    }

    @Test
    fun `decode throws on a torn final record instead of returning the complete ones`() {
        val complete = SpanCollection.ADAPTER.encode(
            SpanCollection(spans = listOf(span("aaaaaaaaaaaaaaa1", "first"), span("aaaaaaaaaaaaaaa2", "second"))),
        )
        val torn = complete.copyOf(complete.size - 1)
        try {
            SpanCollection.ADAPTER.decode(torn)
            fail("expected decoding a torn record to throw")
        } catch (expected: IOException) {
            // the records before the torn one are unreachable, hence the tolerant reader
        }
    }
}
