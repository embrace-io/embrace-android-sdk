package io.embrace.android.embracesdk.capture.crumbs

import io.embrace.android.embracesdk.gating.Sanitizable
import io.embrace.android.embracesdk.gating.SessionGatingKeys.BREADCRUMBS_VIEWS
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
            return Breadcrumbs(
                viewBreadcrumbs = viewBreadcrumbs
            )
        }
    }

    private fun shouldAddViewBreadcrumbs() =
        enabledComponents.contains(BREADCRUMBS_VIEWS)
}
