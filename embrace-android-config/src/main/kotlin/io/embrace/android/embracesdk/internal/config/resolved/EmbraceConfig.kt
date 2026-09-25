package io.embrace.android.embracesdk.internal.config.resolved

/**
 * Config resolved for the life of the process. Each slice is built on first read.
 */
class EmbraceConfig(
    breadcrumb: () -> BreadcrumbConfig = ::BreadcrumbConfig,
) {
    val breadcrumb: BreadcrumbConfig by lazy(breadcrumb)
}
