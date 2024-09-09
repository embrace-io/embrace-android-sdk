package io.embrace.android.embracesdk.internal.logs

/**
 * A wrapper for a log payload that stipulates whether its delivery should be deferred or sent immediately
 */
data class LogRequest<T>(val payload: T, val defer: Boolean = false)
