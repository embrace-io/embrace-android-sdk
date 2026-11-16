package io.embrace.android.embracesdk.internal.vitals

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.fakes.FakeInternalTelemetryService
import io.embrace.android.embracesdk.fakes.FakeProcessStateTracker
import io.embrace.android.embracesdk.fakes.FakeSpanToken
import io.embrace.android.embracesdk.internal.arch.lifecycle.ActivityCreated
import io.embrace.android.embracesdk.internal.arch.lifecycle.ActivityLifecycle
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.state.ProcessState
import io.embrace.android.embracesdk.internal.config.resolved.AutoDataCaptureConfig
import io.embrace.android.embracesdk.internal.config.resolved.EmbraceConfig
import io.embrace.android.embracesdk.internal.config.resolved.VitalsConfig
import io.embrace.android.embracesdk.internal.telemetry.AppliedLimitType
import io.embrace.android.embracesdk.internal.utils.event.EventBus
import io.embrace.android.embracesdk.semconv.EmbFrameCountsAttributes
import io.embrace.android.embracesdk.semconv.EmbInteractionResponsivenessAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric.buildActivity
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(AndroidJUnit4::class)
internal class VitalsDataSourceTest {

    private val telemetryService = FakeInternalTelemetryService()
    private val mainLooper = shadowOf(Looper.getMainLooper())

    private fun createDataSource(
        spanLimit: Int,
        responsivenessEnabled: Boolean = false,
    ): Pair<VitalsDataSource, FakeInstrumentationArgs> {
        val application: Application = ApplicationProvider.getApplicationContext()
        val args = FakeInstrumentationArgs(application, telemetryService = telemetryService)
        application.registerActivityLifecycleCallbacks(BusRelay(args.eventBus))
        args.configService.config = EmbraceConfig(
            autoDataCapture = { AutoDataCaptureConfig(interactionResponsivenessCaptureEnabled = { responsivenessEnabled }) },
            vitals = { VitalsConfig(spanLimit = { spanLimit }) },
        )
        return VitalsDataSource(args) to args
    }

    /**
     * Emits [count] spans through the data source's limit strategy — the same path the smoothness and
     * screen-load results take.
     */
    private fun VitalsDataSource.emit(count: Int) {
        repeat(count) {
            captureTelemetry {
                recordCompletedSpan(name = "smoothness", startTimeMs = 0L, endTimeMs = 1L)
            }
        }
    }

    @Test
    fun `spans are emitted up to the configured limit and then dropped`() {
        val (dataSource, args) = createDataSource(spanLimit = 3)

        dataSource.emit(5)

        assertEquals(3, args.destination.completedSpans().size)
    }

    @Test
    fun `the limit is shared across both vitals span types`() {
        val (dataSource, args) = createDataSource(spanLimit = 250)

        dataSource.emit(400)

        assertEquals(250, args.destination.completedSpans().size)
    }

    @Test
    fun `the limit resets on a session part change`() {
        val (dataSource, args) = createDataSource(spanLimit = 3)
        dataSource.emit(5)

        dataSource.resetDataCaptureLimits()
        dataSource.emit(5)

        assertEquals(6, args.destination.completedSpans().size)
    }

    @Test
    fun `a dropped span is tracked as an applied limit`() {
        val (dataSource, args) = createDataSource(spanLimit = 1)

        dataSource.emit(3)

        assertEquals(1, args.destination.completedSpans().size)
        assertEquals(
            List(2) { "vitals_data_source" to AppliedLimitType.DROP },
            telemetryService.appliedLimits,
        )
    }

