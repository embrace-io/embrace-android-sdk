package io.embrace.android.embracesdk.internal.otel.state

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeOtelKotlinClock
import io.embrace.android.embracesdk.fakes.FakeSpanExporter
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.otel.createSdkOtelInstance
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSpanProcessor
import io.opentelemetry.kotlin.tracing.Tracer
import io.opentelemetry.kotlin.tracing.data.SpanData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

internal class SpanStateTrackerTest {

    private lateinit var clock: FakeClock
    private lateinit var exporter: FakeSpanExporter
    private lateinit var tracker: SpanStateTracker
    private lateinit var tracer: Tracer

    /**
     * Every stamp there has been that is still remembered, oldest first. The last is the current one.
     */
    private var remembered: List<StateStamp> = emptyList()

    @Volatile
    private var state: StateStamp? = null

    @Before
    fun setup() {
        clock = FakeClock()
        change(FakeStateStamp(0))
        createTracker(keepUntilReleased = false)
    }

    @Test
    fun `a span carries the number at its start, and the number at its end only if a state changed while it ran`() {
        change(FakeStateStamp(3, mapOf("network" to "wifi")))
        val unchanged = tracer.startSpan("unchanged")
        val changed = tracer.startSpan("changed")
        unchanged.end()
        change(FakeStateStamp(5, mapOf("network" to "none")))
        changed.end()

        with(exported("unchanged").attributes) {
            assertEquals(3L, get(StateAttributes.SEQUENCE))
            assertFalse(containsKey(StateAttributes.END_SEQUENCE))
        }
        with(exported("changed").attributes) {
            assertEquals(3L, get(StateAttributes.SEQUENCE))
            assertEquals(5L, get(StateAttributes.END_SEQUENCE))
            // a link, and no values
            assertEquals(emptyList<String>(), keys.filter { it.startsWith(StateAttributes.VALUE_PREFIX) })
        }
        assertEquals(0, tracker.trackedSpans())
    }

    @Test
    fun `a span that started before any state had a value carries only the number at its end, if a state changed`() {
        val unchanged = tracer.startSpan("unchanged")
        val changed = tracer.startSpan("changed")
        unchanged.end()
        change(FakeStateStamp(1, mapOf("network" to "wifi")))
        changed.end()

        assertEquals(emptyList<String>(), exported("unchanged").stateKeys())
        assertEquals(listOf(StateAttributes.END_SEQUENCE), exported("changed").stateKeys())
        assertEquals(1L, exported("changed").attributes[StateAttributes.END_SEQUENCE])
    }

    @Test
    fun `a span given a start time from before the last change carries the number that held then, if that is remembered`() {
        val firstChangeMs = clock.now()
        change(FakeStateStamp(3, sinceMs = firstChangeMs))
        val lastChangeMs = clock.tick(500)
        change(FakeStateStamp(4, sinceMs = lastChangeMs))
        clock.tick(500)
        tracer.startSpan("before-the-change", startTimestamp = (lastChangeMs - 1).millisToNanos()).end()
        tracer.startSpan("at-the-change", startTimestamp = lastChangeMs.millisToNanos()).end()
        tracer.startSpan("after-the-change", startTimestamp = (lastChangeMs + 1).millisToNanos()).end()

        // the one that started before the change ended after it, and the two others started and ended after it
        assertEquals(3L, exported("before-the-change").attributes[StateAttributes.SEQUENCE])
        assertEquals(4L, exported("before-the-change").attributes[StateAttributes.END_SEQUENCE])
        listOf("at-the-change", "after-the-change").forEach { name ->
            assertEquals(4L, exported(name).attributes[StateAttributes.SEQUENCE])
            assertFalse(exported(name).attributes.containsKey(StateAttributes.END_SEQUENCE))
        }

        // once the stamp that held at its start is forgotten, a span like the first one is not linked at all
        remembered = remembered.takeLast(1)
        tracer.startSpan("before-what-is-remembered", startTimestamp = (lastChangeMs - 1).millisToNanos()).end()
        assertEquals(emptyList<String>(), exported("before-what-is-remembered").stateKeys())
        assertEquals(0, tracker.trackedSpans())
    }

