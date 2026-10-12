package io.embrace.android.embracesdk.internal.arch.ui

import io.embrace.android.embracesdk.internal.utils.event.EventKey

/**
 * A tap on a named UI element, emitted by whichever UI framework integration observed it.
 */
data class TapSignal(
    val elementName: String,
    val x: Float,
    val y: Float,
) {
    companion object {
        /**
         * The channel every tap is emitted on, shared by every UI framework integration and consumer of them.
         */
        val KEY = EventKey<TapSignal>()
    }
}
