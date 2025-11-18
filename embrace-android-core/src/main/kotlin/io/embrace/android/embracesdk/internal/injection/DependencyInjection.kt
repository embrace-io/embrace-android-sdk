package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.utils.Provider

/**
 * Creates a new dependency that is a singleton, meaning only one object will ever be created in
 * this module.
 *
 * Lazy dependencies are NOT thread safe. It is assumed that dependencies will always be
 * initialized on the same thread.
 */
inline fun <reified T> singleton(
    noinline provider: Provider<T>,
): Lazy<T> = lazy(LazyThreadSafetyMode.PUBLICATION, provider)
