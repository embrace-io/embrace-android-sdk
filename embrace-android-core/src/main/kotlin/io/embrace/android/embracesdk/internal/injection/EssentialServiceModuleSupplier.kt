package io.embrace.android.embracesdk.internal.injection

import androidx.lifecycle.LifecycleOwner
import io.embrace.android.embracesdk.internal.capture.connectivity.NetworkConnectivityService
import io.embrace.android.embracesdk.internal.network.logging.NetworkLoggingService
import io.embrace.android.embracesdk.internal.utils.Provider

/**
 * Function that returns an instance of [EssentialServiceModule]. Matches the signature of the constructor for [EssentialServiceModuleImpl]
 */
typealias EssentialServiceModuleSupplier = (
    initModule: InitModule,
    configModule: ConfigModule,
    openTelemetryModule: OpenTelemetryModule,
    coreModule: CoreModule,
    workerThreadModule: WorkerThreadModule,
    systemServiceModule: SystemServiceModule,
    androidServicesModule: AndroidServicesModule,
    lifecycleOwnerProvider: Provider<LifecycleOwner?>,
    networkConnectivityServiceProvider: Provider<NetworkConnectivityService?>,
    networkLoggingServiceProvider: Provider<NetworkLoggingService?>,
) -> EssentialServiceModule

fun createEssentialServiceModule(
    initModule: InitModule,
    configModule: ConfigModule,
    openTelemetryModule: OpenTelemetryModule,
    coreModule: CoreModule,
    workerThreadModule: WorkerThreadModule,
    systemServiceModule: SystemServiceModule,
    androidServicesModule: AndroidServicesModule,
    lifecycleOwnerProvider: Provider<LifecycleOwner?>,
    networkConnectivityServiceProvider: Provider<NetworkConnectivityService?>,
    networkLoggingServiceProvider: Provider<NetworkLoggingService?>,
): EssentialServiceModule = EssentialServiceModuleImpl(
    initModule,
    configModule,
    openTelemetryModule,
    coreModule,
    workerThreadModule,
    systemServiceModule,
    androidServicesModule,
    lifecycleOwnerProvider,
    networkConnectivityServiceProvider,
    networkLoggingServiceProvider,
)
