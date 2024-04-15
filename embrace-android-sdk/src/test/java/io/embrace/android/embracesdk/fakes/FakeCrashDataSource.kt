package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.arch.destination.LogWriter
import io.embrace.android.embracesdk.capture.crash.CrashDataSource
import io.embrace.android.embracesdk.payload.JsException

internal class FakeCrashDataSource : CrashDataSource {
    internal var exception: Throwable? = null
    internal var jsException: JsException? = null

    override fun alterSessionSpan(
        inputValidation: () -> Boolean,
        captureAction: LogWriter.() -> Unit,
    ): Boolean {
        TODO("Not yet implemented")
    }

    override fun enableDataCapture() {
        TODO("Not yet implemented")
    }

    override fun disableDataCapture() {
        TODO("Not yet implemented")
    }

    override fun resetDataCaptureLimits() {
        TODO("Not yet implemented")
    }

    override fun handleCrash(thread: Thread, exception: Throwable) {
        this.exception = exception
    }

    override fun logUnhandledJsException(exception: JsException) {
        jsException = exception
    }
}
