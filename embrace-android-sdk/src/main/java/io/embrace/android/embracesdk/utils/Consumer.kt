package io.embrace.android.embracesdk.utils

/**
 * Backwards compatible implementation of a Java Consumer.
 */
internal fun interface Consumer<S, T> {

    fun accept(s: S, t: T)
}
