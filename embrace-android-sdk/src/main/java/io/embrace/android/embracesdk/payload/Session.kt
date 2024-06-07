package io.embrace.android.embracesdk.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Represents a particular user's session within the app.
 */
@JsonClass(generateAdapter = true)
internal data class Session @JvmOverloads internal constructor(

    /**
     * A unique ID which identifies the session.
     */
    @Json(name = "id")
    val sessionId: String,

    /**
     * The time that the session started.
     */
    @Json(name = "st")
    val startTime: Long,

    /**
     * The time that the session ended.
     */
    @Json(name = "et")
    val endTime: Long? = null,

    @Json(name = "ht")
    val lastHeartbeatTime: Long? = null,

    @Json(name = "tt")
    val terminationTime: Long? = null
) {

    /**
     * Enum to discriminate the different ways a session can start / end
     */
    enum class LifeEventType {

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

    companion object {

        /**
         * Signals to the API that the application was in the foreground.
         */
        internal const val APPLICATION_STATE_FOREGROUND = "foreground"

        /**
         * Signals to the API that this is a background session.
         */
        internal const val APPLICATION_STATE_BACKGROUND = "background"
    }
}
