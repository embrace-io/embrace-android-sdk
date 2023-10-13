package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.logging.AndroidLogger
import io.embrace.android.embracesdk.logging.EmbraceInternalErrorService
import io.embrace.android.embracesdk.logging.InternalErrorLogger

/**
 * Contains dependencies that are used to gain internal observability into how the SDK
 * is performing.
 */
internal interface SdkObservabilityModule {
    val exceptionService: EmbraceInternalErrorService
    val internalErrorLogger: InternalErrorLogger
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
        EmbraceInternalErrorService(essentialServiceModule.activityService, initModule.clock, logStrictMode)
    }

    override val internalErrorLogger: InternalErrorLogger by singleton {
        InternalErrorLogger(exceptionService, AndroidLogger(), logStrictMode)
    }
}
