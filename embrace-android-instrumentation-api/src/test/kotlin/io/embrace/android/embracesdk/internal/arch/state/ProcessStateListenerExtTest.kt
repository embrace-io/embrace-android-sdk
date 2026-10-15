package io.embrace.android.embracesdk.internal.arch.state

import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.internal.arch.lifecycle.ProcessChannels
import io.embrace.android.embracesdk.internal.utils.event.EventBus
import org.junit.Assert.assertEquals
import org.junit.Test

internal class ProcessStateListenerExtTest {

    private val eventBus = EventBus(FakeInternalLogger())
    private val calls = mutableListOf<String>()
    private val listener = object : ProcessStateListener {
        override fun onBackground() {
            calls.add("background")
        }

        override fun onForeground() {
            calls.add("foreground")
        }
    }

    @Test
    fun `the replayed state is a baseline, not a transition`() {
        eventBus.emitState(ProcessChannels.ProcessState, ProcessState.BACKGROUND)
        eventBus.addProcessStateListener(listener)

        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun `transitions after registration are dispatched`() {
        eventBus.emitState(ProcessChannels.ProcessState, ProcessState.BACKGROUND)
        eventBus.addProcessStateListener(listener)
        eventBus.emitState(ProcessChannels.ProcessState, ProcessState.FOREGROUND)
        eventBus.emitState(ProcessChannels.ProcessState, ProcessState.BACKGROUND)

        assertEquals(listOf("foreground", "background"), calls)
    }

    @Test
    fun `every state emitted after registration is dispatched`() {
        eventBus.emitState(ProcessChannels.ProcessState, ProcessState.FOREGROUND)
        eventBus.addProcessStateListener(listener)
        eventBus.emitState(ProcessChannels.ProcessState, ProcessState.FOREGROUND)

        assertEquals(listOf("foreground"), calls)
    }

    @Test
    fun `the first state is dispatched when none was retained at registration`() {
        eventBus.addProcessStateListener(listener)
        eventBus.emitState(ProcessChannels.ProcessState, ProcessState.FOREGROUND)

        assertEquals(listOf("foreground"), calls)
    }
}
