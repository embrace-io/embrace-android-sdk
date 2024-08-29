package io.embrace.android.embracesdk.internal.config.behavior

public interface DataCaptureEventBehavior {
    public fun isInternalExceptionCaptureEnabled(): Boolean
    public fun isEventEnabled(eventName: String): Boolean
    public fun isLogMessageEnabled(logMessage: String): Boolean
    public fun getEventLimits(): Map<String, Long>
}