    @Test
    fun `a foreground session part records its frame counts when it ends`() {
        val args = FakeInstrumentationArgs(ApplicationProvider.getApplicationContext())
        val dataSource = VitalsDataSource(args)
        dataSource.countFrames(dropped = true, expectedFrames = 7) // before the part started
        dataSource.onPostSessionChange()
        dataSource.countFrames(dropped = false, expectedFrames = 1)
        dataSource.countFrames(dropped = true, expectedFrames = 3)

        dataSource.onPreSessionEnd()

        assertEquals("2", args.destination.attributes[EmbFrameCountsAttributes.SMOOTHNESS_DROPPED_FRAMES])
        assertEquals("4", args.destination.attributes[EmbFrameCountsAttributes.SMOOTHNESS_EXPECTED_FRAMES])
    }

    @Test
    fun `a background session part records no frame counts, and does not carry them into the next part`() {
        val processStateTracker = FakeProcessStateTracker(ProcessState.BACKGROUND)
        val args =
            FakeInstrumentationArgs(ApplicationProvider.getApplicationContext(), processStateTracker = processStateTracker)
        val dataSource = VitalsDataSource(args)
        dataSource.onPostSessionChange()
        dataSource.countFrames(dropped = true, expectedFrames = 2)

        dataSource.onPreSessionEnd()
        assertTrue(args.destination.sessionPartAttributeWrites.isEmpty())

        // the app foregrounds: the next part counts only its own frames
        processStateTracker.state = ProcessState.FOREGROUND
        dataSource.onPostSessionChange()
        dataSource.countFrames(dropped = false, expectedFrames = 1)
        dataSource.onPreSessionEnd()

        assertEquals("0", args.destination.attributes[EmbFrameCountsAttributes.SMOOTHNESS_DROPPED_FRAMES])
        assertEquals("1", args.destination.attributes[EmbFrameCountsAttributes.SMOOTHNESS_EXPECTED_FRAMES])
    }

    @Test
    fun `the screen being left is given the frame counts since the previous screen was left`() {
        val args = FakeInstrumentationArgs(ApplicationProvider.getApplicationContext())
        val dataSource = VitalsDataSource(args)
        dataSource.onDataCaptureEnabled()
        dataSource.countFrames(dropped = true, expectedFrames = 5)

        assertEquals(screenFrameAttributes(dropped = 4, expected = 5), args.collectScreenAttributes())
        assertEquals(screenFrameAttributes(dropped = 0, expected = 0), args.collectScreenAttributes())
    }

    @Test
    fun `screen and session part counts overlap without interfering`() {
        val args = FakeInstrumentationArgs(ApplicationProvider.getApplicationContext())
        val dataSource = VitalsDataSource(args)
        dataSource.onDataCaptureEnabled()

        dataSource.countFrames(dropped = true, expectedFrames = 3)
        assertEquals(screenFrameAttributes(dropped = 2, expected = 3), args.collectScreenAttributes())
        dataSource.countFrames(dropped = false, expectedFrames = 1)
        assertEquals(screenFrameAttributes(dropped = 0, expected = 1), args.collectScreenAttributes())
        dataSource.countFrames(dropped = false, expectedFrames = 1)
        dataSource.onPreSessionEnd()

        // the session part spans all three screens' frames, however often the screen counts were read
        assertEquals("2", args.destination.attributes[EmbFrameCountsAttributes.SMOOTHNESS_DROPPED_FRAMES])
        assertEquals("5", args.destination.attributes[EmbFrameCountsAttributes.SMOOTHNESS_EXPECTED_FRAMES])
    }

    @Test
    fun `enabling data capture registers one screen attributes source`() {
        val args = FakeInstrumentationArgs(ApplicationProvider.getApplicationContext())
        assertEquals(0, args.navigationTrackingService.screenAttributesSources.size)

        VitalsDataSource(args).onDataCaptureEnabled()

        assertEquals(1, args.navigationTrackingService.screenAttributesSources.size)
    }

    @Test
    fun `window callbacks are installed when data capture is enabled`() {
        val (dataSource, _) = createDataSource(spanLimit = 1)
        dataSource.onDataCaptureEnabled()
        val activity = buildActivity(Activity::class.java).setup().get()

        assertTrue(activity.window.callback is VitalsWindowCallback)
        dataSource.onDataCaptureDisabled()
    }

