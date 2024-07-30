package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.logging.InternalErrorHandler
import io.embrace.android.embracesdk.internal.telemetry.errors.InternalErrorService
import io.embrace.android.embracesdk.internal.utils.Provider

public class FakeInternalErrorService : InternalErrorService {

    override var handler: Provider<InternalErrorHandler?> = { null }
    public var throwables: MutableList<Throwable> = mutableListOf()

    override fun handleInternalError(throwable: Throwable) {
        throwables.add(throwable)
    }
}
