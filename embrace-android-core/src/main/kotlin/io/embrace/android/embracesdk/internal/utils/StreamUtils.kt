package io.embrace.android.embracesdk.internal.utils

/**
 * Backwards compatibility streaming for Java, implemented via Kotlin.
 */
inline fun <T> stream(
    collection: Collection<T>,
    function: (T) -> Unit
): Unit = collection.toList().forEach(function)

/**
 * Backwards compatibility filtering for Java, implemented via Kotlin.
 */
inline fun <T> filter(
    collection: Collection<T>,
    function: (T) -> Boolean
): List<T> = collection.toList().filter(function)

/**
 * Backwards compatibility mapping for Java, implemented via Kotlin.
 */
inline fun <T, R> map(
    collection: Collection<T>,
    function: (T) -> R
): List<R> = collection.toList().map(function)
