package io.embrace.android.embracesdk.internal.capture.activity

import android.app.Activity
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeClock.Companion.DEFAULT_FAKE_CURRENT_TIME
import io.embrace.android.embracesdk.fakes.FakeCustomTracedActivity
import io.embrace.android.embracesdk.fakes.FakeDrawEventEmitter
import io.embrace.android.embracesdk.fakes.FakeNotTracedActivity
import io.embrace.android.embracesdk.fakes.FakeTracedActivity
import io.embrace.android.embracesdk.fakes.FakeUiLoadEventListener
import io.embrace.android.embracesdk.fakes.FakeUiLoadEventListener.EventData
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.internal.ClockTickingActivityLifecycleCallbacks
import io.embrace.android.embracesdk.internal.ClockTickingActivityLifecycleCallbacks.Companion.POST_DURATION
import io.embrace.android.embracesdk.internal.ClockTickingActivityLifecycleCallbacks.Companion.PRE_DURATION
import io.embrace.android.embracesdk.internal.ClockTickingActivityLifecycleCallbacks.Companion.STATE_DURATION
import io.embrace.android.embracesdk.internal.session.lifecycle.ActivityLifecycleListener
import io.embrace.android.embracesdk.internal.utils.BuildVersionChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import kotlin.reflect.KClass

@RunWith(AndroidJUnit4::class)
internal class UiLoadExtTest {

    private lateinit var clock: FakeClock
    private lateinit var uiLoadEventListener: FakeUiLoadEventListener
    private lateinit var eventEmitter: ActivityLifecycleListener
    private lateinit var drawEventEmitter: FakeDrawEventEmitter
    private lateinit var activityController: ActivityController<*>

    @Before
    fun setUp() {
        clock = FakeClock(currentTime = DEFAULT_FAKE_CURRENT_TIME)
        uiLoadEventListener = FakeUiLoadEventListener()
        drawEventEmitter = FakeDrawEventEmitter()
        RuntimeEnvironment.getApplication().registerActivityLifecycleCallbacks(
            ClockTickingActivityLifecycleCallbacks(clock)
        )
    }

    @Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
    @Test
    fun `check cold ui load event stages in U`() {
        createEventEmitter(autoTraceEnabled = false, activityClass = FakeTracedActivity::class)
        stepThroughActivityLifecycle()
        uiLoadEventListener.events.assertEventData(expectedColdOpenEvents)
        assertDrawEvents()
    }

    @Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
    @Test
    fun `check hot ui load event stages in U`() {
        createEventEmitter(autoTraceEnabled = false, activityClass = FakeTracedActivity::class)
        stepThroughActivityLifecycle(isColdOpen = false)
        uiLoadEventListener.events.assertEventData(expectedHotOpenEvents)
        assertDrawEvents()
    }

    @Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
    @Test
    fun `unannotated activities will emit ui load events in U if auto capture is enabled`() {
        createEventEmitter(autoTraceEnabled = true, activityClass = Activity::class)
        stepThroughActivityLifecycle()
        uiLoadEventListener.events.assertEventData(expectedColdOpenEvents)
        assertDrawEvents()
    }

    @Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
    @Test
    fun `unannotated activities will not emit ui load events in U if auto capture is not enabled`() {
        createEventEmitter(autoTraceEnabled = false, activityClass = Activity::class)
        stepThroughActivityLifecycle()
        assertTrue(uiLoadEventListener.events.isEmpty())
    }

    @Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
    @Test
    fun `activities will not emit ui load events in U if explicitly disabled`() {
        createEventEmitter(autoTraceEnabled = true, activityClass = FakeNotTracedActivity::class)
        stepThroughActivityLifecycle()
        assertTrue(uiLoadEventListener.events.isEmpty())
    }

    @Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
    @Test
    fun `activities with manual ended trace will emit ui load events in U`() {
        createEventEmitter(autoTraceEnabled = false, activityClass = FakeCustomTracedActivity::class)
        stepThroughActivityLifecycle()
        uiLoadEventListener.events.assertEventData(expectedColdOpenEvents)
        assertDrawEvents()
    }

