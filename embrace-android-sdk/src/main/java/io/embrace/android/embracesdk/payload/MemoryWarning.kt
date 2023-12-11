package io.embrace.android.embracesdk.payload

import com.google.gson.annotations.SerializedName

/**
 * An occasion where the device reported that the memory is running low, due to a trim memory
 * event being called in [EmbraceActivityService].
 *
 * See: [https://developer.android.com/reference/android/content/ComponentCallbacks2.html.onTrimMemory]
) */
internal data class MemoryWarning(

    /**
     * The timestamp at which the memory trim event occurred.
     */
    @SerializedName("ts") val timestamp: Long
)
