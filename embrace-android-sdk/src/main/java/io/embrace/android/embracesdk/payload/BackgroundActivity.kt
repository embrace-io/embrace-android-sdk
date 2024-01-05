package io.embrace.android.embracesdk.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Represents a particular user's session within the app.
 */
@JsonClass(generateAdapter = true)
internal data class BackgroundActivity(

    /**
     * A unique ID which identifies the session.
     */
    @Json(name = "id")
    val sessionId: String,

    /**
     * The time that the session started.
     */
    @Json(name = "st")
    val startTime: Long?,

    /**
     * Application state for this session (foreground or background)
     */
    @Json(name = "as")
    val appState: String?,

    /**
     * The time that the session ended.
     */
    @Json(name = "et")
    val endTime: Long? = null,

    /**
     * The ordinal of the background activity, starting from 1.
     */
    @Json(name = "sn")
    val number: Int? = null,

    /**
     * Type of the session message (start or end)
     */
    @Json(name = "ty")
    val messageType: String? = null,

    @Json(name = "ht")
    val lastHeartbeatTime: Long? = null,

    @Json(name = "ls")
    val lastState: String? = null,

    @Json(name = "ba")
    val startingBatteryLevel: Double? = null,

    @Json(name = "cs")
    val isColdStart: Boolean? = null,

    @Json(name = "ss")
    val eventIds: List<String>? = null,

    @Json(name = "il")
    val infoLogIds: List<String>? = null,

    @Json(name = "wl")
    val warningLogIds: List<String>? = null,

    @Json(name = "el")
    val errorLogIds: List<String>? = null,

    @Json(name = "lic")
    val infoLogsAttemptedToSend: Int? = null,

    @Json(name = "lwc")
    val warnLogsAttemptedToSend: Int? = null,

    @Json(name = "lec")
    val errorLogsAttemptedToSend: Int? = null,

    @Json(name = "e")
    val exceptionError: ExceptionError? = null,

    @Json(name = "ri")
    val crashReportId: String? = null,

    @Json(name = "em")
    val endType: LifeEventType? = null,

    @Json(name = "sm")
    val startType: LifeEventType? = null,

    @Json(name = "sp")
    val properties: Map<String, String>? = null,

    @Json(name = "ue")
    val unhandledExceptions: Int? = null,

    @Transient
    val user: UserInfo? = null
) {

    /**
     * Enum to discriminate the different ways a background session can start / end
     */
    internal enum class LifeEventType {
        @Json(name = "bs")
        BKGND_STATE,

        @Json(name = "bm")
        BKGND_MANUAL,

        @Json(name = "bt")
        BKGND_TIME,

        @Json(name = "be")
        BKGND_SIZE
    }
}
