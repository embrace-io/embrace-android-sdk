package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.utils.Provider

/**
 * Function that returns an instance of [PayloadSourceModule]. Matches the signature of the constructor for [PayloadSourceModuleImpl]
 */
internal typealias PayloadSourceModuleSupplier = (
    initModule: InitModule,
    coreModule: CoreModule,
    workerThreadModule: WorkerThreadModule,
    systemServiceModule: SystemServiceModule,
    androidServicesModule: AndroidServicesModule,
    essentialServiceModule: EssentialServiceModule,
    configModule: ConfigModule,
    nativeCoreModuleProvider: Provider<NativeCoreModule?>,
    nativeFeatureModuleProvider: Provider<NativeFeatureModule?>,
    otelModule: OpenTelemetryModule,
    anrModule: AnrModule,
) -> PayloadSourceModule

internal fun createPayloadSourceModule(
    initModule: InitModule,
    coreModule: CoreModule,
    workerThreadModule: WorkerThreadModule,
    systemServiceModule: SystemServiceModule,
    androidServicesModule: AndroidServicesModule,
    essentialServiceModule: EssentialServiceModule,
    configModule: ConfigModule,
    nativeCoreModuleProvider: Provider<NativeCoreModule?>,
    nativeFeatureModuleProvider: Provider<NativeFeatureModule?>,
    otelModule: OpenTelemetryModule,
    anrModule: AnrModule,
): PayloadSourceModule = PayloadSourceModuleImpl(
    initModule,
    coreModule,
    workerThreadModule,
    systemServiceModule,
    androidServicesModule,
    essentialServiceModule,
    configModule,
    nativeCoreModuleProvider,
    nativeFeatureModuleProvider,
    otelModule,
    anrModule,
)
