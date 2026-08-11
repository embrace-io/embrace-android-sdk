package io.embrace.android.embracesdk.internal.session

import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.fakes.FakeLifecycleTracker
import io.embrace.android.embracesdk.fakes.FakeProcessStateListener
import io.embrace.android.embracesdk.fakes.FakeSessionOrchestrator
import io.embrace.android.embracesdk.internal.arch.state.ProcessState
import io.embrace.android.embracesdk.internal.arch.state.ProcessStateListener
import io.embrace.android.embracesdk.internal.session.lifecycle.ProcessStateTrackerImpl
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionOrchestrator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class ProcessStateTrackerTest {

    private lateinit var stateService: ProcessStateTrackerImpl
    private lateinit var lifecycleTracker: FakeLifecycleTracker
    private lateinit var fakeEmbLogger: FakeInternalLogger

    @Before
    fun before() {
        fakeEmbLogger = FakeInternalLogger()
        lifecycleTracker = FakeLifecycleTracker()
        stateService = ProcessStateTrackerImpl(fakeEmbLogger, lifecycleTracker)
    }

    @Test
    fun `verify on activity foreground for cold start triggers listeners`() {
        val listener = FakeProcessStateListener()
        stateService.addListener(listener)
        stateService.onForeground()
        assertEquals(1, listener.foregroundCount.get())
    }

    @Test
    fun `verify on activity foreground called twice is not a cold start`() {
        val listener = FakeProcessStateListener()
        stateService.addListener(listener)
        stateService.onForeground()
        stateService.onForeground()
        assertEquals(2, listener.foregroundCount.get())
    }

    @Test
    fun `verify on activity background triggers listeners`() {
        val listener = FakeProcessStateListener()
        stateService.addListener(listener)
        stateService.onBackground()
        assertEquals(1, listener.backgroundCount.get())
    }

    @Test
    fun `register subscribes to the lifecycle tracker`() {
        assertNull(lifecycleTracker.listener)
        stateService.register()
        assertSame(stateService, lifecycleTracker.listener)
    }

    @Test
    fun `app state is whatever the lifecycle tracker reports`() {
        lifecycleTracker.state = ProcessState.BACKGROUND
        assertEquals(ProcessState.BACKGROUND, stateService.getAppState())

        lifecycleTracker.state = ProcessState.FOREGROUND
        assertEquals(ProcessState.FOREGROUND, stateService.getAppState())
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
            "DecoratedListener",
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

    private class DecoratedListener(
        private val invocations: MutableList<String>,
    ) : ProcessStateListener {

        override fun onBackground() {
            invocations.add(javaClass.simpleName)
        }

        override fun onForeground() {
            invocations.add(javaClass.simpleName)
        }
    }

    private class DecoratedSessionOrchestrator(
        private val invocations: MutableList<String>,
        private val orchestrator: SessionOrchestrator = FakeSessionOrchestrator(),
    ) : SessionOrchestrator by orchestrator {

        override fun onBackground() {
            invocations.add(javaClass.simpleName)
        }

        override fun onForeground() {
            invocations.add(javaClass.simpleName)
        }
    }
}
