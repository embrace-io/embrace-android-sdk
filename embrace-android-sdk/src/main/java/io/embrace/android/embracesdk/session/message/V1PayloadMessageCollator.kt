package io.embrace.android.embracesdk.session.message

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
import io.embrace.android.embracesdk.internal.spans.CurrentSessionSpan
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanData
import io.embrace.android.embracesdk.internal.spans.SpanSink
import io.embrace.android.embracesdk.internal.utils.Uuid
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
    private val spanSink: SpanSink,
    private val currentSessionSpan: CurrentSessionSpan,
    private val sessionPropertiesService: SessionPropertiesService,
    private val startupService: StartupService
) : PayloadMessageCollator {

    /**
     * Builds a new session object. This should not be sent to the backend but is used
     * to populate essential session information (such as ID), etc
     */
    override fun buildInitialSession(params: InitialEnvelopeParams) = with(params) {
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
    override fun buildFinalSessionMessage(
        params: FinalEnvelopeParams.SessionParams
    ): SessionMessage = with(params) {
        val base = buildFinalBackgroundActivity(params)
        val startupInfo = getStartupEventInfo(eventService)

        val betaFeatures = when (configService.sdkModeBehavior.isBetaFeaturesEnabled()) {
            false -> null
            else -> BetaFeatures(
                thermalStates = captureDataSafely(thermalStatusService::getCapturedData),
            )
        }

        val endSession = base.copy(
            isEndedCleanly = endType.endedCleanly,
            networkLogIds = captureDataSafely {
                logMessageService.findNetworkLogIds(
                    initial.startTime,
                    endTime
                )
            },
            properties = captureDataSafely(sessionPropertiesService::getProperties),
            webViewInfo = captureDataSafely(webViewService::getCapturedData),
            terminationTime = terminationTime,
            isReceivedTermination = receivedTermination,
            endTime = endTimeVal,
            sdkStartupDuration = startupService.getSdkStartupDuration(initial.isColdStart),
            startupDuration = startupInfo?.duration,
            startupThreshold = startupInfo?.threshold,
            betaFeatures = betaFeatures,
            symbols = captureDataSafely { nativeThreadSamplerService?.getNativeSymbols() }
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
            eventIds = captureDataSafely {
                eventService.findEventIdsForSession()
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

    private fun buildWrapperEnvelope(
        params: FinalEnvelopeParams,
        finalPayload: Session,
        startTime: Long,
        endTime: Long,
    ): SessionMessage {
        val spans: List<EmbraceSpanData>? = captureDataSafely {
            when {
                !params.captureSpans -> null
                !params.isCacheAttempt -> {
                    val appTerminationCause = when {
                        finalPayload.crashReportId != null -> AppTerminationCause.Crash
                        else -> null
                    }
                    currentSessionSpan.endSession(appTerminationCause)
                }

                else -> spanSink.completedSpans()
            }
        }
        val breadcrumbs = captureDataSafely {
            when {
                !params.isCacheAttempt -> breadcrumbService.flushBreadcrumbs()
                else -> breadcrumbService.getBreadcrumbs()
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
