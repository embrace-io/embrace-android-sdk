package io.embrace.android.embracesdk.internal.injection

/**
 * Receives a real construction and either returns it or replaces it with an override value for tests
 * */
internal typealias Override<T> = (real: () -> T) -> T

/**
 * Resolves a property that is overridable for tests
 */
internal inline fun <T> Override<T>?.resolve(crossinline real: () -> T): T = when {
    this == null -> real()
    else -> this { real() }
}
