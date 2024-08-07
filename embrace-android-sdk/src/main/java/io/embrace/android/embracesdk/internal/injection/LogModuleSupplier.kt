package io.embrace.android.embracesdk.internal.injection

/**
 * Function that returns an instance of [LogModule]. Matches the signature of the constructor for [LogModuleImpl]
 */
internal typealias LogModuleSupplier = (
    initModule: InitModule,
    openTelemetryModule: OpenTelemetryModule,
    androidServicesModule: AndroidServicesModule,
    essentialServiceModule: EssentialServiceModule,
    deliveryModule: DeliveryModule,
    workerThreadModule: WorkerThreadModule,
    payloadModule: PayloadModule,
) -> LogModule

internal fun createLogModule(
    initModule: InitModule,
    openTelemetryModule: OpenTelemetryModule,
    androidServicesModule: AndroidServicesModule,
    essentialServiceModule: EssentialServiceModule,
    deliveryModule: DeliveryModule,
    workerThreadModule: WorkerThreadModule,
    payloadModule: PayloadModule,
): LogModule = LogModuleImpl(
    initModule,
    openTelemetryModule,
    androidServicesModule,
    essentialServiceModule,
    deliveryModule,
    workerThreadModule,
    payloadModule,
)
