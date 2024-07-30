package io.embrace.android.embracesdk.internal.utils

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * Syntactic sugar that makes it easier to define a property in Kotlin whose value is backed by
 * a ThreadLocal.
 */
public inline fun <reified T> threadLocal(
    noinline provider: Provider<T>
): ReadOnlyProperty<Any?, T> = ThreadLocalDelegate(provider)

public class ThreadLocalDelegate<T>(
    provider: Provider<T>
) : ReadOnlyProperty<Any?, T> {

    private val threadLocal: ThreadLocal<T> = object : ThreadLocal<T>() {
        override fun initialValue(): T = provider()
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return checkNotNull(threadLocal.get())
    }
}
