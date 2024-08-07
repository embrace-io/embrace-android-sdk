package io.embrace.android.embracesdk.internal.injection

import android.content.Context
import io.embrace.android.embracesdk.internal.logging.EmbLogger

/**
 * Function that returns an instance of [OpenTelemetryModule]. Matches the signature of the constructor for [OpenTelemetryModuleImpl]
 */
public typealias OpenTelemetryModuleSupplier = (
    context: Context,
    logger: EmbLogger
) -> OpenTelemetryModule

public fun createOpenTelemetryModule(initModule: InitModule): OpenTelemetryModule =
    OpenTelemetryModuleImpl(initModule)
