package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.comms.delivery.DeliveryService
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
    storageModule: StorageModule,
    essentialServiceModule: EssentialServiceModule,
    payloadStorageServiceProvider: Provider<PayloadStorageService?>,
    cacheStorageServiceProvider: Provider<PayloadStorageService?>,
    requestExecutionServiceProvider: Provider<RequestExecutionService?>,
    deliveryServiceProvider: Provider<DeliveryService?>
) -> DeliveryModule

fun createDeliveryModule(
    configModule: ConfigModule,
    initModule: InitModule,
    otelModule: OpenTelemetryModule,
    workerThreadModule: WorkerThreadModule,
    coreModule: CoreModule,
    storageModule: StorageModule,
    essentialServiceModule: EssentialServiceModule,
    payloadStorageServiceProvider: Provider<PayloadStorageService?>,
    cacheStorageServiceProvider: Provider<PayloadStorageService?>,
    requestExecutionServiceProvider: Provider<RequestExecutionService?>,
    deliveryServiceProvider: Provider<DeliveryService?>
): DeliveryModule = DeliveryModuleImpl(
    configModule,
    initModule,
    otelModule,
    workerThreadModule,
    coreModule,
    storageModule,
    essentialServiceModule,
    requestExecutionServiceProvider,
    payloadStorageServiceProvider,
    cacheStorageServiceProvider,
    deliveryServiceProvider
)
