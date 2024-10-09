package io.embrace.android.embracesdk.internal.logging

interface InternalErrorHandler {

    /**
     * Tracks an internal error. This is sent to our own telemetry so should be used sparingly
     * & only for states that we can take actions to improve.
     */
    fun trackInternalError(type: InternalErrorType, throwable: Throwable)
}
