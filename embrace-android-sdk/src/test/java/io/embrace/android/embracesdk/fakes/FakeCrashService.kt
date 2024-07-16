package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.capture.crash.CrashService
import io.embrace.android.embracesdk.internal.payload.JsException

internal class FakeCrashService : CrashService {
    internal var exception: Throwable? = null
    internal var jsException: JsException? = null

    override fun handleCrash(exception: Throwable) {
        this.exception = exception
    }

    override fun logUnhandledJsException(exception: JsException) {
        jsException = exception
    }
}
