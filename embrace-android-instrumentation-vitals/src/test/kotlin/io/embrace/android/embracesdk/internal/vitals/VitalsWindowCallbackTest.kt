package io.embrace.android.embracesdk.internal.vitals

import android.app.Activity
import android.app.Application
import android.view.MotionEvent
import android.view.Window
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric.buildActivity

@RunWith(AndroidJUnit4::class)
internal class VitalsWindowCallbackTest {

    private lateinit var args: FakeInstrumentationArgs
    private val focalCallbacks = FakeFocalInteractionCallbacks()
    private lateinit var callback: VitalsWindowCallback
    private var delegateCalls = 0
    private var delegateFocusChanges = 0

    @Before
    fun setUp() {
        val application: Application = ApplicationProvider.getApplicationContext()
        args = FakeInstrumentationArgs(application)
        val activity = buildActivity(Activity::class.java).setup().get()
        val delegate = object : Window.Callback by activity.window.callback {
            override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
                delegateCalls++
                return true
            }

            override fun onWindowFocusChanged(hasFocus: Boolean) {
                delegateFocusChanges++
            }
        }
        callback = VitalsWindowCallback(delegate, focalCallbacks)
    }

    @Test
    fun `ACTION_DOWN starts an interaction and forwards to the delegate`() {
        val result = dispatch(MotionEvent.ACTION_DOWN)

        assertTrue(result)
        assertEquals(1, delegateCalls)
        assertEquals(1, focalCallbacks.interactionStartCount)
        assertEquals(0, focalCallbacks.interactionEndCount)
        assertTrue(args.logger.errorMessages.isEmpty())
    }

    @Test
    fun `ACTION_UP ends an interaction and is a committed tap`() {
        dispatch(MotionEvent.ACTION_UP)

        assertEquals(1, focalCallbacks.interactionEndCount)
        assertEquals(1, focalCallbacks.tapCount)
        assertEquals(0, focalCallbacks.interactionStartCount)
    }

    @Test
    fun `ACTION_CANCEL ends an interaction but is not a tap`() {
        dispatch(MotionEvent.ACTION_CANCEL)

        assertEquals(1, focalCallbacks.interactionEndCount)
        assertEquals(0, focalCallbacks.tapCount)
    }

    @Test
    fun `gaining window focus is reported and forwards to the delegate`() {
        callback.onWindowFocusChanged(true)

        assertEquals(1, focalCallbacks.windowFocusedCount)
        assertEquals(1, delegateFocusChanges)
    }

    @Test
    fun `losing window focus is not reported but still forwards to the delegate`() {
        callback.onWindowFocusChanged(false)

        assertEquals(0, focalCallbacks.windowFocusedCount)
        assertEquals(1, delegateFocusChanges)
    }

    @Test
    fun `ACTION_MOVE is a liveness signal, not a start or end, and still forwards to the delegate`() {
        dispatch(MotionEvent.ACTION_MOVE)

        assertEquals(1, focalCallbacks.interactionMoveCount)
        assertEquals(0, focalCallbacks.interactionStartCount)
        assertEquals(0, focalCallbacks.interactionEndCount)
        assertEquals(1, delegateCalls)
    }

    private fun dispatch(action: Int): Boolean {
        val event = MotionEvent.obtain(0L, 0L, action, 1f, 1f, 0)
        try {
            return callback.dispatchTouchEvent(event)
        } finally {
            event.recycle()
        }
    }
}
