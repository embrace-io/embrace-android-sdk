package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.SharedObjectLoader
import io.embrace.android.embracesdk.internal.ndk.jni.JniDelegate
import io.embrace.android.embracesdk.internal.ndk.symbols.SymbolService
import io.embrace.android.embracesdk.internal.utils.Provider

/**
 * Function that returns an instance of [NativeCoreModule]. Matches the signature of the constructor for [NativeCoreModuleImpl]
 */
typealias NativeCoreModuleSupplier = (
    initModule: InitModule,
    coreModule: CoreModule,
    payloadSourceModule: PayloadSourceModule,
    workerThreadModule: WorkerThreadModule,
    configModule: ConfigModule,
    storageModule: StorageModule,
    essentialServiceModule: EssentialServiceModule,
    otelModule: OpenTelemetryModule,
    delegateProvider: Provider<JniDelegate?>,
    sharedObjectLoaderProvider: Provider<SharedObjectLoader?>,
    symbolServiceProvider: Provider<SymbolService?>,
) -> NativeCoreModule

fun createNativeCoreModule(
    initModule: InitModule,
    coreModule: CoreModule,
    payloadSourceModule: PayloadSourceModule,
    workerThreadModule: WorkerThreadModule,
    configModule: ConfigModule,
    storageModule: StorageModule,
    essentialServiceModule: EssentialServiceModule,
    otelModule: OpenTelemetryModule,
    delegateProvider: Provider<JniDelegate?>,
    sharedObjectLoaderProvider: Provider<SharedObjectLoader?>,
    symbolServiceProvider: Provider<SymbolService?>,
): NativeCoreModule = NativeCoreModuleImpl(
    initModule,
    coreModule,
    payloadSourceModule,
    workerThreadModule,
    configModule,
    storageModule,
    essentialServiceModule,
    otelModule,
    delegateProvider,
    sharedObjectLoaderProvider,
    symbolServiceProvider,
)
