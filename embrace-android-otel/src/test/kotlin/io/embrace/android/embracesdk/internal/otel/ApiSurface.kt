package io.embrace.android.embracesdk.internal.otel

/**
 * Reflective helpers that enumerate an API surface using Java reflection.
 *
 * Classification is by JVM method *name*, so overloads share a single entry.
 */
internal fun Class<*>.apiMethodNames(): Set<String> =
    declaredMethods
        .filterNot { it.isSynthetic || it.isBridge || '$' in it.name }
        .mapTo(mutableSetOf()) { it.name }

/**
 * As [apiMethodNames], but also walks superinterfaces.
 */
internal fun Class<*>.inheritedApiMethodNames(): Set<String> =
    apiMethodNames() + interfaces.flatMap { it.inheritedApiMethodNames() }
