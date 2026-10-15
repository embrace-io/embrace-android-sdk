package io.embrace.android.embracesdk.internal.session

import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.fakes.FakeLifecycleTracker
import io.embrace.android.embracesdk.fakes.FakeProcessStateListener
import io.embrace.android.embracesdk.fakes.FakeSessionOrchestrator
import io.embrace.android.embracesdk.internal.arch.lifecycle.ProcessChannels
import io.embrace.android.embracesdk.internal.arch.state.ProcessState
import io.embrace.android.embracesdk.internal.arch.state.ProcessStateListener
import io.embrace.android.embracesdk.internal.session.lifecycle.ProcessStateTrackerImpl
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionOrchestrator
import io.embrace.android.embracesdk.internal.utils.event.EventBus
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
    private lateinit var eventBus: EventBus

    @Before
    fun before() {
        fakeEmbLogger = FakeInternalLogger()
        lifecycleTracker = FakeLifecycleTracker()
        eventBus = EventBus(fakeEmbLogger)
        stateService = ProcessStateTrackerImpl(fakeEmbLogger, lifecycleTracker, eventBus)
    }

    @Test
    fun `verify on activity foreground for cold start triggers listeners`() {
        val listener = FakeProcessStateListener()
        stateService.addListener(listener)
        stateService.onForeground()
        assertEquals(1, listener.foregroundCount.get())
    }

    @Test
    fun `a repeated foreground is not a new transition`() {
        val listener = FakeProcessStateListener()
        stateService.addListener(listener)
        stateService.onForeground()
        stateService.onForeground()
        assertEquals(1, listener.foregroundCount.get())
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
    fun `close closes the lifecycle tracker and drops the session orchestrator`() {
        val invocations = mutableListOf<String>()
        stateService.addListener(DecoratedSessionOrchestrator(invocations))
        stateService.register()
        stateService.close()
        stateService.onForeground()
        stateService.onBackground()

        assertTrue(lifecycleTracker.closed)
        assertNull(lifecycleTracker.listener)
        assertTrue(invocations.isEmpty())
    }

    @Test
    fun `register emits the initial state, which listeners take as their baseline`() {
        lifecycleTracker.state = ProcessState.BACKGROUND
        stateService.register()
        val listener = FakeProcessStateListener()
        stateService.addListener(listener)

        assertEquals(ProcessState.BACKGROUND, eventBus.currentState(ProcessChannels.ProcessState))
        assertEquals(0, listener.backgroundCount.get())
    }

    @Test
    fun `each transition is emitted on the bus`() {
        stateService.onForeground()
        assertEquals(ProcessState.FOREGROUND, eventBus.currentState(ProcessChannels.ProcessState))

        stateService.onBackground()
        assertEquals(ProcessState.BACKGROUND, eventBus.currentState(ProcessChannels.ProcessState))
    }

    @Test
    fun `app state is whatever the lifecycle tracker reports`() {
        lifecycleTracker.state = ProcessState.BACKGROUND
        assertEquals(ProcessState.BACKGROUND, stateService.getAppState())

        lifecycleTracker.state = ProcessState.FOREGROUND
        assertEquals(ProcessState.FOREGROUND, stateService.getAppState())
    }

    /**
     * Confirms that the state is published before the session orchestrator reacts to it. This test case is important for
     * ensuring the session/background activity boundary doesn't lose data during the transition.
     */
    @Test
    fun `verify listener call order`() {
        val invocations = mutableListOf<String>()
        stateService.addListener(DecoratedListener(invocations))
        stateService.addListener(DecoratedSessionOrchestrator(invocations))
        assertTrue(invocations.isEmpty())

        stateService.onForeground()
        assertEquals(listOf("DecoratedListener", "DecoratedSessionOrchestrator"), invocations)

        invocations.clear()
        stateService.onBackground()
        assertEquals(listOf("DecoratedListener", "DecoratedSessionOrchestrator"), invocations)
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
