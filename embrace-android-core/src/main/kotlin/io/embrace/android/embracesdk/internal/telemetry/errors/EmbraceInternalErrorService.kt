package io.embrace.android.embracesdk.internal.telemetry.errors

import io.embrace.android.embracesdk.internal.utils.Provider

/**
 * Intercepts Embrace SDK's exceptions errors and forwards them to the Embrace API.
 */
internal class EmbraceInternalErrorService : InternalErrorService {

    override fun handleInternalError(throwable: Throwable) {
        internalErrorDataSource()?.handleInternalError(throwable)
    }

    override var internalErrorDataSource: Provider<InternalErrorDataSource?> = { null }
}
