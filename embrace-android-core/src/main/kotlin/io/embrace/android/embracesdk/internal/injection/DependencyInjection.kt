package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.utils.Provider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * How the dependency should be loaded.
 */
public enum class LoadType {

    /**
     * The dependency should be instantiated as soon as the module is created.
     */
    EAGER,

    /**
     * The dependency should be instantiated at the point where it is required.
     */
    LAZY
}

/**
 * Creates a new dependency that is a singleton, meaning only one object will ever be created in
 * this module. By default this dependency will be created lazily.
 *
 * Lazy dependencies are NOT thread safe. It is assumed that dependencies will always be
 * initialized on the same thread.
 */
public inline fun <reified T> singleton(
    loadType: LoadType = LoadType.LAZY,
    noinline provider: Provider<T>
): ReadOnlyProperty<Any?, T> = SingletonDelegate(loadType, provider)

/**
 * Creates a new dependency from a factory, meaning every time this property is called a
 * new object will be created.
 */
public inline fun <reified T> factory(
    noinline provider: Provider<T>
): ReadOnlyProperty<Any?, T> = FactoryDelegate(provider)

public class FactoryDelegate<T>(private inline val provider: Provider<T>) : ReadOnlyProperty<Any?, T> {

    override fun getValue(thisRef: Any?, property: KProperty<*>): T = provider()
}

public class SingletonDelegate<T>(
    loadType: LoadType,
    provider: Provider<T>
) : ReadOnlyProperty<Any?, T> {

    // optimization: use atomic checks rather than synchronized in lazy.
    // Taking out a lock is overkill for the vast majority of objects on the graph
    private val value: T by lazy(LazyThreadSafetyMode.PUBLICATION, provider)

    init {
        if (loadType == LoadType.EAGER) {
            value
        }
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): T = value
}
