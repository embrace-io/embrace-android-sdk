package io.embrace.android.embracesdk.minified

/**
 * What [RecordingSpanExporter] captured from a span.
 */
data class ExportedSpan(
    val name: String,

    /**
     * The resource's `telemetry.sdk.language`.
     */
    val sdkLanguage: String?,
)
