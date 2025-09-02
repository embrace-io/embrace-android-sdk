package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.clock.NormalizedIntervalClock
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.EmbLoggerImpl

fun createInitModule(
    clock: Clock = NormalizedIntervalClock(),
    logger: EmbLogger = EmbLoggerImpl(),
    systemInfo: SystemInfo = SystemInfo(),
    useKotlinSdk: Boolean
): InitModule = InitModuleImpl(clock, logger, systemInfo, useKotlinSdk)
