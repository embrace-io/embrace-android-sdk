package io.embrace.android.embracesdk.internal.otel.attrs

import io.opentelemetry.api.common.AttributeKey

/**
 * Converts an [EmbraceAttributeKey] to an OTel Java [AttributeKey].
 */
fun EmbraceAttributeKey.asOtelAttributeKey(): AttributeKey<String> = AttributeKey.stringKey(name)
