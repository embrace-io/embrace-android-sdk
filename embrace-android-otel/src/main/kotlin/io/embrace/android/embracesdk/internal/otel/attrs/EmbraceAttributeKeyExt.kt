package io.embrace.android.embracesdk.internal.otel.attrs

import io.embrace.opentelemetry.kotlin.aliases.OtelJavaAttributeKey

/**
 * Converts an [EmbraceAttributeKey] to an OTel Java [AttributeKey].
 */
fun EmbraceAttributeKey.asOtelAttributeKey(): OtelJavaAttributeKey<String> = OtelJavaAttributeKey.stringKey(name)
