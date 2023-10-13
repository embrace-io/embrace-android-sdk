package io.embrace.android.embracesdk.capture.crumbs

import io.embrace.android.embracesdk.gating.Sanitizable
import io.embrace.android.embracesdk.gating.SessionGatingKeys.BREADCRUMBS_CUSTOM
import io.embrace.android.embracesdk.gating.SessionGatingKeys.BREADCRUMBS_CUSTOM_VIEWS
import io.embrace.android.embracesdk.gating.SessionGatingKeys.BREADCRUMBS_TAPS
import io.embrace.android.embracesdk.gating.SessionGatingKeys.BREADCRUMBS_VIEWS
import io.embrace.android.embracesdk.gating.SessionGatingKeys.BREADCRUMBS_WEB_VIEWS
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger
import io.embrace.android.embracesdk.payload.Breadcrumbs

internal class BreadcrumbsSanitizer(
    private val breadcrumbs: Breadcrumbs?,
    private val enabledComponents: Set<String>
) :
    Sanitizable<Breadcrumbs> {

    override fun sanitize(): Breadcrumbs? {
        InternalStaticEmbraceLogger.logger.logDeveloper(
            "BreadcrumbsSanitizer",
            "sanitize: " + (breadcrumbs != null).toString()
        )
        return breadcrumbs?.let {

            val customBreadcrumbs = if (shouldAddCustomBreadcrumbs()) {
                InternalStaticEmbraceLogger.logger.logDeveloper(
                    "BreadcrumbsSanitizer",
                    "shouldAddCustomBreadcrumbs"
                )
                breadcrumbs.customBreadcrumbs
            } else {
                null
            }

            val viewBreadcrumbs = if (shouldAddViewBreadcrumbs()) {
                InternalStaticEmbraceLogger.logger.logDeveloper(
                    "BreadcrumbsSanitizer",
                    "shouldAddViewBreadcrumbs"
                )
                breadcrumbs.viewBreadcrumbs
            } else {
                null
            }

            val fragmentBreadcrumbs = if (shouldAddCustomViewBreadcrumbs()) {
                InternalStaticEmbraceLogger.logger.logDeveloper(
                    "BreadcrumbsSanitizer",
                    "shouldAddCustomViewBreadcrumbs"
                )
                breadcrumbs.fragmentBreadcrumbs
            } else {
                null
            }

            val tapBreadcrumbs = if (shouldAddTapBreadcrumbs()) {
                InternalStaticEmbraceLogger.logger.logDeveloper(
                    "BreadcrumbsSanitizer",
                    "shouldAddTapBreadcrumbs"
                )
                breadcrumbs.tapBreadcrumbs
            } else {
                null
            }

            val webViewBreadcrumbs = if (shouldAddWebViewBreadcrumbs()) {
                InternalStaticEmbraceLogger.logger.logDeveloper(
                    "BreadcrumbsSanitizer",
                    "shouldAddWebViewBreadcrumbs"
                )
                breadcrumbs.webViewBreadcrumbs
            } else {
                null
            }
            return Breadcrumbs(
                customBreadcrumbs = customBreadcrumbs,
                viewBreadcrumbs = viewBreadcrumbs,
                fragmentBreadcrumbs = fragmentBreadcrumbs,
                tapBreadcrumbs = tapBreadcrumbs,
                webViewBreadcrumbs = webViewBreadcrumbs
            )
        }
    }

    private fun shouldAddTapBreadcrumbs() =
        enabledComponents.contains(BREADCRUMBS_TAPS)

    private fun shouldAddViewBreadcrumbs() =
        enabledComponents.contains(BREADCRUMBS_VIEWS)

    private fun shouldAddCustomViewBreadcrumbs() =
        enabledComponents.contains(BREADCRUMBS_CUSTOM_VIEWS)

    private fun shouldAddWebViewBreadcrumbs() =
        enabledComponents.contains(BREADCRUMBS_WEB_VIEWS)

    private fun shouldAddCustomBreadcrumbs() =
        enabledComponents.contains(BREADCRUMBS_CUSTOM)
}
