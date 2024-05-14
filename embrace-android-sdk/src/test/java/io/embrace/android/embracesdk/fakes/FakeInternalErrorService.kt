package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.logging.InternalErrorService
import io.embrace.android.embracesdk.payload.LegacyExceptionError

internal class FakeInternalErrorService : InternalErrorService {

    override var configService: ConfigService? = null
    var throwables: MutableList<Throwable> = mutableListOf()
    var resetCallCount: Int = 0
    var data: LegacyExceptionError? = null

    override fun handleInternalError(throwable: Throwable) {
        throwables.add(throwable)
    }

    override fun getCapturedData(): LegacyExceptionError? {
        return data
    }

    override fun cleanCollections() {
        resetCallCount++
    }
}
