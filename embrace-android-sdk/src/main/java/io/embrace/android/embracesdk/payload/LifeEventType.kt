package io.embrace.android.embracesdk.payload

import com.squareup.moshi.Json

/**
 * Enum to discriminate the different ways a session can start / end
 */
internal enum class LifeEventType {

    /* Session values */

    @Json(name = "s")
    STATE,

    @Json(name = "m")
    MANUAL,

    /* Background activity values */

    @Json(name = "bs")
    BKGND_STATE,

    @Json(name = "bm")
    BKGND_MANUAL
}
