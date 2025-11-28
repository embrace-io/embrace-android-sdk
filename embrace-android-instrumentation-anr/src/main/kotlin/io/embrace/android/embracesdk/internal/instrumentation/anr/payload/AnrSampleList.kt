package io.embrace.android.embracesdk.internal.instrumentation.anr.payload

/**
 * Holds a list of [AnrSample] objects.
 */
internal data class AnrSampleList(

    /**
     * List of samples.
     */
    val samples: List<AnrSample>,
) {

    /**
     * Retrieves the size of the list.
     */
    internal fun size(): Int = samples.size
}
