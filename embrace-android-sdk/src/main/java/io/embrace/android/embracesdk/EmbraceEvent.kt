package io.embrace.android.embracesdk

import com.google.gson.annotations.SerializedName
import io.embrace.android.embracesdk.EmbraceEvent.Type
import io.embrace.android.embracesdk.annotation.InternalApi

/**
 * Wraps the event [Type]. This class is purely used for backwards-compatibility.
 */
internal class EmbraceEvent private constructor() {

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

        @SerializedName("start")
        START("s"),

        @SerializedName("late")
        LATE("l"),

        @SerializedName("interrupt")
        INTERRUPT("i"),

        @SerializedName("crash")
        CRASH("c"),

        @SerializedName("end")
        END("e"),

        @SerializedName("info")
        INFO_LOG("il"),

        @SerializedName("error")
        ERROR_LOG("el"),

        @SerializedName("warning")
        WARNING_LOG("wl"),

        @SerializedName("network")
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