    @Test
    fun `a span ended at a time from before the last change carries the number that held then, and not the current one`() {
        createTracker(keepUntilReleased = true)
        change(FakeStateStamp(3, mapOf("network" to "wifi", "power" to "normal"), clock.now()))
        val endedBeforeAnyChange = tracer.startSpan("ended-before-any-change")
        val endedBetweenChanges = tracer.startSpan("ended-between-changes")
        val endedAtAChange = tracer.startSpan("ended-at-a-change")
        val endNotRemembered = tracer.startSpan("end-not-remembered")
        val firstEndMs = clock.tick(100)
        val firstChangeMs = clock.tick(100)
        change(FakeStateStamp(4, mapOf("network" to "none", "power" to "normal"), firstChangeMs))
        val secondEndMs = clock.tick(100)
        val secondChangeMs = clock.tick(100)
        change(FakeStateStamp(5, mapOf("network" to "none", "power" to "low"), secondChangeMs))
        clock.tick(100)

        endedBeforeAnyChange.end(firstEndMs.millisToNanos())
        endedBetweenChanges.end(secondEndMs.millisToNanos())
        endedAtAChange.end(secondChangeMs.millisToNanos())
        // the stamp that held when this one ended is forgotten by the time it is ended
        remembered = remembered.takeLast(1)
        endNotRemembered.end(secondEndMs.millisToNanos())

        assertEquals(listOf(StateAttributes.SEQUENCE), exported("ended-before-any-change").stateKeys())
        assertEquals(
            mapOf("emb.states.value.network" to "wifi", "emb.states.value.power" to "normal"),
            tracker.withValues(exported("ended-before-any-change")).valueAttributes(),
        )

        assertEquals(4L, exported("ended-between-changes").attributes[StateAttributes.END_SEQUENCE])
        assertEquals(
            mapOf(
                "emb.states.value.network" to "wifi",
                "emb.states.value.power" to "normal",
                "emb.states.end_value.network" to "none",
            ),
            tracker.withValues(exported("ended-between-changes")).valueAttributes(),
        )

        // a time equal to the time of a change is after that change
        assertEquals(5L, exported("ended-at-a-change").attributes[StateAttributes.END_SEQUENCE])
        assertEquals("low", tracker.withValues(exported("ended-at-a-change")).attributes["emb.states.end_value.power"])

        // nothing can be known of its end, so it keeps the number it started with and gets none for its end
        assertEquals(listOf(StateAttributes.SEQUENCE), exported("end-not-remembered").stateKeys())
        assertEquals(3L, exported("end-not-remembered").attributes[StateAttributes.SEQUENCE])
    }

    @Test
    fun `a span that is given both its times once it is over is linked if the stamp at its start is remembered`() {
        createTracker(keepUntilReleased = true)
        val firstChangeMs = clock.now()
        change(FakeStateStamp(3, mapOf("network" to "wifi"), firstChangeMs))
        val secondChangeMs = clock.tick(1000)
        change(FakeStateStamp(4, mapOf("network" to "none"), secondChangeMs))
        val thirdChangeMs = clock.tick(1000)
        change(FakeStateStamp(5, mapOf("network" to "wan"), thirdChangeMs))
        clock.tick(1000)

        record("across-the-first-change", firstChangeMs + 100, secondChangeMs + 100)
        record("inside-one-stamp", secondChangeMs + 100, secondChangeMs + 200)
        record("into-the-present", secondChangeMs + 100, clock.now())
        remembered = remembered.takeLast(2)
        record("before-what-is-remembered", firstChangeMs + 100, secondChangeMs + 100)

        with(tracker.withValues(exported("across-the-first-change")).attributes) {
            assertEquals(3L, get(StateAttributes.SEQUENCE))
            assertEquals(4L, get(StateAttributes.END_SEQUENCE))
            assertEquals("wifi", get("emb.states.value.network"))
            assertEquals("none", get("emb.states.end_value.network"))
        }
        with(tracker.withValues(exported("inside-one-stamp")).attributes) {
            assertEquals(4L, get(StateAttributes.SEQUENCE))
            assertFalse(containsKey(StateAttributes.END_SEQUENCE))
            assertEquals("none", get("emb.states.value.network"))
            assertFalse(containsKey("emb.states.end_value.network"))
        }
        with(tracker.withValues(exported("into-the-present")).attributes) {
            assertEquals(4L, get(StateAttributes.SEQUENCE))
            assertEquals(5L, get(StateAttributes.END_SEQUENCE))
            assertEquals("wan", get("emb.states.end_value.network"))
        }
        assertEquals(emptyList<String>(), exported("before-what-is-remembered").stateKeys())
        assertSame(exported("before-what-is-remembered"), tracker.withValues(exported("before-what-is-remembered")))
    }

    @Test
    fun `a linked span in progress says the number it started with, and an end time it is given is kept only until it ends`() {
        change(FakeStateStamp(3, sinceMs = clock.now()))
        remembered = remembered.takeLast(1)
        val span = tracer.startSpan("span")
        val unlinked = tracer.startSpan("unlinked", startTimestamp = (clock.now() - 1).millisToNanos())
        val spanId = span.spanContext.spanId
        assertEquals(3L, tracker.startSequence(spanId))
        assertNull(tracker.startSequence(unlinked.spanContext.spanId))

        // nothing is kept for a span that is not linked
        tracker.onEndTimeGiven(unlinked.spanContext.spanId, clock.now())
        assertEquals(1, tracker.trackedSpans())

        tracker.onEndTimeGiven(spanId, clock.now())
        span.end()
        unlinked.end()
        assertNull(tracker.startSequence(spanId))
        assertEquals(0, tracker.trackedSpans())

        val discarded = tracer.startSpan("discarded")
        tracker.onEndTimeGiven(discarded.spanContext.spanId, clock.now())
        tracker.onDiscarded(discarded.spanContext.spanId)
        assertEquals(0, tracker.trackedSpans())
    }

