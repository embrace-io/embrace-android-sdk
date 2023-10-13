package io.embrace.android.embracesdk.session

import android.app.Activity
import android.app.Application
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Bundle
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import io.embrace.android.embracesdk.capture.memory.MemoryService
import io.embrace.android.embracesdk.capture.orientation.OrientationService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.mockk.Called
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test

internal class EmbraceActivityServiceTest {

    private lateinit var activityService: EmbraceActivityService

    companion object {
        private lateinit var mockLooper: Looper
        private lateinit var mockLifeCycleOwner: LifecycleOwner
        private lateinit var mockLifecycle: Lifecycle
        private lateinit var mockApplication: Application
        private lateinit var mockMemoryService: MemoryService
        private lateinit var mockOrientationService: OrientationService
        private val fakeClock = FakeClock()

        @BeforeClass
        @JvmStatic
        fun beforeClass() {
            mockLooper = mockk()
            mockLifeCycleOwner = mockk()
            mockLifecycle = mockk(relaxed = true)
            mockkStatic(Looper::class)
            mockkStatic(ProcessLifecycleOwner::class)
            mockApplication = mockk(relaxed = true)
            mockMemoryService = mockk()
            mockOrientationService = mockk()

            fakeClock.setCurrentTime(1234)
            every { mockApplication.registerActivityLifecycleCallbacks(any()) } returns Unit
            every { Looper.getMainLooper() } returns mockLooper
            every { mockLooper.thread } returns Thread.currentThread()
            every { ProcessLifecycleOwner.get() } returns mockLifeCycleOwner
            every { mockLifeCycleOwner.lifecycle } returns mockLifecycle
            every { mockLifecycle.addObserver(any()) } returns Unit
        }

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

        activityService = EmbraceActivityService(
            mockApplication,
            mockOrientationService,
            fakeClock
        )
        activityService.setMemoryService(mockMemoryService)
    }

    @Test
    fun `test we are adding lifecycle observer on constructor`() {
        verify { mockApplication.registerActivityLifecycleCallbacks(activityService) }
        verify { mockApplication.applicationContext.registerComponentCallbacks(activityService) }
    }

    @Test
    fun `test on activity created updates state and orientation`() {
        val mockActivity = mockk<Activity>()
        every { mockActivity.localClassName } returns "localClassName"
        val mockActivityListener = mockk<ActivityListener>()
        activityService.addListener(mockActivityListener)

        val mockResources = mockk<Resources>()
        val orientation = 1
        val mockConfiguration = Configuration()
        mockConfiguration.orientation = orientation
        every { mockActivity.isFinishing } returns false

        every { mockActivity.resources } returns mockResources
        every { mockResources.configuration } returns mockConfiguration
        val bundle = Bundle()

        activityService.onActivityCreated(mockActivity, bundle)

        assertEquals(mockActivity, activityService.foregroundActivity)
        verify { mockOrientationService.onOrientationChanged(orientation) }
        verify { mockActivityListener.onActivityCreated(mockActivity, bundle) }
    }

    @Test
    fun `test on activity started with no listeners`() {
        val mockActivity = mockk<Activity>()
        every { mockActivity.localClassName } returns "localClassName"

        activityService.onActivityStarted(mockActivity)
        every { mockActivity.isFinishing } returns false

        assertEquals(mockActivity, activityService.foregroundActivity)
    }

    @Test
    fun `test on activity started with activity listener`() {
        val mockActivity = mockk<Activity>()
        every { mockActivity.localClassName } returns "localClassName"
        val mockActivityListener = mockk<ActivityListener>()
        activityService.addListener(mockActivityListener)
        every { mockActivity.isFinishing } returns false

        activityService.onActivityStarted(mockActivity)

        verify { mockActivityListener.onView(mockActivity) }
        assertEquals(mockActivity, activityService.foregroundActivity)
    }

    @Test
    fun `verify on activity resumed for a StartupActivity does not trigger listeners`() {
        val mockActivityListener = mockk<ActivityListener>()
        activityService.addListener(mockActivityListener)

        activityService.onActivityResumed(TestStartupActivity())

        verify { mockActivityListener wasNot Called }
    }

    @Test
    fun `verify on activity resumed for a non StartupActivity does trigger listeners`() {
        val mockActivityListener = mockk<ActivityListener>()
        activityService.addListener(mockActivityListener)

        activityService.onActivityResumed(TestNonStartupActivity())

        verify { mockActivityListener.applicationStartupComplete() }
    }

