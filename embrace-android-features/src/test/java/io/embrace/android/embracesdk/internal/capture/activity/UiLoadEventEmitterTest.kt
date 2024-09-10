package io.embrace.android.embracesdk.internal.capture.activity

import android.app.Activity
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeObservedActivity
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.internal.ClockTickingActivityLifecycleCallbacks
import io.embrace.android.embracesdk.internal.ClockTickingActivityLifecycleCallbacks.Companion.POST_DURATION
import io.embrace.android.embracesdk.internal.ClockTickingActivityLifecycleCallbacks.Companion.PRE_DURATION
import io.embrace.android.embracesdk.internal.ClockTickingActivityLifecycleCallbacks.Companion.STATE_DURATION
import io.embrace.android.embracesdk.internal.capture.activity.UiLoadEventEmitterTest.FakeOpenEvents.EventData
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
internal class UiLoadEventEmitterTest {
    private lateinit var clock: FakeClock
    private lateinit var openEvents: FakeOpenEvents
    private lateinit var eventEmitter: UiLoadEventEmitter
    private lateinit var activityController: ActivityController<*>
    private var startTimeMs: Long = 0L
    private var instanceId = 0
    private var activityName = ""

    @Before
    fun setUp() {
        clock = FakeClock()
        val initModule = FakeInitModule(clock = clock)
        clock.tick(100L)
        openEvents = FakeOpenEvents()
        eventEmitter = UiLoadEventEmitter(
            uiLoadEvents = openEvents,
            clock = initModule.openTelemetryModule.openTelemetryClock,
            versionChecker = BuildVersionChecker,
        )
        RuntimeEnvironment.getApplication().registerActivityLifecycleCallbacks(
            ClockTickingActivityLifecycleCallbacks(clock)
        )
        RuntimeEnvironment.getApplication().registerActivityLifecycleCallbacks(eventEmitter)
        startTimeMs = clock.now()
        setupActivityController(FakeObservedActivity::class)
    }

    @Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
    @Test
    fun `check cold open event stages in U`() {
        stepThroughActivityLifecycle()
        openEvents.events.assertEventData(
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
                    stage = "abandon",
                    timestampMs = startTimeMs + (POST_DURATION + STATE_DURATION + PRE_DURATION) * 3 + PRE_DURATION
                ),
                createEvent(
                    stage = "reset",
                ),
            )
        )
    }

    @Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
    @Test
    fun `check hot open event stages in U`() {
        stepThroughActivityLifecycle(isColdOpen = false)
        openEvents.events.assertEventData(
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
                    stage = "abandon",
                    timestampMs = startTimeMs + (POST_DURATION + STATE_DURATION + PRE_DURATION) * 2 + PRE_DURATION
                ),
                createEvent(
                    stage = "reset",
                ),
            )
        )
    }

    @Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
    @Test
    fun `check cold open event stages in L`() {
        stepThroughActivityLifecycle()
        openEvents.events.assertEventData(
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
                    stage = "abandon",
                    timestampMs = startTimeMs + STATE_DURATION * 4
                ),
                createEvent(
                    stage = "reset",
                ),
            )
        )
    }

    @Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
    @Test
    fun `check hot open event stages in L`() {
        stepThroughActivityLifecycle(isColdOpen = false)
        openEvents.events.assertEventData(
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
                    stage = "abandon",
                    timestampMs = startTimeMs + STATE_DURATION * 3
                ),
                createEvent(
                    stage = "reset",
                ),
            )
        )
    }

    @Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
    @Test
    fun `unobserved activities will not emit open events in U`() {
        setupActivityController(Activity::class)
        stepThroughActivityLifecycle()
        openEvents.events.assertEventData(
            listOf(
                createEvent(
                    stage = "abandon",
                    timestampMs = startTimeMs + (POST_DURATION + STATE_DURATION + PRE_DURATION) * 3 + PRE_DURATION
                ),
                createEvent(
                    stage = "reset",
                ),
            )
        )
    }

    @Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
    @Test
    fun `unobserved activities will not emit open events in L`() {
        setupActivityController(Activity::class)
        stepThroughActivityLifecycle()
        openEvents.events.assertEventData(
            listOf(
                createEvent(
                    stage = "abandon",
                    timestampMs = startTimeMs + STATE_DURATION * 4
                ),
                createEvent(
                    stage = "reset",
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

    class FakeOpenEvents : UiLoadEvents {
        val events = mutableListOf<EventData>()

        override fun abandon(instanceId: Int, activityName: String, timestampMs: Long) {
            events.add(
                EventData(
                    stage = "abandon",
                    instanceId = instanceId,
                    activityName = activityName,
                    timestampMs = timestampMs
                )
            )
        }

        override fun reset(instanceId: Int) {
            events.add(
                EventData(
                    stage = "reset",
                    instanceId = instanceId,
                )
            )
        }

        override fun create(instanceId: Int, activityName: String, timestampMs: Long) {
            events.add(
                EventData(
                    stage = "create",
                    instanceId = instanceId,
                    activityName = activityName,
                    timestampMs = timestampMs
                )
            )
        }

        override fun createEnd(instanceId: Int, timestampMs: Long) {
            events.add(
                EventData(
                    stage = "createEnd",
                    instanceId = instanceId,
                    activityName = null,
                    timestampMs = timestampMs
                )
            )
        }

        override fun start(instanceId: Int, activityName: String, timestampMs: Long) {
            events.add(
                EventData(
                    stage = "start",
                    instanceId = instanceId,
                    activityName = activityName,
                    timestampMs = timestampMs
                )
            )
        }

        override fun startEnd(instanceId: Int, timestampMs: Long) {
            events.add(
                EventData(
                    stage = "startEnd",
                    instanceId = instanceId,
                    timestampMs = timestampMs
                )
            )
        }

        override fun resume(instanceId: Int, activityName: String, timestampMs: Long) {
            events.add(
                EventData(
                    stage = "resume",
                    instanceId = instanceId,
                    activityName = activityName,
                    timestampMs = timestampMs
                )
            )
        }

        override fun resumeEnd(instanceId: Int, timestampMs: Long) {
            events.add(
                EventData(
                    stage = "resumeEnd",
                    instanceId = instanceId,
                    timestampMs = timestampMs
                )
            )
        }

        override fun render(instanceId: Int, activityName: String, timestampMs: Long) {
            events.add(
                EventData(
                    stage = "render",
                    instanceId = instanceId,
                    activityName = activityName,
                    timestampMs = timestampMs
                )
            )
        }

        override fun renderEnd(instanceId: Int, timestampMs: Long) {
            events.add(
                EventData(
                    stage = "renderEnd",
                    instanceId = instanceId,
                    timestampMs = timestampMs
                )
            )
        }

        data class EventData(
            val stage: String,
            val instanceId: Int,
            val activityName: String? = null,
            val timestampMs: Long? = null,
        )
    }
}
