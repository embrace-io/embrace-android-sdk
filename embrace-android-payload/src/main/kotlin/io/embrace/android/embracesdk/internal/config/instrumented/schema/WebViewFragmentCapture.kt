package io.embrace.android.embracesdk.internal.config.instrumented.schema

/**
 * How the fragment of a webview URL (the part after the '#') should be treated when it is captured.
 *
 * The Gradle plugin selects a constant by name, so renaming one is a breaking change.
 */
enum class WebViewFragmentCapture {

    /** Capture the fragment unaltered. */
    KEEP,

    /** Drop the values from any key/value pairs and any long unstructured segment. */
    REDACT,

    /** Do not capture the fragment at all. */
    REMOVE,
}
