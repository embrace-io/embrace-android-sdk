package io.embrace.android.embracesdk.internal.capture.activity

import android.app.Activity
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeObservedActivity
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
    private lateinit var activityController: ActivityController<*>
    private var startTimeMs: Long = 0L
    private var instanceId = 0
    private var activityName = ""

    @Before
    fun setUp() {
        clock = FakeClock()
        uiLoadEventListener = FakeUiLoadEventListener()
        RuntimeEnvironment.getApplication().registerActivityLifecycleCallbacks(
            ClockTickingActivityLifecycleCallbacks(clock)
        )
        eventEmitter = createActivityLoadEventEmitter(
            uiLoadEventListener = uiLoadEventListener,
            clock = FakeInitModule(clock = clock).openTelemetryModule.openTelemetryClock,
            versionChecker = BuildVersionChecker
        )
        RuntimeEnvironment.getApplication().registerActivityLifecycleCallbacks(eventEmitter)
        startTimeMs = clock.now()
        setupActivityController(FakeObservedActivity::class)
    }

    @Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
    @Test
    fun `check cold ui load event stages in U`() {
        stepThroughActivityLifecycle()
        uiLoadEventListener.events.assertEventData(
            listOf(
                createEvent(
                    stage = "create",
                    timestampMs = startTimeMs + PRE_DURATION
                ),
                createEvent(
                    stage = "createEnd",
                    timestampMs = startTimeMs + POST_DURATION + STATE_DURATION + PRE_DURATION
                ),
                createEvent(
                    stage = "start",
                    timestampMs = startTimeMs + POST_DURATION + STATE_DURATION + PRE_DURATION * 2
                ),
                createEvent(
                    stage = "startEnd",
                    timestampMs = startTimeMs + (POST_DURATION + STATE_DURATION + PRE_DURATION) * 2
                ),
                createEvent(
                    stage = "resume",
                    timestampMs = startTimeMs + (POST_DURATION + STATE_DURATION + PRE_DURATION) * 2 + PRE_DURATION
                ),
                createEvent(
                    stage = "resumeEnd",
                    timestampMs = startTimeMs + (POST_DURATION + STATE_DURATION + PRE_DURATION) * 3
                ),
                createEvent(
                    stage = "discard",
                    timestampMs = startTimeMs + (POST_DURATION + STATE_DURATION + PRE_DURATION) * 3 + PRE_DURATION
                ),
            )
        )
    }

    @Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
    @Test
    fun `check hot ui load event stages in U`() {
        stepThroughActivityLifecycle(isColdOpen = false)
        uiLoadEventListener.events.assertEventData(
            listOf(
                createEvent(
                    stage = "start",
                    timestampMs = startTimeMs + PRE_DURATION
                ),
                createEvent(
                    stage = "startEnd",
                    timestampMs = startTimeMs + POST_DURATION + STATE_DURATION + PRE_DURATION
                ),
                createEvent(
                    stage = "resume",
                    timestampMs = startTimeMs + POST_DURATION + STATE_DURATION + PRE_DURATION * 2
                ),
                createEvent(
                    stage = "resumeEnd",
                    timestampMs = startTimeMs + (POST_DURATION + STATE_DURATION + PRE_DURATION) * 2
                ),
                createEvent(
                    stage = "discard",
                    timestampMs = startTimeMs + (POST_DURATION + STATE_DURATION + PRE_DURATION) * 2 + PRE_DURATION
                ),
            )
        )
    }

    @Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
    @Test
    fun `unobserved activities will not emit ui load events in U`() {
        setupActivityController(Activity::class)
        stepThroughActivityLifecycle()
        uiLoadEventListener.events.assertEventData(
            listOf(
                createEvent(
                    stage = "discard",
                    timestampMs = startTimeMs + (POST_DURATION + STATE_DURATION + PRE_DURATION) * 3 + PRE_DURATION
                ),
            )
        )
    }

    @Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
    @Test
    fun `check cold ui load event stages in L`() {
        stepThroughActivityLifecycle()
        uiLoadEventListener.events.assertEventData(
            listOf(
                createEvent(
                    stage = "create",
                    timestampMs = startTimeMs + STATE_DURATION
                ),
                createEvent(
                    stage = "createEnd",
                    timestampMs = startTimeMs + STATE_DURATION * 2
                ),
                createEvent(
                    stage = "start",
                    timestampMs = startTimeMs + STATE_DURATION * 2
                ),
                createEvent(
                    stage = "startEnd",
                    timestampMs = startTimeMs + STATE_DURATION * 3
                ),
                createEvent(
                    stage = "resume",
                    timestampMs = startTimeMs + STATE_DURATION * 3
                ),
                createEvent(
                    stage = "discard",
                    timestampMs = startTimeMs + STATE_DURATION * 4
                ),
            )
        )
    }

    @Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
    @Test
    fun `check hot ui load event stages in L`() {
        stepThroughActivityLifecycle(isColdOpen = false)
        uiLoadEventListener.events.assertEventData(
            listOf(
                createEvent(
                    stage = "createEnd",
                    timestampMs = startTimeMs + STATE_DURATION
                ),
                createEvent(
                    stage = "start",
                    timestampMs = startTimeMs + STATE_DURATION
                ),
                createEvent(
                    stage = "startEnd",
                    timestampMs = startTimeMs + STATE_DURATION * 2
                ),
                createEvent(
                    stage = "resume",
                    timestampMs = startTimeMs + STATE_DURATION * 2
                ),
                createEvent(
                    stage = "discard",
                    timestampMs = startTimeMs + STATE_DURATION * 3
                ),
            )
        )
    }

    @Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
    @Test
    fun `unobserved activities will not emit ui load events in L`() {
        setupActivityController(Activity::class)
        stepThroughActivityLifecycle()
        uiLoadEventListener.events.assertEventData(
            listOf(
                createEvent(
                    stage = "discard",
                    timestampMs = startTimeMs + STATE_DURATION * 4
                ),
            )
        )
    }

    private fun <T : Activity> setupActivityController(activityClass: KClass<T>) {
        activityController = Robolectric.buildActivity(activityClass.java)
        instanceId = activityController.get().hashCode()
        activityName = activityController.get().localClassName
    }

    private fun stepThroughActivityLifecycle(
        isColdOpen: Boolean = true
    ) {
        with(activityController) {
            if (isColdOpen) {
                create()
            }
            start()
            resume()
            pause()
            stop()
        }
    }

    private fun List<EventData>.assertEventData(expectedEvents: List<EventData>) {
        assertEquals(expectedEvents.map { it.stage to it.timestampMs }, map { it.stage to it.timestampMs })
    }

    private fun createEvent(stage: String, timestampMs: Long? = null) =
        EventData(
            stage = stage,
            instanceId = instanceId,
            activityName = activityName,
            timestampMs = timestampMs
        )
}
