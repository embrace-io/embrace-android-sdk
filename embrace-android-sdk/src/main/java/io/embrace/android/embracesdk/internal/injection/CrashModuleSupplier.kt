package io.embrace.android.embracesdk.internal.injection

/**
 * Function that returns an instance of [CrashModule]. Matches the signature of the constructor for [CrashModuleImpl]
 */
internal typealias CrashModuleSupplier = (
    initModule: InitModule,
    storageModule: StorageModule,
    essentialServiceModule: EssentialServiceModule,
    nativeModule: NativeModule,
    sessionModule: SessionModule,
    anrModule: AnrModule,
    androidServicesModule: AndroidServicesModule,
    logModule: CustomerLogModule,
) -> CrashModule

internal fun createCrashModule(
    initModule: InitModule,
    storageModule: StorageModule,
    essentialServiceModule: EssentialServiceModule,
    nativeModule: NativeModule,
    sessionModule: SessionModule,
    anrModule: AnrModule,
    androidServicesModule: AndroidServicesModule,
    logModule: CustomerLogModule,
): CrashModule {
    return CrashModuleImpl(
        initModule,
        storageModule,
        essentialServiceModule,
        nativeModule,
        sessionModule,
        anrModule,
        androidServicesModule,
        logModule,
    )
}