    @Test
    fun `activity events are not handled after data capture is disabled`() {
        val (dataSource, _) = createDataSource(spanLimit = 1)
        dataSource.onDataCaptureEnabled()
        dataSource.onDataCaptureDisabled()
        val activity = buildActivity(Activity::class.java).setup().get()
        assertFalse(activity.window.callback is VitalsWindowCallback)
    }

    @Test
    fun `a blocked touch emits an interaction-responsiveness span when enabled`() {
        val (dataSource, args) = createDataSource(spanLimit = 10, responsivenessEnabled = true)
        val activity = dataSource.startScreen()

        val downTime = SystemClock.uptimeMillis()
        ShadowSystemClock.advanceBy(Duration.ofMillis(30)) // dispatched 30ms after it occurred
        activity.get().touch(MotionEvent.ACTION_DOWN, downTime)
        activity.get().touch(MotionEvent.ACTION_UP)
        dataSource.idleVitalsThread() // the focal moment opens and starts the probe

        Handler(Looper.getMainLooper()).post { ShadowSystemClock.advanceBy(Duration.ofMillis(200)) } // a 200ms block
        mainLooper.idleFor(Duration.ofMillis(50))
        dataSource.idleVitalsThread()
        dataSource.stopScreen(activity)

        val span = args.responsivenessSpans().single()
        assertEquals(EmbType.Performance.InteractionResponsiveness, span.type)
        assertTrue(span.private)
        assertEquals("interrupted", span.attributes[EmbInteractionResponsivenessAttributes.INTERACTION_RESPONSIVENESS_OUTCOME])
        assertEquals("30", span.attributes[EmbInteractionResponsivenessAttributes.INTERACTION_RESPONSIVENESS_DISPATCH_DELAY_MS])
        assertEquals("1", span.attributes[EmbInteractionResponsivenessAttributes.INTERACTION_RESPONSIVENESS_BLOCK_COUNT])
        assertEquals("184", span.attributes[EmbInteractionResponsivenessAttributes.INTERACTION_RESPONSIVENESS_LONGEST_BLOCK_MS])
        assertEquals("184", span.attributes[EmbInteractionResponsivenessAttributes.INTERACTION_RESPONSIVENESS_TOTAL_BLOCKED_MS])
        assertEquals("50", span.attributes[EmbInteractionResponsivenessAttributes.INTERACTION_RESPONSIVENESS_BLOCK_THRESHOLD_MS])
        assertEquals("16", span.attributes[EmbInteractionResponsivenessAttributes.INTERACTION_RESPONSIVENESS_PROBE_INTERVAL_MS])
    }

    @Test
    fun `a moment opened by a move emits a span without a dispatch delay`() {
        val (dataSource, args) = createDataSource(spanLimit = 10, responsivenessEnabled = true)
        val activity = dataSource.startScreen()

        activity.get().touch(MotionEvent.ACTION_MOVE)
        dataSource.idleVitalsThread()
        dataSource.stopScreen(activity)

        val attributes = args.responsivenessSpans().single().attributes
        assertFalse(attributes.containsKey(EmbInteractionResponsivenessAttributes.INTERACTION_RESPONSIVENESS_DISPATCH_DELAY_MS))
        assertEquals("0", attributes[EmbInteractionResponsivenessAttributes.INTERACTION_RESPONSIVENESS_BLOCK_COUNT])
    }

    @Test
    fun `with interaction responsiveness disabled a touch neither probes the main thread nor emits a span`() {
        val (dataSource, args) = createDataSource(spanLimit = 10)
        val activity = dataSource.startScreen()
        val nextBeforeTouch = mainLooper.nextScheduledTaskTime

        activity.get().touch(MotionEvent.ACTION_DOWN)
        dataSource.idleVitalsThread()
        mainLooper.idle()
        assertEquals("the touch scheduled no probe message", nextBeforeTouch, mainLooper.nextScheduledTaskTime)

        dataSource.stopScreen(activity)
        assertTrue(args.responsivenessSpans().isEmpty())
    }

