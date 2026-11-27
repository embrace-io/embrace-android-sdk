package io.embrace.android.embracesdk.internal.resurrection

import io.embrace.android.embracesdk.fakes.fakeIncompleteSessionEnvelope
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.SpanEvent
import io.embrace.android.embracesdk.internal.state.AppStateKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

internal class DeadPartStateTest {

    @Test
    fun `the change log is found in the span snapshots`() {
        val changeLog = changeLog()
        val deadPart = envelope(snapshots = listOf(otherSpan(), changeLog))

        assertNotNull(DeadPartState.of(deadPart))
        assertEquals(5L, DeadPartState.of(deadPart)?.lastSeq)
    }

    @Test
    fun `the change log is found in the completed spans`() {
        val deadPart = envelope(spans = listOf(otherSpan(), changeLog()))

        assertEquals(5L, DeadPartState.of(deadPart)?.lastSeq)
    }

    @Test
    fun `a part with no change log has no dead part state`() {
        assertNull(DeadPartState.of(envelope(snapshots = listOf(otherSpan()), spans = listOf(otherSpan()))))
        // a span of that name that is not of the state type is not the change log
        val impostor = changeLog().copy(attributes = changeLog().attributes.orEmpty().filterNot { it.key == EmbType.State.key })
        assertNull(DeadPartState.of(envelope(snapshots = listOf(impostor))))
    }

    @Test
    fun `the stamp is the last value of each state, which of them the SDK generated, and the last number`() {
        val stamp = DeadPartState(changeLog()).stamp()

        assertEquals(
            mapOf(
                "${AppStateKeys.VALUE_PREFIX}network" to "cell",
                "${AppStateKeys.VALUE_PREFIX}screen-automatic" to "Home",
                "${AppStateKeys.VALUE_TYPE_PREFIX}screen-automatic" to AppStateKeys.VALUE_TYPE_SYSTEM,
                AppStateKeys.SEQ to "5",
            ),
            stamp,
        )
    }

    @Test
    fun `the stamp is the opening values when the part listed no change`() {
        val stamp = DeadPartState(changeLog(events = emptyList())).stamp()

        assertEquals(
            mapOf(
                "${AppStateKeys.VALUE_PREFIX}network" to "wifi",
                "${AppStateKeys.VALUE_PREFIX}screen-automatic" to "Initializing",
                "${AppStateKeys.VALUE_TYPE_PREFIX}screen-automatic" to AppStateKeys.VALUE_TYPE_SYSTEM,
                AppStateKeys.SEQ to "3",
            ),
            stamp,
        )
    }

    @Test
    fun `the stamp leaves out a state named by a change limit`() {
        val limited =
            changeLog(extraAttributes = listOf(Attribute("${AppStateKeys.CHANGE_LIMIT_PREFIX}network", "100")))

        assertEquals(
            mapOf(
                "${AppStateKeys.VALUE_PREFIX}screen-automatic" to "Home",
                "${AppStateKeys.VALUE_TYPE_PREFIX}screen-automatic" to AppStateKeys.VALUE_TYPE_SYSTEM,
                AppStateKeys.SEQ to "5",
            ),
            DeadPartState(limited).stamp(),
        )
    }

    @Test
    fun `the stamp is empty when the limit across every state is stated`() {
        val limited = changeLog(extraAttributes = listOf(Attribute(AppStateKeys.GLOBAL_CHANGE_LIMIT, "10000")))

        assertEquals(emptyMap<String, String>(), DeadPartState(limited).stamp())
    }

    @Test
    fun `the stamp is empty when the change log lists no states`() {
        val empty = changeLog(initialValues = emptyList(), events = emptyList())

        assertEquals(emptyMap<String, String>(), DeadPartState(empty).stamp())
    }

    @Test
    fun `the last number is that of the last listed change, or the initial number with none`() {
        assertEquals(5L, DeadPartState(changeLog()).lastSeq)
        assertEquals(3L, DeadPartState(changeLog(events = emptyList())).lastSeq)
    }

    @Test
    fun `with the limit across every state stated the last number still comes from the change log`() {
        val limit = Attribute(AppStateKeys.GLOBAL_CHANGE_LIMIT, "10000")

        assertEquals(5L, DeadPartState(changeLog(extraAttributes = listOf(limit))).lastSeq)
        assertEquals(3L, DeadPartState(changeLog(events = emptyList(), extraAttributes = listOf(limit))).lastSeq)
        assertNull(DeadPartState(changeLog(initialSeq = null, events = emptyList(), extraAttributes = listOf(limit))).lastSeq)
    }

