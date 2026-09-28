package io.embrace.android.embracesdk.internal.config.resolved

import io.embrace.android.embracesdk.fakes.config.FakeEnabledFeatureConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.internal.config.instrumented.InstrumentedConfigImpl
import io.embrace.android.embracesdk.internal.config.instrumented.schema.WebViewFragmentCapture
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.UiRemoteConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class BreadcrumbConfigTest {

    @Test
    fun `defaults match resolved default local config`() {
        val defaults = EmbraceConfig().breadcrumb
        val resolved = resolveConfig(InstrumentedConfigImpl, null, unreadBucket).breadcrumb
        with(defaults) {
            assertEquals(100, customLimit)
            assertEquals(100, tapLimit)
            assertEquals(100, webViewLimit)
            assertEquals(100, fragmentLimit)
            assertFalse(captureViewClickCoordinates)
            assertTrue(captureActivities)
            assertTrue(captureWebViews)
            assertTrue(captureWebViewQueryParams)
            assertEquals(WebViewFragmentCapture.KEEP, webViewFragmentCapture)
            assertFalse(captureFcmPiiData)
        }
        assertEquals(defaults.snapshot(), resolved.snapshot())
    }

    @Test
    fun `remote limits take precedence`() {
        val remote = RemoteConfig(uiConfig = UiRemoteConfig(99, 98, 97, 96))
        with(resolveBreadcrumb(InstrumentedConfigImpl, remote)) {
            assertEquals(99, customLimit)
            assertEquals(98, tapLimit)
            assertEquals(97, webViewLimit)
            assertEquals(96, fragmentLimit)
        }
    }

    @Test
    fun `local capture flags are read`() {
        val local = FakeInstrumentedConfig(
            enabledFeatures = FakeEnabledFeatureConfig(
                viewClickCoordCapture = true,
                activityBreadcrumbCapture = false,
                webviewBreadcrumbCapture = false,
                webviewQueryCapture = false,
                webviewFragmentCapture = WebViewFragmentCapture.REDACT,
                fcmPiiCapture = true,
            ),
        )
        with(resolveBreadcrumb(local, null)) {
            assertTrue(captureViewClickCoordinates)
            assertFalse(captureActivities)
            assertFalse(captureWebViews)
            assertFalse(captureWebViewQueryParams)
            assertEquals(WebViewFragmentCapture.REDACT, webViewFragmentCapture)
            assertTrue(captureFcmPiiData)
        }
    }

    private fun BreadcrumbConfig.snapshot() = listOf(
        customLimit,
        fragmentLimit,
        tapLimit,
        webViewLimit,
        captureViewClickCoordinates,
        captureActivities,
        captureWebViews,
        captureWebViewQueryParams,
        webViewFragmentCapture,
        captureFcmPiiData,
    )
}
