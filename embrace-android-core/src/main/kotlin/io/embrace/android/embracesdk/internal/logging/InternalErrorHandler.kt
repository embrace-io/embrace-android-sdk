package io.embrace.android.embracesdk.internal.logging

interface InternalErrorHandler {
    fun handleInternalError(throwable: Throwable)
}
