package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.logging.AndroidLogger
import io.embrace.android.embracesdk.logging.EmbraceInternalErrorService
import io.embrace.android.embracesdk.logging.InternalErrorLogger
import io.embrace.android.embracesdk.telemetry.EmbraceTelemetryService

/**
 * Contains dependencies that are used to gain internal observability into how the SDK
 * is performing.
 */
internal interface SdkObservabilityModule {
    val exceptionService: EmbraceInternalErrorService
    val internalErrorLogger: InternalErrorLogger
    val embraceTelemetryService: EmbraceTelemetryService
}

internal class SdkObservabilityModuleImpl(
    initModule: InitModule,
    essentialServiceModule: EssentialServiceModule
) : SdkObservabilityModule {

    private val logStrictMode by lazy {
        val configService = essentialServiceModule.configService
        configService.sessionBehavior.isSessionErrorLogStrictModeEnabled()
    }

    override val exceptionService: EmbraceInternalErrorService by singleton {
        EmbraceInternalErrorService(essentialServiceModule.processStateService, initModule.clock, logStrictMode)
    }

    override val internalErrorLogger: InternalErrorLogger by singleton {
        InternalErrorLogger(exceptionService, AndroidLogger(), logStrictMode)
    }

    override val embraceTelemetryService: EmbraceTelemetryService by singleton {
        EmbraceTelemetryService()
    }
}
