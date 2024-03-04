package io.embrace.android.embracesdk.payload

import io.embrace.android.embracesdk.assertJsonMatchesGoldenFile
import io.embrace.android.embracesdk.deserializeEmptyJsonString
import io.embrace.android.embracesdk.deserializeJsonFromResource
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class BreadcrumbsTest {

    private val info = Breadcrumbs(
        viewBreadcrumbs = listOf(ViewBreadcrumb("View", 1600000000)),
        tapBreadcrumbs =
        listOf(
            TapBreadcrumb(
                null,
                "Tap",
                1600000000,
                TapBreadcrumb.TapBreadcrumbType.TAP
            )
        ),
        webViewBreadcrumbs = listOf(WebViewBreadcrumb("WebView", 1600000000)),
        fragmentBreadcrumbs = listOf(FragmentBreadcrumb("Fragment", 1600000000, 1600005000)),
        rnActionBreadcrumbs = listOf(
            RnActionBreadcrumb(
                "RnAction",
                1600000000,
                1600005000,
                emptyMap(),
                0,
                "output"
            )
        ),
        pushNotifications = listOf(
            PushNotificationBreadcrumb(
                "PushNotification",
                "body",
                "from",
                "id",
                null,
                null,
                1600000000
            )
        )
    )

    @Test
    fun testSerialization() {
        assertJsonMatchesGoldenFile("breadcrumbs_expected.json", info)
    }

    @Test
    fun testDeserialization() {
        val obj = deserializeJsonFromResource<Breadcrumbs>("breadcrumbs_expected.json")
        assertNotNull(obj)
        assertNotNull(obj.viewBreadcrumbs?.single())
        assertNotNull(obj.fragmentBreadcrumbs?.single())
        assertNotNull(obj.tapBreadcrumbs?.single())
        assertNotNull(obj.rnActionBreadcrumbs?.single())
        assertNotNull(obj.pushNotifications?.single())
        assertNotNull(obj.webViewBreadcrumbs?.single())
    }

    @Test
    fun testEmptyObject() {
        val obj = deserializeEmptyJsonString<Breadcrumbs>()
        assertNotNull(obj)
    }
}
