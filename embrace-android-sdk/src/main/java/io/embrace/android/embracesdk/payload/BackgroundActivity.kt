package io.embrace.android.embracesdk.payload

import com.google.gson.annotations.SerializedName

/**
 * Represents a particular user's session within the app.
 */
internal data class BackgroundActivity(

    /**
     * A unique ID which identifies the session.
     */
    @SerializedName("id")
    val sessionId: String,

    /**
     * The time that the session started.
     */
    @SerializedName("st")
    val startTime: Long?,

    /**
     * Application state for this session (foreground or background)
     */
    @SerializedName("as")
    val appState: String?,

    /**
     * The time that the session ended.
     */
    @SerializedName("et")
    val endTime: Long? = null,

    /**
     * The ordinal of the session, starting from 1.
     */
    @SerializedName("sn")
    val number: Int? = null,

    /**
     * Type of the session message (start or end)
     */
    @SerializedName("ty")
    val messageType: String? = null,

    @SerializedName("ht")
    val lastHeartbeatTime: Long? = null,

    @SerializedName("ls")
    val lastState: String? = null,

    @SerializedName("ba")
    val startingBatteryLevel: Double? = null,

    @SerializedName("cs")
    val isColdStart: Boolean? = null,

    @SerializedName("ss")
    val eventIds: List<String>? = null,

    @SerializedName("il")
    val infoLogIds: List<String>? = null,

    @SerializedName("wl")
    val warningLogIds: List<String>? = null,

    @SerializedName("el")
    val errorLogIds: List<String>? = null,

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
    val endType: LifeEventType? = null,

    @SerializedName("sm")
    val startType: LifeEventType? = null,

    @SerializedName("sp")
    val properties: Map<String, String>? = null,

    @SerializedName("ue")
    val unhandledExceptions: Int? = null,

    @Transient
    val user: UserInfo? = null
) {

    /**
     * Enum to discriminate the different ways a background session can start / end
     */
    internal enum class LifeEventType {
        @SerializedName("bs")
        BKGND_STATE,

        @SerializedName("bm")
        BKGND_MANUAL,

        @SerializedName("bt")
        BKGND_TIME,

        @SerializedName("be")
        BKGND_SIZE
    }

    companion object {

        @JvmStatic
        fun createStartMessage(
            embUuid: String,
            startTime: Long,
            coldStart: Boolean,
            startType: LifeEventType,
            applicationState: String,
            userInfo: UserInfo?
        ) = BackgroundActivity(
            sessionId = embUuid,
            startTime = startTime,
            appState = applicationState,
            isColdStart = coldStart,
            startType = startType,
            user = userInfo
        )

        @JvmStatic
        @Suppress("LongParameterList")
        fun createStopMessage(
            original: BackgroundActivity,
            applicationState: String,
            messageType: String,
            endTime: Long?,
            eventIdsForSession: List<String>,
            infoLogIds: List<String>,
            warningLogIds: List<String>,
            errorLogIds: List<String>,
            infoLogsAttemptedToSend: Int,
            warnLogsAttemptedToSend: Int,
            errorLogsAttemptedToSend: Int,
            currentExceptionError: ExceptionError?,
            lastHeartbeatTime: Long,
            endType: LifeEventType?,
            unhandledExceptionsSent: Int,
            crashId: String?
        ) = original.copy(
            appState = applicationState,
            messageType = messageType,
            endTime = endTime,
            eventIds = eventIdsForSession,
            infoLogIds = infoLogIds,
            warningLogIds = warningLogIds,
            errorLogIds = errorLogIds,
            infoLogsAttemptedToSend = infoLogsAttemptedToSend,
            warnLogsAttemptedToSend = warnLogsAttemptedToSend,
            errorLogsAttemptedToSend = errorLogsAttemptedToSend,
            exceptionError = currentExceptionError,
            lastHeartbeatTime = lastHeartbeatTime,
            endType = endType,
            unhandledExceptions = unhandledExceptionsSent,
            crashReportId = crashId
        )
    }
}
