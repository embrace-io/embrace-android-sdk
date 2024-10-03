package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.utils.Provider

/**
 * Function that returns an instance of [WorkerThreadModule]. Matches the signature of the constructor for [WorkerThreadModuleImpl]
 */
typealias WorkerThreadModuleSupplier = (
    initModule: InitModule,
    configServiceProvider: Provider<ConfigService>
) -> WorkerThreadModule

fun createWorkerThreadModule(
    initModule: InitModule,
    configServiceProvider: Provider<ConfigService>
): WorkerThreadModule = WorkerThreadModuleImpl(initModule, configServiceProvider)
