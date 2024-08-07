package io.embrace.android.embracesdk.internal.injection

import android.content.Context
import io.embrace.android.embracesdk.internal.logging.EmbLogger

/**
 * Function that returns an instance of [CoreModule]. Matches the signature of the constructor for [CoreModuleImpl]
 */
public typealias CoreModuleSupplier = (
    context: Context,
    logger: EmbLogger
) -> CoreModule

public fun createCoreModule(
    context: Context,
    logger: EmbLogger
): CoreModule = CoreModuleImpl(context, logger)
