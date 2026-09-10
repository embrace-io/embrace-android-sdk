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
        val drained = decoded.size
        val payload = ArrayList<Span>(drained)
        for (i in drained - 1 downTo 0) {
            payload.add(decoded.removeAt(i).toPayload())
        }
        payload.reverse()
        return payload
    }
}
