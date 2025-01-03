package io.embrace.android.embracesdk.internal.injection

import android.content.Context

/**
 * Function that returns an instance of [CoreModule]. Matches the signature of the constructor for [CoreModuleImpl]
 */
typealias CoreModuleSupplier = (
    context: Context,
    initModule: InitModule,
) -> CoreModule

fun createCoreModule(
    context: Context,
    initModule: InitModule
): CoreModule = CoreModuleImpl(context, initModule)
