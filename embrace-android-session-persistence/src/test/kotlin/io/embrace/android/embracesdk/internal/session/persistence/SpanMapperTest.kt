package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Link
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.SpanEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal class SpanMapperTest {

    @Test
    fun `every span field is mapped`() {
        assertEquals(fullyPopulatedSpanProto, fullyPopulatedSpan.toProto())
    }

    @Test
    fun `null strings are mapped to the proto default`() {
        val proto = Span().toProto()
        assertEquals("", proto.trace_id)
        assertEquals("", proto.span_id)
        assertEquals("", proto.parent_span_id)
        assertEquals("", proto.name)
    }

    @Test
    fun `a null parent span id is not replaced with an invalid span id`() {
        assertEquals("", Span(parentSpanId = null).toProto().parent_span_id)
        assertEquals("0000000000000000", Span(parentSpanId = "0000000000000000").toProto().parent_span_id)
    }

    @Test
    fun `a null start time is mapped to zero`() {
        assertEquals(0L, Span(startTimeNanos = null).toProto().start_time_unix_nano)
    }

    @Test
    fun `an unended span stays distinct from one that ended at zero`() {
        assertNull(Span(endTimeNanos = null).toProto().end_time_unix_nano)
        assertEquals(0L, Span(endTimeNanos = 0).toProto().end_time_unix_nano)
    }

    @Test
    fun `every status is mapped and a null status becomes unset`() {
        assertEquals(SpanProto.Status.UNSET, Span(status = null).toProto().status)
        assertEquals(SpanProto.Status.UNSET, Span(status = Span.Status.UNSET).toProto().status)
        assertEquals(SpanProto.Status.ERROR, Span(status = Span.Status.ERROR).toProto().status)
        assertEquals(SpanProto.Status.OK, Span(status = Span.Status.OK).toProto().status)
    }

    @Test
    fun `null collections are mapped to empty lists`() {
        val proto = Span(events = null, attributes = null, links = null).toProto()
        assertEquals(emptyList<SpanEventProto>(), proto.events)
        assertEquals(emptyList<AttributeProto>(), proto.attributes)
        assertEquals(emptyList<LinkProto>(), proto.links)
    }

    @Test
    fun `attribute order and duplicate keys are preserved`() {
        val attributes = listOf(
            Attribute(key = "b", data = "1"),
            Attribute(key = "a", data = "2"),
            Attribute(key = "b", data = "3"),
        )
        val proto = Span(attributes = attributes).toProto()

        assertEquals(
            listOf(
                AttributeProto(key = "b", value_ = "1"),
                AttributeProto(key = "a", value_ = "2"),
                AttributeProto(key = "b", value_ = "3"),
            ),
            proto.attributes,
        )
    }

    @Test
    fun `null attribute keys and values are mapped to the proto default`() {
        assertEquals(AttributeProto(key = "", value_ = ""), Attribute().toProto())
    }

    @Test
    fun `null span event fields are mapped to the proto default`() {
        assertEquals(SpanEventProto(name = "", time_unix_nano = 0L, attributes = emptyList()), SpanEvent().toProto())
    }

    @Test
    fun `null link fields are mapped to the proto default`() {
        assertEquals(
            LinkProto(span_id = "", trace_id = "", attributes = emptyList(), is_remote = false),
            Link().toProto(),
        )
    }
}
