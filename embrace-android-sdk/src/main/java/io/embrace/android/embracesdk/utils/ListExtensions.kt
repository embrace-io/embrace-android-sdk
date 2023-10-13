package io.embrace.android.embracesdk.utils

/**
 * Safe-wrapper around list's subscript.
 * Returns the element at the specified index.
 * Returns null if the index is out of bounds, instead of the out-of-bounds exception.
 */
internal fun <T> List<T>.at(index: Int): T? {
    return if (index >= 0 && index < count()) {
        this[index]
    } else {
        null
    }
}
