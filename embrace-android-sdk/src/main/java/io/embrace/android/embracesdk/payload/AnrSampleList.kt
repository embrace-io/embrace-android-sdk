package io.embrace.android.embracesdk.payload

import com.google.gson.annotations.SerializedName

/**
 * Holds a list of [AnrSample] objects.
 */
internal data class AnrSampleList(

    /**
     * List of samples.
     */
    @SerializedName("ticks")
    val samples: List<AnrSample>
)
