package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.comms.api.ApiService

/**
 * Function that returns an instance of [DeliveryModule]. Matches the signature of the constructor for [DeliveryModuleImpl]
 */
internal typealias DeliveryModuleSupplier = (
    initModule: InitModule,
    workerThreadModule: WorkerThreadModule,
    storageModule: StorageModule,
    apiService: ApiService?,
) -> DeliveryModule

internal fun createDeliveryModule(
    initModule: InitModule,
    workerThreadModule: WorkerThreadModule,
    storageModule: StorageModule,
    apiService: ApiService?,
): DeliveryModule = DeliveryModuleImpl(
    initModule,
    workerThreadModule,
    storageModule,
    apiService,
)
