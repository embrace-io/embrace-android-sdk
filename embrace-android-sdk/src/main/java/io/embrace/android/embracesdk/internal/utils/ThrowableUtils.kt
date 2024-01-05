package io.embrace.android.embracesdk.internal.utils

import io.embrace.android.embracesdk.annotation.InternalApi

/**
 * Utilities to handle edge cases related to working with Throwables
 */

/**
 * Extension function that returns null for the stacktrace of a [Throwable] if an exception is thrown while trying to get it
 */
@InternalApi
internal fun Throwable.getSafeStackTrace(): Array<StackTraceElement>? {
    return try {
        this.stackTrace
    } catch (ex: Exception) {
        null
    }
}
