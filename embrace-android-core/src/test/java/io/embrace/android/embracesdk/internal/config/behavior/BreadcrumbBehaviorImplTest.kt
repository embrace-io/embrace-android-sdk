package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.instrumented.InstrumentedConfigImpl
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.UiRemoteConfig
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
            96
        )
    )

    @Test
    fun testDefaults() {
        with(
            BreadcrumbBehaviorImpl(
                InstrumentedConfigImpl,
                null,
            )
        ) {
            assertEquals(100, getCustomBreadcrumbLimit())
            assertEquals(100, getTapBreadcrumbLimit())
            assertEquals(100, getWebViewBreadcrumbLimit())
            assertEquals(100, getFragmentBreadcrumbLimit())
            assertFalse(isViewClickCoordinateCaptureEnabled())
            assertTrue(isActivityBreadcrumbCaptureEnabled())
            assertTrue(isWebViewBreadcrumbCaptureEnabled())
            assertTrue(isWebViewBreadcrumbQueryParamCaptureEnabled())
            assertFalse(isFcmPiiDataCaptureEnabled())
        }
    }

    @Test
    fun testRemoteAndLocal() {
        with(
            BreadcrumbBehaviorImpl(
                InstrumentedConfigImpl,
                remote,
            )
        ) {
            assertEquals(99, getCustomBreadcrumbLimit())
            assertEquals(98, getTapBreadcrumbLimit())
            assertEquals(97, getWebViewBreadcrumbLimit())
            assertEquals(96, getFragmentBreadcrumbLimit())
        }
    }
}
