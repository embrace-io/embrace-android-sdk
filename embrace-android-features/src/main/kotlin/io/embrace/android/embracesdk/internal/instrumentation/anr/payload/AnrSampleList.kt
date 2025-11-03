package io.embrace.android.embracesdk.internal.instrumentation.anr.payload

/**
 * Holds a list of [AnrSample] objects.
 */
data class AnrSampleList(

    /**
     * List of samples.
     */
    val samples: List<AnrSample>,
)
