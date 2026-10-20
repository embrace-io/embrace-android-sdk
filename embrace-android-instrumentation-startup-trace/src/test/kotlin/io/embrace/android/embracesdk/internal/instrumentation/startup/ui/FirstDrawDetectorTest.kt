package io.embrace.android.embracesdk.internal.instrumentation.startup.ui

import android.app.Activity
import android.os.Looper
import android.view.View
import android.view.ViewTreeObserver
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.util.ReflectionHelpers

@RunWith(AndroidJUnit4::class)
internal class FirstDrawDetectorTest {

    private lateinit var errorHandler: FakeErrorHandler
    private lateinit var detector: FirstDrawDetector
    private lateinit var activity: Activity
    private var drawBeginCount = 0
    private var drawCompleteCount = 0

    @Before
    fun setUp() {
        errorHandler = FakeErrorHandler()
        detector = FirstDrawDetector(errorHandler)
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        drawBeginCount = 0
        drawCompleteCount = 0
    }

    @Test
    fun `first draw of registered activity invokes callbacks exactly once`() {
        register()
        assertEquals(0, drawBeginCount)
        assertEquals(0, drawCompleteCount)

        drawFrame()
        assertEquals(1, drawBeginCount)
        assertEquals(1, drawCompleteCount)

        // subsequent frames do not invoke the callbacks again
        drawFrame()
        assertEquals(1, drawBeginCount)
        assertEquals(1, drawCompleteCount)
        assertEquals(0, errorHandler.sdkErrorMessages.size)
    }

    @Test
    fun `unregistering before first draw means callbacks are never invoked`() {
        register()
        detector.unregisterFirstDrawCallback(activity)

        drawFrame()
        assertEquals(0, drawBeginCount)
        assertEquals(0, drawCompleteCount)
    }

    @Test
    fun `unregistering after draw begins but before frame commit means complete callback is never invoked`() {
        register()
        dispatchOnDraw()
        assertEquals(1, drawBeginCount)

        detector.unregisterFirstDrawCallback(activity)
        commitFrame()
        assertEquals(1, drawBeginCount)
        assertEquals(0, drawCompleteCount)
    }

    @Test
    fun `registering again after unregistering before first draw invokes only the latest callbacks`() {
        var staleCallbackCount = 0
        detector.registerFirstDrawCallback(
            activity = activity,
            drawBeginCallback = { staleCallbackCount++ },
            drawCompleteCallback = { staleCallbackCount++ },
        )
        detector.unregisterFirstDrawCallback(activity)
        register()

        drawFrame()
        assertEquals(0, staleCallbackCount)
        assertEquals(1, drawBeginCount)
        assertEquals(1, drawCompleteCount)
    }

    @Test
    fun `registering twice before first draw invokes callbacks exactly once`() {
        register()
        register()

        drawFrame()
        drawFrame()
        assertEquals(1, drawBeginCount)
        assertEquals(1, drawCompleteCount)
    }

    @Test
    fun `registering again while a draw is being tracked is ignored`() {
        register()
        dispatchOnDraw()
        register()

        commitFrame()
        drawFrame()
        assertEquals(1, drawBeginCount)
        assertEquals(1, drawCompleteCount)
    }

    @Test
    fun `registering before content is set invokes callbacks on the first draw after content is set`() {
        // registering from onActivityCreated usually happens before setContentView, so the decor view doesn't exist yet
        val controller = Robolectric.buildActivity(Activity::class.java)
        val newActivity = controller.get()
        assertNull(newActivity.window.peekDecorView())
        register(newActivity)

        controller.create()
        newActivity.setContentView(View(newActivity))
        controller.start().resume().visible()
        assertEquals(0, drawBeginCount)
        assertEquals(0, drawCompleteCount)

        drawFrame(newActivity)
        drawFrame(newActivity)
        assertEquals(1, drawBeginCount)
        assertEquals(1, drawCompleteCount)
    }

    @Test
    fun `null window callback is reported once and callbacks are never invoked`() {
        errorHandler.throwOnSdkError = false
        activity.window.callback = null
        register()
        register()

        drawFrame()
        assertEquals(1, errorHandler.sdkErrorMessages.size)
        assertEquals(0, drawBeginCount)
        assertEquals(0, drawCompleteCount)
    }

    @Test
    fun `registering same activity again after completion invokes callbacks for new registration`() {
        register()
        drawFrame()
        detector.unregisterFirstDrawCallback(activity)
        assertEquals(1, drawBeginCount)
        assertEquals(1, drawCompleteCount)

        register()
        drawFrame()
        assertEquals(2, drawBeginCount)
        assertEquals(2, drawCompleteCount)
    }

    @Test
    fun `closing before first draw means callbacks are never invoked`() {
        register()
        detector.close()

        drawFrame()
        assertEquals(0, drawBeginCount)
        assertEquals(0, drawCompleteCount)
    }

    @Test
    fun `closing after draw begins but before frame commit means complete callback is never invoked`() {
        register()
        dispatchOnDraw()
        assertEquals(1, drawBeginCount)

        detector.close()
        commitFrame()
        assertEquals(1, drawBeginCount)
        assertEquals(0, drawCompleteCount)
    }

    @Test
    fun `registering after close is ignored`() {
        detector.close()
        register()

        drawFrame()
        assertEquals(0, drawBeginCount)
        assertEquals(0, drawCompleteCount)
    }

    @Test
    fun `closing before content is set leaves a window callback that only delegates`() {
        val controller = Robolectric.buildActivity(Activity::class.java)
        val newActivity = controller.get()
        register(newActivity)
        detector.close()

        controller.create()
        newActivity.setContentView(View(newActivity))
        controller.start().resume().visible()
        drawFrame(newActivity)
        assertEquals(0, drawBeginCount)
        assertEquals(0, drawCompleteCount)
    }

    @Test
    fun `a new detector does not reuse the window callback of a closed detector`() {
        val controller = Robolectric.buildActivity(Activity::class.java)
        val newActivity = controller.get()
        register(newActivity)
        detector.close()

        detector = FirstDrawDetector(errorHandler)
        register(newActivity)
        controller.create()
        newActivity.setContentView(View(newActivity))
        controller.start().resume().visible()
        drawFrame(newActivity)
        assertEquals(1, drawBeginCount)
        assertEquals(1, drawCompleteCount)
    }

    private fun register(target: Activity = activity) {
        detector.registerFirstDrawCallback(
            activity = target,
            drawBeginCallback = { drawBeginCount++ },
            drawCompleteCallback = { drawCompleteCount++ },
        )
    }

    /**
     * Simulates the platform rendering a frame: draw listeners are dispatched, then the frame commit callbacks
     * are run as the renderer would once the frame has been committed.
     */
    private fun drawFrame(target: Activity = activity) {
        dispatchOnDraw(target)
        commitFrame(target)
    }

    private fun dispatchOnDraw(target: Activity = activity) {
        ReflectionHelpers.callInstanceMethod<Unit>(observer(target), "dispatchOnDraw")
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun commitFrame(target: Activity = activity) {
        val commitCallbacks: List<Runnable>? = ReflectionHelpers.callInstanceMethod(
            observer(target),
            "captureFrameCommitCallbacks",
        )
        commitCallbacks?.forEach(Runnable::run)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun observer(target: Activity): ViewTreeObserver = target.window.decorView.viewTreeObserver
}
