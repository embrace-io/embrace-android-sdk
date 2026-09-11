package io.embrace.android.embracesdk.internal.perfetto

/** The slices sharing one section name, aggregated for the summary. */
internal data class Section(
    val name: String,
    val count: Int,
    val totalNanos: Long,
)