    @Test
    fun `nothing is linked while nothing records state`() {
        state = null
        tracer.startSpan("unrecorded").end()
        val span = tracer.startSpan("recorded-by-its-end")
        change(FakeStateStamp(2))
        span.end()

        assertEquals(emptyList<String>(), exported("unrecorded").stateKeys())
        assertEquals(emptyList<String>(), exported("recorded-by-its-end").stateKeys())
    }

    @Test
    fun `the values are put on what is exported of a span, and never on the span`() {
        createTracker(keepUntilReleased = true)
        change(FakeStateStamp(3, mapOf("network" to "wifi", "screen" to "~Initializing", "power" to "normal"), clock.now()))
        val span = tracer.startSpan("changed")
        remembered = remembered.takeLast(1)
        val unlinked = tracer.startSpan("unlinked", startTimestamp = (clock.now() - 1).millisToNanos())
        change(FakeStateStamp(5, mapOf("network" to "none", "screen" to "~Backgrounded", "power" to "normal"), clock.now()))
        span.end()
        unlinked.end()

        val ended = exported("changed")
        assertEquals(listOf(StateAttributes.END_SEQUENCE, StateAttributes.SEQUENCE), ended.stateKeys())
        val decorated = tracker.withValues(ended)
        assertEquals(
            mapOf(
                StateAttributes.SEQUENCE to 3L,
                StateAttributes.END_SEQUENCE to 5L,
                "emb.states.value.network" to "wifi",
                "emb.states.value.screen" to "Initializing",
                "emb.states.value_type.screen" to "system",
                "emb.states.value.power" to "normal",
                "emb.states.end_value.network" to "none",
                "emb.states.end_value.screen" to "Backgrounded",
                "emb.states.end_value_type.screen" to "system",
            ),
            decorated.attributes.filterKeys { it.startsWith("emb.states.") },
        )
        assertEquals(ended.name, decorated.name)
        assertEquals(ended.spanContext.spanId, decorated.spanContext.spanId)
        assertEquals(ended.attributes["emb.process_identifier"], decorated.attributes["emb.process_identifier"])

        // a span with no link is handed over as it is
        assertSame(exported("unlinked"), tracker.withValues(exported("unlinked")))
    }

    @Test
    fun `what is kept for a span that is exported with the values is let go when it is released`() {
        createTracker(keepUntilReleased = true)
        change(FakeStateStamp(3, mapOf("network" to "wifi")))
        val ended = tracer.startSpan("ended")
        val discarded = tracer.startSpan("discarded")
        change(FakeStateStamp(4, mapOf("network" to "none")))
        ended.end()
        assertEquals(2, tracker.trackedSpans())

        tracker.release(exported("ended").spanContext.spanId)
        tracker.onDiscarded(discarded.spanContext.spanId)
        assertEquals(0, tracker.trackedSpans())
        assertNull(tracker.withValues(exported("ended")).attributes["emb.states.value.network"])
    }

    /**
     * Makes [stamp] the current one, and remembers it.
     */
    private fun change(stamp: StateStamp) {
        remembered = remembered + stamp
        state = stamp
    }

    /**
     * Starts a span at [startMs] and ends it at [endMs], both of which are over.
     */
    private fun record(name: String, startMs: Long, endMs: Long) {
        tracer.startSpan(name, startTimestamp = startMs.millisToNanos()).end(endMs.millisToNanos())
    }

    private fun createTracker(keepUntilReleased: Boolean) {
        exporter = FakeSpanExporter()
        tracker = SpanStateTracker(
            stateProvider = { state },
            stateAt = { timeMs -> remembered.lastOrNull { it.sinceMs <= timeMs } },
            keepUntilReleased = keepUntilReleased,
        )
        val processor = EmbraceSpanProcessor(
            sessionIdsProvider = { null },
            processIdentifier = "pid",
            spanExporter = exporter,
            stateTracker = tracker,
        )
        tracer = createSdkOtelInstance(
            useKotlinSdk = true,
            tracerProvider = { export { processor } },
            clock = FakeOtelKotlinClock(clock),
        ).tracerProvider.getTracer("test")
    }

    private fun exported(name: String): SpanData = exporter.exportedSpans.single { it.name == name }

    private fun SpanData.stateKeys(): List<String> = attributes.keys.filter { it.startsWith("emb.states.") }.sorted()

    private fun SpanData.valueAttributes(): Map<String, Any> = attributes.filterKeys {
        it.startsWith(StateAttributes.VALUE_PREFIX) || it.startsWith(StateAttributes.END_VALUE_PREFIX)
    }
}
