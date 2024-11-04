package io.embrace.android.embracesdk.internal.config.behavior

interface DataCaptureEventBehavior {
    fun isInternalExceptionCaptureEnabled(): Boolean
    fun isEventEnabled(eventName: String): Boolean
    fun isLogMessageEnabled(logMessage: String): Boolean
}
