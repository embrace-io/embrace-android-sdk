package io.embrace.android.embracesdk.internal.injection

/**
 * Function that returns an instance of [CustomerLogModule]. Matches the signature of the constructor for [CustomerLogModuleImpl]
 */
internal typealias CustomerLogModuleSupplier = (
    initModule: InitModule,
    openTelemetryModule: OpenTelemetryModule,
    androidServicesModule: AndroidServicesModule,
    essentialServiceModule: EssentialServiceModule,
    deliveryModule: DeliveryModule,
    workerThreadModule: WorkerThreadModule,
    payloadModule: PayloadModule,
) -> CustomerLogModule

internal fun createCustomerLogModule(
    initModule: InitModule,
    openTelemetryModule: OpenTelemetryModule,
    androidServicesModule: AndroidServicesModule,
    essentialServiceModule: EssentialServiceModule,
    deliveryModule: DeliveryModule,
    workerThreadModule: WorkerThreadModule,
    payloadModule: PayloadModule,
): CustomerLogModule = CustomerLogModuleImpl(
    initModule,
    openTelemetryModule,
    androidServicesModule,
    essentialServiceModule,
    deliveryModule,
    workerThreadModule,
    payloadModule,
)
