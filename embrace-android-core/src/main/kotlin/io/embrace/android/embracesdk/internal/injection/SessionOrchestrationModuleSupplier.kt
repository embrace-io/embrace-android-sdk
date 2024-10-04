package io.embrace.android.embracesdk.internal.injection

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
    startupDurationProvider: (coldStart: Boolean) -> Long?,
    momentsModule: MomentsModule,
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
    startupDurationProvider: (coldStart: Boolean) -> Long?,
    momentsModule: MomentsModule,
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
    startupDurationProvider,
    momentsModule,
    logModule
)
