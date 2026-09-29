package io.embrace.android.embracesdk.internal.arch.navigation

/**
 * Supplies attributes describing the screen being left, written to the given sink.
 */
fun interface ScreenAttributesSource {
    fun writeAttributes(setAttribute: (key: String, value: String) -> Unit)
}
