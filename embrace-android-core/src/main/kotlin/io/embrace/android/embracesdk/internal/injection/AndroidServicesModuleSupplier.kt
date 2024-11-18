package io.embrace.android.embracesdk.internal.injection

/**
 * Function that returns an instance of [AndroidServicesModule]. Matches the signature of the constructor for [AndroidServicesModuleImpl]
 */
typealias AndroidServicesModuleSupplier = (
    initModule: InitModule,
    coreModule: CoreModule,
) -> AndroidServicesModule

fun createAndroidServicesModule(
    initModule: InitModule,
    coreModule: CoreModule,
): AndroidServicesModule = AndroidServicesModuleImpl(initModule, coreModule)
