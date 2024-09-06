package io.embrace.android.embracesdk.internal.injection

import android.content.Context
import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.clock.NormalizedIntervalClock
import io.embrace.android.embracesdk.internal.clock.SystemClock
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.EmbLoggerImpl

/**
 * Function that returns an instance of [InitModule]. Matches the signature of the constructor for [InitModuleImpl]
 */
typealias InitModuleSupplier = (
    context: Context,
    logger: EmbLogger
) -> InitModule

fun createInitModule(
    clock: Clock = NormalizedIntervalClock(systemClock = SystemClock()),
    logger: EmbLogger = EmbLoggerImpl(),
    systemInfo: SystemInfo = SystemInfo()
): InitModule = InitModuleImpl(clock, logger, systemInfo)
