package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.logging.InternalErrorService
import io.embrace.android.embracesdk.payload.ExceptionError

internal class FakeInternalErrorService : InternalErrorService {

    var lastConfigService: ConfigService? = null
    var throwables: MutableList<Throwable> = mutableListOf()
    var resetCallCount: Int = 0

    override fun setConfigService(configService: ConfigService?) {
        this.lastConfigService = configService
    }

    override fun handleInternalError(throwable: Throwable) {
        throwables.add(throwable)
    }

    override fun resetExceptionErrorObject() {
        resetCallCount++
    }

    override var currentExceptionError: ExceptionError? = null
}
