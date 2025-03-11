package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.delivery.debug.DeliveryTracer
import io.embrace.android.embracesdk.internal.delivery.execution.RequestExecutionService
import io.embrace.android.embracesdk.internal.delivery.storage.PayloadStorageService
import io.embrace.android.embracesdk.internal.utils.Provider

/**
 * Function that returns an instance of [DeliveryModule]. Matches the signature of the constructor for [DeliveryModuleImpl]
 */
typealias DeliveryModuleSupplier = (
    configModule: ConfigModule,
    initModule: InitModule,
    otelModule: OpenTelemetryModule,
    workerThreadModule: WorkerThreadModule,
    coreModule: CoreModule,
    essentialServiceModule: EssentialServiceModule,
    androidServicesModule: AndroidServicesModule,
    payloadStorageServiceProvider: Provider<PayloadStorageService>?,
    cacheStorageServiceProvider: Provider<PayloadStorageService>?,
    requestExecutionServiceProvider: Provider<RequestExecutionService>?,
    deliveryTracer: DeliveryTracer?,
) -> DeliveryModule

fun createDeliveryModule(
    configModule: ConfigModule,
    initModule: InitModule,
    otelModule: OpenTelemetryModule,
    workerThreadModule: WorkerThreadModule,
    coreModule: CoreModule,
    essentialServiceModule: EssentialServiceModule,
    androidServicesModule: AndroidServicesModule,
    payloadStorageServiceProvider: Provider<PayloadStorageService>?,
    cacheStorageServiceProvider: Provider<PayloadStorageService>?,
    requestExecutionServiceProvider: Provider<RequestExecutionService>?,
    deliveryTracer: DeliveryTracer?,
): DeliveryModule = DeliveryModuleImpl(
    configModule,
    initModule,
    otelModule,
    workerThreadModule,
    coreModule,
    essentialServiceModule,
    androidServicesModule,
    requestExecutionServiceProvider,
    payloadStorageServiceProvider,
    cacheStorageServiceProvider,
    deliveryTracer
)
