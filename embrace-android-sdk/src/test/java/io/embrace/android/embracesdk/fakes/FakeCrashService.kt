package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.capture.crash.CrashService
import io.embrace.android.embracesdk.payload.JsException

internal class FakeCrashService : CrashService {
    internal var exception: Throwable? = null
    internal var jsException: JsException? = null

    override fun handleCrash(thread: Thread, exception: Throwable) {
        this.exception = exception
    }

    override fun logUnhandledJsException(exception: JsException) {
        jsException = exception
    }
}
