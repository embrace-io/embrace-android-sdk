package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.fakes.createWebViewVitalsBehavior
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.WebViewVitals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class WebVitalsBehaviorTest {

    private val remote = RemoteConfig(webViewVitals = WebViewVitals(0f, 100))

    @Test
    fun testDefaults() {
        with(createWebViewVitalsBehavior()) {
            assertTrue(isWebViewVitalsEnabled())
            assertEquals(300, getMaxWebViewVitals())
        }
    }

    @Test
    fun testRemote() {
        with(createWebViewVitalsBehavior(remoteCfg = { remote })) {
            assertEquals(100, getMaxWebViewVitals())
            assertFalse(isWebViewVitalsEnabled())
        }
    }
}
