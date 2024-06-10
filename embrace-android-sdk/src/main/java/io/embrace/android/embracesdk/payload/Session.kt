package io.embrace.android.embracesdk.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Represents a particular user's session within the app.
 */
@JsonClass(generateAdapter = true)
internal data class Session @JvmOverloads internal constructor(

    /**
     * The time that the session ended.
     */
    @Json(name = "et")
    val endTime: Long? = null,

    @Json(name = "ht")
    val lastHeartbeatTime: Long? = null,

    @Json(name = "tt")
    val terminationTime: Long? = null
)
