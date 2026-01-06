package io.embrace.android.embracesdk.internal.otel.sdk

data class ApiKey(
    val instrumentationScopeName: String,
    var instrumentationScopeVersion: String? = null,
    var schemaUrl: String? = null,
)
