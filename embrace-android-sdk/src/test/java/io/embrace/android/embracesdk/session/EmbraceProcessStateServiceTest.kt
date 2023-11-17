package io.embrace.android.embracesdk.session

import android.app.Application
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import io.embrace.android.embracesdk.FakeSessionService
import io.embrace.android.embracesdk.fakes.FakeBackgroundActivityService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeProcessStateListener
import io.embrace.android.embracesdk.session.lifecycle.EmbraceProcessStateService
import io.embrace.android.embracesdk.session.lifecycle.ProcessStateListener
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test

internal class EmbraceProcessStateServiceTest {

    private lateinit var stateService: EmbraceProcessStateService

    companion object {
        private lateinit var mockLooper: Looper
        private lateinit var mockLifeCycleOwner: LifecycleOwner
        private lateinit var mockLifecycle: Lifecycle
        private lateinit var mockApplication: Application
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

            fakeClock.setCurrentTime(1234)
            every { mockApplication.registerActivityLifecycleCallbacks(any()) } returns Unit
            every { Looper.getMainLooper() } returns mockLooper
            every { mockLooper.thread } returns Thread.currentThread()
            every { ProcessLifecycleOwner.get() } returns mockLifeCycleOwner
            every { mockLifeCycleOwner.lifecycle } returns mockLifecycle
            every { mockLifecycle.addObserver(any()) } returns Unit
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

        stateService = EmbraceProcessStateService(
            fakeClock
        )
    }

    @Test
    fun `verify on activity foreground for cold start triggers listeners`() {
        val listener = FakeProcessStateListener()
        stateService.addListener(listener)
        stateService.onForeground()
        assertTrue(listener.coldStart)
        assertEquals(listener.startupTime, fakeClock.now())
        assertEquals(listener.timestamp, fakeClock.now())
        assertEquals(1, listener.foregroundCount.get())
    }

    @Test
    fun `verify on activity foreground called twice is not a cold start`() {
        val listener = FakeProcessStateListener()
        stateService.addListener(listener)

        stateService.onForeground()
        assertTrue(listener.coldStart)

        stateService.onForeground()
        assertFalse(listener.coldStart)
        assertEquals(2, listener.foregroundCount.get())
    }

    @Test
    fun `verify on activity background triggers listeners`() {
        val listener = FakeProcessStateListener()
        stateService.addListener(listener)
        stateService.onBackground()
        assertEquals(listener.timestamp, fakeClock.now())
        assertEquals(1, listener.backgroundCount.get())
    }

    @Test
    fun `verify isInBackground returns true by default`() {
        assertTrue(stateService.isInBackground)
    }

    @Test
    fun `verify isInBackground returns false if it was previously on foreground`() {
        stateService.onForeground()
        assertFalse(stateService.isInBackground)
    }

    @Test
    fun `verify isInBackground returns true if it was previously on background`() {
        stateService.onBackground()
        assertTrue(stateService.isInBackground)
    }

    @Test
    fun `verify a listener is added`() {
        // assert empty list first
        assertEquals(0, stateService.listeners.size)

        val listener = FakeProcessStateListener()
        stateService.addListener(listener)
        assertEquals(1, stateService.listeners.size)
    }

    @Test
    fun `verify if listener is already present, then it does not add anything`() {
        val listener = FakeProcessStateListener()
        stateService.addListener(listener)
        // add it for a 2nd time
        stateService.addListener(listener)
        assertEquals(1, stateService.listeners.size)
    }

    @Test
    fun `verify a listener is added with priority`() {
        stateService.addListener(FakeProcessStateListener())
        val listener = FakeProcessStateListener()
        stateService.addListener(listener)
        assertEquals(2, stateService.listeners.size)
        assertEquals(listener, stateService.listeners[1])
    }

    @Test
    fun `verify close cleans everything`() {
        // add a listener first, so we then check that listener have been cleared
        stateService.addListener(FakeProcessStateListener())
        stateService.close()
        assertTrue(stateService.listeners.isEmpty())
    }

    /**
     * Confirms that the order of the listeners is respected, using decorated types. This test case
     * is important for ensuring the session/background activity boundary doesn't lose data during
     * the transition.
     */
    @Test
    fun `verify listener call order`() {
        val invocations = mutableListOf<String>()
        stateService.addListener(DecoratedListener(invocations))
        stateService.addListener(DecoratedSessionService(invocations))
        stateService.addListener(DecoratedBackgroundActivityService(invocations))
        assertTrue(invocations.isEmpty())

        // verify on foreground follows specific call order
        stateService.onForeground()
        val foregroundExpected = listOf(
            "DecoratedSessionService",
            "DecoratedListener",
            "DecoratedBackgroundActivityService"
        )
        assertEquals(foregroundExpected, invocations)

        // verify on background follows specific call order
        invocations.clear()
        stateService.onBackground()
        val backgroundExpected = listOf(
            "DecoratedSessionService",
            "DecoratedListener",
            "DecoratedBackgroundActivityService",
        )
        assertEquals(backgroundExpected, invocations)
    }

    private class DecoratedListener(
        private val invocations: MutableList<String>
    ) : ProcessStateListener {

        override fun onBackground(timestamp: Long) {
            invocations.add(javaClass.simpleName)
        }

        override fun onForeground(coldStart: Boolean, startupTime: Long, timestamp: Long) {
            invocations.add(javaClass.simpleName)
        }
    }

    private class DecoratedSessionService(
        private val invocations: MutableList<String>,
        private val service: SessionService = FakeSessionService()
    ) : SessionService by service {

        override fun onBackground(timestamp: Long) {
            invocations.add(javaClass.simpleName)
        }

        override fun onForeground(coldStart: Boolean, startupTime: Long, timestamp: Long) {
            invocations.add(javaClass.simpleName)
        }
    }

    private class DecoratedBackgroundActivityService(
        private val invocations: MutableList<String>,
        private val service: BackgroundActivityService = FakeBackgroundActivityService()
    ) : BackgroundActivityService by service {

        override fun onBackground(timestamp: Long) {
            invocations.add(javaClass.simpleName)
        }

        override fun onForeground(coldStart: Boolean, startupTime: Long, timestamp: Long) {
            invocations.add(javaClass.simpleName)
        }
    }
}
