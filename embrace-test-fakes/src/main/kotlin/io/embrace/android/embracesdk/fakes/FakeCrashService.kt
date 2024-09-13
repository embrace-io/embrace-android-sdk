package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.capture.crash.CrashService
import io.embrace.android.embracesdk.internal.payload.JsException

class FakeCrashService : CrashService {
    var exception: Throwable? = null
    var jsException: JsException? = null

    override fun handleCrash(exception: Throwable) {
        this.exception = exception
    }

    override fun logUnhandledJsException(exception: JsException) {
        jsException = exception
    }
}
