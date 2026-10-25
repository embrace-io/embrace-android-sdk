package io.embrace.android.embracesdk.internal.handler

import android.os.Looper
import android.os.Message
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
internal class AndroidMainThreadHandlerTest {

    private lateinit var errorHandler: FakeErrorHandler
    private lateinit var handler: AndroidMainThreadHandler

    @Before
    fun setUp() {
        errorHandler = FakeErrorHandler(throwOnSdkError = false)
        handler = AndroidMainThreadHandler(errorHandler)
    }

    @Test
    fun `post is guarded`() {
        handler.postAtFrontOfQueue { error("boom") }
        handler.postDelayed({ error("boom") }, 0)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(2, errorHandler.sdkErrorMessages.size)
    }

    @Test
    fun `message callback is guarded`() {
        handler.sendMessageAtFrontOfQueue(Message.obtain(handler.wrappedHandler) { error("boom") })
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("UiCallbackFail", errorHandler.sdkErrorMessages.single().msg)
    }
}
