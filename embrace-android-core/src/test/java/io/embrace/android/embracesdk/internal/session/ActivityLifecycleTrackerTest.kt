package io.embrace.android.embracesdk.internal.session

import android.app.Activity
import android.app.Application
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Bundle
import android.os.Looper
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.internal.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.internal.session.lifecycle.ActivityLifecycleListener
import io.embrace.android.embracesdk.internal.session.lifecycle.ActivityLifecycleTracker
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test

internal class ActivityLifecycleTrackerTest {

    private lateinit var activityLifecycleTracker: ActivityLifecycleTracker

    companion object {
        private lateinit var mockLooper: Looper
        private lateinit var application: Application
        private val fakeClock = FakeClock()

        @BeforeClass
        @JvmStatic
        fun beforeClass() {
            mockLooper = mockk(relaxed = true)
            mockkStatic(Looper::class)
            application = mockk(relaxed = true)

            fakeClock.setCurrentTime(1234)
            every { application.registerActivityLifecycleCallbacks(any()) } returns Unit
            every { Looper.getMainLooper() } returns mockLooper
        }

        @JvmStatic
        @AfterClass
        fun tearDown() {
            unmockkAll()
        }
    }

    @Before
    fun before() {
        clearAllMocks(
            answers = false,
            objectMocks = false,
            constructorMocks = false,
            staticMocks = false
        )

        activityLifecycleTracker = ActivityLifecycleTracker(
            application,
            EmbLoggerImpl()
        )
    }

    @Test
    fun `test we are adding lifecycle observer on constructor`() {
        verify { application.registerActivityLifecycleCallbacks(activityLifecycleTracker) }
    }

    @Test
    fun `test on activity created updates state and orientation`() {
        val mockActivity = mockk<Activity>()
        every { mockActivity.localClassName } returns "localClassName"
        val mockActivityLifecycleListener = mockk<ActivityLifecycleListener>()
        activityLifecycleTracker.addListener(mockActivityLifecycleListener)

        val mockResources = mockk<Resources>()
        val orientation = 1
        val mockConfiguration = Configuration()
        mockConfiguration.orientation = orientation
        every { mockActivity.isFinishing } returns false

        every { mockActivity.resources } returns mockResources
        every { mockResources.configuration } returns mockConfiguration
        val bundle = Bundle()

        activityLifecycleTracker.onActivityCreated(mockActivity, bundle)

        assertEquals(mockActivity, activityLifecycleTracker.foregroundActivity)
        verify { mockActivityLifecycleListener.onActivityCreated(mockActivity, bundle) }
    }

    @Test
    fun `test on activity started with no listeners`() {
        val mockActivity = mockk<Activity>()
        every { mockActivity.localClassName } returns "localClassName"

        activityLifecycleTracker.onActivityStarted(mockActivity)
        every { mockActivity.isFinishing } returns false

        assertEquals(mockActivity, activityLifecycleTracker.foregroundActivity)
    }

    @Test
    fun `test on activity started with activity listener`() {
        val mockActivity = mockk<Activity>()
        every { mockActivity.localClassName } returns "localClassName"
        val mockActivityLifecycleListener = mockk<ActivityLifecycleListener>()
        activityLifecycleTracker.addListener(mockActivityLifecycleListener)
        every { mockActivity.isFinishing } returns false

        activityLifecycleTracker.onActivityStarted(mockActivity)

        verify { mockActivityLifecycleListener.onActivityStarted(mockActivity) }
        assertEquals(mockActivity, activityLifecycleTracker.foregroundActivity)
    }

    @Test
    fun `verify on activity stopped triggers listeners`() {
        val mockActivity = mockk<Activity>()
        every { mockActivity.localClassName } returns "localClassName"
        val mockActivityLifecycleListener = mockk<ActivityLifecycleListener>()
        activityLifecycleTracker.addListener(mockActivityLifecycleListener)

        activityLifecycleTracker.onActivityStopped(mockActivity)

        verify { mockActivityLifecycleListener.onActivityStopped(mockActivity) }
    }

    @Test
    fun `get foreground activity for existing current activity`() {
        val mockActivity = mockk<Activity>()
        every { mockActivity.localClassName } returns "localClassName"
        every { mockActivity.isFinishing } returns false
        activityLifecycleTracker.updateStateWithActivity(mockActivity)

        assertEquals(mockActivity, activityLifecycleTracker.foregroundActivity)
    }

    @Test
    fun `get foreground activity for an activity that is finishing should return absent`() {
        val mockActivity = mockk<Activity>()
        every { mockActivity.localClassName } returns "localClassName"
        every { mockActivity.isFinishing } returns true
        activityLifecycleTracker.updateStateWithActivity(mockActivity)

        assertNull(activityLifecycleTracker.foregroundActivity)
    }

    @Test
    fun `get foreground activity for a non existing activity should return absent`() {
        activityLifecycleTracker.updateStateWithActivity(null)
        assertNull(activityLifecycleTracker.foregroundActivity)
    }

    @Test
    fun `verify a listener is added`() {
        // assert empty list first
        assertEquals(0, activityLifecycleTracker.activityListeners.size)

        val mockActivityLifecycleListener = mockk<ActivityLifecycleListener>()
        activityLifecycleTracker.addListener(mockActivityLifecycleListener)

        assertEquals(1, activityLifecycleTracker.activityListeners.size)
    }

    @Test
    fun `verify if listener is already present, then it does not add anything`() {
        val mockActivityLifecycleListener = mockk<ActivityLifecycleListener>()
        activityLifecycleTracker.addListener(mockActivityLifecycleListener)
        // add it for a 2nd time
        activityLifecycleTracker.addListener(mockActivityLifecycleListener)

        assertEquals(1, activityLifecycleTracker.activityListeners.size)
    }

    @Test
    fun `verify a listener is added with priority`() {
        val mockActivityLifecycleListener = mockk<ActivityLifecycleListener>()
        val mockActivityLifecycleListener2 = mockk<ActivityLifecycleListener>()
        activityLifecycleTracker.addListener(mockActivityLifecycleListener)

        activityLifecycleTracker.addListener(mockActivityLifecycleListener2)

        assertEquals(2, activityLifecycleTracker.activityListeners.size)
        assertEquals(mockActivityLifecycleListener2, activityLifecycleTracker.activityListeners[1])
    }

    @Test
    fun `verify close cleans everything`() {
        // add a listener first, so we then check that listener have been cleared
        val mockActivityLifecycleListener = mockk<ActivityLifecycleListener>()
        activityLifecycleTracker.addListener(mockActivityLifecycleListener)

        every { application.unregisterActivityLifecycleCallbacks(activityLifecycleTracker) } returns Unit

        activityLifecycleTracker.close()

        verify { application.unregisterActivityLifecycleCallbacks(activityLifecycleTracker) }
        assertTrue(activityLifecycleTracker.activityListeners.isEmpty())
    }
}
