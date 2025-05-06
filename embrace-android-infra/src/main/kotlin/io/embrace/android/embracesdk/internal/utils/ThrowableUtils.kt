package io.embrace.android.embracesdk.internal.utils

/**
 * Utilities to handle edge cases related to working with Throwables
 */

/**
 * Extension function that returns null for the stacktrace of a [Throwable] if an exception is thrown while trying to get it
 */
fun Throwable.getSafeStackTrace(): Array<StackTraceElement>? {
    return try {
        this.stackTrace
    } catch (ex: Exception) {
        null
    }
}

/**
 * Returns a string representing the first 200 elements of the stacktrace of this [Throwable], stringified and with a line break in between
 */
fun Throwable.truncatedStacktraceText(): String = stackTrace.truncate().joinToString(separator = " \n")

/**
 * Returns a list of the first 200 elements of the stacktrace (stringified) of this [Throwable]
 */
fun Array<StackTraceElement>.truncate(): List<String> = take(200).map(StackTraceElement::toString).toList()
