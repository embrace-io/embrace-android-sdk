package io.embrace.android.embracesdk.internal.otel.wrapper

import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.context.Context
import io.embrace.opentelemetry.kotlin.context.ContextKey
import io.embrace.opentelemetry.kotlin.k2j.context.ContextKeyAdapter

@OptIn(ExperimentalApi::class)
class KotlinContextWrapper(
    private val impl: io.opentelemetry.context.Context
) : Context {

    override fun <T> createKey(name: String): ContextKey<T> {
        return ContextKeyAdapter(io.opentelemetry.context.ContextKey.named(name))
    }

    override fun <T> get(key: ContextKey<T>): T? {
        return impl.get(io.opentelemetry.context.ContextKey.named(key.name))
    }

    override fun <T> set(key: ContextKey<T>, value: T): Context {
        val keyAdapter = KotlinContextKeyWrapper(key)
        val newCtx = impl.with(keyAdapter, value)
        return KotlinContextWrapper(newCtx)
    }
}
