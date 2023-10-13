package io.embrace.android.embracesdk.capture.crumbs

import io.embrace.android.embracesdk.gating.SessionGatingKeys
import io.embrace.android.embracesdk.payload.Breadcrumbs
import org.junit.Assert
import org.junit.Test

internal class BreadcrumbsSanitizerTest {

    private val breadcrumbs = Breadcrumbs(
        emptyList(),
        emptyList(),
        emptyList(),
        emptyList(),
        emptyList(),
        emptyList(),
        emptyList(),
    )

    @Test
    fun `test if it keeps breadcrumbs`() {
        // enabled components contains everything about breadcrumbs
        val components = setOf(
            SessionGatingKeys.BREADCRUMBS_TAPS,
            SessionGatingKeys.BREADCRUMBS_VIEWS,
            SessionGatingKeys.BREADCRUMBS_CUSTOM_VIEWS,
            SessionGatingKeys.BREADCRUMBS_WEB_VIEWS,
            SessionGatingKeys.BREADCRUMBS_CUSTOM,
        )

        val result = BreadcrumbsSanitizer(breadcrumbs, components).sanitize()

        Assert.assertNotNull(result?.tapBreadcrumbs)
        Assert.assertNotNull(result?.viewBreadcrumbs)
        Assert.assertNotNull(result?.customBreadcrumbs)
        Assert.assertNotNull(result?.webViewBreadcrumbs)
        Assert.assertNotNull(result?.customBreadcrumbs)
    }

    @Test
    fun `test if it sanitizes breadcrumbs`() {
        // enabled components doesn't contain any breadcrumb properties
        val components = setOf<String>()

        val result = BreadcrumbsSanitizer(breadcrumbs, components).sanitize()

        Assert.assertNull(result?.tapBreadcrumbs)
        Assert.assertNull(result?.viewBreadcrumbs)
        Assert.assertNull(result?.customBreadcrumbs)
        Assert.assertNull(result?.webViewBreadcrumbs)
        Assert.assertNull(result?.customBreadcrumbs)
    }
}
