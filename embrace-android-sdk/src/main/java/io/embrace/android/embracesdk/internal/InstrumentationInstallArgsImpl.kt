package io.embrace.android.embracesdk.internal

import io.embrace.android.embracesdk.internal.arch.InstrumentationInstallArgs
import io.embrace.android.embracesdk.internal.arch.destination.SessionSpanWriter
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.logging.EmbLogger

internal class InstrumentationInstallArgsImpl(
    override val configService: ConfigService,
    override val sessionSpanWriter: SessionSpanWriter,
    override val logger: EmbLogger,
    override val clock: Clock,
) : InstrumentationInstallArgs
