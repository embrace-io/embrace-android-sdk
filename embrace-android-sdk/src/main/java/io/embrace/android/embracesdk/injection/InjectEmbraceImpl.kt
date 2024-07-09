package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.EmbraceImpl
import io.embrace.android.embracesdk.internal.api.delegate.SdkCallChecker
import io.embrace.android.embracesdk.internal.utils.Provider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * Provides a lazy property delegate for fields in [EmbraceImpl]. If the SDK is not started, this will
 * return null. If it is started, the value will return the value from the provider.
 *
 * This is convenient syntax to avoid a large number of lateinit vars in [EmbraceImpl].
 */
internal inline fun <reified T> EmbraceImpl.embraceImplInject(
    noinline provider: Provider<T>
): ReadOnlyProperty<Any?, T?> = EmbraceImplFieldDelegate({ this.isStarted }, provider)

internal inline fun <reified T> embraceImplInject(
    checker: SdkCallChecker,
    noinline provider: Provider<T>
): ReadOnlyProperty<Any?, T?> = EmbraceImplFieldDelegate({ checker.started.get() }, provider)

internal class EmbraceImplFieldDelegate<T>(
    private val startedCheck: () -> Boolean,
    provider: Provider<T>
) : ReadOnlyProperty<Any?, T?> {

    // optimization: use atomic checks rather than synchronized in lazy.
    // Taking out a lock is overkill for the vast majority of objects on the graph
    private val value: T by lazy(LazyThreadSafetyMode.PUBLICATION, provider)

    override fun getValue(thisRef: Any?, property: KProperty<*>): T? = when {
        startedCheck() -> value
        else -> null // not started yet - don't resolve anything.
    }
}
