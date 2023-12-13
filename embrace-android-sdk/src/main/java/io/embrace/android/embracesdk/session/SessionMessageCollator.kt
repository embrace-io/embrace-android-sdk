package io.embrace.android.embracesdk.session

import io.embrace.android.embracesdk.anr.ndk.NativeThreadSamplerService
import io.embrace.android.embracesdk.capture.PerformanceInfoService
import io.embrace.android.embracesdk.capture.crumbs.BreadcrumbService
import io.embrace.android.embracesdk.capture.metadata.MetadataService
import io.embrace.android.embracesdk.capture.thermalstate.ThermalStatusService
import io.embrace.android.embracesdk.capture.user.UserService
import io.embrace.android.embracesdk.capture.webview.WebViewService
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.event.EmbraceRemoteLogger
import io.embrace.android.embracesdk.event.EventService
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanData
import io.embrace.android.embracesdk.logging.EmbraceInternalErrorService
import io.embrace.android.embracesdk.payload.BetaFeatures
import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.payload.UserInfo
import io.embrace.android.embracesdk.session.properties.EmbraceSessionProperties

internal class SessionMessageCollator(
    private val configService: ConfigService,
    private val metadataService: MetadataService,
    private val eventService: EventService,
    private val remoteLogger: EmbraceRemoteLogger,
    private val exceptionService: EmbraceInternalErrorService,
    private val performanceInfoService: PerformanceInfoService,
    private val webViewService: WebViewService,
    private val thermalStatusService: ThermalStatusService,
    private val nativeThreadSamplerService: NativeThreadSamplerService?,
    private val breadcrumbService: BreadcrumbService,
    private val userService: UserService,
    private val clock: Clock
) {

    @Suppress("ComplexMethod")
    internal fun buildEndSessionMessage(
        originSession: Session,
        endedCleanly: Boolean,
        forceQuit: Boolean,
        crashId: String?,
        endType: Session.SessionLifeEventType,
        sessionProperties: EmbraceSessionProperties,
        sdkStartupDuration: Long,
        endTime: Long,
        spans: List<EmbraceSpanData>? = null
    ): SessionMessage {
        val startTime: Long = originSession.startTime

        // if it's a crash session, then add the stacktrace to the session payload
        val crashReportId = when {
            !crashId.isNullOrEmpty() -> crashId
            else -> null
        }
        val terminationTime = when {
            forceQuit -> endTime
            else -> null
        }
        val receivedTermination = when {
            forceQuit -> true
            else -> null
        }
        // We don't set end time for force-quit, as the API interprets this to be a clean
        // termination
        val endTimeVal = when {
            forceQuit -> null
            else -> endTime
        }

        val sdkStartDuration = when (originSession.isColdStart) {
            true -> sdkStartupDuration
            false -> null
        }

        val startupEventInfo = captureDataSafely(eventService::getStartupMomentInfo)

        val startupDuration = when (originSession.isColdStart && startupEventInfo != null) {
            true -> startupEventInfo.duration
            false -> null
        }
        val startupThreshold = when (originSession.isColdStart && startupEventInfo != null) {
            true -> startupEventInfo.threshold
            false -> null
        }

        val betaFeatures = when (configService.sdkModeBehavior.isBetaFeaturesEnabled()) {
            false -> null
            else -> BetaFeatures(
                thermalStates = captureDataSafely(thermalStatusService::getCapturedData),
            )
        }

        val endSession = originSession.copy(
            isEndedCleanly = endedCleanly,
            appState = Session.APPLICATION_STATE_FOREGROUND,
            messageType = MESSAGE_TYPE_END,
            eventIds = captureDataSafely { eventService.findEventIdsForSession(startTime, endTime) },
            infoLogIds = captureDataSafely { remoteLogger.findInfoLogIds(startTime, endTime) },
            warningLogIds = captureDataSafely { remoteLogger.findWarningLogIds(startTime, endTime) },
            errorLogIds = captureDataSafely { remoteLogger.findErrorLogIds(startTime, endTime) },
            networkLogIds = captureDataSafely { remoteLogger.findNetworkLogIds(startTime, endTime) },
            infoLogsAttemptedToSend = captureDataSafely(remoteLogger::getInfoLogsAttemptedToSend),
            warnLogsAttemptedToSend = captureDataSafely(remoteLogger::getWarnLogsAttemptedToSend),
            errorLogsAttemptedToSend = captureDataSafely(remoteLogger::getErrorLogsAttemptedToSend),
            lastHeartbeatTime = clock.now(),
            properties = captureDataSafely(sessionProperties::get),
            endType = endType,
            unhandledExceptions = captureDataSafely(remoteLogger::getUnhandledExceptionsSent),
            webViewInfo = captureDataSafely(webViewService::getCapturedData),
            crashReportId = crashReportId,
            terminationTime = terminationTime,
            isReceivedTermination = receivedTermination,
            endTime = endTimeVal,
            sdkStartupDuration = sdkStartDuration,
            startupDuration = startupDuration,
            startupThreshold = startupThreshold,
            user = captureDataSafely(userService::getUserInfo),
            betaFeatures = betaFeatures,
            symbols = captureDataSafely { nativeThreadSamplerService?.getNativeSymbols() }
        )

        val performanceInfo = performanceInfoService.getSessionPerformanceInfo(
            startTime,
            endTime,
            originSession.isColdStart,
            originSession.isReceivedTermination
        )

        val appInfo = captureDataSafely(metadataService::getAppInfo)
        val deviceInfo = captureDataSafely(metadataService::getDeviceInfo)
        val breadcrumbs = captureDataSafely { breadcrumbService.getBreadcrumbs(startTime, endTime) }

        val endSessionWithAllErrors =
            endSession.copy(exceptionError = exceptionService.currentExceptionError)

        return SessionMessage(
            session = endSessionWithAllErrors,
            userInfo = endSessionWithAllErrors.user,
            appInfo = appInfo,
            deviceInfo = deviceInfo,
            performanceInfo = performanceInfo.copy(),
            breadcrumbs = breadcrumbs,
            spans = spans
        )
    }

    internal fun buildInitialSessionMessage(session: Session) = SessionMessage(
        session = session,
        appInfo = captureDataSafely(metadataService::getAppInfo),
        deviceInfo = captureDataSafely(metadataService::getDeviceInfo)
    )

    internal fun buildInitialSession(
        id: String,
        coldStart: Boolean,
        startType: Session.SessionLifeEventType,
        startTime: Long,
        sessionNumber: Int,
        userInfo: UserInfo?,
        sessionProperties: Map<String, String>
    ): Session = Session(
        sessionId = id,
        startTime = startTime,
        number = sessionNumber,
        appState = Session.APPLICATION_STATE_FOREGROUND,
        isColdStart = coldStart,
        startType = startType,
        properties = sessionProperties,
        messageType = MESSAGE_TYPE_START,
        user = userInfo
    )
}

/**
 * Signals to the API the start of a session.
 */
internal const val MESSAGE_TYPE_START = "st"

/**
 * Signals to the API the end of a session.
 */
internal const val MESSAGE_TYPE_END = "en"
