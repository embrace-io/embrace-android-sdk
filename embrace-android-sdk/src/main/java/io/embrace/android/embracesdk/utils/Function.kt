package io.embrace.android.embracesdk.utils

/**
 * Backwards compatible implementation of a Java Function.
 */
internal fun interface Function<T, R> {

    fun apply(t: T): R
}
