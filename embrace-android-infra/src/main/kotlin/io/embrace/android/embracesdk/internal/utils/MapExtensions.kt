package io.embrace.android.embracesdk.internal.utils

/**
 * Returns a new map that does not contain any null values. This
 * performs the necessary casts to ensure Kotlin's type system is happy.
 */
@Suppress("UNCHECKED_CAST")
fun <K, V> Map<K, V?>.toNonNullMap(): Map<K, V> {
    return filter { it.value != null } as Map<K, V>
}
