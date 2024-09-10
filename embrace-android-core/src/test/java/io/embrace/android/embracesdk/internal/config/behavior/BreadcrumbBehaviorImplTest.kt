package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.local.SdkLocalConfig
import io.embrace.android.embracesdk.internal.config.local.TapsLocalConfig
import io.embrace.android.embracesdk.internal.config.local.ViewLocalConfig
import io.embrace.android.embracesdk.internal.config.local.WebViewLocalConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.UiRemoteConfig
import io.embrace.android.embracesdk.internal.utils.Uuid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class BreadcrumbBehaviorImplTest {

    private val remote = RemoteConfig(
        uiConfig = UiRemoteConfig(
            99,
            98,
            97,
            96,
            95
        )
    )

    private val behaviorThresholdCheck = BehaviorThresholdCheck { Uuid.getEmbUuid() }

    private val local = SdkLocalConfig(
        taps = TapsLocalConfig(false),
        viewConfig = ViewLocalConfig(false),
        webViewConfig = WebViewLocalConfig(captureWebViews = false, captureQueryParams = false),
        captureFcmPiiData = true
    )

    @Test
    fun testDefaults() {
        with(BreadcrumbBehaviorImpl(thresholdCheck = behaviorThresholdCheck, localSupplier = { null }) { null }) {
            assertEquals(100, getCustomBreadcrumbLimit())
            assertEquals(100, getTapBreadcrumbLimit())
            assertEquals(100, getViewBreadcrumbLimit())
            assertEquals(100, getWebViewBreadcrumbLimit())
            assertEquals(100, getFragmentBreadcrumbLimit())
            assertTrue(isViewClickCoordinateCaptureEnabled())
            assertTrue(isActivityBreadcrumbCaptureEnabled())
            assertTrue(isWebViewBreadcrumbCaptureEnabled())
            assertTrue(isWebViewBreadcrumbQueryParamCaptureEnabled())
            assertFalse(isFcmPiiDataCaptureEnabled())
        }
    }

    @Test
    fun testLocalOnly() {
        with(BreadcrumbBehaviorImpl(thresholdCheck = behaviorThresholdCheck, localSupplier = { local }) { null }) {
            assertFalse(isViewClickCoordinateCaptureEnabled())
            assertFalse(isActivityBreadcrumbCaptureEnabled())
            assertFalse(isWebViewBreadcrumbCaptureEnabled())
            assertFalse(isWebViewBreadcrumbQueryParamCaptureEnabled())
            assertTrue(isFcmPiiDataCaptureEnabled())
        }
    }

    @Test
    fun testRemoteAndLocal() {
        with(BreadcrumbBehaviorImpl(thresholdCheck = behaviorThresholdCheck, localSupplier = { local }) { remote }) {
            assertEquals(99, getCustomBreadcrumbLimit())
            assertEquals(98, getTapBreadcrumbLimit())
            assertEquals(97, getViewBreadcrumbLimit())
            assertEquals(96, getWebViewBreadcrumbLimit())
            assertEquals(95, getFragmentBreadcrumbLimit())
        }
    }
}
