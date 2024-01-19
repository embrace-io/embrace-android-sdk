package io.embrace.android.embracesdk.session

import io.embrace.android.embracesdk.anr.ndk.NativeThreadSamplerService
import io.embrace.android.embracesdk.capture.PerformanceInfoService
import io.embrace.android.embracesdk.capture.crumbs.BreadcrumbService
import io.embrace.android.embracesdk.capture.metadata.MetadataService
import io.embrace.android.embracesdk.capture.thermalstate.ThermalStatusService
import io.embrace.android.embracesdk.capture.user.UserService
import io.embrace.android.embracesdk.capture.webview.WebViewService
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.event.EventService
import io.embrace.android.embracesdk.event.LogMessageService
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.spans.EmbraceAttributes
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanData
import io.embrace.android.embracesdk.internal.spans.SpansService
import io.embrace.android.embracesdk.internal.utils.Uuid
import io.embrace.android.embracesdk.logging.InternalErrorService
import io.embrace.android.embracesdk.payload.BetaFeatures
import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.prefs.PreferencesService
import io.embrace.android.embracesdk.session.properties.SessionPropertiesService

internal class PayloadMessageCollator(
    private val configService: ConfigService,
    private val metadataService: MetadataService,
    private val eventService: EventService,
    private val logMessageService: LogMessageService,
    private val internalErrorService: InternalErrorService,
    private val performanceInfoService: PerformanceInfoService,
    private val webViewService: WebViewService,
    private val thermalStatusService: ThermalStatusService,
    private val nativeThreadSamplerService: NativeThreadSamplerService?,
    private val breadcrumbService: BreadcrumbService,
    private val userService: UserService,
    private val preferencesService: PreferencesService,
    private val spansService: SpansService,
    private val clock: Clock,
    private val sessionPropertiesService: SessionPropertiesService
) {

    /**
     * Builds a new session object. This should not be sent to the backend but is used
     * to populate essential session information (such as ID), etc
     */
    internal fun buildInitialSession(params: InitialEnvelopeParams) = with(params) {
        Session(
            sessionId = Uuid.getEmbUuid(),
            startTime = startTime,
            isColdStart = coldStart,
            messageType = Session.MESSAGE_TYPE_END,
            appState = appState,
            startType = startType,
            number = getSessionNumber(preferencesService),
            properties = getProperties(sessionPropertiesService),
        )
    }

    /**
     * Builds a fully populated session message. This can be sent to the backend (or stored
     * on disk).
     */
    @Suppress("ComplexMethod")
    internal fun buildFinalSessionMessage(
        params: FinalEnvelopeParams,
        endedCleanly: Boolean,
        forceQuit: Boolean,
        sdkStartupDuration: Long
    ): SessionMessage = with(params) { // TODO: future incorporate these fields into params class
        val startTime: Long = initial.startTime

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

        val sdkStartDuration = when (initial.isColdStart) {
            true -> sdkStartupDuration
            false -> null
        }

        val startupEventInfo = captureDataSafely(eventService::getStartupMomentInfo)

        val startupDuration = when (initial.isColdStart && startupEventInfo != null) {
            true -> startupEventInfo.duration
            false -> null
        }
        val startupThreshold = when (initial.isColdStart && startupEventInfo != null) {
            true -> startupEventInfo.threshold
            false -> null
        }

        val betaFeatures = when (configService.sdkModeBehavior.isBetaFeaturesEnabled()) {
            false -> null
            else -> BetaFeatures(
                thermalStates = captureDataSafely(thermalStatusService::getCapturedData),
            )
        }

        val base = buildFinalBackgroundActivity(params)

        val endSession = base.copy(
            isEndedCleanly = endedCleanly,
            networkLogIds = captureDataSafely {
                logMessageService.findNetworkLogIds(
                    startTime,
                    endTime
                )
            },
            properties = captureDataSafely(sessionPropertiesService::getProperties),
            webViewInfo = captureDataSafely(webViewService::getCapturedData),
            terminationTime = terminationTime,
            isReceivedTermination = receivedTermination,
            endTime = endTimeVal,
            sdkStartupDuration = sdkStartDuration,
            startupDuration = startupDuration,
            startupThreshold = startupThreshold,
            betaFeatures = betaFeatures,
            symbols = captureDataSafely { nativeThreadSamplerService?.getNativeSymbols() }
        )
        return buildWrapperEnvelope(params, endSession, startTime, endTime)
    }

    /**
     * Creates a background activity stop message.
     */
    private fun buildFinalBackgroundActivity(
        params: FinalEnvelopeParams
    ): Session = with(params) {
        val startTime = initial.startTime
        return initial.copy(
            endTime = endTime,
            eventIds = captureDataSafely {
                eventService.findEventIdsForSession(
                    startTime,
                    endTime
                )
            },
            infoLogIds = captureDataSafely { logMessageService.findInfoLogIds(startTime, endTime) },
            warningLogIds = captureDataSafely {
                logMessageService.findWarningLogIds(
                    startTime,
                    endTime
                )
            },
            errorLogIds = captureDataSafely {
                logMessageService.findErrorLogIds(
                    startTime,
                    endTime
                )
            },
            infoLogsAttemptedToSend = captureDataSafely(logMessageService::getInfoLogsAttemptedToSend),
            warnLogsAttemptedToSend = captureDataSafely(logMessageService::getWarnLogsAttemptedToSend),
            errorLogsAttemptedToSend = captureDataSafely(logMessageService::getErrorLogsAttemptedToSend),
            exceptionError = captureDataSafely(internalErrorService::currentExceptionError),
            lastHeartbeatTime = endTime,
            endType = lifeEventType,
            unhandledExceptions = captureDataSafely(logMessageService::getUnhandledExceptionsSent),
            crashReportId = crashId
        )
    }

    /**
     * Create the background session message with the current state of the background activity.
     */
    fun buildFinalBackgroundActivityMessage(
        params: FinalEnvelopeParams.BackgroundActivityParams
    ): SessionMessage {
        val msg = buildFinalBackgroundActivity(params)
        val startTime = msg.startTime
        val endTime = params.endTime
        return buildWrapperEnvelope(params, msg, startTime, endTime)
    }

    private fun buildWrapperEnvelope(
        params: FinalEnvelopeParams,
        finalPayload: Session,
        startTime: Long,
        endTime: Long
    ): SessionMessage {
        val spans: List<EmbraceSpanData>? = captureDataSafely {
            when {
                !params.isCacheAttempt -> {
                    val appTerminationCause = when {
                        finalPayload.crashReportId != null -> EmbraceAttributes.AppTerminationCause.CRASH
                        else -> null
                    }
                    spansService.flushSpans(appTerminationCause)
                }
                else -> spansService.completedSpans()
            }
        }
        val breadcrumbs = captureDataSafely {
            when {
                !params.isCacheAttempt -> breadcrumbService.flushBreadcrumbs()
                else -> breadcrumbService.getBreadcrumbs(startTime, endTime)
            }
        }

        return SessionMessage(
            session = finalPayload,
            userInfo = captureDataSafely(userService::getUserInfo),
            appInfo = captureDataSafely(metadataService::getAppInfo),
            deviceInfo = captureDataSafely(metadataService::getDeviceInfo),
            performanceInfo = captureDataSafely {
                performanceInfoService.getSessionPerformanceInfo(
                    startTime,
                    endTime,
                    finalPayload.isColdStart,
                    null
                )
            },
            breadcrumbs = breadcrumbs,
            spans = spans
        )
    }
}