    @Test
    fun `interaction-responsiveness spans share the vitals span limit`() {
        val (dataSource, args) = createDataSource(spanLimit = 1, responsivenessEnabled = true)
        dataSource.emit(1)
        val activity = dataSource.startScreen()

        activity.get().touch(MotionEvent.ACTION_DOWN)
        dataSource.idleVitalsThread()
        dataSource.stopScreen(activity)

        assertEquals(1, args.destination.completedSpans().size)
        assertTrue("dropped at the shared limit", args.responsivenessSpans().isEmpty())
    }

    @Test
    fun `disabling data capture leaves no probe message queued`() {
        val (dataSource, _) = createDataSource(spanLimit = 10, responsivenessEnabled = true)
        val activity = dataSource.startScreen()
        val nextBeforeTouch = mainLooper.nextScheduledTaskTime
        activity.get().touch(MotionEvent.ACTION_DOWN)
        dataSource.idleVitalsThread()
        assertEquals("the open moment probes from now", Duration.ofMillis(SystemClock.uptimeMillis()), mainLooper.nextScheduledTaskTime)

        val vitalsThread = checkNotNull(dataSource.vitalsScheduler).handler.looper.thread
        dataSource.onDataCaptureDisabled()
        vitalsThread.join(TIMEOUT_MS)
        mainLooper.idle()

        assertEquals("the probe message is gone", nextBeforeTouch, mainLooper.nextScheduledTaskTime)
    }

    /** Enables data capture and resumes an activity, so its window reports touches to the focal moment tracker. */
    private fun VitalsDataSource.startScreen(): ActivityController<Activity> {
        onDataCaptureEnabled()
        val activity = buildActivity(Activity::class.java).setup()
        mainLooper.idle()
        idleVitalsThread()
        return activity
    }

    /** Pauses [activity], which interrupts any open focal moment, and waits for its results to be emitted. */
    private fun VitalsDataSource.stopScreen(activity: ActivityController<Activity>) {
        activity.pause()
        idleVitalsThread()
    }

    private fun VitalsDataSource.idleVitalsThread() = shadowOf(checkNotNull(vitalsScheduler).handler.looper).idle()

    private fun Activity.touch(action: Int, eventTime: Long = SystemClock.uptimeMillis()) {
        val event = MotionEvent.obtain(eventTime, eventTime, action, 0f, 0f, 0)
        window.callback.dispatchTouchEvent(event)
        event.recycle()
    }

    private fun FakeInstrumentationArgs.responsivenessSpans(): List<FakeSpanToken> =
        destination.completedSpans().filter { it.name == "interaction-responsiveness" }

    private fun FakeInstrumentationArgs.collectScreenAttributes(): Map<String, String> {
        val attributes = mutableMapOf<String, String>()
        navigationTrackingService.collectScreenAttributes(attributes::set)
        return attributes
    }

    private fun screenFrameAttributes(dropped: Int, expected: Int) = mapOf(
        EmbFrameCountsAttributes.SMOOTHNESS_DROPPED_FRAMES to dropped.toString(),
        EmbFrameCountsAttributes.SMOOTHNESS_EXPECTED_FRAMES to expected.toString(),
    )

    /**
     * Relays the callbacks [VitalsActivityListener] handles onto [eventBus], as the SDK's relay does.
     */
    private class BusRelay(private val eventBus: EventBus) : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) =
            eventBus.emit(ActivityLifecycle.CREATED, ActivityCreated(activity, savedInstanceState))

        override fun onActivityResumed(activity: Activity) = eventBus.emit(ActivityLifecycle.RESUMED, activity)

        override fun onActivityPaused(activity: Activity) = eventBus.emit(ActivityLifecycle.PAUSED, activity)

        override fun onActivityStarted(activity: Activity) {}

        override fun onActivityStopped(activity: Activity) {}

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

        override fun onActivityDestroyed(activity: Activity) {}
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
