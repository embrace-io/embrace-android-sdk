package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeEmbraceSdkSpan
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSdkSpan
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class SpanSnapshotTrackerTest {

    private lateinit var clock: FakeClock
    private lateinit var tracker: SpanSnapshotTracker

    @Before
    fun setUp() {
        clock = FakeClock()
        tracker = SpanSnapshotTracker()
    }

    @Test
    fun `a span that changed is in flight, however often it changes`() {
        val span = startedSpan()
        repeat(3) { tracker.onSpanChanged(span) }
        assertEquals(listOf(span), tracker.inFlightSpans())
    }

    @Test
    fun `a span that has stopped is evicted, leaving the others in flight`() {
        val stopped = startedSpan()
        val recording = startedSpan()
        tracker.onSpanChanged(stopped)
        tracker.onSpanChanged(recording)

        stopped.stop()
        tracker.onSpanChanged(stopped)
        assertEquals(listOf(recording), tracker.inFlightSpans())
    }

    @Test
    fun `a span that has stopped is not tracked in the first place`() {
        tracker.onSpanChanged(startedSpan().apply { stop() })
        assertEquals(emptyList<EmbraceSdkSpan>(), tracker.inFlightSpans())
    }

    @Test
    fun `seeding tracks the spans that were already recording, and only those`() {
        val recording = startedSpan()
        val notStarted = FakeEmbraceSdkSpan(name = "not-started")
        val stopped = startedSpan().apply { stop() }

        tracker.seed(listOf(recording, notStarted, stopped))
        assertEquals(listOf(recording), tracker.inFlightSpans())
    }

    @Test
    fun `seeding evicts a span that has since stopped, and tracks none of them twice`() {
        val stopped = startedSpan()
        val recording = startedSpan()
        tracker.onSpanChanged(stopped)
        tracker.onSpanChanged(recording)

        stopped.stop()
        tracker.seed(listOf(recording))
        assertEquals(listOf(recording), tracker.inFlightSpans())
    }

    @Test
    fun `a session span is tracked like any other span`() {
        val sessionSpan = sessionSpan()
        tracker.seed(listOf(sessionSpan))
        assertEquals(listOf(sessionSpan), tracker.inFlightSpans())

        tracker.onSpanChanged(sessionSpan.apply { stop() })
        assertEquals(emptyList<EmbraceSdkSpan>(), tracker.inFlightSpans())
    }

    private fun startedSpan(name: String = "span") =
        FakeEmbraceSdkSpan(name = name).apply { start(clock.now()) }

    private fun sessionSpan() =
        FakeEmbraceSdkSpan(name = "session-span", type = EmbType.Ux.Session).apply { start(clock.now()) }
}
