package io.embrace.android.embracesdk.internal.instrumentation.navigation

import android.app.Activity
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.internal.instrumentation.navigation.NavigationEvent.ActivityPaused
import io.embrace.android.embracesdk.internal.instrumentation.navigation.NavigationEvent.ActivityResumed
import io.embrace.android.embracesdk.internal.instrumentation.navigation.NavigationEvent.ActivityStarted
import io.embrace.android.embracesdk.internal.instrumentation.navigation.NavigationEvent.Backgrounded
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
internal class NavigationEventBrokerTest {
    private lateinit var clock: FakeClock
    private lateinit var shadowMainLooper: ShadowLooper
    private lateinit var loadTimes: MutableList<Long>
    private lateinit var states: MutableList<Pair<Long, String>>
    private lateinit var homeActivity: Activity
    private lateinit var settingsActivity: Activity
    private lateinit var profileActivity: Activity
    private lateinit var broker: NavigationEventBroker

    @Before
    fun setUp() {
        clock = FakeClock()
        shadowMainLooper = Shadows.shadowOf(Looper.getMainLooper())
        loadTimes = mutableListOf()
        states = CopyOnWriteArrayList()
        broker = NavigationEventBroker { loadTimeMs, newScreenName ->
            states.add(Pair(loadTimeMs, newScreenName))
        }
        homeActivity = Robolectric.buildActivity(HomeActivity::class.java).get()
        settingsActivity = Robolectric.buildActivity(SettingsActivity::class.java).get()
        profileActivity = Robolectric.buildActivity(ProfileActivity::class.java).get()
    }

    @Test
    fun `start time of activity used and instead of resume time when notifying screen load`() {
        val latch = CountDownLatch(1)
        loadTimes.add(broker.queueAndMoveClock(ActivityStarted(homeActivity, clock.now())))
        broker.queueAndMoveClock(ActivityResumed(homeActivity, clock.now()))
        broker.queueAndMoveClock(ActivityPaused(homeActivity, clock.now()))
        loadTimes.add(broker.queueAndMoveClock(ActivityStarted(settingsActivity, clock.now())))
        broker.queueAndMoveClock(ActivityResumed(settingsActivity, clock.now()))
        broker.queueAndMoveClock(ActivityPaused(settingsActivity, clock.now()))

        // Background should always be queued before the profile activity event because the latter depends on the
        // former to finish to unlock the latch
        Thread {
            clock.tick()
            broker.queueEvent(Backgrounded(clock.now()))
            latch.countDown()
        }.start()
        latch.await(1, TimeUnit.SECONDS)
        assertEquals(0, latch.count)
        shadowMainLooper.idle()
        loadTimes.add(clock.now())
        loadTimes.add(broker.queueAndMoveClock(ActivityStarted(profileActivity, clock.now())))
        broker.queueAndMoveClock(ActivityResumed(profileActivity, clock.now()))
        assertEquals(
            listOf(
                Pair(loadTimes[0], homeActivity.localClassName),
                Pair(loadTimes[1], settingsActivity.localClassName),
                Pair(loadTimes[2], "Backgrounded"),
                Pair(loadTimes[3], profileActivity.localClassName)
            ),
            states
        )
    }

    @Test
    fun `duplicate events are dropped`() {
        broker.queueAndMoveClock(ActivityStarted(homeActivity, clock.now()))
        loadTimes.add(broker.queueAndMoveClock(ActivityStarted(homeActivity, clock.now())))
        broker.queueAndMoveClock(ActivityResumed(homeActivity, clock.now()))
        broker.queueAndMoveClock(ActivityResumed(homeActivity, clock.now()))
        broker.queueAndMoveClock(ActivityPaused(homeActivity, clock.now()))
        broker.queueAndMoveClock(ActivityPaused(homeActivity, clock.now()))
        loadTimes.add(broker.queueAndMoveClock(Backgrounded(clock.now())))
        broker.queueAndMoveClock(Backgrounded(clock.now()))
        assertEquals(
            listOf(
                Pair(loadTimes[0], homeActivity.localClassName),
                Pair(loadTimes[1], "Backgrounded"),
            ),
            states
        )
    }

    @Test
    fun `no screen load recorded for activities that do not hit resume`() {
        broker.queueAndMoveClock(ActivityStarted(homeActivity, clock.now()))
        loadTimes.add(broker.queueAndMoveClock(ActivityStarted(settingsActivity, clock.now())))
        broker.queueAndMoveClock(ActivityResumed(settingsActivity, clock.now()))
        assertEquals(Pair(loadTimes[0], settingsActivity.localClassName), states.single())
    }

    @Test
    fun `multiple visible activities results in compound state`() {
        loadTimes.add(broker.queueAndMoveClock(ActivityStarted(homeActivity, clock.now())))
        broker.queueAndMoveClock(ActivityResumed(homeActivity, clock.now()))
        broker.queueAndMoveClock(ActivityStarted(settingsActivity, clock.now()))
        loadTimes.add(broker.queueAndMoveClock(ActivityResumed(settingsActivity, clock.now())))
        assertEquals(
            listOf(
                Pair(loadTimes[0], homeActivity.localClassName),
                Pair(loadTimes[1], "${homeActivity.localClassName} + ${settingsActivity.localClassName}")
            ),
            states
        )
    }

    @Test
    fun `multiple activities with interleaved activity callback orders results in compound state`() {
        broker.queueAndMoveClock(ActivityStarted(homeActivity, clock.now()))
        loadTimes.add(broker.queueAndMoveClock(ActivityStarted(settingsActivity, clock.now())))
        broker.queueAndMoveClock(ActivityResumed(settingsActivity, clock.now()))
        loadTimes.add(broker.queueAndMoveClock(ActivityResumed(homeActivity, clock.now())))

        assertEquals(
            listOf(
                Pair(loadTimes[0], settingsActivity.localClassName),
                Pair(loadTimes[1], "${homeActivity.localClassName} + ${settingsActivity.localClassName}")
            ),
            states
        )
    }

    private fun NavigationEventBroker.queueAndMoveClock(event: NavigationEvent): Long {
        val eventTime = clock.now()
        queueEvent(event)
        shadowMainLooper.idle()
        clock.tick()
        return eventTime
    }

    private class HomeActivity : Activity()
    private class SettingsActivity : Activity()
    private class ProfileActivity : Activity()
}
