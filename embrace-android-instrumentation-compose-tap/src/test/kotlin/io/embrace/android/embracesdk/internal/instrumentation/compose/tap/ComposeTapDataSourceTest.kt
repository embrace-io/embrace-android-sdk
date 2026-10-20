package io.embrace.android.embracesdk.internal.instrumentation.compose.tap

import android.app.Activity
import android.app.Application
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.ui.TapSignal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric.buildActivity

@RunWith(AndroidJUnit4::class)
internal class ComposeTapDataSourceTest {

    private lateinit var args: FakeInstrumentationArgs
    private lateinit var source: ComposeTapDataSource
    private val taps = mutableListOf<TapSignal>()

    @Before
    fun setUp() {
        val application: Application = ApplicationProvider.getApplicationContext()
        args = FakeInstrumentationArgs(application)
        args.eventBus.addHandler(TapSignal.KEY) { taps.add(it) }
        source = ComposeTapDataSource(args)
    }

    @Test
    fun `a compose tap is emitted as a tap signal and not reported here`() {
        source.logComposeTap(Pair(126f, 309f), "my-button-id")

        assertEquals(listOf(TapSignal("my-button-id", 126f, 309f)), taps)
        assertTrue(args.destination.addedEvents.isEmpty())
    }

    @Test
    fun `a tap emitted with no handler registered is dropped`() {
        val isolated = FakeInstrumentationArgs(ApplicationProvider.getApplicationContext())

        ComposeTapDataSource(isolated).logComposeTap(Pair(1f, 2f), "my-button-id")

        assertTrue(isolated.destination.addedEvents.isEmpty())
        assertTrue(isolated.errorHandler.errorMessages.isEmpty())
    }

    @Test
    fun `window callbacks are installed when data capture is enabled`() {
        source.onDataCaptureEnabled()
        val activity = buildActivity(Activity::class.java).setup().get()
        assertTrue(activity.window.callback is EmbraceWindowCallback)
    }

    @Test
    fun `activity lifecycle callbacks are unregistered when data capture is disabled`() {
        source.onDataCaptureEnabled()
        source.onDataCaptureDisabled()
        val activity = buildActivity(Activity::class.java).setup().get()
        assertFalse(activity.window.callback is EmbraceWindowCallback)
    }

    @Test
    fun `installed window callbacks stop feeding gestures when data capture is disabled`() {
        source.onDataCaptureEnabled()
        val activity = buildActivity(Activity::class.java).setup().get()
        val callback = activity.window.callback as EmbraceWindowCallback
        source.onDataCaptureDisabled()

        val down = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 1f, 1f, 0)
        val up = MotionEvent.obtain(0L, 10L, MotionEvent.ACTION_UP, 1f, 1f, 0)
        try {
            callback.dispatchTouchEvent(down)
            callback.dispatchTouchEvent(up)
        } finally {
            down.recycle()
            up.recycle()
        }

        assertTrue(taps.isEmpty())
        assertTrue(args.errorHandler.errorMessages.isEmpty())
    }

    @Test
    fun `window callbacks are installed again when data capture is re-enabled`() {
        source.onDataCaptureEnabled()
        source.onDataCaptureDisabled()
        source.onDataCaptureEnabled()
        val activity = buildActivity(Activity::class.java).setup().get()
        assertTrue(activity.window.callback is EmbraceWindowCallback)
    }
}
