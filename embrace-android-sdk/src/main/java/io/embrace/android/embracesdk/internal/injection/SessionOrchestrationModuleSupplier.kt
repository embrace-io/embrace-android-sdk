package io.embrace.android.embracesdk.internal.injection

/**
 * Function that returns an instance of [SessionOrchestrationModule]. Matches the signature of the constructor for [SessionOrchestrationModuleImpl]
 */
internal typealias SessionOrchestrationModuleSupplier = (
    initModule: InitModule,
    openTelemetryModule: OpenTelemetryModule,
    androidServicesModule: AndroidServicesModule,
    essentialServiceModule: EssentialServiceModule,
    deliveryModule: DeliveryModule,
    workerThreadModule: WorkerThreadModule,
    dataSourceModule: DataSourceModule,
    payloadSourceModule: PayloadSourceModule,
    dataCaptureServiceModule: DataCaptureServiceModule,
    momentsModule: MomentsModule,
    logModule: LogModule
) -> SessionOrchestrationModule

internal fun createSessionOrchestrationModule(
    initModule: InitModule,
    openTelemetryModule: OpenTelemetryModule,
    androidServicesModule: AndroidServicesModule,
    essentialServiceModule: EssentialServiceModule,
    deliveryModule: DeliveryModule,
    workerThreadModule: WorkerThreadModule,
    dataSourceModule: DataSourceModule,
    payloadSourceModule: PayloadSourceModule,
    dataCaptureServiceModule: DataCaptureServiceModule,
    momentsModule: MomentsModule,
    logModule: LogModule
): SessionOrchestrationModule = SessionOrchestrationModuleImpl(
    initModule,
    openTelemetryModule,
    androidServicesModule,
    essentialServiceModule,
    deliveryModule,
    workerThreadModule,
    dataSourceModule,
    payloadSourceModule,
    dataCaptureServiceModule,
    momentsModule,
    logModule
)
