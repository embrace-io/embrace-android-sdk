package io.embrace.android.embracesdk.config.behavior

import io.embrace.android.embracesdk.config.local.SdkLocalConfig
import io.embrace.android.embracesdk.config.local.TapsLocalConfig
import io.embrace.android.embracesdk.config.local.ViewLocalConfig
import io.embrace.android.embracesdk.config.local.WebViewLocalConfig
import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.config.remote.UiRemoteConfig
import io.embrace.android.embracesdk.fakes.fakeBreadcrumbBehavior
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class BreadcrumbBehaviorTest {

    private val remote = RemoteConfig(
        uiConfig = UiRemoteConfig(
            99,
            98,
            97,
            96,
            95
        )
    )

    private val local = SdkLocalConfig(
        taps = TapsLocalConfig(false),
        viewConfig = ViewLocalConfig(false),
        webViewConfig = WebViewLocalConfig(captureWebViews = false, captureQueryParams = false),
        captureFcmPiiData = true
    )

    @Test
    fun testDefaults() {
        with(fakeBreadcrumbBehavior()) {
            assertEquals(100, getCustomBreadcrumbLimit())
            assertEquals(100, getTapBreadcrumbLimit())
            assertEquals(100, getViewBreadcrumbLimit())
            assertEquals(100, getWebViewBreadcrumbLimit())
            assertEquals(100, getFragmentBreadcrumbLimit())
            assertTrue(isTapCoordinateCaptureEnabled())
            assertTrue(isActivityBreadcrumbCaptureEnabled())
            assertTrue(isWebViewBreadcrumbCaptureEnabled())
            assertTrue(isQueryParamCaptureEnabled())
            assertFalse(isCaptureFcmPiiDataEnabled())
        }
    }

    @Test
    fun testLocalOnly() {
        with(fakeBreadcrumbBehavior(localCfg = { local })) {
            assertFalse(isTapCoordinateCaptureEnabled())
            assertFalse(isActivityBreadcrumbCaptureEnabled())
            assertFalse(isWebViewBreadcrumbCaptureEnabled())
            assertFalse(isQueryParamCaptureEnabled())
            assertTrue(isCaptureFcmPiiDataEnabled())
        }
    }

    @Test
    fun testRemoteAndLocal() {
        with(fakeBreadcrumbBehavior(localCfg = { local }, remoteCfg = { remote })) {
            assertEquals(99, getCustomBreadcrumbLimit())
            assertEquals(98, getTapBreadcrumbLimit())
            assertEquals(97, getViewBreadcrumbLimit())
            assertEquals(96, getWebViewBreadcrumbLimit())
            assertEquals(95, getFragmentBreadcrumbLimit())
        }
    }
}
