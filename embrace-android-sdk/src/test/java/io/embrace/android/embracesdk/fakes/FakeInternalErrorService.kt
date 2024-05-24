package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.capture.internal.errors.InternalErrorDataSource
import io.embrace.android.embracesdk.capture.internal.errors.InternalErrorService
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.payload.LegacyExceptionError

internal class FakeInternalErrorService : InternalErrorService {

    override var internalErrorDataSource: Provider<InternalErrorDataSource?> = { null }
    var throwables: MutableList<Throwable> = mutableListOf()
    var resetCallCount: Int = 0
    var data: LegacyExceptionError? = null

    override fun handleInternalError(throwable: Throwable) {
        throwables.add(throwable)
    }
}
