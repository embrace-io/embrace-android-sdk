package io.embrace.android.embracesdk.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * An occasion where the device reported that the memory is running low, due to a trim memory
 * event being called in [EmbraceActivityService].
 *
 * See: [https://developer.android.com/reference/android/content/ComponentCallbacks2.html.onTrimMemory]
) */
@JsonClass(generateAdapter = true)
internal data class MemoryWarning(

    /**
     * The timestamp at which the memory trim event occurred.
     */
    @Json(name = "ts") val timestamp: Long
)
