package io.embrace.android.embracesdk.internal.config.resolved

/**
 * Config resolved for the life of the process. Each slice is built on first read.
 */
class EmbraceConfig(
    breadcrumb: () -> BreadcrumbConfig = { BreadcrumbConfig() },
    persistence: () -> PersistenceConfig = { PersistenceConfig() },
) {
    val breadcrumb: BreadcrumbConfig = breadcrumb()
    val persistence: PersistenceConfig = persistence()
}
