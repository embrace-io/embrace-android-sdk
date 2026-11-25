package io.embrace.android.embracesdk.internal.vitals

import android.app.Activity
import android.os.Build
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.internal.arch.lifecycle.ActivityLifecycle
import io.embrace.android.embracesdk.internal.arch.navigation.getId
import io.embrace.android.embracesdk.internal.arch.screen.ScreenSource
import io.embrace.android.embracesdk.internal.arch.screen.ScreenSource.ACTIVITY
import io.embrace.android.embracesdk.internal.arch.screen.ScreenStart
import io.embrace.android.embracesdk.internal.arch.screen.ScreenStop
import io.embrace.android.embracesdk.internal.arch.screen.ScreenStop.Reason.HIDDEN
import io.embrace.android.embracesdk.internal.utils.VersionChecker
import io.embrace.android.embracesdk.internal.utils.event.EventBus
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class ActivityScreenSourceTest {

    /**
     * Starts and stops recorded into one list, so that their order is asserted.
     */
    private sealed interface Emitted {
        data class Start(
            val name: String,
            val activityId: Int,
            val timestampMs: Long,
            val source: ScreenSource = ACTIVITY,
        ) : Emitted
        data class Stop(
            val activityId: Int,
            val timestampMs: Long,
            val source: ScreenSource = ACTIVITY,
            val reason: ScreenStop.Reason = HIDDEN,
        ) : Emitted
    }

    private lateinit var clock: FakeClock
    private lateinit var eventBus: EventBus
    private lateinit var emitted: MutableList<Emitted>
    private lateinit var first: Activity
    private lateinit var second: Activity

    @Before
    fun setUp() {
        clock = FakeClock()
        eventBus = EventBus(FakeErrorHandler())
        emitted = mutableListOf()
        eventBus.addHandler(ScreenStart.KEY) { emitted.add(Emitted.Start(it.name, it.activityId, it.timestampMs, it.source)) }
        eventBus.addHandler(ScreenStop.KEY) { emitted.add(Emitted.Stop(it.activityId, it.timestampMs, it.source, it.reason)) }
        first = FirstActivity()
        second = SecondActivity()
    }

    @Test
    fun `activity transitions produce paired starts and stops in P`() {
        createSource(Build.VERSION_CODES.P)
        assertActivityTransitions()
    }

    @Test
    fun `activity transitions produce paired starts and stops in Q`() {
        createSource(Build.VERSION_CODES.Q)
        assertActivityTransitions()
    }

    @Test
    fun `main events produce nothing in Q`() {
        createSource(Build.VERSION_CODES.Q)
        eventBus.emit(ActivityLifecycle.STARTED, first)
        eventBus.emit(ActivityLifecycle.RESUMED, first)
        eventBus.emit(ActivityLifecycle.PAUSED, first)
        assertEquals(emptyList<Emitted>(), emitted)
    }

    @Test
    fun `pre and post events produce nothing in P`() {
        createSource(Build.VERSION_CODES.P)
        eventBus.emit(ActivityLifecycle.PRE_STARTED, first)
        eventBus.emit(ActivityLifecycle.PRE_RESUMED, first)
        eventBus.emit(ActivityLifecycle.POST_PAUSED, first)
        assertEquals(emptyList<Emitted>(), emitted)
    }

    @Test
    fun `nothing is emitted after close`() {
        createSource(Build.VERSION_CODES.Q).close()
        eventBus.startActivity(first)
        eventBus.resumeActivity(first)
        eventBus.pauseActivity(first)
        assertEquals(emptyList<Emitted>(), emitted)
    }

    private fun assertActivityTransitions() {
        // a screen starts when its activity resumes, not when it starts
        eventBus.startActivity(first)
        val firstResume = clock.tick(10)
        eventBus.resumeActivity(first)

        // covered by a second activity that starts and resumes
        val firstPause = clock.tick(10)
        eventBus.pauseActivity(first)
        clock.tick(10)
        eventBus.startActivity(second)
        val secondResume = clock.tick(10)
        eventBus.resumeActivity(second)

        // the second finishes, and the first resumes again
        val secondPause = clock.tick(10)
        eventBus.pauseActivity(second)
        val firstResumeAgain = clock.tick(10)
        eventBus.resumeActivity(first)

        assertEquals(
            listOf(
                Emitted.Start(FIRST, first.getId(), firstResume),
                Emitted.Stop(first.getId(), firstPause),
                Emitted.Start(SECOND, second.getId(), secondResume),
                Emitted.Stop(second.getId(), secondPause),
                Emitted.Start(FIRST, first.getId(), firstResumeAgain),
            ),
            emitted,
        )
    }

    private fun createSource(sdkInt: Int) = ActivityScreenSource(
        clock = clock,
        eventBus = eventBus,
        errorHandler = FakeErrorHandler(),
        versionChecker = VersionChecker { sdkInt >= it },
    ).apply { subscribe() }

    private class FirstActivity : Activity() {
        override fun getLocalClassName(): String = FIRST
    }

    private class SecondActivity : Activity() {
        override fun getLocalClassName(): String = SECOND
    }

    private fun EventBus.startActivity(activity: Activity) {
        emit(ActivityLifecycle.PRE_STARTED, activity)
        emit(ActivityLifecycle.STARTED, activity)
        emit(ActivityLifecycle.POST_STARTED, activity)
    }

    private fun EventBus.resumeActivity(activity: Activity) {
        emit(ActivityLifecycle.PRE_RESUMED, activity)
        emit(ActivityLifecycle.RESUMED, activity)
        emit(ActivityLifecycle.POST_RESUMED, activity)
    }

    private fun EventBus.pauseActivity(activity: Activity) {
        emit(ActivityLifecycle.PRE_PAUSED, activity)
        emit(ActivityLifecycle.PAUSED, activity)
        emit(ActivityLifecycle.POST_PAUSED, activity)
    }

    private companion object {
        const val FIRST = "FirstActivity"
        const val SECOND = "SecondActivity"
    }
}
