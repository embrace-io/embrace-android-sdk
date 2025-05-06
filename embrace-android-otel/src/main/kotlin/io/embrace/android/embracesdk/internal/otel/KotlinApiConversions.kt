package io.embrace.android.embracesdk.internal.otel

import io.embrace.opentelemetry.kotlin.StatusCode
import io.opentelemetry.api.common.AttributeKey

internal fun StatusCode.toOtelJava(): io.opentelemetry.api.trace.StatusCode = when (this) {
    is StatusCode.Unset -> io.opentelemetry.api.trace.StatusCode.UNSET
    is StatusCode.Ok -> io.opentelemetry.api.trace.StatusCode.OK
    is StatusCode.Error -> io.opentelemetry.api.trace.StatusCode.ERROR
}

internal fun io.opentelemetry.api.trace.StatusCode.toOtelKotlin(): StatusCode = when (this) {
    io.opentelemetry.api.trace.StatusCode.UNSET -> StatusCode.Unset
    io.opentelemetry.api.trace.StatusCode.OK -> StatusCode.Ok
    io.opentelemetry.api.trace.StatusCode.ERROR -> StatusCode.Error(null)
}

internal fun String.toOtelJavaAttributeKey() = AttributeKey.stringKey(this)
