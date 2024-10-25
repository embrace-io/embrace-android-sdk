package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.capture.startup.StartupService

/**
 * Function that returns an instance of [SessionOrchestrationModule]. Matches the signature of the constructor for [SessionOrchestrationModuleImpl]
 */
typealias SessionOrchestrationModuleSupplier = (
    initModule: InitModule,
    openTelemetryModule: OpenTelemetryModule,
    androidServicesModule: AndroidServicesModule,
    essentialServiceModule: EssentialServiceModule,
    configModule: ConfigModule,
    deliveryModule: DeliveryModule,
    dataSourceModule: DataSourceModule,
    payloadSourceModule: PayloadSourceModule,
    startupService: StartupService,
    logModule: LogModule
) -> SessionOrchestrationModule

fun createSessionOrchestrationModule(
    initModule: InitModule,
    openTelemetryModule: OpenTelemetryModule,
    androidServicesModule: AndroidServicesModule,
    essentialServiceModule: EssentialServiceModule,
    configModule: ConfigModule,
    deliveryModule: DeliveryModule,
    dataSourceModule: DataSourceModule,
    payloadSourceModule: PayloadSourceModule,
    startupService: StartupService,
    logModule: LogModule
): SessionOrchestrationModule = SessionOrchestrationModuleImpl(
    initModule,
    openTelemetryModule,
    androidServicesModule,
    essentialServiceModule,
    configModule,
    deliveryModule,
    dataSourceModule,
    payloadSourceModule,
    startupService,
    logModule
)