    @Test
    fun `verify on activity stopped triggers listeners`() {
        val mockActivity = mockk<Activity>()
        every { mockActivity.localClassName } returns "localClassName"
        val mockActivityListener = mockk<ActivityListener>()
        activityService.addListener(mockActivityListener)

        activityService.onActivityStopped(mockActivity)

        verify { mockActivityListener.onViewClose(mockActivity) }
    }

    @Test
    fun `verify on activity foreground for cold start triggers listeners`() {
        val mockActivityListener = mockk<ActivityListener>()
        activityService.addListener(mockActivityListener)

        activityService.onForeground()

        verify { mockActivityListener.onForeground(true, fakeClock.now(), fakeClock.now()) }
    }

    @Test
    fun `verify on activity foreground called twice is not a cold start`() {
        val mockActivityListener = mockk<ActivityListener>()
        activityService.addListener(mockActivityListener)

        with(activityService) {
            onForeground()
            // repeat so it's not a cold start
            onForeground()
        }

        verify { mockActivityListener.onForeground(true, fakeClock.now(), fakeClock.now()) }
        verify { mockActivityListener.onForeground(true, fakeClock.now(), fakeClock.now()) }
    }

    @Test
    fun `verify on activity background triggers listeners`() {
        val mockActivityListener = mockk<ActivityListener>()
        activityService.addListener(mockActivityListener)

        activityService.onBackground()

        verify { mockActivityListener.onBackground(any()) }
    }

    @Test
    fun `verify on trim on level not running low does not do anything`() {
        activityService.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE)

        verify { mockMemoryService wasNot Called }
    }

    @Test
    fun `verify on trim on level running low triggers memory warning`() {
        activityService.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)

        verify { mockMemoryService.onMemoryWarning() }
    }

    @Test
    fun `verify isInBackground returns true by default`() {
        assertTrue(activityService.isInBackground)
    }

    @Test
    fun `verify isInBackground returns false if it was previously on foreground`() {
        activityService.onForeground()

        assertFalse(activityService.isInBackground)
    }

    @Test
    fun `verify isInBackground returns true if it was previously on background`() {
        activityService.onBackground()

        assertTrue(activityService.isInBackground)
    }

    @Test
    fun `get foreground activity for existing current activity`() {
        val mockActivity = mockk<Activity>()
        every { mockActivity.localClassName } returns "localClassName"
        every { mockActivity.isFinishing } returns false
        activityService.updateStateWithActivity(mockActivity)

        assertEquals(mockActivity, activityService.foregroundActivity)
    }

    @Test
    fun `get foreground activity for an activity that is finishing should return absent`() {
        val mockActivity = mockk<Activity>()
        every { mockActivity.localClassName } returns "localClassName"
        every { mockActivity.isFinishing } returns true
        activityService.updateStateWithActivity(mockActivity)

        assertNull(activityService.foregroundActivity)
    }

    @Test
    fun `get foreground activity for a non existing activity should return absent`() {
        activityService.updateStateWithActivity(null)
        assertNull(activityService.foregroundActivity)
    }

    @Test
    fun `verify a listener is added`() {
        // assert empty list first
        assertEquals(0, activityService.listeners.size)

        val mockActivityListener = mockk<ActivityListener>()
        activityService.addListener(mockActivityListener)

        assertEquals(1, activityService.listeners.size)
    }

    @Test
    fun `verify if listener is already present, then it does not add anything`() {
        val mockActivityListener = mockk<ActivityListener>()
        activityService.addListener(mockActivityListener)
        // add it for a 2nd time
        activityService.addListener(mockActivityListener)

        assertEquals(1, activityService.listeners.size)
    }

    @Test
    fun `verify a listener is added with priority`() {
        val mockActivityListener = mockk<ActivityListener>()
        val mockActivityListener2 = mockk<ActivityListener>()
        activityService.addListener(mockActivityListener)

        activityService.addListener(mockActivityListener2)

        assertEquals(2, activityService.listeners.size)
        assertEquals(mockActivityListener2, activityService.listeners[1])
    }

    @Test
    fun `verify close cleans everything`() {
        // add a listener first, so we then check that listener have been cleared
        val mockActivityListener = mockk<ActivityListener>()
        activityService.addListener(mockActivityListener)
        every { mockApplication.unregisterActivityLifecycleCallbacks(activityService) } returns Unit

        activityService.close()

        verify { mockApplication.applicationContext.unregisterComponentCallbacks(activityService) }
        verify { mockApplication.unregisterActivityLifecycleCallbacks(activityService) }
        assertTrue(activityService.listeners.isEmpty())
    }

    @io.embrace.android.embracesdk.annotation.StartupActivity
    private class TestStartupActivity : Activity()

    private class TestNonStartupActivity : Activity()
}
