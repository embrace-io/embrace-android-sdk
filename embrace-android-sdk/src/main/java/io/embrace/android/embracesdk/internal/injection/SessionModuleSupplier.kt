package io.embrace.android.embracesdk.internal.injection

/**
 * Function that returns an instance of [SessionModule]. Matches the signature of the constructor for [SessionModuleImpl]
 */
internal typealias SessionModuleSupplier = (
    initModule: InitModule,
    openTelemetryModule: OpenTelemetryModule,
    androidServicesModule: AndroidServicesModule,
    essentialServiceModule: EssentialServiceModule,
    nativeModule: NativeModule,
    deliveryModule: DeliveryModule,
    workerThreadModule: WorkerThreadModule,
    dataSourceModule: DataSourceModule,
    payloadModule: PayloadModule,
    dataCaptureServiceModule: DataCaptureServiceModule,
    dataContainerModule: DataContainerModule,
    customerLogModule: CustomerLogModule
) -> SessionModule

internal fun createSessionModule(
    initModule: InitModule,
    openTelemetryModule: OpenTelemetryModule,
    androidServicesModule: AndroidServicesModule,
    essentialServiceModule: EssentialServiceModule,
    nativeModule: NativeModule,
    deliveryModule: DeliveryModule,
    workerThreadModule: WorkerThreadModule,
    dataSourceModule: DataSourceModule,
    payloadModule: PayloadModule,
    dataCaptureServiceModule: DataCaptureServiceModule,
    dataContainerModule: DataContainerModule,
    customerLogModule: CustomerLogModule
): SessionModule = SessionModuleImpl(
    initModule,
    openTelemetryModule,
    androidServicesModule,
    essentialServiceModule,
    nativeModule,
    deliveryModule,
    workerThreadModule,
    dataSourceModule,
    payloadModule,
    dataCaptureServiceModule,
    dataContainerModule,
    customerLogModule
)