    @Test
    fun `a span the process left running gets the last number as its end number`() {
        val state = DeadPartState(changeLog())

        val completed = state.completeLink(span(seq = "3"))

        assertEquals(
            listOf(Attribute("custom", "x"), Attribute(AppStateKeys.SEQ, "3"), Attribute(AppStateKeys.END_SEQ, "5")),
            completed.attributes,
        )
    }

    @Test
    fun `a span with no start number gets the last number as its end number`() {
        val completed = DeadPartState(changeLog()).completeLink(span(seq = null))

        assertEquals(listOf(Attribute("custom", "x"), Attribute(AppStateKeys.END_SEQ, "5")), completed.attributes)
    }

    @Test
    fun `a span that already has an end number is left as it is`() {
        val span = span(seq = "3", endSeq = "4")

        assertSame(span, DeadPartState(changeLog()).completeLink(span))
    }

    @Test
    fun `a span that started at the last number is left as it is`() {
        val atLast = span(seq = "5")
        val afterLast = span(seq = "6")
        val state = DeadPartState(changeLog())

        assertSame(atLast, state.completeLink(atLast))
        assertSame(afterLast, state.completeLink(afterLast))
    }

    @Test
    fun `the change log itself is left as it is`() {
        val changeLog = changeLog()

        assertSame(changeLog, DeadPartState(changeLog).completeLink(changeLog))
    }

    @Test
    fun `a span is left as it is when the change log has no last number`() {
        val unknown =
            changeLog(initialSeq = null, events = emptyList(), extraAttributes = listOf(Attribute(AppStateKeys.GLOBAL_CHANGE_LIMIT, "1")))
        val span = span(seq = "3")

        assertSame(span, DeadPartState(unknown).completeLink(span))
    }

    private fun span(seq: String?, endSeq: String? = null) = Span(
        spanId = "span-id",
        name = "a-span",
        attributes = listOfNotNull(
            Attribute("custom", "x"),
            seq?.let { Attribute(AppStateKeys.SEQ, it) },
            endSeq?.let { Attribute(AppStateKeys.END_SEQ, it) },
        ),
    )

    private fun otherSpan() = Span(spanId = "other", name = "other-span", attributes = listOf(Attribute("custom", "x")))

    private fun envelope(
        snapshots: List<Span>? = null,
        spans: List<Span>? = null,
    ): Envelope<SessionPartPayload> {
        val base = fakeIncompleteSessionEnvelope()
        return base.copy(data = base.data.copy(spanSnapshots = snapshots, spans = spans))
    }

    companion object {

        /**
         * The change log of a part that opened at number 3 with two states, and listed a change of each: the number 4 of the
         * network and the number 5 of the screen.
         */
        fun changeLog(
            initialSeq: Long? = 3L,
            initialValues: List<Attribute> = listOf(
                Attribute("${AppStateKeys.INITIAL_VALUE_PREFIX}network", "wifi"),
                Attribute("${AppStateKeys.INITIAL_VALUE_PREFIX}screen-automatic", "Initializing"),
                Attribute("${AppStateKeys.INITIAL_VALUE_TYPE_PREFIX}screen-automatic", AppStateKeys.VALUE_TYPE_SYSTEM),
            ),
            events: List<SpanEvent> = listOf(
                change("network", seq = 4, value = "cell"),
                change("screen-automatic", seq = 5, value = "Home", system = true),
            ),
            extraAttributes: List<Attribute> = emptyList(),
        ) = Span(
            spanId = "change-log-span-id",
            name = AppStateKeys.CHANGE_LOG_SPAN_NAME,
            attributes = listOfNotNull(
                Attribute(EmbType.State.key, EmbType.State.value),
                initialSeq?.let { Attribute(AppStateKeys.INITIAL_SEQ, it.toString()) },
            ) + initialValues + extraAttributes,
            events = events,
        )

        fun change(state: String, seq: Long, value: String, system: Boolean = false) = SpanEvent(
            name = "${AppStateKeys.CHANGE_LOG_EVENT_PREFIX}$state",
            timestampNanos = seq,
            attributes = listOfNotNull(
                Attribute(AppStateKeys.SEQ, seq.toString()),
                Attribute(AppStateKeys.NEW_VALUE, value),
                if (system) Attribute(AppStateKeys.NEW_VALUE_TYPE, AppStateKeys.VALUE_TYPE_SYSTEM) else null,
            ),
        )
    }
}
