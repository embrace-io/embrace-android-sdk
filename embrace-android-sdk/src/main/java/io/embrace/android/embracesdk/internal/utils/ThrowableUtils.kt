package io.embrace.android.embracesdk.internal.utils

import io.embrace.android.embracesdk.InternalApi

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

/**
 * Return the canonical name of the cause of a [Throwable]. Handles null elements throughout,
 * including the throwable and its cause, in which case [defaultName] is returned
 */
@InternalApi
public fun causeName(throwable: Throwable?, defaultName: String = ""): String {
    return throwable?.cause?.javaClass?.canonicalName ?: defaultName
}

/**
 * Return the message of the cause of a [Throwable]. Handles null elements throughout,
 * including the throwable and its cause, in which case [defaultMessage] is returned
 */
@InternalApi
public fun causeMessage(throwable: Throwable?, defaultMessage: String = ""): String {
    return throwable?.cause?.message ?: defaultMessage
}
