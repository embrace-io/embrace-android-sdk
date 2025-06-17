package io.embrace.android.embracesdk.internal.otel.wrapper

import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.context.Context
import io.embrace.opentelemetry.kotlin.context.ContextKey
import io.embrace.opentelemetry.kotlin.k2j.context.ContextKeyAdapter

@OptIn(ExperimentalApi::class)
class KotlinContextWrapper(
    private val impl: OtelJavaContext
) : Context {

    override fun <T> createKey(name: String): ContextKey<T> {
        return ContextKeyAdapter(OtelJavaContextKey.named(name))
    }

    override fun <T> get(key: ContextKey<T>): T? {
        // FIXME: creates a new key each time
        return impl.get(OtelJavaContextKey.named(key.name))
    }

    override fun <T> set(key: ContextKey<T>, value: T): Context {
        // FIXME: creates a new key each time
        val keyAdapter = KotlinContextKeyWrapper(key)
        val newCtx = impl.with(keyAdapter, value)
        return KotlinContextWrapper(newCtx)
    }
}
