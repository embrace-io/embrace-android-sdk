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
    fun `a span that changed is in flight and has yet to reach disk`() {
        val span = startedSpan()
        tracker.onSpanChanged(span)
        assertEquals(listOf(span), tracker.inFlightSpans())
        assertEquals(listOf(span), tracker.drainDirtySpans())
    }

    @Test
    fun `a span that changed more than once is only reported once`() {
        val span = startedSpan()
        repeat(3) { tracker.onSpanChanged(span) }
        assertEquals(listOf(span), tracker.inFlightSpans())
        assertEquals(listOf(span), tracker.drainDirtySpans())
    }

    @Test
    fun `a span that has stopped is evicted`() {
        val span = startedSpan()
        tracker.onSpanChanged(span)

        span.stop()
        tracker.onSpanChanged(span)

        assertEquals(emptyList<EmbraceSdkSpan>(), tracker.inFlightSpans())
        assertEquals(emptyList<EmbraceSdkSpan>(), tracker.drainDirtySpans())
    }

    @Test
    fun `a span that has stopped is not tracked in the first place`() {
        val span = startedSpan().apply { stop() }
        tracker.onSpanChanged(span)
        assertEquals(emptyList<EmbraceSdkSpan>(), tracker.inFlightSpans())
        assertEquals(emptyList<EmbraceSdkSpan>(), tracker.drainDirtySpans())
    }

    @Test
    fun `evicting one span leaves the others tracked`() {
        val stopped = startedSpan()
        val recording = startedSpan()
        tracker.onSpanChanged(stopped)
        tracker.onSpanChanged(recording)

        stopped.stop()
        tracker.onSpanChanged(stopped)

        assertEquals(listOf(recording), tracker.inFlightSpans())
        assertEquals(listOf(recording), tracker.drainDirtySpans())
    }

    @Test
    fun `draining leaves nothing to report until the next change`() {
        val span = startedSpan()
        tracker.onSpanChanged(span)
        tracker.drainDirtySpans()

        assertEquals(emptyList<EmbraceSdkSpan>(), tracker.drainDirtySpans())
        assertEquals(listOf(span), tracker.inFlightSpans())
        tracker.onSpanChanged(span)
        assertEquals(listOf(span), tracker.drainDirtySpans())
    }

    @Test
    fun `seeding tracks spans that were already recording`() {
        val span = startedSpan()
        tracker.seed(listOf(span))
        assertEquals(listOf(span), tracker.inFlightSpans())
        assertEquals(listOf(span), tracker.drainDirtySpans())
    }

    @Test
    fun `seeding ignores spans that are not recording`() {
        val notStarted = FakeEmbraceSdkSpan(name = "not-started")
        val stopped = startedSpan().apply { stop() }
        tracker.seed(listOf(notStarted, stopped))
        assertEquals(emptyList<EmbraceSdkSpan>(), tracker.inFlightSpans())
        assertEquals(emptyList<EmbraceSdkSpan>(), tracker.drainDirtySpans())
    }

    @Test
    fun `seeding evicts a tracked span that has since stopped`() {
        val stopped = startedSpan()
        val recording = startedSpan()
        tracker.onSpanChanged(stopped)
        tracker.onSpanChanged(recording)

        stopped.stop()
        tracker.seed(emptyList())
        assertEquals(listOf(recording), tracker.inFlightSpans())
        assertEquals(listOf(recording), tracker.drainDirtySpans())
    }

    @Test
    fun `seeding a span that is already tracked does not report it twice`() {
        val span = startedSpan()
        tracker.onSpanChanged(span)
        tracker.seed(listOf(span))
        assertEquals(listOf(span), tracker.inFlightSpans())
        assertEquals(listOf(span), tracker.drainDirtySpans())
    }

    @Test
    fun `a session span is not tracked`() {
        val sessionSpan = sessionSpan()
        tracker.onSpanChanged(sessionSpan)
        assertEquals(emptyList<EmbraceSdkSpan>(), tracker.inFlightSpans())
        assertEquals(emptyList<EmbraceSdkSpan>(), tracker.drainDirtySpans())
    }

    @Test
    fun `seeding does not track a session span`() {
        tracker.seed(listOf(sessionSpan()))
        assertEquals(emptyList<EmbraceSdkSpan>(), tracker.inFlightSpans())
        assertEquals(emptyList<EmbraceSdkSpan>(), tracker.drainDirtySpans())
    }

    private fun startedSpan(name: String = "span") =
        FakeEmbraceSdkSpan(name = name).apply { start(clock.now()) }

    private fun sessionSpan() =
        FakeEmbraceSdkSpan(name = "session-span", type = EmbType.Ux.Session).apply { start(clock.now()) }
}
