package io.embrace.android.embracesdk.internal.logging

interface InternalErrorHandler {

    /**
     * Tracks an internal error. This is sent to our own telemetry so should be used sparingly
     * & only for states that we can take actions to improve.
     */
    fun trackInternalError(type: InternalErrorType, throwable: Throwable)

    /**
     * Reports a failure in code supplied by the app. This is not an SDK defect so it is not sent to our telemetry.
     */
    fun trackUserCodeError(type: InternalErrorType, throwable: Throwable) = Unit
}
