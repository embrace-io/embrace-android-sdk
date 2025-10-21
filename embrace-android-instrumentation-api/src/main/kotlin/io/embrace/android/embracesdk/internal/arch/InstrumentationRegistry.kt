package io.embrace.android.embracesdk.internal.arch

import io.embrace.android.embracesdk.internal.arch.datasource.DataSource
import java.util.concurrent.ConcurrentHashMap

private val registry: MutableMap<String, Any> = ConcurrentHashMap()

/**
 * Gets a [DataSource] matching the given key if the SDK is initialized and the feature is
 * enabled. If these conditions aren't true, the function may return null.
 *
 * Instrumentation within the SDK must call [registerDataSource] for this function to
 * return anything.
 */
@Suppress("UNCHECKED_CAST")
fun <T> retrieveDataSource(key: String): T? = registry[key] as? T

/**
 * Associates a [DataSource] with the given key in the registry. Instrumentation within the SDK
 * must call [retrieveDataSource] to work.
 */
fun registerDataSource(key: String, dataSource: DataSource<*>) {
    registry[key] = dataSource
}
