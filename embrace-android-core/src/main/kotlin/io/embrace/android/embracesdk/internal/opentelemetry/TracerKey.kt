package io.embrace.android.embracesdk.internal.opentelemetry

internal data class TracerKey(
    val instrumentationScopeName: String,
    var instrumentationScopeVersion: String? = null,
    var schemaUrl: String? = null
)
