package io.embrace.android.embracesdk.internal.session

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.testing.TestLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeProcessStateListener
import io.embrace.android.embracesdk.internal.arch.state.ProcessState
import io.embrace.android.embracesdk.internal.session.lifecycle.AndroidxProcessLifecycleTracker
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers the ProcessLifecycleOwner-based implementation, i.e. deciding when a transition happened
 * from lifecycle events.
 */
@RunWith(AndroidJUnit4::class)
internal class AndroidxProcessLifecycleTrackerTest {

    private lateinit var listener: FakeProcessStateListener

    @Before
    fun before() {
        listener = FakeProcessStateListener()
    }

    private fun createTracker(
        lifecycleOwner: TestLifecycleOwner,
        registerTracker: Boolean = true,
    ): AndroidxProcessLifecycleTracker = AndroidxProcessLifecycleTracker(lifecycleOwner).apply {
        if (registerTracker) {
            register(listener)
        }
    }

    @Test
    fun `launched in background`() {
        val tracker = createTracker(TestLifecycleOwner(Lifecycle.State.INITIALIZED))
        assertEquals(ProcessState.BACKGROUND, tracker.getProcessState())
    }

    @Test
    fun `created but not started counts as background`() {
        val tracker = createTracker(TestLifecycleOwner(Lifecycle.State.CREATED))
        assertEquals(ProcessState.BACKGROUND, tracker.getProcessState())
    }

    @Test
    fun `launched in foreground`() {
        val tracker = createTracker(TestLifecycleOwner(Lifecycle.State.STARTED))
        assertEquals(ProcessState.FOREGROUND, tracker.getProcessState())
    }

    @Test
    fun `resumed counts as foreground`() {
        val tracker = createTracker(TestLifecycleOwner(Lifecycle.State.RESUMED))
        assertEquals(ProcessState.FOREGROUND, tracker.getProcessState())
    }

    @Test
    fun `lifecycle events move the app between the foreground and the background`() {
        val lifecycleOwner = TestLifecycleOwner(Lifecycle.State.INITIALIZED)
        val tracker = createTracker(lifecycleOwner)

        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
        assertEquals(1, listener.foregroundCount.get())
        assertEquals(0, listener.backgroundCount.get())
        assertEquals(ProcessState.FOREGROUND, tracker.getProcessState())

        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        assertEquals(1, listener.foregroundCount.get())
        assertEquals(1, listener.backgroundCount.get())
        assertEquals(ProcessState.BACKGROUND, tracker.getProcessState())
    }

    @Test
    fun `lifecycle events are ignored until the tracker registers`() {
        val lifecycleOwner = TestLifecycleOwner(Lifecycle.State.INITIALIZED)
        createTracker(lifecycleOwner, registerTracker = false)

        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)

        assertEquals(0, listener.foregroundCount.get())
        assertEquals(0, listener.backgroundCount.get())
    }

    @Test
    fun `lifecycle events other than start and stop are ignored`() {
        val lifecycleOwner = TestLifecycleOwner(Lifecycle.State.INITIALIZED)
        createTracker(lifecycleOwner)

        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        assertEquals(0, listener.foregroundCount.get())
        assertEquals(0, listener.backgroundCount.get())

        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)

        assertEquals(1, listener.foregroundCount.get())
        assertEquals(0, listener.backgroundCount.get())
    }

    @Test
    fun `registering while already started reports a foreground transition`() {
        createTracker(TestLifecycleOwner(Lifecycle.State.STARTED))

        assertEquals(1, listener.foregroundCount.get())
        assertEquals(0, listener.backgroundCount.get())
    }
}
