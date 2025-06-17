package io.embrace.android.embracesdk.internal.otel.wrapper

internal typealias OtelJavaContextKey<T> = io.opentelemetry.context.ContextKey<T>
internal typealias OtelJavaSpanContext = io.opentelemetry.api.trace.SpanContext
internal typealias OtelJavaContext = io.opentelemetry.context.Context
internal typealias OtelJavaTraceFlags = io.opentelemetry.api.trace.TraceFlags
internal typealias OtelJavaTraceState = io.opentelemetry.api.trace.TraceState
internal typealias OtelJavaStatusCode = io.opentelemetry.api.trace.StatusCode
internal typealias OtelJavaSpanKind = io.opentelemetry.api.trace.SpanKind
