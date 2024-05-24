package io.embrace.android.embracesdk.capture.internal.errors

internal interface InternalErrorHandler {
    fun handleInternalError(throwable: Throwable)
}
