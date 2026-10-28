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
 * Returns the first 200 elements of the stacktrace of this [Throwable], followed by up to 3 causes as
 * `Caused by: <class>: <message>` blocks with their first 200 elements each, separated by line breaks.
 */
fun Throwable.stacktraceTextWithCauses(): String {
    val seen = mutableSetOf<Throwable>(this)
    val lines = stackTrace.truncate().toMutableList()
    var cause = cause
    var count = 0
    while (cause != null && count < 3 && seen.add(cause)) {
        val message = cause.message
        lines += if (message == null) "Caused by: ${cause.javaClass.name}" else "Caused by: ${cause.javaClass.name}: $message"
        lines += cause.stackTrace.truncate()
        cause = cause.cause
        count++
    }
    return lines.joinToString(separator = "\n")
}

/**
 * Returns a list of the first 200 elements of the stacktrace (stringified) of this [Throwable]
 */
fun Array<StackTraceElement>.truncate(): List<String> = take(200).map(StackTraceElement::toString).toList()
