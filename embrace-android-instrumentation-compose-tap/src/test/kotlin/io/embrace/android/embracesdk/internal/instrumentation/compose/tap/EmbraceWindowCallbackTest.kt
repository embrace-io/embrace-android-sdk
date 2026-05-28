package io.embrace.android.embracesdk.internal.instrumentation.compose.tap

import android.app.Activity
import android.app.Application
import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.Window
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric.buildActivity

@RunWith(AndroidJUnit4::class)
internal class EmbraceWindowCallbackTest {

    private lateinit var context: Context
    private lateinit var args: FakeInstrumentationArgs

    @Before
    fun setUp() {
        val application: Application = ApplicationProvider.getApplicationContext()
        context = application
        args = FakeInstrumentationArgs(application)
    }

    @Test
    fun `dispatchTouchEvent forwards original event to delegate, a copy to the gesture detector, and returns delegate's value`() {
        val activity = buildActivity(Activity::class.java).setup().get()
        val recordingDelegate = object : Window.Callback by activity.window.callback {
            var lastTouchEvent: MotionEvent? = null
            var touchEventCallCount: Int = 0

            override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
                lastTouchEvent = event
                touchEventCallCount++
                return true
            }
        }
        val recordingDetector = object : GestureDetector(context, SimpleOnGestureListener()) {
            var lastEvent: MotionEvent? = null
            var callCount: Int = 0

            override fun onTouchEvent(ev: MotionEvent): Boolean {
                lastEvent = ev
                callCount++
                return super.onTouchEvent(ev)
            }
        }
        val callback = EmbraceWindowCallback(recordingDelegate, recordingDetector, args.logger)
        val event = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 10f, 20f, 0)

        try {
            val result = callback.dispatchTouchEvent(event)

            assertTrue(result)
            assertEquals(1, recordingDelegate.touchEventCallCount)
            assertSame(event, recordingDelegate.lastTouchEvent)
            assertEquals(1, recordingDetector.callCount)
            assertNotSame(event, recordingDetector.lastEvent)
            assertTrue(args.logger.errorMessages.isEmpty())
        } finally {
            event.recycle()
        }
    }
}
