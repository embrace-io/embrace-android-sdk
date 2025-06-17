package io.embrace.android.embracesdk.internal.otel.wrapper

import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.context.ContextKey

@OptIn(ExperimentalApi::class)
internal class KotlinContextKeyWrapper<T>(
    val impl: ContextKey<T>,
) : OtelJavaContextKey<T>
