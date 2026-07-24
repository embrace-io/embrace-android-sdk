package io.embrace.android.embracesdk.internal.instrumentation.compose.tap

import android.app.Activity
import android.app.Application
import android.view.Window
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.internal.instrumentation.view.taps.TapDataSource
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric.buildActivity

@RunWith(AndroidJUnit4::class)
internal class ComposeActivityListenerTest {

    private lateinit var args: FakeInstrumentationArgs
    private lateinit var listener: ComposeActivityListener

    @Before
    fun setUp() {
        val application: Application = ApplicationProvider.getApplicationContext()
        args = FakeInstrumentationArgs(application)
        val tapDataSource = TapDataSource(args)
        val composeTapDataSource = ComposeTapDataSource(args) { tapDataSource }
        listener = ComposeActivityListener(args.logger, composeTapDataSource)
    }

    @Test
    fun `onActivityResumed wraps the window callback in EmbraceWindowCallback`() {
        val activity = buildActivity(Activity::class.java).setup().get()

        listener.onActivityResumed(activity)

        assertTrue(activity.window.callback is EmbraceWindowCallback)
        assertTrue(args.logger.errorMessages.isEmpty())
    }

    @Test
    fun `onActivityResumed does not re-wrap an existing EmbraceWindowCallback`() {
        val activity = buildActivity(Activity::class.java).setup().get()

        listener.onActivityResumed(activity)
        val firstCallback = activity.window.callback
        listener.onActivityResumed(activity)
        val secondCallback = activity.window.callback

        assertSame(firstCallback, secondCallback)
        assertTrue(args.logger.errorMessages.isEmpty())
    }

    @Test
    fun `onActivityResumed does not re-wrap when a foreign callback sits on top`() {
        val activity = buildActivity(Activity::class.java).setup().get()

        // First resume installs our wrapper.
        listener.onActivityResumed(activity)
        // Another SDK (or vitals) then wraps our callback, so ours is no longer outermost.
        val foreign: Window.Callback = object : Window.Callback by activity.window.callback {}
        activity.window.callback = foreign

        // A later resume must not add a second EmbraceWindowCallback layer.
        listener.onActivityResumed(activity)

        assertSame(foreign, activity.window.callback)
        assertTrue(args.logger.errorMessages.isEmpty())
    }
}
