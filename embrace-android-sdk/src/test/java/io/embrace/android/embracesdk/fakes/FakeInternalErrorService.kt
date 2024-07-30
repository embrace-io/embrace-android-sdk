package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.logging.InternalErrorHandler
import io.embrace.android.embracesdk.internal.telemetry.errors.InternalErrorService
import io.embrace.android.embracesdk.internal.utils.Provider

internal class FakeInternalErrorService : InternalErrorService {

    override var handler: Provider<InternalErrorHandler?> = { null }
    var throwables: MutableList<Throwable> = mutableListOf()

    override fun handleInternalError(throwable: Throwable) {
        throwables.add(throwable)
    }
}
