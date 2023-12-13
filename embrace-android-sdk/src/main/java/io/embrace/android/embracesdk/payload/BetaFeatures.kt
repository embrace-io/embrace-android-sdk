package io.embrace.android.embracesdk.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * This part of the session payload contains data that is collected from beta features.
 *
 * Putting it in this class segregates it from the rest of the payload & makes it obvious
 * where we should be querying information. Once the beta features are promoted to stable
 * features we should move the functionality into a different location.
 */
@JsonClass(generateAdapter = true)
internal data class BetaFeatures(

    @Json(name = "ts")
    internal val thermalStates: List<ThermalState>? = null
)
