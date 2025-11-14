package io.embrace.android.embracesdk.internal.session

import android.app.Application
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.testing.TestLifecycleOwner
import io.embrace.android.embracesdk.fakes.FakeAppStateListener
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.FakeSessionOrchestrator
import io.embrace.android.embracesdk.internal.session.lifecycle.AppState
import io.embrace.android.embracesdk.internal.session.lifecycle.AppStateListener
import io.embrace.android.embracesdk.internal.session.lifecycle.AppStateServiceImpl
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionOrchestrator
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

internal class AppStateServiceTest {

    private lateinit var stateService: AppStateServiceImpl

    companion object {
        private lateinit var looper: Looper
        private lateinit var mockApplication: Application
        private val fakeClock = FakeClock()

        @BeforeClass
        @JvmStatic
        fun beforeClass() {
            looper = mockk(relaxed = true)
            mockkStatic(Looper::class)
            mockApplication = mockk(relaxed = true)

            fakeClock.setCurrentTime(1234)
            every { mockApplication.registerActivityLifecycleCallbacks(any()) } returns Unit
            every { Looper.getMainLooper() } returns looper
        }

        @JvmStatic
        @AfterClass
        fun tearDown() {
            unmockkAll()
        }
    }

    private lateinit var fakeEmbLogger: FakeEmbLogger

    @Before
    fun before() {
        clearAllMocks(
            answers = false,
            objectMocks = false,
            constructorMocks = false,
            staticMocks = false
        )
        fakeEmbLogger = FakeEmbLogger()
        stateService = AppStateServiceImpl(
            fakeClock,
            fakeEmbLogger,
            TestLifecycleOwner(Lifecycle.State.INITIALIZED)
        )
    }

    @Test
    fun `verify on activity foreground for cold start triggers listeners`() {
        val listener = FakeAppStateListener()
        stateService.addListener(listener)
        stateService.onForeground()
        assertTrue(listener.coldStart)
        assertEquals(listener.timestamp, fakeClock.now())
        assertEquals(1, listener.foregroundCount.get())
    }

    @Test
    fun `verify on activity foreground called twice is not a cold start`() {
        val listener = FakeAppStateListener()
        stateService.addListener(listener)

        stateService.onForeground()
        assertTrue(listener.coldStart)

        stateService.onForeground()
        assertFalse(listener.coldStart)
        assertEquals(2, listener.foregroundCount.get())
    }

    @Test
    fun `verify on activity background triggers listeners`() {
        val listener = FakeAppStateListener()
        stateService.addListener(listener)
        stateService.onBackground()
        assertEquals(listener.timestamp, fakeClock.now())
        assertEquals(1, listener.backgroundCount.get())
    }

    @Test
    fun `verify isInBackground returns true by default`() {
        assertEquals(AppState.BACKGROUND, stateService.getAppState())
    }

    @Test
    fun `verify isInBackground returns false if it was previously on foreground`() {
        stateService.onForeground()
        assertEquals(AppState.FOREGROUND, stateService.getAppState())
    }

    @Test
    fun `verify isInBackground returns true if it was previously on background`() {
        stateService.onBackground()
        assertEquals(AppState.BACKGROUND, stateService.getAppState())
    }

    @Test
    fun `verify a listener is added`() {
        // assert empty list first
        assertEquals(0, stateService.listeners.size)

        val listener = FakeAppStateListener()
        stateService.addListener(listener)
        assertEquals(1, stateService.listeners.size)
    }

    @Test
    fun `verify if listener is already present, then it does not add anything`() {
        val listener = FakeAppStateListener()
        stateService.addListener(listener)
        // add it for a 2nd time
        stateService.addListener(listener)
        assertEquals(1, stateService.listeners.size)
    }

    @Test
    fun `verify a listener is added with priority`() {
        stateService.addListener(FakeAppStateListener())
        val listener = FakeAppStateListener()
        stateService.addListener(listener)
        assertEquals(2, stateService.listeners.size)
        assertEquals(listener, stateService.listeners[1])
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
        stateService.addListener(DecoratedSessionOrchestrator(invocations))
        assertTrue(invocations.isEmpty())

        // verify on foreground follows specific call order
        stateService.onForeground()
        val foregroundExpected = listOf(
            "DecoratedSessionOrchestrator",
            "DecoratedListener"
        )
        assertEquals(foregroundExpected, invocations)

        // verify on background follows specific call order
        invocations.clear()
        stateService.onBackground()
        val backgroundExpected = listOf(
            "DecoratedListener",
            "DecoratedSessionOrchestrator",
        )
        assertEquals(backgroundExpected, invocations)
    }

    @Test
    fun testBalancedLifecycleCalls() {
        repeat(10) {
            stateService.onForeground()
            stateService.onBackground()
        }
        val messages = fakeEmbLogger.internalErrorMessages
        assertTrue(messages.isEmpty())
    }

    @Test
    fun testUnbalancedForegroundCall() {
        repeat(3) {
            stateService.onForeground()
        }
        stateService.onBackground()
        stateService.onForeground()

        val messages = fakeEmbLogger.internalErrorMessages
        assertEquals(0, messages.size)
    }

    @Test
    fun testUnbalancedBackgroundCall() {
        repeat(4) {
            stateService.onBackground()
        }
        stateService.onForeground()
        stateService.onBackground()

        val messages = fakeEmbLogger.internalErrorMessages
        assertTrue(messages.isEmpty())
    }

    @Test
    fun `launched in background`() {
        stateService = AppStateServiceImpl(
            fakeClock,
            fakeEmbLogger,
            mockk {
                every { lifecycle } returns mockk<Lifecycle> {
                    every { currentState } returns Lifecycle.State.INITIALIZED
                }
            }
        )
        assertEquals(AppState.BACKGROUND, stateService.getAppState())
    }

    @Test
    fun `launched in foreground`() {
        stateService = AppStateServiceImpl(
            fakeClock,
            fakeEmbLogger,
            mockk {
                every { lifecycle } returns mockk<Lifecycle> {
                    every { currentState } returns Lifecycle.State.STARTED
                }
            }
        )
        assertEquals(AppState.FOREGROUND, stateService.getAppState())
    }

    @Test
    fun `verify app state`() {
        assertEquals(AppState.BACKGROUND, stateService.getAppState())
        stateService.onForeground()
        assertEquals(AppState.FOREGROUND, stateService.getAppState())
    }

    private class DecoratedListener(
        private val invocations: MutableList<String>,
    ) : AppStateListener {

        override fun onBackground(timestamp: Long) {
            invocations.add(javaClass.simpleName)
        }

        override fun onForeground(coldStart: Boolean, timestamp: Long) {
            invocations.add(javaClass.simpleName)
        }
    }

    private class DecoratedSessionOrchestrator(
        private val invocations: MutableList<String>,
        private val orchestrator: SessionOrchestrator = FakeSessionOrchestrator(),
    ) : SessionOrchestrator by orchestrator {

        override fun onBackground(timestamp: Long) {
            invocations.add(javaClass.simpleName)
        }

        override fun onForeground(coldStart: Boolean, timestamp: Long) {
            invocations.add(javaClass.simpleName)
        }
    }
}
