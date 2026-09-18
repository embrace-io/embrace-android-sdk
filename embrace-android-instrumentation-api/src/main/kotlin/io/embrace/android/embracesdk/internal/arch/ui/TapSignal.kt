package io.embrace.android.embracesdk.internal.arch.ui

/**
 * A tap on a named UI element, emitted by whichever UI framework integration observed it.
 */
data class TapSignal(
    val elementName: String,
    val x: Float,
    val y: Float,
)
