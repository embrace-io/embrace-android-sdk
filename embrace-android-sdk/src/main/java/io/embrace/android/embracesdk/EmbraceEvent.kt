package io.embrace.android.embracesdk

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import io.embrace.android.embracesdk.EmbraceEvent.Type
import io.embrace.android.embracesdk.annotation.InternalApi

/**
 * Wraps the event [Type]. This class is purely used for backwards-compatibility.
 */
@JsonClass(generateAdapter = true)
internal class EmbraceEvent internal constructor() {

    /**
     * This actually belongs in [Event], but to maintain backwards-compatibility of the API,
     * this enum has been moved here rather than making [Event] public.
     */
    @InternalApi
    enum class Type(

        /**
         * The abbreviation used in the story ID header when sending the event to the Embrace
         * API using the [ApiClient].
         *
         * @return the abbreviation for the event type
         */
        val abbreviation: String
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
        NETWORK_LOG("n");

        companion object {
            fun fromSeverity(severity: Severity): Type {
                return when (severity) {
                    Severity.INFO -> INFO_LOG
                    Severity.WARNING -> WARNING_LOG
                    Severity.ERROR -> ERROR_LOG
                }
            }
        }
    }
}
