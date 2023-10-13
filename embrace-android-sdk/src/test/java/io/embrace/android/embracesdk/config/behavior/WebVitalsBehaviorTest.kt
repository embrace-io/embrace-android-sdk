package io.embrace.android.embracesdk.config.behavior

import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.config.remote.WebViewVitals
import io.embrace.android.embracesdk.fakes.fakeWebViewVitalsBehavior
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class WebVitalsBehaviorTest {

    private val remote = RemoteConfig(webViewVitals = WebViewVitals(0f, 100))

    @Test
    fun testDefaults() {
        with(fakeWebViewVitalsBehavior()) {
            assertTrue(isWebViewVitalsEnabled())
            assertEquals(300, getMaxWebViewVitals())
        }
    }

    @Test
    fun testRemote() {
        with(fakeWebViewVitalsBehavior(remoteCfg = { remote })) {
            assertEquals(100, getMaxWebViewVitals())
            assertFalse(isWebViewVitalsEnabled())
        }
    }
}
