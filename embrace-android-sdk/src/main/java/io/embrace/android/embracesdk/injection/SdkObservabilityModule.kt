package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.logging.EmbraceInternalErrorService
import io.embrace.android.embracesdk.logging.InternalErrorService
import io.embrace.android.embracesdk.logging.ReportingLoggerAction

/**
 * Contains dependencies that are used to gain internal observability into how the SDK
 * is performing.
 */
internal interface SdkObservabilityModule {
    val internalErrorService: InternalErrorService
    val reportingLoggerAction: ReportingLoggerAction
}

internal class SdkObservabilityModuleImpl(
    initModule: InitModule,
    essentialServiceModule: EssentialServiceModule
) : SdkObservabilityModule {

    override val internalErrorService: InternalErrorService by singleton {
        EmbraceInternalErrorService(
            essentialServiceModule.processStateService,
            initModule.clock
        )
    }

    override val reportingLoggerAction: ReportingLoggerAction by singleton {
        ReportingLoggerAction(internalErrorService)
    }
}