    @Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
    @Test
    fun `check cold ui load event stages in L`() {
        createEventEmitter(autoTraceEnabled = false, activityClass = FakeTracedActivity::class)
        stepThroughActivityLifecycle()
        uiLoadEventListener.events.assertEventData(legacyColdOpenEvents)
        assertDrawEvents()
    }

    @Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
    @Test
    fun `check hot ui load event stages in L`() {
        createEventEmitter(autoTraceEnabled = false, activityClass = FakeTracedActivity::class)
        stepThroughActivityLifecycle(isColdOpen = false)
        uiLoadEventListener.events.assertEventData(legacyHotOpenEvents)
        assertDrawEvents()
    }

    @Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
    @Test
    fun `unannotated activities will emit ui load events in L if auto capture is enabled`() {
        createEventEmitter(autoTraceEnabled = true, activityClass = Activity::class)
        stepThroughActivityLifecycle()
        uiLoadEventListener.events.assertEventData(legacyColdOpenEvents)
        assertDrawEvents()
    }

    @Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
    @Test
    fun `unannotated activities will not emit ui load events in L if auto capture is not enabled`() {
        createEventEmitter(autoTraceEnabled = false, activityClass = Activity::class)
        stepThroughActivityLifecycle()
        assertTrue(uiLoadEventListener.events.isEmpty())
    }

    @Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
    @Test
    fun `activities will not emit ui load events in L if explicitly disabled`() {
        createEventEmitter(autoTraceEnabled = true, activityClass = FakeNotTracedActivity::class)
        stepThroughActivityLifecycle()
        assertTrue(uiLoadEventListener.events.isEmpty())
    }

    @Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
    @Test
    fun `activities with manual ended trace will emit ui load events in L`() {
        createEventEmitter(autoTraceEnabled = false, activityClass = FakeCustomTracedActivity::class)
        stepThroughActivityLifecycle()
        uiLoadEventListener.events.assertEventData(legacyColdOpenEvents)
        assertDrawEvents()
    }

    private fun <T : Activity> createEventEmitter(autoTraceEnabled: Boolean, activityClass: KClass<T>) {
        eventEmitter = createActivityLoadEventEmitter(
            uiLoadEventListener = uiLoadEventListener,
            firstDrawDetector = drawEventEmitter,
            autoTraceEnabled = autoTraceEnabled,
            clock = FakeInitModule(clock = clock).openTelemetryModule.openTelemetryClock,
            versionChecker = BuildVersionChecker
        ).apply {
            RuntimeEnvironment.getApplication().registerActivityLifecycleCallbacks(this)
            activityController = Robolectric.buildActivity(activityClass.java)
        }
    }

    private fun stepThroughActivityLifecycle(isColdOpen: Boolean = true) {
        with(activityController) {
            if (isColdOpen) {
                create()
            }
            start()
            resume()
            if (hasRenderEvent(BuildVersionChecker)) {
                drawEventEmitter.draw(activityController.get()) {
                    clock.tick(RENDER_DURATION)
                }
            }
            pause()
            stop()
        }
    }

    private fun List<EventData>.assertEventData(expectedEvents: List<EventData>) {
        assertEquals(expectedEvents.map { it.stage to it.timestampMs }, map { it.stage to it.timestampMs })
    }

    private fun assertDrawEvents() {
        assertNotNull(drawEventEmitter.lastRegisteredActivity)
        assertNotNull(drawEventEmitter.lastFirstFrameDeliveredCallback)
        assertNotNull(drawEventEmitter.lastUnregisteredActivity)
        assertEquals(drawEventEmitter.lastRegisteredActivity, drawEventEmitter.lastRegisteredActivity)
    }

