package io.embrace.android.embracesdk.internal.injection

/**
 * Function that returns an instance of [InstrumentationModule]. Matches the signature of the constructor for [InstrumentationModuleImpl]
 */
typealias InstrumentationModuleSupplier = (
    initModule: InitModule,
    workerThreadModule: WorkerThreadModule,
    configModule: ConfigModule,
    essentialServiceModule: EssentialServiceModule,
    androidServicesModule: AndroidServicesModule,
    coreModule: CoreModule,
) -> InstrumentationModule

fun createInstrumentationModule(
    initModule: InitModule,
    workerThreadModule: WorkerThreadModule,
    configModule: ConfigModule,
    essentialServiceModule: EssentialServiceModule,
    androidServicesModule: AndroidServicesModule,
    coreModule: CoreModule,
): InstrumentationModule = InstrumentationModuleImpl(
    initModule,
    workerThreadModule,
    configModule,
    essentialServiceModule,
    androidServicesModule,
    coreModule,
)
