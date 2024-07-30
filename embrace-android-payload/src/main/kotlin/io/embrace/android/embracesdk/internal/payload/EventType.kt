package io.embrace.android.embracesdk.internal.payload

import android.annotation.SuppressLint
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Models the different types of Event.
 */
@SuppressLint("EmbracePublicApiPackageRule")
@JsonClass(generateAdapter = false)
public enum class EventType(

    /**
     * The abbreviation used in the story ID header when sending the event to the Embrace
     * API using the [ApiClient].
     *
     * @return the abbreviation for the event type
     */
    public val abbreviation: String
) {

    @Json(name = "start")
    START("s"),

    @Json(name = "late")
    LATE("l"),

    @Json(name = "interrupt")
    INTERRUPT("i"),

    @Json(name = "crash")
    CRASH("c"),

    @Json(name = "end")
    END("e"),

    @Json(name = "info")
    INFO_LOG("il"),

    @Json(name = "error")
    ERROR_LOG("el"),

    @Json(name = "warning")
    WARNING_LOG("wl"),

    @Json(name = "network")
    NETWORK_LOG("n")
}
