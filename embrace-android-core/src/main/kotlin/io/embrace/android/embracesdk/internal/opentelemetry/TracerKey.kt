package io.embrace.android.embracesdk.internal.opentelemetry

data class TracerKey(
    val instrumentationScopeName: String,
    var instrumentationScopeVersion: String? = null,
    var schemaUrl: String? = null,
)
