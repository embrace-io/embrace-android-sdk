package io.embrace.android.embracesdk.internal.utils

/**
 * Returns a new map that does not contain any null values. This
 * performs the necessary casts to ensure Kotlin's type system is happy.
 */
@Suppress("UNCHECKED_CAST")
fun <K, V> Map<K, V?>.toNonNullMap(): Map<K, V> {
    return filter { it.value != null } as Map<K, V>
}

/**
 * Adds the given entry to the map if the value is not null. This allows a map of non-null values to
 * be built directly from nullable sources, without allocating intermediate maps to filter them out.
 */
fun <K, V> MutableMap<K, V>.putIfNotNull(key: K, value: V?) {
    if (value != null) {
        put(key, value)
    }
}
