package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.IdGenerator
import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.clock.NormalizedIntervalClock
import io.embrace.android.embracesdk.internal.clock.SystemClock
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.telemetry.EmbraceTelemetryService
import io.embrace.android.embracesdk.internal.telemetry.TelemetryService
import io.embrace.android.embracesdk.internal.telemetry.errors.EmbraceInternalErrorService
import io.embrace.android.embracesdk.internal.telemetry.errors.InternalErrorService

internal class InitModuleImpl(
    override val clock: Clock = NormalizedIntervalClock(systemClock = SystemClock()),
    override val logger: EmbLogger = EmbLoggerImpl(),
    override val systemInfo: SystemInfo = SystemInfo()
) : InitModule {

    override val internalErrorService: InternalErrorService = EmbraceInternalErrorService()

    init {
        logger.internalErrorService = internalErrorService
    }

    override val telemetryService: TelemetryService by singleton {
        EmbraceTelemetryService(
            systemInfo = systemInfo
        )
    }

    override val processIdentifier: String = IdGenerator.generateLaunchInstanceId()

    override val jsonSerializer: PlatformSerializer by singleton {
        EmbraceSerializer()
    }
}
