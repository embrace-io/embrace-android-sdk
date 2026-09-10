package io.embrace.android.embracesdk.internal.session.persistence

/**
 * The spans read back from a completed spans file, including the first exception (if any) found when decoding
 */
internal class DecodedSpans(
    val spans: List<SpanProto>,
    val corruption: Throwable?,
    val spanLimitReached: Boolean = false,
)
