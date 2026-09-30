package io.embrace.android.embracesdk.internal.config.resolved

/**
 * Config resolved for the life of the process. Each slice is built on first read.
 */
class EmbraceConfig(
    breadcrumb: () -> BreadcrumbConfig = { BreadcrumbConfig() },
    persistence: () -> PersistenceConfig = { PersistenceConfig() },
    threadBlockage: () -> ThreadBlockageConfig = { ThreadBlockageConfig() },
    aei: () -> AeiConfig = { AeiConfig() },
    log: () -> LogConfig = { LogConfig() },
    experiment: () -> ExperimentConfig = { ExperimentConfig() },
    vitals: () -> VitalsConfig = { VitalsConfig() },
    sdkMode: () -> SdkModeConfig = { SdkModeConfig() },
) {
    val breadcrumb: BreadcrumbConfig = breadcrumb()
    val persistence: PersistenceConfig = persistence()
    val threadBlockage: ThreadBlockageConfig = threadBlockage()
    val aei: AeiConfig = aei()
    val log: LogConfig = log()
    val experiment: ExperimentConfig = experiment()
    val vitals: VitalsConfig = vitals()
    val sdkMode: SdkModeConfig = sdkMode()
}
