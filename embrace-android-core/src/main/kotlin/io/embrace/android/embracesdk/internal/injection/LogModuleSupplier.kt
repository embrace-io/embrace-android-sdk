package io.embrace.android.embracesdk.internal.injection

/**
 * Function that returns an instance of [LogModule]. Matches the signature of the constructor for [LogModuleImpl]
 */
typealias LogModuleSupplier = (
    initModule: InitModule,
    openTelemetryModule: OpenTelemetryModule,
    androidServicesModule: AndroidServicesModule,
    essentialServiceModule: EssentialServiceModule,
    configModule: ConfigModule,
    deliveryModule: DeliveryModule,
    workerThreadModule: WorkerThreadModule,
    payloadSourceModule: PayloadSourceModule,
    dataSourceModule: DataSourceModule,
) -> LogModule

fun createLogModule(
    initModule: InitModule,
    openTelemetryModule: OpenTelemetryModule,
    androidServicesModule: AndroidServicesModule,
    essentialServiceModule: EssentialServiceModule,
    configModule: ConfigModule,
    deliveryModule: DeliveryModule,
    workerThreadModule: WorkerThreadModule,
    payloadSourceModule: PayloadSourceModule,
    dataSourceModule: DataSourceModule,
): LogModule = LogModuleImpl(
    initModule,
    openTelemetryModule,
    androidServicesModule,
    essentialServiceModule,
    configModule,
    deliveryModule,
    workerThreadModule,
    payloadSourceModule,
    dataSourceModule,
)
