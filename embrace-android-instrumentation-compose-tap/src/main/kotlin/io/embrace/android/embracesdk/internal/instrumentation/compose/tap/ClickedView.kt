package io.embrace.android.embracesdk.internal.instrumentation.compose.tap

/**
 * Represents the clicked element to log
 */
internal data class ClickedView(
    val tag: String,
    val x: Float,
    val y: Float,
)
