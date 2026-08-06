package io.embrace.android.embracesdk.internal.session

import android.app.Activity
import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeProcessStateListener
import io.embrace.android.embracesdk.internal.arch.state.ProcessState
import io.embrace.android.embracesdk.internal.session.lifecycle.ActivityProcessLifecycleTracker
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import java.time.Duration

/**
 * Covers the ActivityLifecycleCallbacks-based implementation, i.e. deciding when a transition
 * happened by counting started activities, and guessing the state the process is already in when
 * the SDK starts too late to observe it.
 */
@RunWith(AndroidJUnit4::class)
internal class ActivityProcessLifecycleTrackerTest {

    private lateinit var application: Application
    private lateinit var listener: FakeProcessStateListener

    @Before
    fun before() {
        application = RuntimeEnvironment.getApplication()
        listener = FakeProcessStateListener()
        setProcessImportance(RunningAppProcessInfo.IMPORTANCE_CACHED)
    }

    @Test
    fun `an application context with a backgrounded process counts as background`() {
        val tracker = createTracker()

        assertEquals(ProcessState.BACKGROUND, tracker.getProcessState())
        assertEquals(0, listener.foregroundCount.get())
        assertEquals(0, listener.backgroundCount.get())
    }

    @Test
    fun `an activity context counts as foreground`() {
        val tracker = createTracker(startupContext = buildActivity().get())

        assertEquals(ProcessState.FOREGROUND, tracker.getProcessState())
    }

    @Test
    fun `an activity wrapped in a ContextWrapper counts as foreground`() {
        val activity = buildActivity().get()
        val tracker = createTracker(startupContext = ContextWrapper(ContextWrapper(activity)))

        assertEquals(ProcessState.FOREGROUND, tracker.getProcessState())
    }

    @Test
    fun `a finishing activity does not count as foreground`() {
        val controller = buildActivity()
        controller.get().finish()

        val tracker = createTracker(startupContext = controller.get())

        assertEquals(ProcessState.BACKGROUND, tracker.getProcessState())
    }

    @Test
    fun `a destroyed activity does not count as foreground`() {
        val controller = buildActivity()
        val activity = controller.get()
        controller.destroy()

        val tracker = createTracker(startupContext = activity)

        assertEquals(ProcessState.BACKGROUND, tracker.getProcessState())
    }

    @Test
    fun `a foreground process counts as foreground when the context is not an activity`() {
        setProcessImportance(RunningAppProcessInfo.IMPORTANCE_FOREGROUND)

        val tracker = createTracker()

        assertEquals(ProcessState.FOREGROUND, tracker.getProcessState())
    }

    @Test
    fun `a foreground service does not count as foreground`() {
        setProcessImportance(RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE)

        val tracker = createTracker()

        assertEquals(ProcessState.BACKGROUND, tracker.getProcessState())
    }

    @Test
    fun `a missing ActivityManager counts as background`() {
        shadowOf(activityManager()).setProcesses(emptyList())

        val tracker = createTracker()

        assertEquals(ProcessState.BACKGROUND, tracker.getProcessState())
    }

    @Test
    fun `registering while already in the foreground reports a foreground transition`() {
        createTracker(startupContext = buildActivity().get())

        assertEquals(1, listener.foregroundCount.get())
        assertEquals(0, listener.backgroundCount.get())
    }

    @Test
    fun `activity callbacks are ignored until the tracker registers`() {
        val tracker = createTracker(registerTracker = false)

        buildActivity().start()

        assertEquals(0, listener.foregroundCount.get())
        assertEquals(ProcessState.BACKGROUND, tracker.getProcessState())
    }

    @Test
    fun `activity callbacks move the app between the foreground and the background`() {
        val tracker = createTracker()
        val controller = buildActivity()

        controller.start()
        assertEquals(1, listener.foregroundCount.get())
        assertEquals(0, listener.backgroundCount.get())
        assertEquals(ProcessState.FOREGROUND, tracker.getProcessState())

        controller.stop()
        elapseDebounce()
        assertEquals(1, listener.foregroundCount.get())
        assertEquals(1, listener.backgroundCount.get())
        assertEquals(ProcessState.BACKGROUND, tracker.getProcessState())
    }

    @Test
    fun `a background transition is not reported until the debounce elapses`() {
        val tracker = createTracker()
        val controller = buildActivity()
        controller.start()

        controller.stop()
        shadowOf(application.mainLooper).idle()

        assertEquals(0, listener.backgroundCount.get())
        assertEquals(ProcessState.FOREGROUND, tracker.getProcessState())
    }

