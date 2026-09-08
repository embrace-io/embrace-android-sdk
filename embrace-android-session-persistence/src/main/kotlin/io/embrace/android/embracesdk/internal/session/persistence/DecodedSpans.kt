package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.internal.payload.Span

/**
 * The spans read back from a completed spans file, including the first exception (if any) found when decoding
 */
internal class DecodedSpans(
    private val decoded: MutableList<SpanProto>,
    val corruption: Throwable?,
    val spanLimitReached: Boolean = false,
) {

    val spans: List<SpanProto> get() = decoded

    /**
     * Maps every decoded span to its payload form, releasing each proto as it is mapped so that a
     * whole log is never held in both representations at once. [spans] is left empty.
     */
    fun drainToPayload(): List<Span> {
        val payload = ArrayList<Span>(decoded.size)
        while (decoded.isNotEmpty()) {
            payload.add(decoded.removeAt(decoded.lastIndex).toPayload())
        }
        payload.reverse()
        return payload
    }
}
