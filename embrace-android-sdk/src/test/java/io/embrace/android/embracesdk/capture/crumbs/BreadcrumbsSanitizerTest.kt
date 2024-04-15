package io.embrace.android.embracesdk.capture.crumbs

import io.embrace.android.embracesdk.gating.SessionGatingKeys
import io.embrace.android.embracesdk.payload.Breadcrumbs
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

internal class BreadcrumbsSanitizerTest {

    private val breadcrumbs = Breadcrumbs(
        emptyList(),
        emptyList(),
        emptyList()
    )

    @Test
    fun `test if it keeps breadcrumbs`() {
        // enabled components contains everything about breadcrumbs
        val components = setOf(
            SessionGatingKeys.BREADCRUMBS_VIEWS,
            SessionGatingKeys.BREADCRUMBS_CUSTOM_VIEWS,
            SessionGatingKeys.BREADCRUMBS_CUSTOM,
        )

        val result = BreadcrumbsSanitizer(breadcrumbs, components).sanitize()

        assertNotNull(result?.viewBreadcrumbs)
    }

    @Test
    fun `test if it sanitizes breadcrumbs`() {
        // enabled components doesn't contain any breadcrumb properties
        val components = setOf<String>()

        val result = BreadcrumbsSanitizer(breadcrumbs, components).sanitize()

        assertNull(result?.viewBreadcrumbs)
    }
}
