package io.embrace.android.embracesdk.internal.config.resolved

/**
 * Config resolved for the life of the process. Each slice is built on first read.
 */
class EmbraceConfig(
    breadcrumb: () -> BreadcrumbConfig = ::BreadcrumbConfig,
    persistence: () -> PersistenceConfig = ::PersistenceConfig,
    threadBlockage: () -> ThreadBlockageConfig = ::ThreadBlockageConfig,
    aei: () -> AeiConfig = ::AeiConfig,
) {
    val breadcrumb: BreadcrumbConfig by lazy(breadcrumb)
    val persistence: PersistenceConfig by lazy(persistence)
    val threadBlockage: ThreadBlockageConfig by lazy(threadBlockage)
    val aei: AeiConfig by lazy(aei)
}
