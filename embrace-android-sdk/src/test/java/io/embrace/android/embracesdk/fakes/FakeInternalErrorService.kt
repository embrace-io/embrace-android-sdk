package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.capture.internal.errors.InternalErrorDataSource
import io.embrace.android.embracesdk.capture.internal.errors.InternalErrorService
import io.embrace.android.embracesdk.internal.utils.Provider

internal class FakeInternalErrorService : InternalErrorService {

    override var internalErrorDataSource: Provider<InternalErrorDataSource?> = { null }
    var throwables: MutableList<Throwable> = mutableListOf()

    override fun handleInternalError(throwable: Throwable) {
        throwables.add(throwable)
    }
}
