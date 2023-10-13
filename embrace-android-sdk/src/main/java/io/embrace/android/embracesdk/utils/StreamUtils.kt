package io.embrace.android.embracesdk.utils

/**
 * Backwards compatibility streaming for Java, implemented via Kotlin.
 */
internal inline fun <T> stream(
    collection: Collection<T>,
    function: (T) -> Unit
) = collection.toList().forEach(function)

/**
 * Backwards compatibility filtering for Java, implemented via Kotlin.
 */
internal inline fun <T> filter(
    collection: Collection<T>,
    function: (T) -> Boolean
) = collection.toList().filter(function)

/**
 * Backwards compatibility mapping for Java, implemented via Kotlin.
 */
internal inline fun <T, R> map(
    collection: Collection<T>,
    function: (T) -> R
) = collection.toList().map(function)
