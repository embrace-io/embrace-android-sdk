package io.embrace.android.embracesdk.internal.arch

import io.embrace.android.embracesdk.internal.arch.destination.SessionSpanWriter
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.logging.EmbLogger

/**
 * Provides references to essential functionality that can be used when registering instrumentation via SPI.
 */
interface InstrumentationInstallArgs {
    val configService: ConfigService
    val sessionSpanWriter: SessionSpanWriter
    val logger: EmbLogger
    val clock: Clock
}
