package io.embrace.android.embracesdk.internal.session.persistence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal class SpanProtoTest {

    @Test
    fun `fully populated span round-trips`() {
        val span = SpanProto(
            trace_id = "0af7651916cd43dd8448eb211c80319c",
            span_id = "b7ad6b7169203331",
            parent_span_id = "00f067aa0ba902b7",
            name = "emb-session",
            start_time_unix_nano = 1_700_000_000_000_000_000L,
            end_time_unix_nano = 1_700_000_001_000_000_000L,
            status = SpanProto.Status.OK,
            events = listOf(
                SpanEventProto(
                    name = "event",
                    time_unix_nano = 1_700_000_000_500_000_000L,
                    attributes = listOf(AttributeProto("event.key", "event.value")),
                ),
            ),
            attributes = listOf(
                AttributeProto("emb.type", "ux.session"),
                AttributeProto("emb.error_log_count", "0"),
            ),
            links = listOf(
                LinkProto(
                    span_id = "b7ad6b7169203331",
                    trace_id = "0af7651916cd43dd8448eb211c80319c",
                    attributes = listOf(AttributeProto("link.key", "link.value")),
                    is_remote = true,
                ),
            ),
        )

        assertEquals(span, SpanProto.ADAPTER.decode(SpanProto.ADAPTER.encode(span)))
    }

    @Test
    fun `minimal span round-trips`() {
        val span = SpanProto()
        assertEquals(span, SpanProto.ADAPTER.decode(SpanProto.ADAPTER.encode(span)))
    }

    @Test
    fun `null end time on an in-flight span is not coerced to zero`() {
        val span = SpanProto(
            span_id = "b7ad6b7169203331",
            start_time_unix_nano = 1_700_000_000_000_000_000L,
            end_time_unix_nano = null,
        )

        val decoded = SpanProto.ADAPTER.decode(SpanProto.ADAPTER.encode(span))
        assertNull(decoded.end_time_unix_nano)
        assertEquals(span, decoded)
    }

    @Test
    fun `zero end time stays distinct from a null end time`() {
        val zero = SpanProto(end_time_unix_nano = 0L)
        val absent = SpanProto(end_time_unix_nano = null)

        val decodedZero = SpanProto.ADAPTER.decode(SpanProto.ADAPTER.encode(zero))
        val decodedAbsent = SpanProto.ADAPTER.decode(SpanProto.ADAPTER.encode(absent))
        assertEquals(0L, decodedZero.end_time_unix_nano)
        assertNull(decodedAbsent.end_time_unix_nano)
    }

    @Test
    fun `every status round-trips and unset is the default`() {
        assertEquals(SpanProto.Status.UNSET, SpanProto().status)

        SpanProto.Status.entries.forEach { status ->
            val span = SpanProto(status = status)
            assertEquals(status, SpanProto.ADAPTER.decode(SpanProto.ADAPTER.encode(span)).status)
        }
    }

    @Test
    fun `attribute order and duplicate keys survive`() {
        val span = SpanProto(
            attributes = listOf(
                AttributeProto("dupe", "first"),
                AttributeProto("other", "value"),
                AttributeProto("dupe", "second"),
            ),
        )

        val decoded = SpanProto.ADAPTER.decode(SpanProto.ADAPTER.encode(span))
        assertEquals(span.attributes, decoded.attributes)
        assertEquals(listOf("first", "value", "second"), decoded.attributes.map { it.value_ })
    }
}
