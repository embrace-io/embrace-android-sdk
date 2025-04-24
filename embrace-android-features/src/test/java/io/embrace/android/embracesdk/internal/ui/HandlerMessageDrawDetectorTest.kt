package io.embrace.android.embracesdk.internal.ui

import android.app.Activity
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeMainThreadHandler
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import java.util.concurrent.CountDownLatch

@RunWith(AndroidJUnit4::class)
internal class HandlerMessageDrawDetectorTest {
    private lateinit var handler: FakeMainThreadHandler
    private lateinit var detector: HandlerMessageDrawDetector
    private lateinit var messageHandlerLatch: CountDownLatch

    @Before
    fun setUp() {
        messageHandlerLatch = CountDownLatch(1)
        handler = FakeMainThreadHandler()
        detector = HandlerMessageDrawDetector(handler)
    }

    @Test
    fun `check invocation`() {
        var beginCallbackInvoked = false
        var endCallbackInvoked = false
        detector.registerFirstDrawCallback(
            activity = Robolectric.buildActivity(Activity::class.java).get(),
            drawBeginCallback = { beginCallbackInvoked = true },
            drawCompleteCallback = { endCallbackInvoked = true }
        )
        assertTrue(beginCallbackInvoked)
        with(handler.messageQueue.single()) {
            assertTrue(isAsynchronous)
            callback.run()
        }
        assertTrue(endCallbackInvoked)
    }
}
