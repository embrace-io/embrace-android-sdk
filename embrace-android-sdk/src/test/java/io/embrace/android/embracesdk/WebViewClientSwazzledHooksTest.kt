package io.embrace.android.embracesdk

import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

internal class WebViewClientSwazzledHooksTest {

    @Test
    fun `verify logWebView is called`() {
        val impl = mockk<EmbraceImpl>(relaxed = true)
        Embrace.setImpl(impl)
        val url = "url"
        WebViewClientSwazzledHooks._preOnPageStarted(mockk(), url, mockk())
        verify { impl.logWebView(url) }
    }

    @Test
    fun `verify logWebView is called with null values`() {
        val impl = mockk<EmbraceImpl>(relaxed = true)
        Embrace.setImpl(impl)
        WebViewClientSwazzledHooks._preOnPageStarted(null, null, null)
        verify { impl.logWebView(null) }
    }
}
