package io.embrace.android.embracesdk.payload

import io.embrace.android.embracesdk.ResourceReader
import io.embrace.android.embracesdk.internal.EmbraceSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class BreadcrumbsTest {

    private val serializer = EmbraceSerializer()

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
        customBreadcrumbs = listOf(CustomBreadcrumb("Custom", 1600000000)),
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
        val expectedInfo = ResourceReader.readResourceAsText("breadcrumbs_expected.json")
            .filter { !it.isWhitespace() }
        val observed = serializer.toJson(info)
        assertEquals(expectedInfo, observed)
    }

    @Test
    fun testDeserialization() {
        val json = ResourceReader.readResourceAsText("breadcrumbs_expected.json")
        val obj = serializer.fromJson(json, Breadcrumbs::class.java)
        assertNotNull(obj)
        assertNotNull(obj.viewBreadcrumbs?.single())
        assertNotNull(obj.customBreadcrumbs?.single())
        assertNotNull(obj.fragmentBreadcrumbs?.single())
        assertNotNull(obj.tapBreadcrumbs?.single())
        assertNotNull(obj.rnActionBreadcrumbs?.single())
        assertNotNull(obj.pushNotifications?.single())
        assertNotNull(obj.webViewBreadcrumbs?.single())
    }

    @Test
    fun testEmptyObject() {
        val info = serializer.fromJson("{}", Breadcrumbs::class.java)
        assertNotNull(info)
    }
}
