package io.embrace.android.embracesdk.internal.vitals

import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.fakes.FakeSpanToken
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.screen.ScreenSource
import io.embrace.android.embracesdk.internal.arch.screen.ScreenSource.ACTIVITY
import io.embrace.android.embracesdk.internal.arch.screen.ScreenSource.NAVIGATION
import io.embrace.android.embracesdk.internal.arch.screen.ScreenStart
import io.embrace.android.embracesdk.internal.arch.screen.ScreenStop
import io.embrace.android.embracesdk.internal.arch.screen.ScreenStop.Reason.HIDDEN
import io.embrace.android.embracesdk.internal.arch.screen.ScreenStop.Reason.NAVIGATED
import io.embrace.android.embracesdk.semconv.EmbScreenAttributes.EMB_SCREEN_ID
import io.embrace.android.embracesdk.semconv.EmbScreenAttributes.EMB_SCREEN_NAME
import io.embrace.android.embracesdk.semconv.EmbScreenAttributes.EMB_SCREEN_SOURCE
import io.embrace.android.embracesdk.semconv.EmbScreenAttributes.EMB_SCREEN_STOP_REASON
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class ScreenSpanRecorderTest {

    private var partId: String? = "part"
    private lateinit var args: FakeInstrumentationArgs
    private lateinit var dataSource: ScreenDataSource

    private val spans: List<FakeSpanToken>
        get() = args.destination.createdSpans

    @Before
    fun setUp() {
        args = FakeInstrumentationArgs(sessionPartIdSupplier = { partId })
        dataSource = ScreenDataSource(args)
        dataSource.onDataCaptureEnabled()
    }

    @Test
    fun `a private screen span is started for the active part`() {
        val span = spans.single()
        assertEquals("screen", span.name)
        assertEquals(EmbType.Ux.Screen, span.type)
        assertTrue(span.private)
        assertTrue(span.isRecording())
    }

    @Test
    fun `starts and stops are recorded as events paired by id, including overlapping screens`() {
        start("A", ACTIVITY, 10)
        start("Home", NAVIGATION, 20)
        stop(NAVIGATION, 30, NAVIGATED)
        start("Settings", NAVIGATION, 30)
        stop(NAVIGATION, 40, HIDDEN)
        stop(ACTIVITY, 40, HIDDEN)

        val events = spans.single().events
        assertEquals(
            listOf("screen_start", "screen_start", "screen_stop", "screen_start", "screen_stop", "screen_stop"),
            events.map { it.name },
        )
        assertEquals(listOf(10L, 20L, 30L, 30L, 40L, 40L), events.map { it.timestampNanos / 1_000_000 })
        assertEquals(
            mapOf(EMB_SCREEN_ID to ACTIVITY_SCREEN, EMB_SCREEN_NAME to "A", EMB_SCREEN_SOURCE to "activity"),
            events[0].attributes,
        )
        assertEquals(mapOf(EMB_SCREEN_ID to NAVIGATION_SCREEN, EMB_SCREEN_STOP_REASON to "navigated"), events[2].attributes)
        assertEquals(
            listOf(ACTIVITY_SCREEN, NAVIGATION_SCREEN, NAVIGATION_SCREEN, NAVIGATION_SCREEN, NAVIGATION_SCREEN, ACTIVITY_SCREEN),
            events.map { it.attributes[EMB_SCREEN_ID] },
        )
    }

    @Test
    fun `what a handler writes on stop is recorded on the stop event`() {
        args.eventBus.addHandler(ScreenStop.KEY) { it.setAttribute("summary", "yes") }
        start("A", ACTIVITY, 10)
        stop(ACTIVITY, 20, HIDDEN)
        assertEquals("yes", spans.single().events.last().attributes["summary"])
    }

    @Test
    fun `the recorder runs after handlers registered with any lower order`() {
        args.eventBus.addHandler(ScreenStop.KEY, Int.MAX_VALUE - 1) { it.setAttribute("late", "yes") }
        start("A", ACTIVITY, 10)
        stop(ACTIVITY, 20, HIDDEN)
        assertEquals("yes", spans.single().events.last().attributes["late"])
    }

    @Test
    fun `a handler cannot replace the id or reason`() {
        args.eventBus.addHandler(ScreenStop.KEY) {
            it.setAttribute(EMB_SCREEN_ID, "x")
            it.setAttribute(EMB_SCREEN_STOP_REASON, "x")
        }
        start("A", ACTIVITY, 10)
        stop(ACTIVITY, 20, HIDDEN)
        val stopEvent = spans.single().events.last()
        assertEquals(ACTIVITY_SCREEN, stopEvent.attributes[EMB_SCREEN_ID])
        assertEquals("hidden", stopEvent.attributes[EMB_SCREEN_STOP_REASON])
    }

    @Test
    fun `each part has its own span, and a screen open across parts pairs across them`() {
        start("A", ACTIVITY, 10)
        endPart()
        assertNotNull(spans.single().endTimeMs)

        startPart()
        stop(ACTIVITY, 20, HIDDEN)
        assertEquals(2, spans.size)
        assertEquals(listOf("screen_start"), spans[0].events.map { it.name })
        assertEquals(listOf("screen_stop"), spans[1].events.map { it.name })
        assertEquals(ACTIVITY_SCREEN, spans[1].events.single().attributes[EMB_SCREEN_ID])
    }

    @Test
    fun `nothing is recorded while no part is active`() {
        endPart()
        partId = null
        startPart()
        start("A", ACTIVITY, 10)
        assertEquals(1, spans.size)
        assertTrue(spans.single().events.isEmpty())
    }

    @Test
    fun `nothing is recorded after data capture is disabled`() {
        dataSource.onDataCaptureDisabled()
        assertNotNull(spans.single().endTimeMs)
        start("A", ACTIVITY, 10)
        startPart()
        assertEquals(1, spans.size)
        assertTrue(spans.single().events.isEmpty())
    }

    private fun start(name: String, source: ScreenSource, timeMs: Long) =
        args.eventBus.emit(ScreenStart.KEY, ScreenStart(name, source, ACTIVITY_ID, timeMs))

    private fun stop(source: ScreenSource, timeMs: Long, reason: ScreenStop.Reason) =
        args.eventBus.emit(ScreenStop.KEY, ScreenStop(source, ACTIVITY_ID, timeMs, reason))

    private fun endPart() = args.sessionEndListeners.forEach { it.onPreSessionEnd() }

    private fun startPart() = args.sessionChangeListeners.forEach { it.onPostSessionChange() }

    private companion object {
        const val ACTIVITY_ID = 1
        const val ACTIVITY_SCREEN = "activity-$ACTIVITY_ID"
        const val NAVIGATION_SCREEN = "navigation-$ACTIVITY_ID"
    }
}
