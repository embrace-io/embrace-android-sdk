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

    @Test
    fun `every span field is mapped back`() {
        assertEquals(fullyPopulatedSpan, fullyPopulatedSpanProto.toPayload())
    }

    @Test
    fun `a fully populated span round-trips`() {
        assertEquals(fullyPopulatedSpan, fullyPopulatedSpan.toProto().toPayload())
    }

    @Test
    fun `proto string defaults are mapped back to null`() {
        val span = SpanProto().toPayload()
        assertNull(span.traceId)
        assertNull(span.spanId)
        assertNull(span.parentSpanId)
        assertNull(span.name)
    }

    @Test
    fun `a zero start time is mapped back to null`() {
        assertNull(SpanProto(start_time_unix_nano = 0).toPayload().startTimeNanos)
        assertEquals(1L, SpanProto(start_time_unix_nano = 1).toPayload().startTimeNanos)
    }

    @Test
    fun `an unended span stays distinct from one that ended at zero when mapped back`() {
        assertNull(SpanProto(end_time_unix_nano = null).toPayload().endTimeNanos)
        assertEquals(0L, SpanProto(end_time_unix_nano = 0).toPayload().endTimeNanos)
    }

    @Test
    fun `every status is mapped back and unset does not become null`() {
        assertEquals(Span.Status.UNSET, SpanProto(status = SpanProto.Status.UNSET).toPayload().status)
        assertEquals(Span.Status.ERROR, SpanProto(status = SpanProto.Status.ERROR).toPayload().status)
        assertEquals(Span.Status.OK, SpanProto(status = SpanProto.Status.OK).toPayload().status)
    }

    @Test
    fun `empty proto collections are mapped back to empty collections`() {
        val span = SpanProto(events = emptyList(), attributes = emptyList(), links = emptyList()).toPayload()
        assertEquals(emptyList<SpanEvent>(), span.events)
        assertEquals(emptyList<Attribute>(), span.attributes)
        assertEquals(emptyList<Link>(), span.links)
    }

    @Test
    fun `attribute order and duplicate keys are preserved when mapped back`() {
        val attributes = listOf(
            AttributeProto(key = "b", value_ = "1"),
            AttributeProto(key = "a", value_ = "2"),
            AttributeProto(key = "b", value_ = "3"),
        )
        val span = SpanProto(attributes = attributes).toPayload()
        assertEquals(
            listOf(
                Attribute(key = "b", data = "1"),
                Attribute(key = "a", data = "2"),
                Attribute(key = "b", data = "3"),
            ),
            span.attributes,
        )
    }

    @Test
    fun `an empty attribute key and value survive the round trip`() {
        assertEquals(Attribute(key = "", data = ""), AttributeProto().toPayload())
    }

    @Test
    fun `span event proto defaults are mapped back to null, other than its attributes`() {
        assertEquals(SpanEvent(name = null, timestampNanos = null, attributes = emptyList()), SpanEventProto().toPayload())
    }

    @Test
    fun `link proto defaults are mapped back to null, other than is remote and its attributes`() {
        assertEquals(Link(spanId = null, traceId = null, attributes = emptyList(), isRemote = false), LinkProto().toPayload())
        assertEquals(true, LinkProto(is_remote = true).toPayload().isRemote)
    }
}
