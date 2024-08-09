package io.embrace.android.embracesdk.internal.injection

/**
 * Function that returns an instance of [SessionModule]. Matches the signature of the constructor for [SessionModuleImpl]
 */
internal typealias SessionModuleSupplier = (
    initModule: InitModule,
    openTelemetryModule: OpenTelemetryModule,
    androidServicesModule: AndroidServicesModule,
    essentialServiceModule: EssentialServiceModule,
    deliveryModule: DeliveryModule,
    workerThreadModule: WorkerThreadModule,
    dataSourceModule: DataSourceModule,
    payloadModule: PayloadModule,
    dataCaptureServiceModule: DataCaptureServiceModule,
    dataContainerModule: DataContainerModule,
    logModule: LogModule
) -> SessionModule

internal fun createSessionModule(
    initModule: InitModule,
    openTelemetryModule: OpenTelemetryModule,
    androidServicesModule: AndroidServicesModule,
    essentialServiceModule: EssentialServiceModule,
    deliveryModule: DeliveryModule,
    workerThreadModule: WorkerThreadModule,
    dataSourceModule: DataSourceModule,
    payloadModule: PayloadModule,
    dataCaptureServiceModule: DataCaptureServiceModule,
    dataContainerModule: DataContainerModule,
    logModule: LogModule
): SessionModule = SessionModuleImpl(
    initModule,
    openTelemetryModule,
    androidServicesModule,
    essentialServiceModule,
    deliveryModule,
    workerThreadModule,
    dataSourceModule,
    payloadModule,
    dataCaptureServiceModule,
    dataContainerModule,
    logModule
)
