package io.embrace.android.embracesdk

import android.annotation.SuppressLint
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import io.embrace.android.embracesdk.annotation.InternalApi

/**
 * Models the different types of Event.
 */
@InternalApi
@SuppressLint("EmbracePublicApiPackageRule")
@JsonClass(generateAdapter = false)
internal enum class EventType(

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
        fun fromSeverity(severity: Severity): EventType {
            return when (severity) {
                Severity.INFO -> INFO_LOG
                Severity.WARNING -> WARNING_LOG
                Severity.ERROR -> ERROR_LOG
            }
        }
    }

    fun getSeverity(): Severity? {
        return when (this) {
            INFO_LOG -> Severity.INFO
            WARNING_LOG -> Severity.WARNING
            ERROR_LOG -> Severity.ERROR
            else -> null
        }
    }
}
