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
     * The ordinal of the session, starting from 1.
     */
    @Json(name = "sn")
    val number: Int,

    /**
     * Type of the session message (start or end)
     */
    @Json(name = "ty")
    val messageType: String,

    /**
     * Application state for this session (foreground or background)
     */
    @Json(name = "as")
    val appState: String,

    @Json(name = "cs")
    val isColdStart: Boolean,

    /**
     * The time that the session ended.
     */
    @Json(name = "et")
    val endTime: Long? = null,

    @Json(name = "ht")
    val lastHeartbeatTime: Long? = null,

    @Json(name = "tt")
    val terminationTime: Long? = null,

    @Json(name = "ce")
    val isEndedCleanly: Boolean? = null,

    @Json(name = "tr")
    val isReceivedTermination: Boolean? = null,

    @Json(name = "ss")
    val eventIds: List<String>? = null,

    @Json(name = "il")
    val infoLogIds: List<String>? = null,

    @Json(name = "wl")
    val warningLogIds: List<String>? = null,

    @Json(name = "el")
    val errorLogIds: List<String>? = null,

    @Json(name = "nc")
    val networkLogIds: List<String>? = null,

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

    @Json(name = "oc")
    val orientations: List<Orientation>? = null,

    @Json(name = "sp")
    val properties: Map<String, String>? = null,

    @Json(name = "sd")
    val startupDuration: Long? = null,

    @Json(name = "sdt")
    val startupThreshold: Long? = null,

    @Json(name = "si")
    val sdkStartupDuration: Long? = null,

    @Json(name = "ue")
    val unhandledExceptions: Int? = null,

    /**
     * Beta feature data that was captured during this session
     */
    @Json(name = "bf")
    val betaFeatures: BetaFeatures? = null,

    @Json(name = "sb")
    val symbols: Map<String, String>? = null,

    @Json(name = "wvi_beta")
    val webViewInfo: List<WebViewInfo>? = null,

    @Transient
    val user: UserInfo? = null
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
        BKGND_MANUAL,

        @Json(name = "bt")
        BKGND_TIME,

        @Json(name = "be")
        BKGND_SIZE
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

        /**
         * Signals to the API the end of a session.
         */
        internal const val MESSAGE_TYPE_END = "en"
    }
}
