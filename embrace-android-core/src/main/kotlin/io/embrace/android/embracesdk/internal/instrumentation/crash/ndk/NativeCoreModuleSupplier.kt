package io.embrace.android.embracesdk.internal.instrumentation.crash.ndk

import io.embrace.android.embracesdk.internal.injection.ConfigModule
import io.embrace.android.embracesdk.internal.injection.CoreModule
import io.embrace.android.embracesdk.internal.injection.EssentialServiceModule
import io.embrace.android.embracesdk.internal.injection.InitModule
import io.embrace.android.embracesdk.internal.injection.OpenTelemetryModule
import io.embrace.android.embracesdk.internal.injection.PayloadSourceModule
import io.embrace.android.embracesdk.internal.injection.StorageModule
import io.embrace.android.embracesdk.internal.injection.WorkerThreadModule
import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.jni.JniDelegate
import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.symbols.SymbolService
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
