package io.embrace.android.embracesdk.payload

import com.google.gson.annotations.SerializedName
import io.embrace.android.embracesdk.InternalApi
import io.embrace.android.embracesdk.session.EmbraceSessionService
import io.embrace.android.embracesdk.session.MESSAGE_TYPE_START

/**
 * Represents a particular user's session within the app.
 */
@InternalApi
internal data class Session @JvmOverloads internal constructor(

    /**
     * A unique ID which identifies the session.
     */
    @SerializedName("id")
    val sessionId: String,

    /**
     * The time that the session started.
     */
    @SerializedName("st")
    val startTime: Long,

    /**
     * The ordinal of the session, starting from 1.
     */
    @SerializedName("sn")
    val number: Int,

    /**
     * Type of the session message (start or end)
     */
    @SerializedName("ty")
    val messageType: String,

    /**
     * Application state for this session (foreground or background)
     */
    @SerializedName("as")
    val appState: String,

    @SerializedName("cs")
    val isColdStart: Boolean,

    /**
     * The time that the session ended.
     */
    @SerializedName("et")
    val endTime: Long? = null,

    @SerializedName("ht")
    val lastHeartbeatTime: Long? = null,

    @SerializedName("tt")
    val terminationTime: Long? = null,

    @SerializedName("ce")
    val isEndedCleanly: Boolean? = null,

    @SerializedName("tr")
    val isReceivedTermination: Boolean? = null,

    @SerializedName("ss")
    val eventIds: List<String>? = null,

    @SerializedName("il")
    val infoLogIds: List<String>? = null,

    @SerializedName("wl")
    val warningLogIds: List<String>? = null,

    @SerializedName("el")
    val errorLogIds: List<String>? = null,

    @SerializedName("nc")
    val networkLogIds: List<String>? = null,

    @SerializedName("lic")
    val infoLogsAttemptedToSend: Int? = null,

    @SerializedName("lwc")
    val warnLogsAttemptedToSend: Int? = null,

    @SerializedName("lec")
    val errorLogsAttemptedToSend: Int? = null,

    @SerializedName("e")
    val exceptionError: ExceptionError? = null,

    @SerializedName("ri")
    val crashReportId: String? = null,

    @SerializedName("em")
    val endType: SessionLifeEventType? = null,

    @SerializedName("sm")
    val startType: SessionLifeEventType? = null,

    @SerializedName("oc")
    val orientations: List<Orientation>? = null,

    @SerializedName("sp")
    val properties: Map<String, String>? = null,

    @SerializedName("sd")
    val startupDuration: Long? = null,

    @SerializedName("sdt")
    val startupThreshold: Long? = null,

    @SerializedName("si")
    val sdkStartupDuration: Long? = null,

    @SerializedName("ue")
    val unhandledExceptions: Int? = null,

    /**
     * Beta feature data that was captured during this session
     */
    @SerializedName("bf")
    val betaFeatures: BetaFeatures? = null,

    @SerializedName("sb")
    val symbols: Map<String, String>? = null,

    @SerializedName("wvi_beta")
    val webViewInfo: List<WebViewInfo>? = null,

    @Transient
    val user: UserInfo? = null
) {

    /**
     * Enum to discriminate the different ways a session can start / end
     */
    enum class SessionLifeEventType {
        @SerializedName("s")
        STATE, @SerializedName("m")
        MANUAL, @SerializedName("t")
        TIMED
    }

    companion object {

        @JvmStatic
        fun buildStartSession(
            id: String,
            coldStart: Boolean,
            startType: SessionLifeEventType,
            startTime: Long,
            sessionNumber: Int,
            userInfo: UserInfo?,
            sessionProperties: Map<String, String>
        ): Session = Session(
            sessionId = id,
            startTime = startTime,
            number = sessionNumber,
            appState = EmbraceSessionService.APPLICATION_STATE_FOREGROUND,
            isColdStart = coldStart,
            startType = startType,
            properties = sessionProperties,
            messageType = MESSAGE_TYPE_START,
            user = userInfo
        )
    }
}