    @Test
    fun `a configuration change does not report a background transition`() {
        val tracker = createTracker()
        buildActivity().start().stop()

        // the replacement activity starts before the debounce elapses
        buildActivity().start()
        elapseDebounce()

        assertEquals(1, listener.foregroundCount.get())
        assertEquals(0, listener.backgroundCount.get())
        assertEquals(ProcessState.FOREGROUND, tracker.getProcessState())
    }

    @Test
    fun `navigating between activities does not report a background transition`() {
        val tracker = createTracker()
        val first = buildActivity()
        val second = buildActivity()

        first.start()
        second.start()
        first.stop()
        elapseDebounce()

        assertEquals(1, listener.foregroundCount.get())
        assertEquals(0, listener.backgroundCount.get())

        second.stop()
        elapseDebounce()

        assertEquals(1, listener.backgroundCount.get())
        assertEquals(ProcessState.BACKGROUND, tracker.getProcessState())
    }

    @Test
    fun `repeated activity starts do not report duplicate foreground transitions`() {
        createTracker()

        buildActivity().start()
        buildActivity().start()
        buildActivity().start()

        assertEquals(1, listener.foregroundCount.get())
    }

    @Test
    fun `stopping an activity that started before registration reports a background transition`() {
        val controller = buildActivity().start()
        val tracker = createTracker(startupContext = controller.get())
        assertEquals(1, listener.foregroundCount.get())

        controller.stop()
        elapseDebounce()

        assertEquals(1, listener.backgroundCount.get())
        assertEquals(ProcessState.BACKGROUND, tracker.getProcessState())
    }

    @Test
    fun `an activity that started before registration is detected via process importance`() {
        setProcessImportance(RunningAppProcessInfo.IMPORTANCE_FOREGROUND)
        val controller = buildActivity().start()
        val tracker = createTracker()
        assertEquals(1, listener.foregroundCount.get())

        controller.stop()
        elapseDebounce()

        assertEquals(1, listener.backgroundCount.get())
        assertEquals(ProcessState.BACKGROUND, tracker.getProcessState())
    }

    @Test
    fun `navigating away from an activity that started before registration stays in the foreground`() {
        val first = buildActivity().start()
        val tracker = createTracker(startupContext = first.get())
        val second = buildActivity()

        second.start()
        first.stop()
        elapseDebounce()

        assertEquals(1, listener.foregroundCount.get())
        assertEquals(0, listener.backgroundCount.get())

        second.stop()
        elapseDebounce()

        assertEquals(1, listener.backgroundCount.get())
        assertEquals(ProcessState.BACKGROUND, tracker.getProcessState())
    }

    @Test
    fun `unbalanced stop callbacks do not corrupt the started activity count`() {
        val tracker = createTracker()
        val controller = buildActivity().start()

        // more stops than starts should never happen, but must not drive the count negative
        controller.stop()
        tracker.onActivityStopped(controller.get())
        elapseDebounce()
        assertEquals(1, listener.backgroundCount.get())

        // the tracker still reports subsequent transitions
        val next = buildActivity().start()
        assertEquals(2, listener.foregroundCount.get())
        assertEquals(ProcessState.FOREGROUND, tracker.getProcessState())

        next.stop()
        elapseDebounce()
        assertEquals(2, listener.backgroundCount.get())
        assertEquals(ProcessState.BACKGROUND, tracker.getProcessState())
    }

    private fun createTracker(
        startupContext: Context? = application,
        registerTracker: Boolean = true,
    ): ActivityProcessLifecycleTracker = ActivityProcessLifecycleTracker(application, startupContext).apply {
        if (registerTracker) {
            register(listener)
        }
    }

    private fun buildActivity(): ActivityController<TestActivity> =
        Robolectric.buildActivity(TestActivity::class.java).create()

    private fun elapseDebounce() {
        shadowOf(application.mainLooper)
            .idleFor(Duration.ofMillis(ActivityProcessLifecycleTracker.BACKGROUND_DEBOUNCE_MS))
    }

    private fun activityManager(): ActivityManager =
        checkNotNull(application.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)

    private fun setProcessImportance(importance: Int) {
        val info = RunningAppProcessInfo(application.packageName, Process.myPid(), emptyArray())
        info.importance = importance
        shadowOf(activityManager()).setProcesses(listOf(info))
    }

    private class TestActivity : Activity()
}
