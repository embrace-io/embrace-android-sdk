package io.embrace.android.embracesdk.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Holds a list of [AnrSample] objects.
 */
@JsonClass(generateAdapter = true)
internal data class AnrSampleList(

    /**
     * List of samples.
     */
    @Json(name = "ticks")
    val samples: List<AnrSample>
)
