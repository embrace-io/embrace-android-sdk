package io.embrace.android.embracesdk.internal.injection

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
): NativeCoreModule = NativeCoreModuleImpl(
    initModule,
    coreModule,
    payloadSourceModule,
    workerThreadModule,
    configModule,
    storageModule,
    essentialServiceModule,
    otelModule
)
