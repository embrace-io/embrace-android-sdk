package io.embrace.android.embracesdk.fakes

import java.lang.reflect.Proxy

/**
 * An implementation of interface [T] that throws on every call, for dependencies a test should never use.
 */
inline fun <reified T : Any> unimplemented(): T =
    Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { proxy, method, args ->
        when (method.name) {
            "toString" -> "unimplemented ${T::class.java.simpleName}"
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === args?.firstOrNull()
            else -> throw UnsupportedOperationException("${T::class.java.simpleName}.${method.name}")
        }
    } as T
