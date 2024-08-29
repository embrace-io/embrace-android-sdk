package io.embrace.android.embracesdk.internal

import io.embrace.android.embracesdk.internal.utils.Provider

/**
 * Holds a property whose value can be cached (if its inputs do not change).
 */
internal class CacheableValue<T>(

    /**
     * Used to generate a hashcode from all the inputs that might affect a value. If the hashcode
     * is the same for the inputs, then it is assumed a cached value can be returned. If the
     * hashcode is not the same, then the value is calculated from scratch.
     *
     * Be careful about defining inputs. For example, the hashcode for a collection generally
     * won't change if new objects are added, so you need to be wary of accidentally
     * returning stale values.
     */
    private val input: Provider<Any>
) {

    private var initialized = false
    private var prevHashCode = -1
    private var value: T? = null

    // input: used to determine whether inputs have changed since last call
    // action: generates a value if inputs are changed (or haven't been initialized)

    /**
     * Resolves and returns a value. If inputs are unchanged then a cached value will be returned.
     * If inputs are changed or no cached value is present, then [action] will be invoked
     * to calculate a value that is placed in the cache.
     */
    fun value(action: Provider<T>): T {
        val hashCode = input().hashCode()

        if (prevHashCode != hashCode || !initialized) {
            initialized = true
            value = action()
        }
        prevHashCode = hashCode
        return checkNotNull(value) {
            "Value to be cached is null"
        }
    }
}