    companion object {
        private const val START_TIME_MS: Long = DEFAULT_FAKE_CURRENT_TIME
        private const val RENDER_DURATION: Long = 66L

        private val expectedColdOpenEvents = listOf(
            createEvent(
                stage = "create",
                timestampMs = START_TIME_MS + PRE_DURATION
            ),
            createEvent(
                stage = "createEnd",
                timestampMs = START_TIME_MS + POST_DURATION + STATE_DURATION + PRE_DURATION
            ),
            createEvent(
                stage = "start",
                timestampMs = START_TIME_MS + POST_DURATION + STATE_DURATION + PRE_DURATION * 2
            ),
            createEvent(
                stage = "startEnd",
                timestampMs = START_TIME_MS + (POST_DURATION + STATE_DURATION + PRE_DURATION) * 2
            ),
            createEvent(
                stage = "resume",
                timestampMs = START_TIME_MS + (POST_DURATION + STATE_DURATION + PRE_DURATION) * 2 + PRE_DURATION
            ),
            createEvent(
                stage = "resumeEnd",
                timestampMs = START_TIME_MS + (POST_DURATION + STATE_DURATION + PRE_DURATION) * 3
            ),
            createEvent(
                stage = "render",
                timestampMs = START_TIME_MS + (POST_DURATION + STATE_DURATION + PRE_DURATION) * 3
            ),
            createEvent(
                stage = "renderEnd",
                timestampMs = START_TIME_MS + (POST_DURATION + STATE_DURATION + PRE_DURATION) * 3 + RENDER_DURATION
            ),
            createEvent(
                stage = "discard",
                timestampMs = START_TIME_MS + (POST_DURATION + STATE_DURATION + PRE_DURATION) * 3 + RENDER_DURATION + PRE_DURATION
            ),
        )

        val expectedHotOpenEvents = listOf(
            createEvent(
                stage = "start",
                timestampMs = START_TIME_MS + PRE_DURATION
            ),
            createEvent(
                stage = "startEnd",
                timestampMs = START_TIME_MS + POST_DURATION + STATE_DURATION + PRE_DURATION
            ),
            createEvent(
                stage = "resume",
                timestampMs = START_TIME_MS + POST_DURATION + STATE_DURATION + PRE_DURATION * 2
            ),
            createEvent(
                stage = "resumeEnd",
                timestampMs = START_TIME_MS + (POST_DURATION + STATE_DURATION + PRE_DURATION) * 2
            ),
            createEvent(
                stage = "render",
                timestampMs = START_TIME_MS + (POST_DURATION + STATE_DURATION + PRE_DURATION) * 2
            ),
            createEvent(
                stage = "renderEnd",
                timestampMs = START_TIME_MS + (POST_DURATION + STATE_DURATION + PRE_DURATION) * 2 + RENDER_DURATION
            ),
            createEvent(
                stage = "discard",
                timestampMs = START_TIME_MS + (POST_DURATION + STATE_DURATION + PRE_DURATION) * 2 + RENDER_DURATION + PRE_DURATION
            ),
        )

        val legacyColdOpenEvents = listOf(
            createEvent(
                stage = "create",
                timestampMs = START_TIME_MS + STATE_DURATION
            ),
            createEvent(
                stage = "createEnd",
                timestampMs = START_TIME_MS + STATE_DURATION * 2
            ),
            createEvent(
                stage = "start",
                timestampMs = START_TIME_MS + STATE_DURATION * 2
            ),
            createEvent(
                stage = "startEnd",
                timestampMs = START_TIME_MS + STATE_DURATION * 3
            ),
            createEvent(
                stage = "resume",
                timestampMs = START_TIME_MS + STATE_DURATION * 3
            ),
            createEvent(
                stage = "discard",
                timestampMs = START_TIME_MS + STATE_DURATION * 4
            ),
        )

        val legacyHotOpenEvents = listOf(
            createEvent(
                stage = "createEnd",
                timestampMs = START_TIME_MS + STATE_DURATION
            ),
            createEvent(
                stage = "start",
                timestampMs = START_TIME_MS + STATE_DURATION
            ),
            createEvent(
                stage = "startEnd",
                timestampMs = START_TIME_MS + STATE_DURATION * 2
            ),
            createEvent(
                stage = "resume",
                timestampMs = START_TIME_MS + STATE_DURATION * 2
            ),
            createEvent(
                stage = "discard",
                timestampMs = START_TIME_MS + STATE_DURATION * 3
            ),
        )

        private fun createEvent(stage: String, timestampMs: Long? = null) =
            EventData(
                stage = stage,
                instanceId = 0,
                timestampMs = timestampMs
            )
    }
}
