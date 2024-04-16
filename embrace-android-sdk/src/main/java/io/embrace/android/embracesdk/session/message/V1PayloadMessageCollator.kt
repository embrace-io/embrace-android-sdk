package io.embrace.android.embracesdk.session.message

import io.embrace.android.embracesdk.anr.AnrOtelMapper
import io.embrace.android.embracesdk.anr.ndk.NativeThreadSamplerService
import io.embrace.android.embracesdk.arch.schema.AppTerminationCause
import io.embrace.android.embracesdk.capture.PerformanceInfoService
import io.embrace.android.embracesdk.capture.crumbs.BreadcrumbService
import io.embrace.android.embracesdk.capture.metadata.MetadataService
import io.embrace.android.embracesdk.capture.startup.StartupService
import io.embrace.android.embracesdk.capture.thermalstate.ThermalStatusService
import io.embrace.android.embracesdk.capture.user.UserService
import io.embrace.android.embracesdk.capture.webview.WebViewService
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.event.EventService
import io.embrace.android.embracesdk.event.LogMessageService
import io.embrace.android.embracesdk.gating.GatingService
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.toOldPayload
import io.embrace.android.embracesdk.internal.spans.CurrentSessionSpan
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanData
import io.embrace.android.embracesdk.internal.spans.SpanRepository
import io.embrace.android.embracesdk.internal.spans.SpanSink
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.logging.InternalErrorService
import io.embrace.android.embracesdk.payload.BetaFeatures
import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.prefs.PreferencesService
import io.embrace.android.embracesdk.session.captureDataSafely
import io.embrace.android.embracesdk.session.properties.SessionPropertiesService

internal class V1PayloadMessageCollator(
    private val gatingService: GatingService,
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
    private val spanRepository: SpanRepository,
    private val spanSink: SpanSink,
    private val currentSessionSpan: CurrentSessionSpan,
    private val sessionPropertiesService: SessionPropertiesService,
    private val startupService: StartupService,
    @Suppress("UnusedPrivateProperty") private val anrOtelMapper: AnrOtelMapper,
    private val logger: InternalEmbraceLogger,
) : PayloadMessageCollator {

    /**
     * Builds a new session object. This should not be sent to the backend but is used
     * to populate essential session information (such as ID), etc
     */
    override fun buildInitialSession(params: InitialEnvelopeParams) = with(params) {
        Session(
            sessionId = currentSessionSpan.getSessionId(),
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
    override fun buildFinalSessionMessage(
        params: FinalEnvelopeParams.SessionParams
    ): SessionMessage = with(params) {
        val base = buildFinalBackgroundActivity(params)
        val startupInfo = getStartupEventInfo(eventService)

        val betaFeatures = when (configService.sdkModeBehavior.isBetaFeaturesEnabled()) {
            false -> null
            else -> BetaFeatures(
                thermalStates = captureDataSafely(logger, thermalStatusService::getCapturedData),
            )
        }

        val endSession = base.copy(
            isEndedCleanly = endType.endedCleanly,
            networkLogIds = captureDataSafely(logger) {
                logMessageService.findNetworkLogIds(
                    initial.startTime,
                    endTime
                )
            },
            properties = captureDataSafely(logger, sessionPropertiesService::getProperties),
            webViewInfo = captureDataSafely(logger, webViewService::getCapturedData),
            terminationTime = terminationTime,
            isReceivedTermination = receivedTermination,
            endTime = endTimeVal,
            sdkStartupDuration = startupService.getSdkStartupDuration(initial.isColdStart),
            startupDuration = startupInfo?.duration,
            startupThreshold = startupInfo?.threshold,
            betaFeatures = betaFeatures,
            symbols = captureDataSafely(logger) { nativeThreadSamplerService?.getNativeSymbols() }
        )
        val envelope = buildWrapperEnvelope(params, endSession, initial.startTime, endTime)
        return gatingService.gateSessionMessage(envelope)
    }

    /**
     * Create the background session message with the current state of the background activity.
     */
    override fun buildFinalBackgroundActivityMessage(
        params: FinalEnvelopeParams.BackgroundActivityParams
    ): SessionMessage {
        val msg = buildFinalBackgroundActivity(params)
        val startTime = msg.startTime
        val endTime = params.endTime
        val envelope = buildWrapperEnvelope(params, msg, startTime, endTime)
        return gatingService.gateSessionMessage(envelope)
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
            eventIds = captureDataSafely(logger) {
                eventService.findEventIdsForSession()
            },
            infoLogIds = captureDataSafely(logger) { logMessageService.findInfoLogIds(startTime, endTime) },
            warningLogIds = captureDataSafely(logger) {
                logMessageService.findWarningLogIds(
                    startTime,
                    endTime
                )
            },
            errorLogIds = captureDataSafely(logger) {
                logMessageService.findErrorLogIds(
                    startTime,
                    endTime
                )
            },
            infoLogsAttemptedToSend = captureDataSafely(logger, logMessageService::getInfoLogsAttemptedToSend),
            warnLogsAttemptedToSend = captureDataSafely(logger, logMessageService::getWarnLogsAttemptedToSend),
            errorLogsAttemptedToSend = captureDataSafely(logger, logMessageService::getErrorLogsAttemptedToSend),
            exceptionError = captureDataSafely(logger, internalErrorService::currentExceptionError),
            lastHeartbeatTime = endTime,
            endType = lifeEventType,
            unhandledExceptions = captureDataSafely(logger, logMessageService::getUnhandledExceptionsSent),
            crashReportId = crashId
        )
    }

    private fun buildWrapperEnvelope(
        params: FinalEnvelopeParams,
        finalPayload: Session,
        startTime: Long,
        endTime: Long,
    ): SessionMessage {
        val spans: List<EmbraceSpanData>? = captureDataSafely(logger) {
            val result = when {
                !params.captureSpans -> null
                !params.isCacheAttempt -> {
                    val appTerminationCause = when {
                        finalPayload.crashReportId != null -> AppTerminationCause.Crash
                        else -> null
                    }
                    val spans = currentSessionSpan.endSession(appTerminationCause)
                    if (appTerminationCause == null) {
                        sessionPropertiesService.populateCurrentSession()
                    }
                    spans
                }
                else -> spanSink.completedSpans()
            }
            // add ANR spans if the payload is capturing spans.
            result?.plus(anrOtelMapper.snapshot().map(Span::toOldPayload)) ?: result
        }
        val breadcrumbs = captureDataSafely(logger) {
            when {
                !params.isCacheAttempt -> breadcrumbService.flushBreadcrumbs()
                else -> breadcrumbService.getBreadcrumbs()
            }
        }
        val spanSnapshots = captureDataSafely(logger) {
            spanRepository.getActiveSpans().mapNotNull { it.snapshot()?.toOldPayload() }
        }

        return SessionMessage(
            session = finalPayload,
            userInfo = captureDataSafely(logger, userService::getUserInfo),
            appInfo = captureDataSafely(logger, metadataService::getAppInfo),
            deviceInfo = captureDataSafely(logger, metadataService::getDeviceInfo),
            performanceInfo = captureDataSafely(logger) {
                performanceInfoService.getSessionPerformanceInfo(
                    startTime,
                    endTime,
                    finalPayload.isColdStart,
                    null
                )
            },
            breadcrumbs = breadcrumbs,
            spans = spans,
            spanSnapshots = spanSnapshots,
        )
    }
}
