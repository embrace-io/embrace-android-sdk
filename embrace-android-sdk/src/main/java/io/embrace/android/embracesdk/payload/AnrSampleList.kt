package io.embrace.android.embracesdk.payload

/**
 * Holds a list of [AnrSample] objects.
 */
internal data class AnrSampleList(

    /**
     * List of samples.
     */
    val samples: List<AnrSample>
)
