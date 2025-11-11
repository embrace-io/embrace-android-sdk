package io.embrace.android.embracesdk.internal.instrumentation.crash

import io.embrace.android.embracesdk.internal.injection.ConfigModule
import io.embrace.android.embracesdk.internal.injection.InitModule
import io.embrace.android.embracesdk.internal.injection.InstrumentationModule

/**
 * Function that returns an instance of [CrashModule]. Matches the signature of the constructor for [CrashModuleImpl]
 */
typealias CrashModuleSupplier = (
    initModule: InitModule,
    configModule: ConfigModule,
    instrumentationModule: InstrumentationModule,
) -> CrashModule

fun createCrashModule(
    initModule: InitModule,
    configModule: ConfigModule,
    instrumentationModule: InstrumentationModule,
): CrashModule {
    return CrashModuleImpl(
        initModule,
        configModule,
        instrumentationModule,
    )
}
