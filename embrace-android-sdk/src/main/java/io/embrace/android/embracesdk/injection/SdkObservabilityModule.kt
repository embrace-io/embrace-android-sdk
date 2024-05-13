package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.logging.EmbraceInternalErrorService
import io.embrace.android.embracesdk.logging.InternalErrorService

/**
 * Contains dependencies that are used to gain internal observability into how the SDK
 * is performing.
 */
internal interface SdkObservabilityModule {
    val internalErrorService: InternalErrorService
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
}
