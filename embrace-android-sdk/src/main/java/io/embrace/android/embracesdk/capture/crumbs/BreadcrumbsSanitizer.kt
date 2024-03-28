package io.embrace.android.embracesdk.capture.crumbs

import io.embrace.android.embracesdk.gating.Sanitizable
import io.embrace.android.embracesdk.gating.SessionGatingKeys.BREADCRUMBS_TAPS
import io.embrace.android.embracesdk.gating.SessionGatingKeys.BREADCRUMBS_VIEWS
import io.embrace.android.embracesdk.gating.SessionGatingKeys.BREADCRUMBS_WEB_VIEWS
import io.embrace.android.embracesdk.payload.Breadcrumbs

internal class BreadcrumbsSanitizer(
    private val breadcrumbs: Breadcrumbs?,
    private val enabledComponents: Set<String>
) : Sanitizable<Breadcrumbs> {

    override fun sanitize(): Breadcrumbs? {
        return breadcrumbs?.let {
            val viewBreadcrumbs = if (shouldAddViewBreadcrumbs()) {
                breadcrumbs.viewBreadcrumbs
            } else {
                null
            }

            val tapBreadcrumbs = if (shouldAddTapBreadcrumbs()) {
                breadcrumbs.tapBreadcrumbs
            } else {
                null
            }

            val webViewBreadcrumbs = if (shouldAddWebViewBreadcrumbs()) {
                breadcrumbs.webViewBreadcrumbs
            } else {
                null
            }
            return Breadcrumbs(
                viewBreadcrumbs = viewBreadcrumbs,
                tapBreadcrumbs = tapBreadcrumbs,
                webViewBreadcrumbs = webViewBreadcrumbs
            )
        }
    }

    private fun shouldAddTapBreadcrumbs() =
        enabledComponents.contains(BREADCRUMBS_TAPS)

    private fun shouldAddViewBreadcrumbs() =
        enabledComponents.contains(BREADCRUMBS_VIEWS)

    private fun shouldAddWebViewBreadcrumbs() =
        enabledComponents.contains(BREADCRUMBS_WEB_VIEWS)
}
