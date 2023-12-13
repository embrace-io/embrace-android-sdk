package io.embrace.android.embracesdk.payload

import com.google.gson.annotations.SerializedName

/**
 * This part of the session payload contains data that is collected from beta features.
 *
 * Putting it in this class segregates it from the rest of the payload & makes it obvious
 * where we should be querying information. Once the beta features are promoted to stable
 * features we should move the functionality into a different location.
 */
internal data class BetaFeatures(

    @SerializedName("ts")
    internal var thermalStates: List<ThermalState>? = null
)
